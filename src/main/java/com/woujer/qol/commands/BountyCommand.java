package com.woujer.qol.commands;

import com.woujer.qol.manager.BountyManager;
import com.woujer.qol.manager.BountyManager.TargetBounty;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class BountyCommand implements CommandExecutor, TabCompleter {

    private final BountyManager bountyManager;

    public BountyCommand(BountyManager bountyManager) {
        this.bountyManager = bountyManager;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Only players can use bounty commands.", NamedTextColor.RED));
            return true;
        }

        // Default to showing the leaderboard if they type "/bounty" or "/bounty list"
        if (args.length == 0 || args[0].equalsIgnoreCase("list")) {
            sendBountyList(player);
            return true;
        }

        if (args[0].equalsIgnoreCase("items") || args[0].equalsIgnoreCase("rates")) {
            sendAllowedItems(player);
            return true;
        }

        if (args[0].equalsIgnoreCase("remove") || args[0].equalsIgnoreCase("cancel")) {
            if (args.length < 2) {
                player.sendMessage(Component.text("Usage: /bounty remove <player>", NamedTextColor.RED));
                return true;
            }
            bountyManager.removeBounty(player, args[1]);
            return true;
        }

        if (args[0].equalsIgnoreCase("help")) {
            sendHelp(player);
            return true;
        }

        // Support both "/bounty add <player> <item> <amount>" AND "/bounty <player> <item> <amount>"
        int offset = args[0].equalsIgnoreCase("add") ? 1 : 0;
        if (args.length < offset + 3) {
            sendHelp(player);
            return true;
        }

        String targetName = args[offset];
        String matName = args[offset + 1];
        String amountStr = args[offset + 2];

        Player target = Bukkit.getPlayerExact(targetName);
        if (target == null || !target.isOnline()) {
            player.sendMessage(Component.text("Player '" + targetName + "' is not online.", NamedTextColor.RED));
            return true;
        }

        Material material = Material.matchMaterial(matName);
        if (material == null || !BountyManager.isAllowed(material)) {
            player.sendMessage(Component.text("Invalid or disallowed bounty item! Type ", NamedTextColor.RED)
                .append(Component.text("/bounty items", NamedTextColor.YELLOW))
                .append(Component.text(" to view allowed items.", NamedTextColor.RED)));
            return true;
        }

        int amount;
        try {
            amount = Integer.parseInt(amountStr);
        } catch (NumberFormatException e) {
            player.sendMessage(Component.text("Invalid amount: " + amountStr, NamedTextColor.RED));
            return true;
        }

        bountyManager.addBounty(player, target, material, amount);
        return true;
    }

    private void sendBountyList(Player player) {
        List<TargetBounty> sorted = bountyManager.getSortedBounties();
        if (sorted.isEmpty()) {
            player.sendMessage(Component.text("There are currently no active bounties on Parquet SMP.", NamedTextColor.YELLOW));
            return;
        }

        player.sendMessage(Component.text("\n=== 🎯 ACTIVE BOUNTIES (HIGHEST TO LOWEST) ===", NamedTextColor.GOLD, TextDecoration.BOLD));

        int rank = 1;
        for (TargetBounty tb : sorted) {
            Map<Material, Integer> combined = tb.getCombinedItems();
            StringBuilder itemsStr = new StringBuilder();
            for (Map.Entry<Material, Integer> entry : combined.entrySet()) {
                if (!itemsStr.isEmpty()) itemsStr.append(", ");
                itemsStr.append(entry.getValue()).append("x ").append(BountyManager.formatMaterial(entry.getKey()));
            }

            String sponsorsList = String.join(", ", tb.getSponsorNames().values());
            Component hoverInfo = Component.text("Sponsors: ", NamedTextColor.GRAY)
                .append(Component.text(sponsorsList, NamedTextColor.YELLOW))
                .append(Component.text("\nKill " + tb.getTargetName() + " in PvP to claim!", NamedTextColor.GREEN));

            Component line = Component.text("#" + rank + " ", NamedTextColor.DARK_GRAY, TextDecoration.BOLD)
                .append(Component.text(tb.getTargetName(), NamedTextColor.RED, TextDecoration.BOLD))
                .append(Component.text(" - ", NamedTextColor.DARK_GRAY))
                .append(Component.text(tb.getTotalValue() + " BP ", NamedTextColor.GOLD, TextDecoration.BOLD))
                .append(Component.text("[" + itemsStr + "]", NamedTextColor.AQUA))
                .hoverEvent(HoverEvent.showText(hoverInfo));

            player.sendMessage(line);
            rank++;
        }
        player.sendMessage(Component.text("============================================\n", NamedTextColor.GOLD, TextDecoration.BOLD));
    }

    private void sendAllowedItems(Player player) {
        player.sendMessage(Component.text("\n=== 💎 ALLOWED BOUNTY ITEMS & VALUES ===", NamedTextColor.GOLD, TextDecoration.BOLD));
        for (Map.Entry<Material, Integer> entry : BountyManager.getAllowedItems().entrySet()) {
            player.sendMessage(
                Component.text("• ", NamedTextColor.DARK_GRAY)
                    .append(Component.text(BountyManager.formatMaterial(entry.getKey()), NamedTextColor.AQUA))
                    .append(Component.text(" = ", NamedTextColor.GRAY))
                    .append(Component.text(entry.getValue() + " BP each", NamedTextColor.GOLD))
            );
        }
        player.sendMessage(Component.text("(Note: Gear like Elytras/Tridents must be unenchanted & 100% durability)\n", NamedTextColor.GRAY));
    }

    private void sendHelp(Player player) {
        player.sendMessage(Component.text("\n=== 🎯 BOUNTY COMMANDS ===", NamedTextColor.GOLD, TextDecoration.BOLD));
        player.sendMessage(Component.text("/bounty <player> <item> <amount>", NamedTextColor.YELLOW)
            .append(Component.text(" - Place a bounty on a player", NamedTextColor.GRAY)));
        player.sendMessage(Component.text("/bounty list", NamedTextColor.YELLOW)
            .append(Component.text(" - View active bounties (Highest to Lowest)", NamedTextColor.GRAY)));
        player.sendMessage(Component.text("/bounty remove <player>", NamedTextColor.YELLOW)
            .append(Component.text(" - Withdraw your bounty on a player", NamedTextColor.GRAY)));
        player.sendMessage(Component.text("/bounty items", NamedTextColor.YELLOW)
            .append(Component.text(" - View allowed items & point values\n", NamedTextColor.GRAY)));
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            List<String> suggestions = new ArrayList<>(List.of("list", "add", "remove", "items", "help"));
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (!p.getName().equalsIgnoreCase(sender.getName())) {
                    suggestions.add(p.getName());
                }
            }
            return filter(suggestions, args[0]);
        }

        if (args.length == 2) {
            if (args[0].equalsIgnoreCase("remove") || args[0].equalsIgnoreCase("cancel")) {
                List<String> targets = new ArrayList<>();
                for (TargetBounty tb : bountyManager.getSortedBounties()) {
                    targets.add(tb.getTargetName());
                }
                return filter(targets, args[1]);
            }

            if (args[0].equalsIgnoreCase("add")) {
                List<String> players = new ArrayList<>();
                for (Player p : Bukkit.getOnlinePlayers()) {
                    if (!p.getName().equalsIgnoreCase(sender.getName())) {
                        players.add(p.getName());
                    }
                }
                return filter(players, args[1]);
            }

            // Otherwise args[0] is a player name, so args[1] is the Material!
            if (!args[0].equalsIgnoreCase("list") && !args[0].equalsIgnoreCase("items") && !args[0].equalsIgnoreCase("help")) {
                return filterMaterials(args[1]);
            }
        }

        if (args.length == 3) {
            if (args[0].equalsIgnoreCase("add")) {
                return filterMaterials(args[2]);
            }
            if (!args[0].equalsIgnoreCase("remove") && !args[0].equalsIgnoreCase("list") && !args[0].equalsIgnoreCase("items")) {
                return filter(List.of("1", "4", "8", "16", "32", "64"), args[2]);
            }
        }

        if (args.length == 4 && args[0].equalsIgnoreCase("add")) {
            return filter(List.of("1", "4", "8", "16", "32", "64"), args[3]);
        }

        return List.of();
    }

    private List<String> filterMaterials(String input) {
        List<String> mats = new ArrayList<>();
        for (Material mat : BountyManager.getAllowedItems().keySet()) {
            mats.add(mat.name().toLowerCase());
        }
        return filter(mats, input);
    }

    private List<String> filter(List<String> list, String prefix) {
        List<String> result = new ArrayList<>();
        for (String s : list) {
            if (s.toLowerCase().startsWith(prefix.toLowerCase())) {
                result.add(s);
            }
        }
        return result;
    }
}