package com.manhunt;

import org.bukkit.Material;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.PiglinBarterEvent;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Random;

public class RunnersLuck implements Listener {

    private final Random random = new Random();
    private final CompassTracker tracker;
    private final double endermanChance;
    private final double blazeChance;
    private final double piglinChance;

    public RunnersLuck(ManhuntPlugin plugin, CompassTracker tracker) {
        this.tracker = tracker;
        this.endermanChance = chance(plugin, "runners-luck.enderman-extra-drop-chance", 0.80);
        this.blazeChance = chance(plugin, "runners-luck.blaze-extra-drop-chance", 0.80);
        this.piglinChance = chance(plugin, "runners-luck.piglin-pearl-chance", 0.20);
    }

    private boolean isRunner(Player player) {
        return tracker.getRunners().contains(player.getUniqueId());
    }

    // vanilla is roughly 50% for 0-1 pearl, runner's luck raises the total to roughly 90%
    @EventHandler
    public void onEndermanDeath(EntityDeathEvent event) {
        if (event.getEntityType() != EntityType.ENDERMAN) return;
        Player killer = event.getEntity().getKiller();
        if (killer == null || !isRunner(killer)) return;

        boolean alreadyDropped = event.getDrops().stream()
                .anyMatch(item -> item.getType() == Material.ENDER_PEARL);
        if (!alreadyDropped && random.nextDouble() < endermanChance) {
            event.getDrops().add(new ItemStack(Material.ENDER_PEARL, 1));
        }
    }

    // vanilla is roughly 50% for 0-1 rod, runner's luck raises the total to roughly 90%
    @EventHandler
    public void onBlazeDeath(EntityDeathEvent event) {
        if (event.getEntityType() != EntityType.BLAZE) return;
        Player killer = event.getEntity().getKiller();
        if (killer == null || !isRunner(killer)) return;

        boolean alreadyDropped = event.getDrops().stream()
                .anyMatch(item -> item.getType() == Material.BLAZE_ROD);
        if (!alreadyDropped && random.nextDouble() < blazeChance) {
            event.getDrops().add(new ItemStack(Material.BLAZE_ROD, 1));
        }
    }

    // runner's luck gives non-pearl barters an additional chance to become 1-3 pearls
    @EventHandler
    public void onPiglinBarter(PiglinBarterEvent event) {
        Player nearest = event.getEntity().getLocation()
                .getNearbyPlayers(10).stream()
                .filter(this::isRunner)
                .findFirst().orElse(null);
        if (nearest == null) return;

        boolean alreadyPearls = event.getOutcome().stream()
                .anyMatch(item -> item.getType() == Material.ENDER_PEARL);
        if (alreadyPearls) return;

        List<ItemStack> outcome = event.getOutcome();
        if (random.nextDouble() < piglinChance) {
            outcome.clear();
            int count = 1 + random.nextInt(3);
            outcome.add(new ItemStack(Material.ENDER_PEARL, count));
        }
    }

    private double chance(ManhuntPlugin plugin, String path, double defaultValue) {
        return Math.max(0.0, Math.min(1.0, plugin.getConfig().getDouble(path, defaultValue)));
    }
}
