package dev.petthoughts.paper;

import dev.petthoughts.core.Personality;
import dev.petthoughts.core.bubble.Names;
import dev.petthoughts.core.platform.OwnerView;
import dev.petthoughts.core.thought.PetEvents;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Cat;
import org.bukkit.entity.Enemy;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Parrot;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Tameable;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.entity.EntityBreedEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityTameEvent;
import org.bukkit.event.entity.EntityTeleportEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerAdvancementDoneEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

/** Translates Bukkit events into the shared {@link PetEvents}. Only observes, except for petting. */
final class PetListener implements Listener {

    private static final int PET_REACH = 4;

    private final PetThoughtsPlugin plugin;
    private final Map<UUID, Long> lastPet = new java.util.concurrent.ConcurrentHashMap<>();

    PetListener(PetThoughtsPlugin plugin) {
        this.plugin = plugin;
    }

    private PetEvents events() {
        return plugin.events();
    }

    /** The online owner of a supported tamed pet, or null. */
    private static Player ownerOf(PaperPet pet) {
        UUID owner = pet == null ? null : pet.ownerId();
        return owner == null ? null : Bukkit.getPlayer(owner);
    }

    // ------------------------------------------------------------------
    //  Players
    // ------------------------------------------------------------------

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        plugin.setHidden(player.getUniqueId(),
                player.getPersistentDataContainer().has(plugin.keys().hidden, PersistentDataType.BYTE));
        plugin.scanner().start(player);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        plugin.scanner().stop(id);
        plugin.setHidden(id, false);
        plugin.director().forgetOwner(id);
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        player.getScheduler().runDelayed(plugin, task -> plugin.scanner().ensure(player), null, 20);
    }

    @EventHandler
    public void onChangedWorld(PlayerChangedWorldEvent event) {
        plugin.scanner().ensure(event.getPlayer());
    }

    // ------------------------------------------------------------------
    //  Taming, feeding, petting
    // ------------------------------------------------------------------

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTame(EntityTameEvent event) {
        if (!(event.getOwner() instanceof Player owner) || !(event.getEntity() instanceof Tameable tameable)) {
            return;
        }
        // The entity is not "tamed" yet while the event fires, so wrap it a tick later.
        tameable.getScheduler().runDelayed(plugin, task -> {
            PaperPet pet = PaperPet.of(plugin, tameable);
            if (pet == null || !plugin.settings().speciesEnabled(pet.species())) {
                return;
            }
            Personality personality = plugin.director().ensurePersonality(pet);
            if (!plugin.lang().raw("messages.tamed-hint").isBlank()) {
                owner.sendActionBar(PaperText.message(plugin.lang(), "messages.tamed-hint", Map.of(
                        "pet", pet.name(),
                        "personality", plugin.lang().raw("personality." + personality.id() + ".name"))));
            }
            events().tamed(pet, PaperPet.owner(owner));
        }, null, 1);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteractPet(PlayerInteractEntityEvent event) {
        Player player = event.getPlayer();
        PaperPet pet = PaperPet.of(plugin, event.getRightClicked());
        if (pet == null || !player.getUniqueId().equals(pet.ownerId())
                || !plugin.settings().speciesEnabled(pet.species())) {
            return;
        }
        ItemStack main = player.getInventory().getItemInMainHand();

        // Petting: sneak + empty main hand. Both hand events are cancelled so the pet does not sit/stand.
        if (plugin.settings().pettingSneakClick && player.isSneaking() && main.getType().isAir()
                && !pet.species().rideable() && player.hasPermission("petthoughts.pet")) {
            event.setCancelled(true);
            if (event.getHand() == EquipmentSlot.HAND) {
                pet(player, pet);
            }
            return;
        }

        // Feeding: check a tick later whether the food was really eaten.
        EquipmentSlot hand = event.getHand();
        ItemStack held = player.getInventory().getItem(hand);
        Material food = held.getType();
        if (!pet.species().eats(PaperPet.itemId(food))) {
            return;
        }
        int before = held.getAmount();
        player.getScheduler().runDelayed(plugin, task -> {
            ItemStack after = player.getInventory().getItem(hand);
            boolean eaten = player.getGameMode() == GameMode.CREATIVE
                    || after.getType() != food || after.getAmount() < before;
            if (eaten) {
                events().fed(pet, PaperPet.owner(player), PaperPet.itemId(food));
            }
        }, null, 1);
    }

    /** Petting key: looking at your own pet and pressing "swap hands" (F) pets it instead of swapping. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSwapHands(PlayerSwapHandItemsEvent event) {
        Player player = event.getPlayer();
        if (!plugin.settings().pettingSwapKey || !player.hasPermission("petthoughts.pet")) {
            return;
        }
        PaperPet pet = PaperPet.of(plugin, player.getTargetEntity(PET_REACH));
        if (pet == null || !player.getUniqueId().equals(pet.ownerId())
                || !plugin.settings().speciesEnabled(pet.species())) {
            return;
        }
        event.setCancelled(true);
        pet(player, pet);
    }

    private void pet(Player player, PaperPet pet) {
        // Holding the key repeats the action: at most two pets per second.
        long now = System.currentTimeMillis();
        Long last = lastPet.put(player.getUniqueId(), now);
        if (last != null && now - last < 500) {
            return;
        }
        Tameable entity = pet.entity();
        // Above the bubble, so the hearts do not cover the text.
        Location at = entity.getLocation().add(0, entity.getHeight() + pet.species().bubbleGap() + 0.75, 0);
        if (plugin.settings().pettingHearts) {
            entity.getWorld().spawnParticle(Particle.HEART, at, 5, 0.45, 0.12, 0.45, 0);
        }
        Sound sound = entity instanceof Cat ? Sound.ENTITY_CAT_PURR
                : entity instanceof Parrot ? Sound.ENTITY_PARROT_AMBIENT : Sound.ENTITY_WOLF_PANT;
        entity.getWorld().playSound(at, sound, 0.8f, 1.1f);
        player.swingMainHand();
        events().petted(pet, PaperPet.owner(player));
    }

    // ------------------------------------------------------------------
    //  Damage and death
    // ------------------------------------------------------------------

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (event.getFinalDamage() <= 0) {
            return;
        }
        Entity victim = event.getEntity();
        if (victim instanceof Player player) {
            if (event.getFinalDamage() >= 2 && event.getFinalDamage() < player.getHealth()) {
                events().ownerHurt(PaperPet.owner(player));
            }
            return;
        }
        PaperPet pet = PaperPet.of(plugin, victim);
        Player owner = ownerOf(pet);
        if (owner == null || event.getFinalDamage() >= pet.entity().getHealth()) {
            return;
        }
        boolean byOwner = false;
        if (event instanceof EntityDamageByEntityEvent byEntity) {
            Entity damager = byEntity.getDamager();
            if (damager instanceof Projectile projectile && projectile.getShooter() instanceof Entity shooter) {
                damager = shooter;
            }
            byOwner = damager.getUniqueId().equals(owner.getUniqueId());
        }
        events().petHurt(pet, PaperPet.owner(owner), byOwner);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDeath(EntityDeathEvent event) {
        LivingEntity dead = event.getEntity();
        PaperPet deadPet = PaperPet.of(plugin, dead);
        if (deadPet != null) {
            events().petDied(deadPet);
            return;
        }
        if (dead instanceof Player || !(dead instanceof Enemy) || !Names.scary(dead.getType().getKey().getKey())) {
            return;
        }
        String mobName = PaperText.entityName(plugin.lang(), dead);
        if (dead.getLastDamageCause() instanceof EntityDamageByEntityEvent last) {
            PaperPet killerPet = PaperPet.of(plugin, last.getDamager());
            Player petOwner = ownerOf(killerPet);
            if (petOwner != null) {
                events().petKilledMob(killerPet, PaperPet.owner(petOwner), mobName);
                return;
            }
        }
        Player killer = dead.getKiller();
        if (killer != null) {
            events().ownerKilledMob(PaperPet.owner(killer), mobName);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerDeath(PlayerDeathEvent event) {
        events().ownerDied(PaperPet.owner(event.getEntity()));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        explosion(event.getLocation());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        explosion(event.getBlock().getLocation());
    }

    private void explosion(Location at) {
        for (Entity entity : at.getWorld().getNearbyEntities(at, 12, 12, 12)) {
            PaperPet pet = PaperPet.of(plugin, entity);
            Player owner = ownerOf(pet);
            if (owner != null) {
                events().explosionNear(pet, PaperPet.owner(owner));
            }
        }
    }

    // ------------------------------------------------------------------
    //  Things the owner does
    // ------------------------------------------------------------------

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onConsume(PlayerItemConsumeEvent event) {
        Material type = event.getItem().getType();
        if (type.isEdible()) {
            events().ownerAte(PaperPet.owner(event.getPlayer()), PaperPet.itemId(type));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFish(PlayerFishEvent event) {
        if (event.getState() == PlayerFishEvent.State.CAUGHT_FISH) {
            events().ownerFished(PaperPet.owner(event.getPlayer()));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        events().ownerBrokeBlock(PaperPet.owner(event.getPlayer()), PaperPet.itemId(event.getBlock().getType()));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onAdvancement(PlayerAdvancementDoneEvent event) {
        var display = event.getAdvancement().getDisplay();
        if (display != null && display.doesAnnounceToChat()) {
            events().ownerAdvancement(PaperPet.owner(event.getPlayer()));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onJukebox(PlayerInteractEvent event) {
        Block block = event.getClickedBlock();
        ItemStack item = event.getItem();
        if (event.getAction() == Action.RIGHT_CLICK_BLOCK && block != null && block.getType() == Material.JUKEBOX
                && item != null && item.getType().isRecord()) {
            events().ownerPlayedMusic(PaperPet.owner(event.getPlayer()),
                    block.getX() + 0.5, block.getY() + 0.5, block.getZ() + 0.5);
        }
    }

    // ------------------------------------------------------------------
    //  Things the pet does
    // ------------------------------------------------------------------

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(EntityTeleportEvent event) {
        PaperPet pet = PaperPet.of(plugin, event.getEntity());
        Player owner = ownerOf(pet);
        Location to = event.getTo();
        if (owner == null || to == null) {
            return;
        }
        Location from = event.getFrom();
        if (!from.getWorld().equals(to.getWorld()) || from.distanceSquared(to) > 64) {
            events().teleported(pet, PaperPet.owner(owner));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreed(EntityBreedEvent event) {
        boolean first = true;
        for (LivingEntity parent : List.of(event.getMother(), event.getFather())) {
            PaperPet pet = PaperPet.of(plugin, parent);
            Player owner = ownerOf(pet);
            if (owner != null) {
                events().hadBaby(pet, PaperPet.owner(owner), first);
                first = false;
            }
        }
    }

    // ------------------------------------------------------------------
    //  Housekeeping
    // ------------------------------------------------------------------

    /** Bubbles are not persistent, but if one ever got saved (crash, another plugin) it is removed on load. */
    @EventHandler
    public void onEntitiesLoad(EntitiesLoadEvent event) {
        for (Entity entity : event.getEntities()) {
            if (entity instanceof TextDisplay
                    && entity.getPersistentDataContainer().has(plugin.keys().bubble, PersistentDataType.BYTE)) {
                entity.remove();
            }
        }
    }
}
