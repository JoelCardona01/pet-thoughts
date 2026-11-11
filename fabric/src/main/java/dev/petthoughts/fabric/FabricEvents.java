package dev.petthoughts.fabric;

import dev.petthoughts.core.Personality;
import dev.petthoughts.core.bubble.Names;
import dev.petthoughts.core.platform.OwnerView;
import dev.petthoughts.core.platform.PetView;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Translates game events into the shared {@code PetEvents}. Fabric API covers most of them; the
 * rest (taming, eating, fishing, advancements, breeding, explosions, the petting key) come from
 * the mixins in {@code dev.petthoughts.fabric.mixin}, which call the static methods at the bottom.
 */
public final class FabricEvents {

    private static final double PET_REACH = 4.0;
    private static final Map<UUID, Long> LAST_PET = new HashMap<>();

    private FabricEvents() {
    }

    static void register(PetThoughtsMod mod) {
        ServerPlayerEvents.JOIN.register(player ->
                mod.setHidden(player.getUUID(), Boolean.TRUE.equals(player.getAttached(FabricPet.HIDDEN))));
        ServerPlayerEvents.LEAVE.register(player -> {
            mod.setHidden(player.getUUID(), false);
            mod.director().forgetOwner(player.getUUID());
            LAST_PET.remove(player.getUUID());
        });

        // A bubble that got saved with the world (crash, forced quit) is removed when it loads again.
        ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
            if (entity instanceof Display.TextDisplay && entity.entityTags().contains(FabricBubbles.TAG)
                    && !mod.bubbles().owns(entity)) {
                level.getServer().execute(entity::discard);
            }
        });

        UseEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
            if (level.isClientSide() || !(player instanceof ServerPlayer serverPlayer)) {
                return InteractionResult.PASS;
            }
            FabricPet pet = FabricPet.of(mod, entity);
            if (pet == null || !player.getUUID().equals(pet.ownerId()) || !mod.settings().speciesEnabled(pet.species())) {
                return InteractionResult.PASS;
            }
            // Petting: sneak + empty main hand. Consuming the interaction keeps the pet from sitting/standing.
            if (mod.settings().pettingSneakClick && player.isShiftKeyDown() && player.getMainHandItem().isEmpty()
                    && !pet.species().rideable()) {
                if (hand == InteractionHand.MAIN_HAND) {
                    pet(mod, serverPlayer, pet);
                }
                return InteractionResult.SUCCESS;
            }
            // Feeding: check a tick later whether the food was really eaten.
            ItemStack held = player.getItemInHand(hand);
            String food = FabricPet.itemId(held);
            if (pet.species().eats(food)) {
                int before = held.getCount();
                mod.later(pet, 1, () -> {
                    ItemStack after = player.getItemInHand(hand);
                    if (player.hasInfiniteMaterials() || !FabricPet.itemId(after).equals(food) || after.getCount() < before) {
                        mod.events().fed(pet, FabricPet.owner(serverPlayer), food);
                    }
                });
            }
            return InteractionResult.PASS;
        });

        ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, baseDamage, damageTaken, blocked) -> {
            if (damageTaken <= 0 || !entity.isAlive()) {
                return;
            }
            if (entity instanceof ServerPlayer player) {
                if (damageTaken >= 2) {
                    mod.events().ownerHurt(FabricPet.owner(player));
                }
                return;
            }
            FabricPet pet = FabricPet.of(mod, entity);
            ServerPlayer owner = ownerOf(mod, pet);
            if (owner != null) {
                Entity attacker = source.getEntity();
                mod.events().petHurt(pet, FabricPet.owner(owner), attacker != null && attacker.getUUID().equals(owner.getUUID()));
            }
        });

        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            if (entity instanceof ServerPlayer player) {
                mod.events().ownerDied(FabricPet.owner(player));
                return;
            }
            FabricPet deadPet = FabricPet.of(mod, entity);
            if (deadPet != null) {
                mod.events().petDied(deadPet);
                return;
            }
            if (!(entity instanceof Enemy) || !Names.scary(FabricText.typeId(entity))) {
                return;
            }
            String mobName = FabricText.entityName(mod.lang(), entity);
            Entity killer = source.getEntity();
            FabricPet killerPet = killer == null ? null : FabricPet.of(mod, killer);
            ServerPlayer petOwner = ownerOf(mod, killerPet);
            if (petOwner != null) {
                mod.events().petKilledMob(killerPet, FabricPet.owner(petOwner), mobName);
            } else if (killer instanceof ServerPlayer player) {
                mod.events().ownerKilledMob(FabricPet.owner(player), mobName);
            }
        });

        PlayerBlockBreakEvents.AFTER.register((level, player, pos, state, blockEntity) -> {
            if (player instanceof ServerPlayer serverPlayer) {
                mod.events().ownerBrokeBlock(FabricPet.owner(serverPlayer),
                        BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath());
            }
        });

        UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
            if (!level.isClientSide() && player instanceof ServerPlayer serverPlayer
                    && level.getBlockState(hit.getBlockPos()).is(Blocks.JUKEBOX)
                    && player.getItemInHand(hand).has(DataComponents.JUKEBOX_PLAYABLE)) {
                BlockPos pos = hit.getBlockPos();
                mod.events().ownerPlayedMusic(FabricPet.owner(serverPlayer), pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
            }
            return InteractionResult.PASS;
        });
    }

    /** The online owner of a wrapped pet, or null. */
    static ServerPlayer ownerOf(PetThoughtsMod mod, PetView pet) {
        if (pet == null || pet.ownerId() == null || mod.server() == null) {
            return null;
        }
        return mod.server().getPlayerList().getPlayer(pet.ownerId());
    }

    /** The supported tamed pet the player is looking at within {@code reach} blocks, or null. */
    static FabricPet lookedAt(PetThoughtsMod mod, ServerPlayer player, double reach) {
        Vec3 eye = player.getEyePosition(1f);
        Vec3 view = player.getViewVector(1f).scale(reach);
        AABB box = player.getBoundingBox().expandTowards(view).inflate(1.0);
        EntityHitResult hit = ProjectileUtil.getEntityHitResult(
                player, eye, eye.add(view), box, entity -> !entity.isSpectator() && entity.isPickable(), reach * reach);
        return hit == null ? null : FabricPet.of(mod, hit.getEntity());
    }

    private static void pet(PetThoughtsMod mod, ServerPlayer player, FabricPet pet) {
        // Holding the key repeats the action: at most two pets per second.
        long now = System.currentTimeMillis();
        Long last = LAST_PET.put(player.getUUID(), now);
        if (last != null && now - last < 500) {
            return;
        }
        LivingEntity entity = pet.entity();
        if (mod.settings().pettingHearts && entity.level() instanceof ServerLevel level) {
            // Above the bubble, so the hearts do not cover the text.
            level.sendParticles(ParticleTypes.HEART, entity.getX(),
                    entity.getY() + entity.getBbHeight() + pet.species().bubbleGap() + 0.75, entity.getZ(),
                    5, 0.45, 0.12, 0.45, 0.0);
        }
        if (entity instanceof Mob mob) {
            mob.playAmbientSound();
        }
        mod.events().petted(pet, FabricPet.owner(player));
    }

    // ------------------------------------------------------------------
    //  Called from mixins (always on the server thread)
    // ------------------------------------------------------------------

    /**
     * The player pressed "swap hands" (F). Returns true if they were looking at their own pet: the
     * pet gets petted and the swap must be cancelled.
     */
    public static boolean onSwapKey(ServerPlayer player) {
        PetThoughtsMod mod = PetThoughtsMod.get();
        if (mod == null || !mod.settings().pettingSwapKey) {
            return false;
        }
        FabricPet pet = lookedAt(mod, player, PET_REACH);
        if (pet == null || !player.getUUID().equals(pet.ownerId()) || !mod.settings().speciesEnabled(pet.species())) {
            return false;
        }
        pet(mod, player, pet);
        return true;
    }

    public static void onTamed(Entity entity, Entity tamer) {
        PetThoughtsMod mod = PetThoughtsMod.get();
        if (mod == null || !(tamer instanceof ServerPlayer player)) {
            return;
        }
        FabricPet pet = FabricPet.of(mod, entity);
        if (pet == null || !mod.settings().speciesEnabled(pet.species())) {
            return;
        }
        Personality personality = mod.director().ensurePersonality(pet);
        if (!mod.lang().raw("messages.tamed-hint").isBlank()) {
            player.sendOverlayMessage(FabricText.message(mod.lang(), "messages.tamed-hint", Map.of(
                    "pet", pet.name(),
                    "personality", mod.lang().raw("personality." + personality.id() + ".name"))));
        }
        mod.events().tamed(pet, FabricPet.owner(player));
    }

    public static void onTeleported(Entity entity) {
        PetThoughtsMod mod = PetThoughtsMod.get();
        FabricPet pet = mod == null ? null : FabricPet.of(mod, entity);
        ServerPlayer owner = mod == null ? null : ownerOf(mod, pet);
        if (owner != null) {
            mod.events().teleported(pet, FabricPet.owner(owner));
        }
    }

    public static void onAte(ServerPlayer player, ItemStack stack) {
        PetThoughtsMod mod = PetThoughtsMod.get();
        if (mod != null && stack.has(DataComponents.FOOD)) {
            mod.events().ownerAte(FabricPet.owner(player), FabricPet.itemId(stack));
        }
    }

    public static void onFished(Entity angler) {
        PetThoughtsMod mod = PetThoughtsMod.get();
        if (mod != null && angler instanceof ServerPlayer player) {
            mod.events().ownerFished(FabricPet.owner(player));
        }
    }

    public static void onAdvancement(ServerPlayer player) {
        PetThoughtsMod mod = PetThoughtsMod.get();
        if (mod != null) {
            mod.events().ownerAdvancement(FabricPet.owner(player));
        }
    }

    public static void onBred(Entity first, Entity second) {
        PetThoughtsMod mod = PetThoughtsMod.get();
        if (mod == null) {
            return;
        }
        boolean isFirst = true;
        for (Entity parent : new Entity[] {first, second}) {
            FabricPet pet = FabricPet.of(mod, parent);
            ServerPlayer owner = ownerOf(mod, pet);
            if (owner != null) {
                mod.events().hadBaby(pet, FabricPet.owner(owner), isFirst);
                isFirst = false;
            }
        }
    }

    public static void onExplosion(ServerLevel level, Vec3 center) {
        PetThoughtsMod mod = PetThoughtsMod.get();
        if (mod == null) {
            return;
        }
        AABB box = new AABB(center, center).inflate(12);
        for (Entity entity : level.getEntities((Entity) null, box, candidate -> FabricPet.speciesOf(candidate) != null)) {
            FabricPet pet = FabricPet.of(mod, entity);
            ServerPlayer owner = ownerOf(mod, pet);
            if (owner != null) {
                OwnerView view = FabricPet.owner(owner);
                mod.events().explosionNear(pet, view);
            }
        }
    }
}
