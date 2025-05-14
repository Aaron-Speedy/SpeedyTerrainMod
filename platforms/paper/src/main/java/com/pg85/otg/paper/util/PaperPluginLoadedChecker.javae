package com.pg85.otg.paper.util;

import com.pg85.otg.interfaces.IModLoadedChecker;
import org.bukkit.Bukkit;

public class PaperPluginLoadedChecker implements IModLoadedChecker {
    @Override
    public boolean isModLoaded(String mod) {
        return Bukkit.getServer().getPluginManager().isPluginEnabled(mod);
    }
}
