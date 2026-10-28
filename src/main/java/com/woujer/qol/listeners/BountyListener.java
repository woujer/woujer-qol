package com.woujer.qol.listeners;

import com.woujer.qol.manager.BountyManager;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;

public final class BountyListener implements Listener {

    private final BountyManager bountyManager;

    public BountyListener(BountyManager bountyManager) {
        this.bountyManager = bountyManager;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        Player killer = victim.getKiller();

        // Only award bounty if killed by a different player in PvP
        if (killer != null && !killer.getUniqueId().equals(victim.getUniqueId())) {
            if (bountyManager.hasBounty(victim.getUniqueId())) {
                bountyManager.claimBounty(killer, victim);
            }
        }
    }

    /**
     * Tracks PvP combat so sponsors cannot rug-pull/withdraw a bounty while the target is actively losing a fight.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPvpDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) return;

        Player attacker = null;
        if (event.getDamager() instanceof Player p) {
            attacker = p;
        } else if (event.getDamager() instanceof Projectile proj && proj.getShooter() instanceof Player p) {
            attacker = p;
        }

        if (attacker != null && !attacker.getUniqueId().equals(victim.getUniqueId())) {
            bountyManager.tagCombat(victim.getUniqueId());
            bountyManager.tagCombat(attacker.getUniqueId());
        }
    }
}