package dev.petthoughts.mod;

import dev.petthoughts.core.Personality;
import dev.petthoughts.core.Species;
import dev.petthoughts.core.bubble.Names;
import dev.petthoughts.core.platform.OwnerView;
import dev.petthoughts.core.platform.PetView;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
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

/**
 * A vanilla pet seen through the common {@link PetView}. Persistent data lives in the entity's
 * mod data tag ({@code getPersistentData()}, which NeoForge and Forge both add and save with the
 * entity), inside a {@code petthoughts} compound.
 */
public final class ModPet implements PetView {

    private static final String DATA = PetThoughtsMod.MOD_ID;
    private static final String PERSONALITY = "personality";
    private static final String MUTED = "muted";
    private static final String OWNER_SEEN = "owner_seen";
    private static final String SIT_SINCE = "sit_since";
    /** Stored on players: they turned bubbles off with /petthoughts toggle. */
    private static final String HIDDEN = "hidden";
    /** The part of a player's mod data that both loaders copy to the new player after a death. */
    private static final String PLAYER_PERSISTED = "PlayerPersisted";

    private final PetThoughtsMod mod;
    private final LivingEntity entity;
    private final Species species;
    private final UUID owner;

    private ModPet(PetThoughtsMod mod, LivingEntity entity, Species species, UUID owner) {
        this.mod = mod;
        this.entity = entity;
        this.species = species;
        this.owner = owner;
    }

    /** Wraps a tamed pet of a supported species; null for anything else. */
    public static ModPet of(PetThoughtsMod mod, Entity entity) {
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
        return new ModPet(mod, living, species, reference.getUUID());
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
                candidate -> candidate instanceof Enemy && candidate.isAlive() && Names.scary(ModText.typeId(candidate)))) {
            double d = other.distanceToSqr(entity);
            if (d <= best && entity.hasLineOfSight(other)) {
                best = d;
                nearest = other;
            }
        }
        return nearest == null ? null : new Hostile(nearest instanceof Creeper, ModText.entityName(mod.lang(), nearest));
    }

    // ---- persistent data ----

    /** This mod's data on an entity, for reading: empty if there is none yet. */
    private static CompoundTag read(CompoundTag holder) {
        return holder.getCompoundOrEmpty(DATA);
    }

    /** This mod's data on an entity, for writing: created if there is none yet. */
    private static CompoundTag write(CompoundTag holder) {
        if (!holder.contains(DATA)) {
            holder.put(DATA, new CompoundTag());
        }
        return holder.getCompoundOrEmpty(DATA);
    }

    private static void putLong(CompoundTag holder, String key, long value) {
        if (value <= 0) {
            if (holder.contains(DATA)) {
                write(holder).remove(key);
            }
        } else {
            write(holder).putLong(key, value);
        }
    }

    private static void putFlag(CompoundTag holder, String key, boolean value) {
        if (value) {
            write(holder).putBoolean(key, true);
        } else if (holder.contains(DATA)) {
            write(holder).remove(key);
        }
    }

    static boolean bubblesHidden(ServerPlayer player) {
        return read(player.getPersistentData().getCompoundOrEmpty(PLAYER_PERSISTED)).getBooleanOr(HIDDEN, false);
    }

    static void setBubblesHidden(ServerPlayer player, boolean hidden) {
        CompoundTag data = player.getPersistentData();
        if (!data.contains(PLAYER_PERSISTED)) {
            if (!hidden) {
                return;
            }
            data.put(PLAYER_PERSISTED, new CompoundTag());
        }
        putFlag(data.getCompoundOrEmpty(PLAYER_PERSISTED), HIDDEN, hidden);
    }

    @Override
    public Personality personality() {
        String id = read(entity.getPersistentData()).getStringOr(PERSONALITY, "");
        return Personality.byId(id.isEmpty() ? null : id);
    }

    @Override
    public void setPersonality(Personality personality) {
        write(entity.getPersistentData()).putString(PERSONALITY, personality.id());
    }

    @Override
    public boolean muted() {
        return read(entity.getPersistentData()).getBooleanOr(MUTED, false);
    }

    @Override
    public void setMuted(boolean muted) {
        putFlag(entity.getPersistentData(), MUTED, muted);
    }

    @Override
    public long ownerSeen() {
        return read(entity.getPersistentData()).getLongOr(OWNER_SEEN, 0L);
    }

    @Override
    public void setOwnerSeen(long epochMs) {
        write(entity.getPersistentData()).putLong(OWNER_SEEN, epochMs);
    }

    @Override
    public long sitSince() {
        return read(entity.getPersistentData()).getLongOr(SIT_SINCE, 0L);
    }

    @Override
    public void setSitSince(long epochMs) {
        putLong(entity.getPersistentData(), SIT_SINCE, epochMs);
    }
}
