package com.woujer.qol.manager;

import com.woujer.qol.WoujersQOL;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;

import java.io.File;
import java.io.IOException;
import java.util.*;

public final class BountyManager {

    private final WoujersQOL plugin;
    private final File file;
    private FileConfiguration config;

    // Allowed high-tier survival items and their point value for sorting Highest -> Lowest
    private static final Map<Material, Integer> ALLOWED_ITEMS = new LinkedHashMap<>();

    static {
        ALLOWED_ITEMS.put(Material.IRON_INGOT, 1);
        ALLOWED_ITEMS.put(Material.GOLD_INGOT, 2);
        ALLOWED_ITEMS.put(Material.EMERALD, 3);
        ALLOWED_ITEMS.put(Material.IRON_BLOCK, 9);
        ALLOWED_ITEMS.put(Material.DIAMOND, 10);
        ALLOWED_ITEMS.put(Material.GOLD_BLOCK, 18);
        ALLOWED_ITEMS.put(Material.NETHERITE_SCRAP, 25);
        ALLOWED_ITEMS.put(Material.ANCIENT_DEBRIS, 25);
        ALLOWED_ITEMS.put(Material.EMERALD_BLOCK, 27);
        ALLOWED_ITEMS.put(Material.TOTEM_OF_UNDYING, 50);
        ALLOWED_ITEMS.put(Material.TRIDENT, 75);
        ALLOWED_ITEMS.put(Material.DIAMOND_BLOCK, 90);
        ALLOWED_ITEMS.put(Material.NETHERITE_INGOT, 100);
        ALLOWED_ITEMS.put(Material.ENCHANTED_GOLDEN_APPLE, 150);
        ALLOWED_ITEMS.put(Material.NETHER_STAR, 200);
        ALLOWED_ITEMS.put(Material.HEAVY_CORE, 250);
        ALLOWED_ITEMS.put(Material.ELYTRA, 300);
        ALLOWED_ITEMS.put(Material.NETHERITE_BLOCK, 900);
    }

    // TargetUUID -> TargetBountyData
    private final Map<UUID, TargetBounty> bounties = new HashMap<>();
    // TargetUUID -> Last PvP combat timestamp (ms) to prevent mid-fight bounty removal
    private final Map<UUID, Long> combatTag = new HashMap<>();

    public static class TargetBounty {
        private final UUID targetUuid;
        private String targetName;
        // SponsorUUID -> (Material -> Amount)
        private final Map<UUID, Map<Material, Integer>> contributions = new HashMap<>();
        private final Map<UUID, String> sponsorNames = new HashMap<>();

        public TargetBounty(UUID targetUuid, String targetName) {
            this.targetUuid = targetUuid;
            this.targetName = targetName;
        }

        public UUID getTargetUuid() { return targetUuid; }
        public String getTargetName() { return targetName; }
        public void setTargetName(String name) { this.targetName = name; }
        public Map<UUID, Map<Material, Integer>> getContributions() { return contributions; }
        public Map<UUID, String> getSponsorNames() { return sponsorNames; }

        public int getTotalValue() {
            int total = 0;
            for (Map<Material, Integer> items : contributions.values()) {
                for (Map.Entry<Material, Integer> entry : items.entrySet()) {
                    total += ALLOWED_ITEMS.getOrDefault(entry.getKey(), 0) * entry.getValue();
                }
            }
            return total;
        }

        public Map<Material, Integer> getCombinedItems() {
            Map<Material, Integer> combined = new EnumMap<>(Material.class);
            for (Map<Material, Integer> items : contributions.values()) {
                for (Map.Entry<Material, Integer> entry : items.entrySet()) {
                    combined.merge(entry.getKey(), entry.getValue(), Integer::sum);
                }
            }
            return combined;
        }
    }

    public BountyManager(WoujersQOL plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "bounties.yml");
        loadBounties();
    }

    public static Map<Material, Integer> getAllowedItems() {
        return Collections.unmodifiableMap(ALLOWED_ITEMS);
    }

    public static boolean isAllowed(Material material) {
        return ALLOWED_ITEMS.containsKey(material);
    }

    public void tagCombat(UUID playerUuid) {
        combatTag.put(playerUuid, System.currentTimeMillis());
    }

    public boolean isInCombat(UUID playerUuid) {
        Long lastHit = combatTag.get(playerUuid);
        return lastHit != null && (System.currentTimeMillis() - lastHit) < 15_000L;
    }

    /**
     * Places a bounty on a target player by removing clean items from the sponsor's inventory.
     */
    public void addBounty(Player sponsor, Player target, Material material, int amount) {
        if (sponsor.getUniqueId().equals(target.getUniqueId())) {
            sponsor.sendMessage(Component.text("You cannot place a bounty on yourself!", NamedTextColor.RED));
            return;
        }

        if (!isAllowed(material)) {
            sponsor.sendMessage(Component.text("That item cannot be used as a bounty! Use ", NamedTextColor.RED)
                .append(Component.text("/bounty items", NamedTextColor.YELLOW))
                .append(Component.text(" to see valid valuables.", NamedTextColor.RED)));
            return;
        }

        if (amount <= 0) {
            sponsor.sendMessage(Component.text("Amount must be at least 1.", NamedTextColor.RED));
            return;
        }

        int available = countCleanItems(sponsor, material);
        if (available < amount) {
            sponsor.sendMessage(Component.text("You do not have " + amount + "x clean, undamaged " + formatMaterial(material) + " in your inventory!", NamedTextColor.RED));
            return;
        }

        removeCleanItems(sponsor, material, amount);

        TargetBounty tb = bounties.computeIfAbsent(target.getUniqueId(), id -> new TargetBounty(id, target.getName()));
        tb.setTargetName(target.getName());
        tb.getSponsorNames().put(sponsor.getUniqueId(), sponsor.getName());

        Map<Material, Integer> sponsorItems = tb.getContributions().computeIfAbsent(sponsor.getUniqueId(), id -> new EnumMap<>(Material.class));
        sponsorItems.merge(material, amount, Integer::sum);

        saveBounties();

        int totalVal = tb.getTotalValue();

        Bukkit.broadcast(
            Component.text("[", NamedTextColor.DARK_GRAY)
                .append(Component.text("🎯 BOUNTY", NamedTextColor.GOLD, TextDecoration.BOLD))
                .append(Component.text("] ", NamedTextColor.DARK_GRAY))
                .append(Component.text(sponsor.getName(), NamedTextColor.YELLOW, TextDecoration.BOLD))
                .append(Component.text(" added ", NamedTextColor.GRAY))
                .append(Component.text(amount + "x " + formatMaterial(material), NamedTextColor.AQUA, TextDecoration.BOLD))
                .append(Component.text(" to the bounty on ", NamedTextColor.GRAY))
                .append(Component.text(target.getName(), NamedTextColor.RED, TextDecoration.BOLD))
                .append(Component.text("! (Total Value: ", NamedTextColor.GRAY))
                .append(Component.text(totalVal + " BP", NamedTextColor.GOLD))
                .append(Component.text(")", NamedTextColor.GRAY))
        );

        for (Player p : Bukkit.getOnlinePlayers()) {
            p.playSound(p.getLocation(), Sound.BLOCK_BELL_USE, 0.7f, 1.2f);
        }
    }

    /**
     * Refunds and removes a sponsor's bounty contribution on a target.
     */
    public void removeBounty(Player sponsor, String targetName) {
        TargetBounty foundBounty = null;
        for (TargetBounty tb : bounties.values()) {
            if (tb.getTargetName().equalsIgnoreCase(targetName)) {
                foundBounty = tb;
                break;
            }
        }

        if (foundBounty == null || !foundBounty.getContributions().containsKey(sponsor.getUniqueId())) {
            sponsor.sendMessage(Component.text("You do not have an active bounty placed on " + targetName + ".", NamedTextColor.RED));
            return;
        }

        if (isInCombat(foundBounty.getTargetUuid())) {
            sponsor.sendMessage(Component.text("You cannot withdraw a bounty while " + foundBounty.getTargetName() + " is in active PvP combat!", NamedTextColor.RED));
            return;
        }

        Map<Material, Integer> refundedItems = foundBounty.getContributions().remove(sponsor.getUniqueId());
        foundBounty.getSponsorNames().remove(sponsor.getUniqueId());

        if (foundBounty.getContributions().isEmpty()) {
            bounties.remove(foundBounty.getTargetUuid());
        }

        saveBounties();
        giveItemsSafely(sponsor, refundedItems);

        sponsor.sendMessage(Component.text("Your bounty on ", NamedTextColor.GREEN)
            .append(Component.text(foundBounty.getTargetName(), NamedTextColor.YELLOW, TextDecoration.BOLD))
            .append(Component.text(" has been withdrawn and returned to your inventory.", NamedTextColor.GREEN)));
        sponsor.playSound(sponsor.getLocation(), Sound.ENTITY_ITEM_PICKUP, 1.0f, 1.0f);
    }

    /**
     * Triggered when a player is killed by another player in PvP.
     */
    public void claimBounty(Player killer, Player victim) {
        TargetBounty tb = bounties.remove(victim.getUniqueId());
        if (tb == null) return;

        Map<Material, Integer> rewardItems = tb.getCombinedItems();
        int totalValue = tb.getTotalValue();
        saveBounties();

        giveItemsSafely(killer, rewardItems);

        StringBuilder summary = new StringBuilder();
        for (Map.Entry<Material, Integer> entry : rewardItems.entrySet()) {
            if (!summary.isEmpty()) summary.append(", ");
            summary.append(entry.getValue()).append("x ").append(formatMaterial(entry.getKey()));
        }

        Bukkit.broadcast(
            Component.text("[", NamedTextColor.DARK_GRAY)
                .append(Component.text("🎯 BOUNTY CLAIMED", NamedTextColor.GREEN, TextDecoration.BOLD))
                .append(Component.text("] ", NamedTextColor.DARK_GRAY))
                .append(Component.text(killer.getName(), NamedTextColor.GOLD, TextDecoration.BOLD))
                .append(Component.text(" eliminated ", NamedTextColor.GRAY))
                .append(Component.text(victim.getName(), NamedTextColor.RED, TextDecoration.BOLD))
                .append(Component.text(" and claimed: ", NamedTextColor.GRAY))
                .append(Component.text(summary.toString(), NamedTextColor.AQUA, TextDecoration.BOLD))
                .append(Component.text(" (" + totalValue + " BP)!", NamedTextColor.GOLD))
        );

        killer.playSound(killer.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
    }

    public List<TargetBounty> getSortedBounties() {
        List<TargetBounty> list = new ArrayList<>(bounties.values());
        list.sort(Comparator.comparingInt(TargetBounty::getTotalValue).reversed());
        return list;
    }

    public boolean hasBounty(UUID targetUuid) {
        return bounties.containsKey(targetUuid);
    }

    private int countCleanItems(Player player, Material material) {
        int count = 0;
        for (ItemStack item : player.getInventory().getStorageContents()) {
            if (isCleanMatch(item, material)) {
                count += item.getAmount();
            }
        }
        return count;
    }

    private void removeCleanItems(Player player, Material material, int amountToRemove) {
        int remaining = amountToRemove;
        ItemStack[] contents = player.getInventory().getStorageContents();
        for (int i = 0; i < contents.length && remaining > 0; i++) {
            ItemStack item = contents[i];
            if (isCleanMatch(item, material)) {
                int take = Math.min(remaining, item.getAmount());
                item.setAmount(item.getAmount() - take);
                remaining -= take;
                if (item.getAmount() <= 0) {
                    contents[i] = null;
                }
            }
        }
        player.getInventory().setStorageContents(contents);
    }

    private boolean isCleanMatch(ItemStack item, Material material) {
        if (item == null || item.getType() != material) return false;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return true;
        if (meta.hasDisplayName() || meta.hasEnchants() || meta.hasLore()) return false;
        if (meta instanceof Damageable damageable && damageable.hasDamage()) return false;
        return true;
    }

    private void giveItemsSafely(Player player, Map<Material, Integer> items) {
        for (Map.Entry<Material, Integer> entry : items.entrySet()) {
            Material mat = entry.getKey();
            int remaining = entry.getValue();
            int maxStack = mat.getMaxStackSize();

            while (remaining > 0) {
                int stackSize = Math.min(remaining, maxStack);
                ItemStack stack = new ItemStack(mat, stackSize);
                Map<Integer, ItemStack> overflow = player.getInventory().addItem(stack);
                for (ItemStack drop : overflow.values()) {
                    player.getWorld().dropItemNaturally(player.getLocation(), drop);
                }
                remaining -= stackSize;
            }
        }
    }

    public static String formatMaterial(Material material) {
        String raw = material.name().toLowerCase().replace('_', ' ');
        String[] words = raw.split(" ");
        StringBuilder sb = new StringBuilder();
        for (String w : words) {
            if (!w.isEmpty()) {
                sb.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1)).append(" ");
            }
        }
        return sb.toString().trim();
    }

    public void loadBounties() {
        bounties.clear();
        if (!file.exists()) return;

        config = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection root = config.getConfigurationSection("bounties");
        if (root == null) return;

        for (String targetUuidStr : root.getKeys(false)) {
            try {
                UUID targetUuid = UUID.fromString(targetUuidStr);
                ConfigurationSection targetSec = root.getConfigurationSection(targetUuidStr);
                if (targetSec == null) continue;

                String targetName = targetSec.getString("targetName", "Unknown");
                TargetBounty tb = new TargetBounty(targetUuid, targetName);

                ConfigurationSection sponsorsSec = targetSec.getConfigurationSection("sponsors");
                if (sponsorsSec != null) {
                    for (String sponsorUuidStr : sponsorsSec.getKeys(false)) {
                        UUID sponsorUuid = UUID.fromString(sponsorUuidStr);
                        ConfigurationSection spSec = sponsorsSec.getConfigurationSection(sponsorUuidStr);
                        if (spSec == null) continue;

                        String sponsorName = spSec.getString("name", "Unknown");
                        tb.getSponsorNames().put(sponsorUuid, sponsorName);

                        ConfigurationSection itemsSec = spSec.getConfigurationSection("items");
                        if (itemsSec != null) {
                            Map<Material, Integer> matMap = new EnumMap<>(Material.class);
                            for (String matName : itemsSec.getKeys(false)) {
                                Material mat = Material.matchMaterial(matName);
                                int amt = itemsSec.getInt(matName, 0);
                                if (mat != null && amt > 0) {
                                    matMap.put(mat, amt);
                                }
                            }
                            if (!matMap.isEmpty()) {
                                tb.getContributions().put(sponsorUuid, matMap);
                            }
                        }
                    }
                }

                if (!tb.getContributions().isEmpty()) {
                    bounties.put(targetUuid, tb);
                }
            } catch (IllegalArgumentException ignored) {}
        }
    }

    public void saveBounties() {
        config = new YamlConfiguration();
        ConfigurationSection root = config.createSection("bounties");

        for (TargetBounty tb : bounties.values()) {
            ConfigurationSection targetSec = root.createSection(tb.getTargetUuid().toString());
            targetSec.set("targetName", tb.getTargetName());
            ConfigurationSection sponsorsSec = targetSec.createSection("sponsors");

            for (Map.Entry<UUID, Map<Material, Integer>> entry : tb.getContributions().entrySet()) {
                UUID sponsorUuid = entry.getKey();
                ConfigurationSection spSec = sponsorsSec.createSection(sponsorUuid.toString());
                spSec.set("name", tb.getSponsorNames().getOrDefault(sponsorUuid, "Unknown"));
                ConfigurationSection itemsSec = spSec.createSection("items");

                for (Map.Entry<Material, Integer> itemEntry : entry.getValue().entrySet()) {
                    itemsSec.set(itemEntry.getKey().name(), itemEntry.getValue());
                }
            }
        }

        try {
            if (!plugin.getDataFolder().exists()) {
                plugin.getDataFolder().mkdirs();
            }
            config.save(file);
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to save bounties.yml: " + e.getMessage());
        }
    }
}