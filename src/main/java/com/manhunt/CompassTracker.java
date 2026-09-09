package com.manhunt;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class CompassTracker implements Listener {

    private final ManhuntPlugin plugin;
    private final ManhuntGame game = new ManhuntGame();
    private final NamespacedKey trackerCompassKey;
    private final Map<UUID, Map<String, Location>> lastKnownLocations = new HashMap<>();

    public CompassTracker(ManhuntPlugin plugin) {
        this.plugin = plugin;
        this.trackerCompassKey = new NamespacedKey(plugin, "tracker_compass");
    }

    public boolean addRunner(Player player) {
        UUID playerId = player.getUniqueId();
        boolean wasHunter = game.isHunter(playerId);
        boolean added = game.addRunner(playerId);
        if (wasHunter) {
            removeTrackingCompasses(player);
        }
        return added;
    }

    public boolean addHunter(Player player) {
        UUID playerId = player.getUniqueId();
        if (game.isRunner(playerId)) {
            lastKnownLocations.remove(playerId);
        }
        boolean added = game.addHunter(playerId);
        giveCompass(player);
        return added;
    }

    public boolean removeRunner(UUID playerId) {
        lastKnownLocations.remove(playerId);
        return game.removeRunner(playerId);
    }

    public boolean removeHunter(UUID playerId) {
        boolean removed = game.removeHunter(playerId);
        Player player = Bukkit.getPlayer(playerId);
        if (player != null) {
            removeTrackingCompasses(player);
        }
        return removed;
    }

    public void reset() {
        for (UUID playerId : game.getHunters()) {
            Player player = Bukkit.getPlayer(playerId);
            if (player != null) {
                removeTrackingCompasses(player);
            }
        }
        game.reset();
        lastKnownLocations.clear();
    }

    // saves runner locations and updates hunter compasses
    public void updateCompasses() {
        List<UUID> runners = game.getRunners();
        if (runners.isEmpty()) {
            return;
        }

        for (UUID runnerId : runners) {
            Player runner = Bukkit.getPlayer(runnerId);
            if (runner != null && runner.isOnline()) {
                lastKnownLocations
                        .computeIfAbsent(runnerId, ignored -> new HashMap<>())
                        .put(runner.getWorld().getName(), runner.getLocation());
            }
        }

        for (UUID hunterId : game.getHunters()) {
            Player hunter = Bukkit.getPlayer(hunterId);
            if (hunter == null || !hunter.isOnline()) {
                continue;
            }

            int selectedIndex = normalizeIndex(game.getTrackingIndex(hunterId), runners.size());
            int onlineIndex = findOnlineRunnerIndex(runners, selectedIndex);
            if (onlineIndex >= 0) {
                selectedIndex = onlineIndex;
                game.setTrackingIndex(hunterId, selectedIndex);
            }

            UUID targetId = runners.get(selectedIndex);
            Player target = Bukkit.getPlayer(targetId);
            if (target != null && target.isOnline() && hunter.getWorld().equals(target.getWorld())) {
                hunter.setCompassTarget(target.getLocation());
                continue;
            }

            Location lastLocation = lastKnownLocations
                    .getOrDefault(targetId, Map.of())
                    .get(hunter.getWorld().getName());
            hunter.setCompassTarget(lastLocation != null
                    ? lastLocation
                    : hunter.getWorld().getSpawnLocation());
        }
    }

    // right-click to cycle between runners
    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        if (!game.isHunter(player.getUniqueId()) || !isTrackingCompass(event.getItem())) {
            return;
        }
        if (!event.getAction().isRightClick()) {
            return;
        }

        List<UUID> runners = game.getRunners();
        if (runners.size() <= 1) {
            return;
        }

        int current = normalizeIndex(game.getTrackingIndex(player.getUniqueId()), runners.size());
        int next = findOnlineRunnerIndex(runners, (current + 1) % runners.size());
        if (next < 0 || next == current) {
            player.sendMessage(Component.text("no other runners are currently online.", NamedTextColor.YELLOW));
            return;
        }

        game.setTrackingIndex(player.getUniqueId(), next);
        Player target = Bukkit.getPlayer(runners.get(next));
        String targetName = target != null ? target.getName() : "unknown";
        player.sendMessage(Component.text("now tracking: ", NamedTextColor.YELLOW)
                .append(Component.text(targetName, NamedTextColor.WHITE)));
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        if (game.isHunter(event.getEntity().getUniqueId())) {
            event.getDrops().removeIf(this::isTrackingCompass);
        }
    }

    @EventHandler
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        if (game.isHunter(player.getUniqueId())) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> giveCompass(player), 1L);
        }
    }

    public void giveCompass(Player player) {
        for (ItemStack item : player.getInventory().getContents()) {
            if (isTrackingCompass(item)) {
                applyTrackerMeta(item);
                return;
            }
        }

        ItemStack compass = new ItemStack(Material.COMPASS);
        applyTrackerMeta(compass);

        Map<Integer, ItemStack> overflow = player.getInventory().addItem(compass);
        overflow.values().forEach(item -> player.getWorld().dropItemNaturally(player.getLocation(), item));
    }

    private void applyTrackerMeta(ItemStack compass) {
        var meta = compass.getItemMeta();
        meta.displayName(color(plugin.getConfig().getString("compass.display-name", "&ctracker")));
        meta.lore(List.of(color(plugin.getConfig().getString(
                "compass.description", "&7right-click to cycle runners"))));
        meta.getPersistentDataContainer().set(trackerCompassKey, PersistentDataType.BYTE, (byte) 1);
        compass.setItemMeta(meta);
    }

    public List<UUID> getRunners() {
        return game.getRunners();
    }

    public Set<UUID> getHunters() {
        return game.getHunters();
    }

    private boolean isTrackingCompass(ItemStack item) {
        if (item == null || item.getType() != Material.COMPASS || !item.hasItemMeta()) {
            return false;
        }
        return item.getItemMeta().getPersistentDataContainer()
                .has(trackerCompassKey, PersistentDataType.BYTE);
    }

    private void removeTrackingCompasses(Player player) {
        for (int slot = 0; slot < player.getInventory().getSize(); slot++) {
            if (isTrackingCompass(player.getInventory().getItem(slot))) {
                player.getInventory().setItem(slot, null);
            }
        }
    }

    private int findOnlineRunnerIndex(List<UUID> runners, int startingIndex) {
        for (int offset = 0; offset < runners.size(); offset++) {
            int index = (startingIndex + offset) % runners.size();
            Player runner = Bukkit.getPlayer(runners.get(index));
            if (runner != null && runner.isOnline()) {
                return index;
            }
        }
        return -1;
    }

    private int normalizeIndex(int index, int size) {
        return Math.floorMod(index, size);
    }

    private Component color(String text) {
        return LegacyComponentSerializer.legacyAmpersand().deserialize(text);
    }
}
