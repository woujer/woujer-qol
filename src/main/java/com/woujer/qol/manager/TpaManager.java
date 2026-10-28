package com.woujer.qol.manager;

import com.woujer.qol.WoujersQOL;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class TpaManager implements Listener {

    private final WoujersQOL plugin;
    private final Map<UUID, UUID> pendingRequests = new HashMap<>();
    private final Map<UUID, WarmupSession> activeWarmups = new HashMap<>();

    private record WarmupSession(BukkitTask task, Location startLoc) {}

    public TpaManager(WoujersQOL plugin) {
        this.plugin = plugin;
    }

    public void sendRequest(Player sender, Player target) {
        if (sender.getUniqueId().equals(target.getUniqueId())) {
            sender.sendMessage(Component.text("You cannot teleport to yourself!", NamedTextColor.RED));
            return;
        }

        pendingRequests.put(target.getUniqueId(), sender.getUniqueId());

        sender.sendMessage(Component.text("Teleport request sent to ", NamedTextColor.YELLOW)
            .append(Component.text(target.getName(), NamedTextColor.GOLD, TextDecoration.BOLD))
            .append(Component.text(". It will expire in 60 seconds.", NamedTextColor.YELLOW)));

        Component acceptButton = Component.text("[ACCEPT]", NamedTextColor.GREEN, TextDecoration.BOLD)
            .clickEvent(ClickEvent.runCommand("/tpaccept"))
            .hoverEvent(HoverEvent.showText(Component.text("Click to accept teleport request", NamedTextColor.GREEN)));

        Component denyButton = Component.text("[DENY]", NamedTextColor.RED, TextDecoration.BOLD)
            .clickEvent(ClickEvent.runCommand("/tpdeny"))
            .hoverEvent(HoverEvent.showText(Component.text("Click to deny teleport request", NamedTextColor.RED)));

        target.sendMessage(Component.text(sender.getName(), NamedTextColor.GOLD, TextDecoration.BOLD)
            .append(Component.text(" has requested to teleport to you.\n", NamedTextColor.YELLOW))
            .append(acceptButton)
            .append(Component.text("  "))
            .append(denyButton));

        new BukkitRunnable() {
            @Override
            public void run() {
                if (pendingRequests.get(target.getUniqueId()) != null 
                        && pendingRequests.get(target.getUniqueId()).equals(sender.getUniqueId())) {
                    pendingRequests.remove(target.getUniqueId());
                }
            }
        }.runTaskLater(this.plugin, 1200L);
    }

    public void acceptRequest(Player target) {
        UUID senderUuid = pendingRequests.remove(target.getUniqueId());
        if (senderUuid == null) {
            target.sendMessage(Component.text("You have no pending teleport requests.", NamedTextColor.RED));
            return;
        }

        Player sender = plugin.getServer().getPlayer(senderUuid);
        if (sender == null || !sender.isOnline()) {
            target.sendMessage(Component.text("The player who sent the request is no longer online.", NamedTextColor.RED));
            return;
        }

        target.sendMessage(Component.text("Accepted teleport request from ", NamedTextColor.GREEN)
            .append(Component.text(sender.getName(), NamedTextColor.YELLOW)));

        startWarmup(sender, target);
    }

    public void denyRequest(Player target) {
        UUID senderUuid = pendingRequests.remove(target.getUniqueId());
        if (senderUuid == null) {
            target.sendMessage(Component.text("You have no pending teleport requests.", NamedTextColor.RED));
            return;
        }

        Player sender = plugin.getServer().getPlayer(senderUuid);
        if (sender != null && sender.isOnline()) {
            sender.sendMessage(Component.text(target.getName() + " denied your teleport request.", NamedTextColor.RED));
        }
        target.sendMessage(Component.text("Teleport request denied.", NamedTextColor.YELLOW));
    }

    private void startWarmup(Player sender, Player target) {
        cancelWarmup(sender.getUniqueId(), false);

        sender.sendMessage(Component.text("Teleporting in 5 seconds. Do not move or take damage!", NamedTextColor.YELLOW));

        BukkitTask task = new BukkitRunnable() {
            int secondsLeft = 5;

            @Override
            public void run() {
                if (!sender.isOnline() || !target.isOnline()) {
                    cancelWarmup(sender.getUniqueId(), false);
                    cancel();
                    return;
                }

                if (secondsLeft > 0) {
                    sender.playSound(sender.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.2f);
                    sender.getWorld().spawnParticle(Particle.PORTAL, sender.getLocation().add(0, 1, 0), 10, 0.3, 0.3, 0.3, 0.05);
                    secondsLeft--;
                } else {
                    activeWarmups.remove(sender.getUniqueId());
                    sender.teleport(target.getLocation());
                    sender.playSound(sender.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.0f);
                    target.playSound(target.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.0f);
                    sender.getWorld().spawnParticle(Particle.PORTAL, sender.getLocation().add(0, 1, 0), 30, 0.5, 0.5, 0.5, 0.1);
                    sender.sendMessage(Component.text("Teleported successfully!", NamedTextColor.GREEN));
                    cancel();
                }
            }
        }.runTaskTimer(this.plugin, 0L, 20L);

        activeWarmups.put(sender.getUniqueId(), new WarmupSession(task, sender.getLocation().clone()));
    }

    public void cancelWarmup(UUID playerUuid, boolean notify) {
        WarmupSession session = activeWarmups.remove(playerUuid);
        if (session != null) {
            session.task().cancel();
            if (notify) {
                Player player = plugin.getServer().getPlayer(playerUuid);
                if (player != null && player.isOnline()) {
                    player.sendMessage(Component.text("Teleport cancelled because you moved or took damage!", NamedTextColor.RED));
                    player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                }
            }
        }
    }

    public void cancelAllTasks() {
        for (WarmupSession session : activeWarmups.values()) {
            session.task().cancel();
        }
        activeWarmups.clear();
        pendingRequests.clear();
    }

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        WarmupSession session = activeWarmups.get(uuid);
        if (session != null) {
            Location from = session.startLoc();
            Location to = event.getTo();
            if (to != null && (from.getBlockX() != to.getBlockX() 
                    || from.getBlockY() != to.getBlockY() 
                    || from.getBlockZ() != to.getBlockZ())) {
                cancelWarmup(uuid, true);
            }
        }
    }

    @EventHandler
    public void onDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player player) {
            if (activeWarmups.containsKey(player.getUniqueId())) {
                cancelWarmup(player.getUniqueId(), true);
            }
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        cancelWarmup(uuid, false);
        pendingRequests.remove(uuid);
    }
}