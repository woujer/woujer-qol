package com.woujer.qol;

import com.woujer.qol.commands.BountyCommand;
import com.woujer.qol.commands.TpaCommand;
import com.woujer.qol.listeners.BountyListener;
import com.woujer.qol.listeners.HeadDropListener;
import com.woujer.qol.manager.BountyManager;
import com.woujer.qol.manager.TpaManager;
import org.bukkit.plugin.java.JavaPlugin;

public final class WoujersQOL extends JavaPlugin {

    private TpaManager tpaManager;
    private BountyManager bountyManager;

    @Override
    public void onEnable() {
        this.tpaManager = new TpaManager(this);
        this.bountyManager = new BountyManager(this);

        // 1. Register Event Listeners
        getServer().getPluginManager().registerEvents(new HeadDropListener(this), this);
        getServer().getPluginManager().registerEvents(this.tpaManager, this);
        getServer().getPluginManager().registerEvents(new BountyListener(this.bountyManager), this);

        // 2. Register TPA Commands
        TpaCommand tpaCommand = new TpaCommand(this.tpaManager);
        getCommand("tpa").setExecutor(tpaCommand);
        getCommand("tpa").setTabCompleter(tpaCommand);
        getCommand("tpaccept").setExecutor(tpaCommand);
        getCommand("tpdeny").setExecutor(tpaCommand);

        // 3. Register Bounty Command
        BountyCommand bountyCommand = new BountyCommand(this.bountyManager);
        getCommand("bounty").setExecutor(bountyCommand);
        getCommand("bounty").setTabCompleter(bountyCommand);

        getLogger().info("WoujersQOL initialized for Parquet SMP (TPA, HeadDrops, Bounties).");
    }

    @Override
    public void onDisable() {
        if (this.tpaManager != null) {
            this.tpaManager.cancelAllTasks();
        }
        if (this.bountyManager != null) {
            this.bountyManager.saveBounties();
        }
    }
}