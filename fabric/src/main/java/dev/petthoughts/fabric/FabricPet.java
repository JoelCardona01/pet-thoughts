package dev.petthoughts.fabric;

import com.mojang.serialization.Codec;
import dev.petthoughts.core.Personality;
import dev.petthoughts.core.Species;
import dev.petthoughts.core.bubble.Names;
import dev.petthoughts.core.platform.OwnerView;
import dev.petthoughts.core.platform.PetView;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityReference;
import net.minecraft.world.entity.Leashable;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.animal.equine.AbstractHorse;
import net.minecraft.world.entity.animal.equine.Donkey;
import net.minecraft.world.entity.animal.equine.Horse;
import net.minecraft.world.entity.animal.equine.Llama;
import net.minecraft.world.entity.animal.equine.Mule;
import net.minecraft.world.entity.animal.feline.Cat;
import net.minecraft.world.entity.animal.parrot.Parrot;
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.vehicle.boat.AbstractBoat;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.phys.AABB;

/** A vanilla pet seen through the common {@link PetView}. Persistent data lives in Fabric data attachments. */
public final class FabricPet implements PetView {

    private static final AttachmentType<String> PERSONALITY = attachment("personality", Codec.STRING);
    private static final AttachmentType<Boolean> MUTED = attachment("muted", Codec.BOOL);
    private static final AttachmentType<Long> OWNER_SEEN = attachment("owner_seen", Codec.LONG);
    private static final AttachmentType<Long> SIT_SINCE = attachment("sit_since", Codec.LONG);
    /** Stored on players: they turned bubbles off with /petthoughts toggle. */
    static final AttachmentType<Boolean> HIDDEN = AttachmentRegistry.create(
            Identifier.fromNamespaceAndPath(PetThoughtsMod.MOD_ID, "hidden"),
            builder -> builder.persistent(Codec.BOOL).copyOnDeath());

    private static <T> AttachmentType<T> attachment(String name, Codec<T> codec) {
        return AttachmentRegistry.createPersistent(Identifier.fromNamespaceAndPath(PetThoughtsMod.MOD_ID, name), codec);
    }

    /** Forces the static attachment types to register during mod initialization. */
    static void init() {
    }

    private final PetThoughtsMod mod;
    private final LivingEntity entity;
    private final Species species;
    private final UUID owner;

    private FabricPet(PetThoughtsMod mod, LivingEntity entity, Species species, UUID owner) {
        this.mod = mod;
        this.entity = entity;
        this.species = species;
        this.owner = owner;
    }

    /** Wraps a tamed pet of a supported species; null for anything else. */
    public static FabricPet of(PetThoughtsMod mod, Entity entity) {
        Species species = speciesOf(entity);
        if (species == null || !(entity instanceof LivingEntity living) || !(entity instanceof OwnableEntity ownable)) {
            return null;
        }
        boolean tamed = entity instanceof TamableAnimal tamable ? tamable.isTame()
                : entity instanceof AbstractHorse horse && horse.isTamed();
        EntityReference<LivingEntity> reference = ownable.getOwnerReference();
        if (!tamed || reference == null) {
            return null;
        }
        return new FabricPet(mod, living, species, reference.getUUID());
    }

    public static Species speciesOf(Entity entity) {
        if (entity instanceof Wolf) {
            return Species.WOLF;
        }
        if (entity instanceof Cat) {
            return Species.CAT;
        }
        if (entity instanceof Parrot) {
            return Species.PARROT;
        }
        if (entity instanceof Llama) {
            return Species.LLAMA; // includes trader llamas
        }
        if (entity instanceof Horse) {
            return Species.HORSE;
        }
        if (entity instanceof Donkey) {
            return Species.DONKEY;
        }
        if (entity instanceof Mule) {
            return Species.MULE;
        }
        return null;
    }

    public static String itemId(ItemStack stack) {
        return stack.isEmpty() ? "air" : BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
    }

    public static String worldId(Level level) {
        return level.dimension().identifier().toString();
    }

    /** Snapshot of a player for the brain. */
    public static OwnerView owner(ServerPlayer player) {
        return new OwnerView(
                player.getUUID(),
                player.getGameProfile().name(),
                worldId(player.level()),
                player.getX(), player.getY(), player.getZ(),
                player.isAlive() ? player.getHealth() / Math.max(1f, player.getMaxHealth()) : 0.0,
                player.getFoodData().getFoodLevel(),
                player.isSleeping(),
                player.isFallFlying(),
                itemId(player.getMainHandItem()),
                itemId(player.getOffhandItem()));
    }

    public LivingEntity entity() {
        return entity;
    }

    @Override
    public UUID id() {
        return entity.getUUID();
    }

    @Override
    public UUID ownerId() {
        return owner;
    }

    @Override
    public Species species() {
        return species;
    }

    @Override
    public Component name() {
        Component custom = entity.getCustomName();
        return custom != null ? custom : Component.literal(mod.lang().raw("species-names." + species.id()));
    }

    @Override
    public String plainName() {
        return name().getString();
    }

    @Override
    public boolean alive() {
        return entity.isAlive() && !entity.isRemoved();
    }

    @Override
    public boolean invisible() {
        return entity.isInvisible();
    }

    @Override
    public double healthRatio() {
        return entity.getHealth() / Math.max(1f, entity.getMaxHealth());
    }

    @Override
    public int health() {
        return (int) Math.ceil(entity.getHealth());
    }

    @Override
    public int maxHealth() {
        return (int) Math.ceil(entity.getMaxHealth());
    }

    @Override
    public boolean onFire() {
        return entity.isOnFire();
    }

    @Override
    public boolean inWater() {
        return entity.isInWater();
    }

    @Override
    public boolean inRain() {
        BlockPos pos = entity.blockPosition();
        return entity.level().isRainingAt(pos) || entity.level().isRainingAt(pos.above());
    }

    @Override
    public Boolean sitting() {
        return entity instanceof TamableAnimal tamable ? tamable.isOrderedToSit() : null;
    }

    @Override
    public boolean inBoat() {
        return entity.getVehicle() instanceof AbstractBoat;
    }

    @Override
    public boolean leashed() {
        return entity instanceof Leashable leashable && leashable.isLeashed();
    }

    @Override
    public boolean riddenBy(UUID player) {
        for (Entity passenger : entity.getPassengers()) {
            if (passenger.getUUID().equals(player)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public String worldId() {
        return worldId(entity.level());
    }

    @Override
    public double x() {
        return entity.getX();
    }

    @Override
    public double y() {
        return entity.getY();
    }

    @Override
    public double z() {
        return entity.getZ();
    }

    @Override
    public Dimension dimension() {
        var key = entity.level().dimension();
        if (key == Level.OVERWORLD) {
            return Dimension.OVERWORLD;
        }
        if (key == Level.NETHER) {
            return Dimension.NETHER;
        }
        return key == Level.END ? Dimension.END : Dimension.OTHER;
    }

    @Override
    public long dayTime() {
        return Math.floorMod(entity.level().getOverworldClockTime(), 24000L);
    }

    @Override
    public long gameTime() {
        return entity.level().getOverworldClockTime();
    }

    @Override
    public boolean storming() {
        return entity.level().isRaining();
    }

    @Override
    public boolean thundering() {
        return entity.level().isThundering();
    }

    @Override
    public int skyLight() {
        return entity.level().getBrightness(LightLayer.SKY, entity.blockPosition());
    }

    @Override
    public double temperature() {
        return entity.level().getBiome(entity.blockPosition()).value().getBaseTemperature();
    }

    @Override
    public Hostile nearestHostile() {
        LivingEntity nearest = null;
        double best = 64.0;
        AABB box = entity.getBoundingBox().inflate(8, 6, 8);
        for (LivingEntity other : entity.level().getEntitiesOfClass(LivingEntity.class, box,
                candidate -> candidate instanceof Enemy && candidate.isAlive() && Names.scary(FabricText.typeId(candidate)))) {
            double d = other.distanceToSqr(entity);
            if (d <= best && entity.hasLineOfSight(other)) {
                best = d;
                nearest = other;
            }
        }
        return nearest == null ? null : new Hostile(nearest instanceof Creeper, FabricText.entityName(mod.lang(), nearest));
    }

    // ---- attachments ----

    @Override
    public Personality personality() {
        return Personality.byId(entity.getAttached(PERSONALITY));
    }

    @Override
    public void setPersonality(Personality personality) {
        entity.setAttached(PERSONALITY, personality.id());
    }

    @Override
    public boolean muted() {
        return Boolean.TRUE.equals(entity.getAttached(MUTED));
    }

    @Override
    public void setMuted(boolean muted) {
        if (muted) {
            entity.setAttached(MUTED, true);
        } else {
            entity.removeAttached(MUTED);
        }
    }

    @Override
    public long ownerSeen() {
        Long value = entity.getAttached(OWNER_SEEN);
        return value == null ? 0L : value;
    }

    @Override
    public void setOwnerSeen(long epochMs) {
        entity.setAttached(OWNER_SEEN, epochMs);
    }

    @Override
    public long sitSince() {
        Long value = entity.getAttached(SIT_SINCE);
        return value == null ? 0L : value;
    }

    @Override
    public void setSitSince(long epochMs) {
        if (epochMs <= 0) {
            entity.removeAttached(SIT_SINCE);
        } else {
            entity.setAttached(SIT_SINCE, epochMs);
        }
    }
}
