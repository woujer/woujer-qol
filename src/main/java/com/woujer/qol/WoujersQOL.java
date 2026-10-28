package com.woujer.qol;

import com.woujer.qol.commands.TpaCommand;
import com.woujer.qol.listeners.HeadDropListener;
import com.woujer.qol.manager.TpaManager;
import org.bukkit.plugin.java.JavaPlugin;

public final class WoujersQOL extends JavaPlugin {

    private TpaManager tpaManager;

    @Override
    public void onEnable() {
        this.tpaManager = new TpaManager(this);

        // 1. Load the separate HeadDropListener file (pass 'this' for block data keys)
        getServer().getPluginManager().registerEvents(new HeadDropListener(this), this);

        // 2. Load the separate TpaManager & TpaCommand files
        getServer().getPluginManager().registerEvents(this.tpaManager, this);
        TpaCommand tpaCommand = new TpaCommand(this.tpaManager);
        getCommand("tpa").setExecutor(tpaCommand);
        getCommand("tpa").setTabCompleter(tpaCommand);
        getCommand("tpaccept").setExecutor(tpaCommand);
        getCommand("tpdeny").setExecutor(tpaCommand);

        getLogger().info("WoujersQOL initialized for Parquet SMP.");
    }

    @Override
    public void onDisable() {
        if (this.tpaManager != null) {
            this.tpaManager.cancelAllTasks();
        }
    }
}