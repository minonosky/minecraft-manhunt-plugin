package com.manhunt;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public class WorldResetManager implements Listener {

    private static final String WORLD_PREFIX = "manhunt_";
    private static final String CONFIG_PATH = "world-reset.active.";

    private final ManhuntPlugin plugin;
    private boolean resetting;
    private World activeOverworld;
    private World activeNether;
    private World activeEnd;

    public WorldResetManager(ManhuntPlugin plugin) {
        this.plugin = plugin;
        loadActiveWorlds();
    }

    public void requestReset(CommandSender sender, String seedArgument) {
        if (resetting) {
            sender.sendMessage(Component.text("a world reset is already in progress.", NamedTextColor.YELLOW));
            return;
        }

        Long seed = parseSeed(seedArgument);
        if (seed == null) {
            sender.sendMessage(Component.text("seed must be a whole number.", NamedTextColor.RED));
            return;
        }

        resetting = true;
        Bukkit.broadcast(Component.text("generating a fresh world...", NamedTextColor.GOLD));
        runWhenWorldsIdle(() -> createAndActivateWorlds(sender, seed), 100);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerPortal(PlayerPortalEvent event) {
        if (activeOverworld == null || event.getTo() == null || !isActiveWorld(event.getFrom().getWorld())) {
            return;
        }

        World destination = portalDestination(event.getFrom().getWorld(), event.getCause());
        if (destination == null) {
            return;
        }

        if (event.getCause() == PlayerTeleportEvent.TeleportCause.END_PORTAL
                && event.getFrom().getWorld().equals(activeEnd)) {
            event.setTo(safeSpawn(activeOverworld));
            return;
        }

        Location target = event.getTo().clone();
        target.setWorld(destination);
        event.setTo(target);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        if (activeOverworld != null && !isActiveWorld(event.getRespawnLocation().getWorld())) {
            event.setRespawnLocation(safeSpawn(activeOverworld));
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (activeOverworld == null || isActiveWorld(event.getPlayer().getWorld())) {
            return;
        }
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (event.getPlayer().isOnline() && activeOverworld != null) {
                event.getPlayer().teleport(safeSpawn(activeOverworld));
            }
        });
    }

    private void createAndActivateWorlds(CommandSender sender, long seed) {
        WorldSet previous = activeWorldSet();
        String baseName = uniqueWorldName();
        List<World> createdWorlds = new ArrayList<>();

        try {
            World overworld = createWorld(baseName, World.Environment.NORMAL, seed);
            createdWorlds.add(overworld);
            World nether = createWorld(baseName + "_nether", World.Environment.NETHER, seed);
            createdWorlds.add(nether);
            World end = createWorld(baseName + "_the_end", World.Environment.THE_END, seed);
            createdWorlds.add(end);

            activeOverworld = overworld;
            activeNether = nether;
            activeEnd = end;
            saveActiveWorlds();

            Location spawn = safeSpawn(activeOverworld);
            for (Player player : Bukkit.getOnlinePlayers()) {
                player.teleport(spawn);
            }
            plugin.getCompassTracker().reset();

            resetting = false;
            Bukkit.broadcast(Component.text("world reset complete. seed: " + seed, NamedTextColor.GREEN));
            retire(previous);
        } catch (RuntimeException exception) {
            plugin.getLogger().severe("could not create fresh manhunt worlds: " + exception.getMessage());
            cleanupFailedWorlds(createdWorlds);
            activeOverworld = previous.overworld();
            activeNether = previous.nether();
            activeEnd = previous.end();
            resetting = false;
            sender.sendMessage(Component.text(
                    "fresh world generation failed. current world is still active.", NamedTextColor.RED));
        }
    }

    private World createWorld(String name, World.Environment environment, long seed) {
        World world = new WorldCreator(name)
                .environment(environment)
                .seed(seed)
                .generateStructures(true)
                .createWorld();
        if (world == null) {
            throw new IllegalStateException("Paper returned no world for " + name);
        }
        return world;
    }

    private void loadActiveWorlds() {
        String overworldName = plugin.getConfig().getString(CONFIG_PATH + "overworld");
        String netherName = plugin.getConfig().getString(CONFIG_PATH + "nether");
        String endName = plugin.getConfig().getString(CONFIG_PATH + "end");
        if (!isManagedName(overworldName) || !isManagedName(netherName) || !isManagedName(endName)) {
            return;
        }

        try {
            activeOverworld = loadExistingWorld(overworldName, World.Environment.NORMAL);
            activeNether = loadExistingWorld(netherName, World.Environment.NETHER);
            activeEnd = loadExistingWorld(endName, World.Environment.THE_END);
            plugin.getLogger().info("loaded active manhunt world set: " + overworldName);
        } catch (RuntimeException exception) {
            plugin.getLogger().warning("could not restore the active manhunt worlds: " + exception.getMessage());
            activeOverworld = null;
            activeNether = null;
            activeEnd = null;
            clearActiveWorldConfig();
        }
    }

    private World loadExistingWorld(String name, World.Environment environment) {
        World loaded = Bukkit.getWorld(name);
        if (loaded != null) {
            return loaded;
        }

        Path folder = Bukkit.getWorldContainer().toPath().toAbsolutePath().normalize().resolve(name);
        if (!Files.isDirectory(folder)) {
            throw new IllegalStateException("missing world folder " + name);
        }

        World world = new WorldCreator(name).environment(environment).createWorld();
        if (world == null) {
            throw new IllegalStateException("could not load " + name);
        }
        return world;
    }

    private void saveActiveWorlds() {
        plugin.getConfig().set(CONFIG_PATH + "overworld", activeOverworld.getName());
        plugin.getConfig().set(CONFIG_PATH + "nether", activeNether.getName());
        plugin.getConfig().set(CONFIG_PATH + "end", activeEnd.getName());
        plugin.saveConfig();
    }

    private void clearActiveWorldConfig() {
        plugin.getConfig().set("world-reset.active", null);
        plugin.saveConfig();
    }

    private World portalDestination(World source, PlayerTeleportEvent.TeleportCause cause) {
        if (cause == PlayerTeleportEvent.TeleportCause.NETHER_PORTAL) {
            if (source.equals(activeOverworld)) {
                return activeNether;
            }
            if (source.equals(activeNether)) {
                return activeOverworld;
            }
        }
        if (cause == PlayerTeleportEvent.TeleportCause.END_PORTAL) {
            if (source.equals(activeOverworld)) {
                return activeEnd;
            }
            if (source.equals(activeEnd)) {
                return activeOverworld;
            }
        }
        return null;
    }

    private boolean isActiveWorld(World world) {
        return world != null && (world.equals(activeOverworld)
                || world.equals(activeNether)
                || world.equals(activeEnd));
    }

    private void retire(WorldSet previous) {
        for (World world : previous.worlds()) {
            if (world == null || !isManagedName(world.getName()) || isActiveWorld(world)) {
                continue;
            }

            Path folder = safeManagedFolder(world);
            if (!Bukkit.unloadWorld(world, false)) {
                plugin.getLogger().warning("could not unload retired world " + world.getName()
                        + "; its folder was left untouched.");
                continue;
            }

            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> deleteManagedWorld(folder));
        }
    }

    private void cleanupFailedWorlds(List<World> worlds) {
        for (World world : worlds.reversed()) {
            Path folder = safeManagedFolder(world);
            if (Bukkit.unloadWorld(world, false)) {
                Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> deleteManagedWorld(folder));
            }
        }
    }

    private Path safeManagedFolder(World world) {
        Path container = Bukkit.getWorldContainer().toPath().toAbsolutePath().normalize();
        Path folder = world.getWorldFolder().toPath().toAbsolutePath().normalize();
        if (!isManagedName(world.getName()) || !folder.startsWith(container)
                || folder.equals(container) || !folder.getParent().equals(container)) {
            throw new SecurityException("refusing unsafe world path: " + folder);
        }
        return folder;
    }

    private void deleteManagedWorld(Path directory) {
        try {
            Files.walkFileTree(directory, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) throws IOException {
                    Files.delete(file);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path folder, IOException exception) throws IOException {
                    if (exception != null) {
                        throw exception;
                    }
                    Files.delete(folder);
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException exception) {
            plugin.getLogger().warning("could not fully delete retired world " + directory
                    + ": " + exception.getMessage());
        }
    }

    private WorldSet activeWorldSet() {
        return new WorldSet(activeOverworld, activeNether, activeEnd);
    }

    private String uniqueWorldName() {
        String name;
        do {
            name = WORLD_PREFIX + Long.toUnsignedString(ThreadLocalRandom.current().nextLong(), 36);
        } while (Bukkit.getWorld(name) != null
                || Files.exists(Bukkit.getWorldContainer().toPath().resolve(name)));
        return name;
    }

    private boolean isManagedName(String name) {
        return name != null && name.startsWith(WORLD_PREFIX)
                && name.matches("[a-zA-Z0-9_-]+");
    }

    private void runWhenWorldsIdle(Runnable action, int attemptsRemaining) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!Bukkit.isTickingWorlds()) {
                action.run();
            } else if (attemptsRemaining > 0) {
                runWhenWorldsIdle(action, attemptsRemaining - 1);
            } else {
                resetting = false;
                plugin.getLogger().severe("timed out waiting for a safe world management tick.");
            }
        });
    }

    private Location safeSpawn(World world) {
        Location spawn = world.getSpawnLocation().clone().add(0.5, 0.1, 0.5);
        world.getChunkAt(spawn).load();
        return spawn;
    }

    public static Long parseSeed(String input) {
        if (input == null || input.isBlank()) {
            return ThreadLocalRandom.current().nextLong();
        }
        try {
            return Long.parseLong(input);
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private record WorldSet(World overworld, World nether, World end) {
        private List<World> worlds() {
            List<World> worlds = new ArrayList<>();
            if (end != null) worlds.add(end);
            if (nether != null) worlds.add(nether);
            if (overworld != null) worlds.add(overworld);
            return worlds;
        }
    }
}