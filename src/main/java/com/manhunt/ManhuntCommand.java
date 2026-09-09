package com.manhunt;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Collection;
import java.util.UUID;

public class ManhuntCommand implements CommandExecutor {

    private final ManhuntPlugin plugin;

    public ManhuntCommand(ManhuntPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sendUsage(sender);
            return true;
        }

        CompassTracker tracker = plugin.getCompassTracker();
        String subcommand = args[0].toLowerCase();

        switch (subcommand) {
            case "r" -> addRole(sender, tracker, true, args);
            case "h" -> addRole(sender, tracker, false, args);
            case "reset" -> {
                tracker.reset();
                Bukkit.broadcast(Component.text("manhunt has been reset!", NamedTextColor.YELLOW));
            }
            case "status" -> sendStatus(sender, tracker);
            case "resetworld" -> plugin.getWorldResetManager()
                    .requestReset(sender, args.length >= 2 ? args[1] : null);
            default -> sendUsage(sender);
        }

        return true;
    }

    private void addRole(CommandSender sender, CompassTracker tracker, boolean runner, String[] args) {
        if (args.length < 2) {
            sender.sendMessage(Component.text("please provide an online player name.", NamedTextColor.RED));
            return;
        }

        Player player = Bukkit.getPlayerExact(args[1]);
        if (player == null) {
            sender.sendMessage(Component.text("player is not online.", NamedTextColor.RED));
            return;
        }

        boolean added = runner ? tracker.addRunner(player) : tracker.addHunter(player);
        if (!added) {
            sender.sendMessage(Component.text(player.getName() + " already has that role.", NamedTextColor.YELLOW));
            return;
        }

        String role = runner ? "runner" : "hunter";
        Bukkit.broadcast(Component.text(player.getName() + " is now a " + role + "!", NamedTextColor.GOLD));
        if (!runner) {
            player.sendMessage(Component.text("use your tracker to hunt the runner!", NamedTextColor.GREEN));
        }
    }

    private void sendStatus(CommandSender sender, CompassTracker tracker) {
        sender.sendMessage(Component.text("=== Manhunt Status ===", NamedTextColor.GOLD));
        sender.sendMessage(Component.text("Runners: " + names(tracker.getRunners()), NamedTextColor.WHITE));
        sender.sendMessage(Component.text("Hunters: " + names(tracker.getHunters()), NamedTextColor.WHITE));
    }

    private String names(Collection<UUID> playerIds) {
        if (playerIds.isEmpty()) {
            return "none";
        }
        return playerIds.stream()
                .map(Bukkit::getOfflinePlayer)
                .map(player -> player.getName() != null ? player.getName() : player.getUniqueId().toString())
                .reduce((first, second) -> first + ", " + second)
                .orElse("none");
    }

    private void sendUsage(CommandSender sender) {
        sender.sendMessage(Component.text("=== Manhunt Commands ===", NamedTextColor.GOLD));
        sender.sendMessage(commandHelp("/manhunt r <player>", "set a runner"));
        sender.sendMessage(commandHelp("/manhunt h <player>", "set a hunter"));
        sender.sendMessage(commandHelp("/manhunt status", "show current roles"));
        sender.sendMessage(commandHelp("/manhunt reset", "reset the game"));
        sender.sendMessage(commandHelp("/manhunt resetworld [seed]", "generate a fresh world"));
    }

    private Component commandHelp(String command, String description) {
        return Component.text(command, NamedTextColor.WHITE)
                .append(Component.text(" - " + description, NamedTextColor.GRAY));
    }
}
