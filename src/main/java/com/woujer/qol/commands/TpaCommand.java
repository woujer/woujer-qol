package com.woujer.qol.commands;

import com.woujer.qol.manager.TpaManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public final class TpaCommand implements CommandExecutor, TabCompleter {

    private final TpaManager tpaManager;

    public TpaCommand(TpaManager tpaManager) {
        this.tpaManager = tpaManager;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("This command can only be executed by players.", NamedTextColor.RED));
            return true;
        }

        switch (command.getName().toLowerCase()) {
            case "tpa" -> {
                if (args.length < 1) {
                    player.sendMessage(Component.text("Usage: /tpa <player>", NamedTextColor.RED));
                    return true;
                }
                Player target = Bukkit.getPlayerExact(args[0]);
                if (target == null || !target.isOnline()) {
                    player.sendMessage(Component.text("Player not found or offline.", NamedTextColor.RED));
                    return true;
                }
                tpaManager.sendRequest(player, target);
            }
            case "tpaccept" -> tpaManager.acceptRequest(player);
            case "tpdeny" -> tpaManager.denyRequest(player);
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (command.getName().equalsIgnoreCase("tpa") && args.length == 1) {
            List<String> names = new ArrayList<>();
            for (Player target : Bukkit.getOnlinePlayers()) {
                if (!target.getName().equalsIgnoreCase(sender.getName()) 
                        && target.getName().toLowerCase().startsWith(args[0].toLowerCase())) {
                    names.add(target.getName());
                }
            }
            return names;
        }
        return List.of();
    }
}