package com.woujer.qol.listeners;

import com.woujer.qol.WoujersQOL;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.Skull;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public final class HeadDropListener implements Listener {

    private final NamespacedKey loreKey;
    private final NamespacedKey nameKey;
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd MMM yyyy");

    public HeadDropListener(WoujersQOL plugin) {
        this.loreKey = new NamespacedKey(plugin, "trophy_lore");
        this.nameKey = new NamespacedKey(plugin, "trophy_name");
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        Player killer = victim.getKiller();

        // Only trigger on actual PvP kills (not self-inflicted or mob deaths)
        if (killer == null || killer.getUniqueId().equals(victim.getUniqueId())) {
            return;
        }

        ItemStack skull = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) skull.getItemMeta();

        if (meta != null) {
            meta.setOwningPlayer(victim);

            // 1. Trophy Display Name
            Component displayName = Component.text(victim.getName() + "'s Head", NamedTextColor.GOLD, TextDecoration.BOLD)
                    .decoration(TextDecoration.ITALIC, false);
            meta.displayName(displayName);

            // 2. Detect Murder Weapon
            Component weaponName = getWeaponComponent(killer);
            String dateStr = LocalDate.now().format(DATE_FORMAT);

            // 3. Build Trophy Lore
            List<Component> lore = List.of(
                Component.text("⚔ Slain by ", NamedTextColor.GRAY)
                    .append(Component.text(killer.getName(), NamedTextColor.RED, TextDecoration.BOLD))
                    .decoration(TextDecoration.ITALIC, false),
                Component.text("🗡 Weapon: ", NamedTextColor.GRAY)
                    .append(weaponName)
                    .decoration(TextDecoration.ITALIC, false),
                Component.text("📅 Date: ", NamedTextColor.GRAY)
                    .append(Component.text(dateStr, NamedTextColor.YELLOW))
                    .decoration(TextDecoration.ITALIC, false),
                Component.empty(),
                Component.text("Parquet SMP PvP Trophy", NamedTextColor.DARK_GRAY)
                    .decoration(TextDecoration.ITALIC, true)
            );
            meta.lore(lore);

            // 4. Serialize Lore into PersistentDataContainer so it survives block placement
            List<String> serializedLore = new ArrayList<>();
            for (Component line : lore) {
                serializedLore.add(GsonComponentSerializer.gson().serialize(line));
            }
            meta.getPersistentDataContainer().set(loreKey, PersistentDataType.LIST.strings(), serializedLore);
            meta.getPersistentDataContainer().set(nameKey, PersistentDataType.STRING, GsonComponentSerializer.gson().serialize(displayName));

            skull.setItemMeta(meta);
        }

        // Drop the head at victim's location
        victim.getWorld().dropItemNaturally(victim.getLocation(), skull);

        // Play visual & audio cue
        victim.getWorld().spawnParticle(Particle.SOUL, victim.getLocation().add(0, 1, 0), 25, 0.4, 0.4, 0.4, 0.05);
        victim.getWorld().playSound(victim.getLocation(), Sound.ENTITY_WITHER_SPAWN, 0.5f, 1.8f);

        // Broadcast beheading in chat
        Bukkit.broadcast(
            Component.text("[", NamedTextColor.DARK_GRAY)
                .append(Component.text("☠", NamedTextColor.RED))
                .append(Component.text("] ", NamedTextColor.DARK_GRAY))
                .append(Component.text(killer.getName(), NamedTextColor.RED, TextDecoration.BOLD))
                .append(Component.text(" claimed ", NamedTextColor.GRAY))
                .append(Component.text(victim.getName() + "'s Head", NamedTextColor.GOLD, TextDecoration.BOLD))
                .append(Component.text("!", NamedTextColor.GRAY))
        );
    }

    /**
     * When a player places a Trophy Head on the ground or wall, save its Lore inside the Block state.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSkullPlace(BlockPlaceEvent event) {
        ItemStack item = event.getItemInHand();
        if (item.getType() != Material.PLAYER_HEAD) return;

        ItemMeta itemMeta = item.getItemMeta();
        if (itemMeta == null) return;

        PersistentDataContainer itemPdc = itemMeta.getPersistentDataContainer();
        if (!itemPdc.has(loreKey, PersistentDataType.LIST.strings())) return;

        Block block = event.getBlockPlaced();
        if (block.getState() instanceof Skull skullBlock) {
            PersistentDataContainer blockPdc = skullBlock.getPersistentDataContainer();
            List<String> savedLore = itemPdc.get(loreKey, PersistentDataType.LIST.strings());
            String savedName = itemPdc.get(nameKey, PersistentDataType.STRING);

            if (savedLore != null) {
                blockPdc.set(loreKey, PersistentDataType.LIST.strings(), savedLore);
            }
            if (savedName != null) {
                blockPdc.set(nameKey, PersistentDataType.STRING, savedName);
            }
            skullBlock.update();
        }
    }

    /**
     * When a placed Trophy Head is broken, intercept the vanilla drop and restore the full Trophy Lore.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onSkullBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        if (block.getType() != Material.PLAYER_HEAD && block.getType() != Material.PLAYER_WALL_HEAD) {
            return;
        }

        if (!(block.getState() instanceof Skull skullBlock)) return;

        PersistentDataContainer blockPdc = skullBlock.getPersistentDataContainer();
        if (!blockPdc.has(loreKey, PersistentDataType.LIST.strings())) return;

        List<String> savedLore = blockPdc.get(loreKey, PersistentDataType.LIST.strings());
        String savedName = blockPdc.get(nameKey, PersistentDataType.STRING);
        if (savedLore == null) return;

        // Prevent vanilla from dropping a blank, lore-wiped head
        event.setDropItems(false);

        ItemStack restoredSkull = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) restoredSkull.getItemMeta();

        if (meta != null) {
            if (skullBlock.getPlayerProfile() != null) {
                meta.setPlayerProfile(skullBlock.getPlayerProfile());
            }

            if (savedName != null) {
                meta.displayName(GsonComponentSerializer.gson().deserialize(savedName));
                meta.getPersistentDataContainer().set(nameKey, PersistentDataType.STRING, savedName);
            }

            List<Component> restoredLore = new ArrayList<>();
            for (String lineJson : savedLore) {
                restoredLore.add(GsonComponentSerializer.gson().deserialize(lineJson));
            }
            meta.lore(restoredLore);
            meta.getPersistentDataContainer().set(loreKey, PersistentDataType.LIST.strings(), savedLore);

            restoredSkull.setItemMeta(meta);
        }

        block.getWorld().dropItemNaturally(block.getLocation(), restoredSkull);
    }

    private Component getWeaponComponent(Player killer) {
        ItemStack weapon = killer.getInventory().getItemInMainHand();
        if (weapon.getType() == Material.AIR) {
            return Component.text("Bare Fists", NamedTextColor.AQUA);
        }

        ItemMeta meta = weapon.getItemMeta();
        if (meta != null && meta.hasDisplayName() && meta.displayName() != null) {
            return meta.displayName().colorIfAbsent(NamedTextColor.AQUA);
        }

        // Convert Material enum (e.g. NETHERITE_SWORD) to clean Title Case ("Netherite Sword")
        String rawName = weapon.getType().name().toLowerCase().replace('_', ' ');
        String[] words = rawName.split(" ");
        StringBuilder formatted = new StringBuilder();
        for (String word : words) {
            if (!word.isEmpty()) {
                formatted.append(Character.toUpperCase(word.charAt(0)))
                         .append(word.substring(1))
                         .append(" ");
            }
        }
        return Component.text(formatted.toString().trim(), NamedTextColor.AQUA);
    }
}