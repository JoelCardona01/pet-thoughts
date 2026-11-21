package dev.petthoughts.mod;

import dev.petthoughts.core.Personality;
import dev.petthoughts.core.bubble.Names;
import dev.petthoughts.core.config.LangStore;
import dev.petthoughts.core.platform.OwnerView;
import dev.petthoughts.core.platform.PetView;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Translates game events into the shared {@code PetEvents}. The first group of methods is called by
 * the loader's mod class from NeoForge / Forge events; the rest (taming, eating, fishing,
 * advancements, breeding, explosions, the petting key) come from the mixins in
 * {@code dev.petthoughts.mod.mixin}, which call the static methods at the bottom.
 */
public final class ModEvents {

    private static final double PET_REACH = 4.0;
    private static final Map<UUID, Long> LAST_PET = new HashMap<>();

    private ModEvents() {
    }

    // ------------------------------------------------------------------
    //  Called from loader events (always on the server thread)
    // ------------------------------------------------------------------

    public static void onJoin(ServerPlayer player) {
        PetThoughtsMod mod = PetThoughtsMod.get();
        if (mod != null) {
            mod.setHidden(player.getUUID(), ModPet.bubblesHidden(player));
        }
    }

    public static void onLeave(ServerPlayer player) {
        PetThoughtsMod mod = PetThoughtsMod.get();
        if (mod != null) {
            mod.setHidden(player.getUUID(), false);
            mod.director().forgetOwner(player.getUUID());
        }
        LAST_PET.remove(player.getUUID());
    }

    /** A bubble that got saved with the world (crash, forced quit) is removed when it loads again. */
    public static void onEntityLoad(Entity entity, Level level) {
        PetThoughtsMod mod = PetThoughtsMod.get();
        if (mod != null && level instanceof ServerLevel serverLevel && entity instanceof Display.TextDisplay
                && entity.entityTags().contains(ModBubbles.TAG) && !mod.bubbles().owns(entity)) {
            serverLevel.getServer().execute(entity::discard);
        }
    }

    /**
     * A player right-clicked an entity. Returns true if the click was a petting and must be
     * consumed, which keeps the pet from sitting / standing.
     */
    public static boolean onUseEntity(ServerPlayer player, InteractionHand hand, Entity entity) {
        PetThoughtsMod mod = PetThoughtsMod.get();
        if (mod == null) {
            return false;
        }
        ModPet pet = ModPet.of(mod, entity);
        if (pet == null || !player.getUUID().equals(pet.ownerId()) || !mod.settings().speciesEnabled(pet.species())) {
            return false;
        }
        // Petting: sneak + empty main hand.
        if (mod.settings().pettingSneakClick && player.isShiftKeyDown() && player.getMainHandItem().isEmpty()
                && !pet.species().rideable()) {
            if (hand == InteractionHand.MAIN_HAND) {
                pet(mod, player, pet);
            }
            return true;
        }
        // Feeding: check a tick later whether the food was really eaten.
        ItemStack held = player.getItemInHand(hand);
        String food = ModPet.itemId(held);
        if (pet.species().eats(food)) {
            int before = held.getCount();
            mod.later(pet, 1, () -> {
                ItemStack after = player.getItemInHand(hand);
                if (player.hasInfiniteMaterials() || !ModPet.itemId(after).equals(food) || after.getCount() < before) {
                    mod.events().fed(pet, ModPet.owner(player), food);
                }
            });
        }
        return false;
    }

    /**
     * A living entity took damage and survived it.
     *
     * @param damageTaken what it really lost, after armour and effects
     */
    public static void onDamaged(LivingEntity entity, DamageSource source, float damageTaken) {
        PetThoughtsMod mod = PetThoughtsMod.get();
        if (mod == null || damageTaken <= 0 || !entity.isAlive()) {
            return;
        }
        if (entity instanceof ServerPlayer player) {
            if (damageTaken >= 2) {
                mod.events().ownerHurt(ModPet.owner(player));
            }
            return;
        }
        ModPet pet = ModPet.of(mod, entity);
        ServerPlayer owner = ownerOf(mod, pet);
        if (owner != null) {
            Entity attacker = source.getEntity();
            mod.events().petHurt(pet, ModPet.owner(owner), attacker != null && attacker.getUUID().equals(owner.getUUID()));
        }
    }

    public static void onDeath(LivingEntity entity, DamageSource source) {
        PetThoughtsMod mod = PetThoughtsMod.get();
        if (mod == null) {
            return;
        }
        if (entity instanceof ServerPlayer player) {
            mod.events().ownerDied(ModPet.owner(player));
            return;
        }
        ModPet deadPet = ModPet.of(mod, entity);
        if (deadPet != null) {
            mod.events().petDied(deadPet);
            return;
        }
        if (!(entity instanceof Enemy) || !Names.scary(ModText.typeId(entity))) {
            return;
        }
        Entity killer = source.getEntity();
        ModPet killerPet = killer == null ? null : ModPet.of(mod, killer);
        ServerPlayer petOwner = ownerOf(mod, killerPet);
        if (petOwner != null) {
            mod.events().petKilledMob(killerPet, ModPet.owner(petOwner), ModText.entityName(mod.lang(petOwner), entity));
        } else if (killer instanceof ServerPlayer player) {
            mod.events().ownerKilledMob(ModPet.owner(player), ModText.entityName(mod.lang(player), entity));
        }
    }

    public static void onBlockBroken(ServerPlayer player, BlockState state) {
        PetThoughtsMod mod = PetThoughtsMod.get();
        if (mod != null) {
            mod.events().ownerBrokeBlock(ModPet.owner(player), BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath());
        }
    }

    /** A player right-clicked a block: the only case that matters is putting a disc in a jukebox. */
    public static void onUseBlock(ServerPlayer player, InteractionHand hand, BlockPos pos) {
        PetThoughtsMod mod = PetThoughtsMod.get();
        if (mod != null && player.level().getBlockState(pos).is(Blocks.JUKEBOX)
                && player.getItemInHand(hand).has(DataComponents.JUKEBOX_PLAYABLE)) {
            mod.events().ownerPlayedMusic(ModPet.owner(player), pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
        }
    }

    // ------------------------------------------------------------------

    /** The online owner of a wrapped pet, or null. */
    static ServerPlayer ownerOf(PetThoughtsMod mod, PetView pet) {
        if (pet == null || pet.ownerId() == null || mod.server() == null) {
            return null;
        }
        return mod.server().getPlayerList().getPlayer(pet.ownerId());
    }

    /** The supported tamed pet the player is looking at within {@code reach} blocks, or null. */
    static ModPet lookedAt(PetThoughtsMod mod, ServerPlayer player, double reach) {
        Vec3 eye = player.getEyePosition(1f);
        Vec3 view = player.getViewVector(1f).scale(reach);
        AABB box = player.getBoundingBox().expandTowards(view).inflate(1.0);
        EntityHitResult hit = ProjectileUtil.getEntityHitResult(
                player, eye, eye.add(view), box, entity -> !entity.isSpectator() && entity.isPickable(), reach * reach);
        return hit == null ? null : ModPet.of(mod, hit.getEntity());
    }

    private static void pet(PetThoughtsMod mod, ServerPlayer player, ModPet pet) {
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
        mod.events().petted(pet, ModPet.owner(player));
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
        ModPet pet = lookedAt(mod, player, PET_REACH);
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
        ModPet pet = ModPet.of(mod, entity);
        if (pet == null || !mod.settings().speciesEnabled(pet.species())) {
            return;
        }
        Personality personality = mod.director().ensurePersonality(pet);
        LangStore lang = mod.lang(player);
        if (!lang.raw("messages.tamed-hint").isBlank()) {
            player.sendOverlayMessage(ModText.message(lang, "messages.tamed-hint", Map.of(
                    "pet", pet.name(),
                    "personality", lang.raw("personality." + personality.id() + ".name"))));
        }
        mod.events().tamed(pet, ModPet.owner(player));
    }

    public static void onTeleported(Entity entity) {
        PetThoughtsMod mod = PetThoughtsMod.get();
        ModPet pet = mod == null ? null : ModPet.of(mod, entity);
        ServerPlayer owner = mod == null ? null : ownerOf(mod, pet);
        if (owner != null) {
            mod.events().teleported(pet, ModPet.owner(owner));
        }
    }

    public static void onAte(ServerPlayer player, ItemStack stack) {
        PetThoughtsMod mod = PetThoughtsMod.get();
        if (mod != null && stack.has(DataComponents.FOOD)) {
            mod.events().ownerAte(ModPet.owner(player), ModPet.itemId(stack));
        }
    }

    public static void onFished(Entity angler) {
        PetThoughtsMod mod = PetThoughtsMod.get();
        if (mod != null && angler instanceof ServerPlayer player) {
            mod.events().ownerFished(ModPet.owner(player));
        }
    }

    public static void onAdvancement(ServerPlayer player) {
        PetThoughtsMod mod = PetThoughtsMod.get();
        if (mod != null) {
            mod.events().ownerAdvancement(ModPet.owner(player));
        }
    }

    public static void onBred(Entity first, Entity second) {
        PetThoughtsMod mod = PetThoughtsMod.get();
        if (mod == null) {
            return;
        }
        boolean isFirst = true;
        for (Entity parent : new Entity[] {first, second}) {
            ModPet pet = ModPet.of(mod, parent);
            ServerPlayer owner = ownerOf(mod, pet);
            if (owner != null) {
                mod.events().hadBaby(pet, ModPet.owner(owner), isFirst);
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
        for (Entity entity : level.getEntities((Entity) null, box, candidate -> ModPet.speciesOf(candidate) != null)) {
            ModPet pet = ModPet.of(mod, entity);
            ServerPlayer owner = ownerOf(mod, pet);
            if (owner != null) {
                OwnerView view = ModPet.owner(owner);
                mod.events().explosionNear(pet, view);
            }
        }
    }
}
