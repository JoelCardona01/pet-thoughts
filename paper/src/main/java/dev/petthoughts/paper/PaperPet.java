package dev.petthoughts.paper;

import dev.petthoughts.core.Personality;
import dev.petthoughts.core.Species;
import dev.petthoughts.core.bubble.Names;
import dev.petthoughts.core.platform.OwnerView;
import dev.petthoughts.core.platform.PetView;
import java.util.Locale;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Boat;
import org.bukkit.entity.Cat;
import org.bukkit.entity.Creeper;
import org.bukkit.entity.Donkey;
import org.bukkit.entity.Enemy;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Horse;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Llama;
import org.bukkit.entity.Mule;
import org.bukkit.entity.Parrot;
import org.bukkit.entity.Player;
import org.bukkit.entity.Sittable;
import org.bukkit.entity.Tameable;
import org.bukkit.entity.Wolf;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

/** A Bukkit pet seen through the common {@link PetView}. Persistent data lives in the entity's PDC. */
public final class PaperPet implements PetView {

    /** PDC keys of the plugin. */
    public static final class Keys {
        final NamespacedKey personality;
        final NamespacedKey muted;
        final NamespacedKey ownerSeen;
        final NamespacedKey sitSince;
        public final NamespacedKey bubble;
        public final NamespacedKey hidden;

        Keys(Plugin plugin) {
            personality = new NamespacedKey(plugin, "personality");
            muted = new NamespacedKey(plugin, "muted");
            ownerSeen = new NamespacedKey(plugin, "owner_seen");
            sitSince = new NamespacedKey(plugin, "sit_since");
            bubble = new NamespacedKey(plugin, "bubble");
            hidden = new NamespacedKey(plugin, "hidden");
        }
    }

    private final PetThoughtsPlugin plugin;
    private final Tameable entity;
    private final Species species;

    private PaperPet(PetThoughtsPlugin plugin, Tameable entity, Species species) {
        this.plugin = plugin;
        this.entity = entity;
        this.species = species;
    }

    /** Wraps a tamed pet of a supported species; null for anything else. */
    public static PaperPet of(PetThoughtsPlugin plugin, Entity entity) {
        if (!(entity instanceof Tameable tameable) || !tameable.isTamed()) {
            return null;
        }
        Species species = speciesOf(entity);
        return species == null ? null : new PaperPet(plugin, tameable, species);
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

    public static String itemId(Material material) {
        return material.name().toLowerCase(Locale.ROOT);
    }

    /** Snapshot of a player for the brain. Call it on the player's thread. */
    public static OwnerView owner(Player player) {
        AttributeInstance max = player.getAttribute(Attribute.MAX_HEALTH);
        double maxHealth = max == null ? 20.0 : Math.max(1.0, max.getValue());
        Location at = player.getLocation();
        ItemStack main = player.getInventory().getItemInMainHand();
        ItemStack off = player.getInventory().getItemInOffHand();
        return new OwnerView(
                player.getUniqueId(),
                player.getName(),
                player.getWorld().getName(),
                at.getX(), at.getY(), at.getZ(),
                player.isDead() ? 0.0 : player.getHealth() / maxHealth,
                player.getFoodLevel(),
                player.isSleeping(),
                player.isGliding(),
                itemId(main.getType()),
                itemId(off.getType()));
    }

    public Tameable entity() {
        return entity;
    }

    @Override
    public UUID id() {
        return entity.getUniqueId();
    }

    @Override
    public UUID ownerId() {
        return entity.getOwnerUniqueId();
    }

    @Override
    public Species species() {
        return species;
    }

    @Override
    public Component name() {
        Component custom = entity.customName();
        return custom != null ? custom : Component.text(plugin.lang().raw("species-names." + species.id()));
    }

    @Override
    public String plainName() {
        return PlainTextComponentSerializer.plainText().serialize(name());
    }

    @Override
    public boolean alive() {
        return entity.isValid() && !entity.isDead();
    }

    @Override
    public boolean invisible() {
        return entity.isInvisible();
    }

    @Override
    public double healthRatio() {
        return entity.getHealth() / Math.max(1, maxHealthValue());
    }

    @Override
    public int health() {
        return (int) Math.ceil(entity.getHealth());
    }

    @Override
    public int maxHealth() {
        return (int) Math.ceil(maxHealthValue());
    }

    private double maxHealthValue() {
        AttributeInstance max = entity.getAttribute(Attribute.MAX_HEALTH);
        return max == null ? 20.0 : max.getValue();
    }

    @Override
    public boolean onFire() {
        return entity.getFireTicks() > 0;
    }

    @Override
    public boolean inWater() {
        return entity.isInWater();
    }

    @Override
    public boolean inRain() {
        return entity.isInRain();
    }

    @Override
    public Boolean sitting() {
        return entity instanceof Sittable sittable ? sittable.isSitting() : null;
    }

    @Override
    public boolean inBoat() {
        return entity.getVehicle() instanceof Boat;
    }

    @Override
    public boolean leashed() {
        return entity.isLeashed();
    }

    @Override
    public boolean riddenBy(UUID player) {
        for (Entity passenger : entity.getPassengers()) {
            if (passenger.getUniqueId().equals(player)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public String worldId() {
        return entity.getWorld().getName();
    }

    @Override
    public double x() {
        return entity.getLocation().getX();
    }

    @Override
    public double y() {
        return entity.getLocation().getY();
    }

    @Override
    public double z() {
        return entity.getLocation().getZ();
    }

    @Override
    public Dimension dimension() {
        return switch (entity.getWorld().getEnvironment()) {
            case NORMAL -> Dimension.OVERWORLD;
            case NETHER -> Dimension.NETHER;
            case THE_END -> Dimension.END;
            default -> Dimension.OTHER;
        };
    }

    @Override
    public long dayTime() {
        return entity.getWorld().getTime();
    }

    @Override
    public long gameTime() {
        return entity.getWorld().getFullTime();
    }

    @Override
    public boolean storming() {
        return entity.getWorld().hasStorm();
    }

    @Override
    public boolean thundering() {
        World world = entity.getWorld();
        return world.hasStorm() && world.isThundering();
    }

    @Override
    public int skyLight() {
        return entity.getLocation().getBlock().getLightFromSky();
    }

    @Override
    public double temperature() {
        return entity.getLocation().getBlock().getTemperature();
    }

    @Override
    public Hostile nearestHostile() {
        LivingEntity nearest = null;
        double best = 64.0;
        Location location = entity.getLocation();
        for (Entity other : entity.getNearbyEntities(8, 6, 8)) {
            if (!(other instanceof Enemy) || !(other instanceof LivingEntity living) || living.isDead()
                    || !Names.scary(other.getType().getKey().getKey())) {
                continue;
            }
            double d = other.getLocation().distanceSquared(location);
            if (d <= best && entity.hasLineOfSight(living)) {
                best = d;
                nearest = living;
            }
        }
        return nearest == null ? null : new Hostile(nearest instanceof Creeper, PaperText.entityName(plugin.lang(), nearest));
    }

    // ---- PDC ----

    private PersistentDataContainer pdc() {
        return entity.getPersistentDataContainer();
    }

    @Override
    public Personality personality() {
        return Personality.byId(pdc().get(plugin.keys().personality, PersistentDataType.STRING));
    }

    @Override
    public void setPersonality(Personality personality) {
        pdc().set(plugin.keys().personality, PersistentDataType.STRING, personality.id());
    }

    @Override
    public boolean muted() {
        return pdc().has(plugin.keys().muted, PersistentDataType.BYTE);
    }

    @Override
    public void setMuted(boolean muted) {
        if (muted) {
            pdc().set(plugin.keys().muted, PersistentDataType.BYTE, (byte) 1);
        } else {
            pdc().remove(plugin.keys().muted);
        }
    }

    @Override
    public long ownerSeen() {
        return pdc().getOrDefault(plugin.keys().ownerSeen, PersistentDataType.LONG, 0L);
    }

    @Override
    public void setOwnerSeen(long epochMs) {
        pdc().set(plugin.keys().ownerSeen, PersistentDataType.LONG, epochMs);
    }

    @Override
    public long sitSince() {
        return pdc().getOrDefault(plugin.keys().sitSince, PersistentDataType.LONG, 0L);
    }

    @Override
    public void setSitSince(long epochMs) {
        if (epochMs <= 0) {
            pdc().remove(plugin.keys().sitSince);
        } else {
            pdc().set(plugin.keys().sitSince, PersistentDataType.LONG, epochMs);
        }
    }
}
