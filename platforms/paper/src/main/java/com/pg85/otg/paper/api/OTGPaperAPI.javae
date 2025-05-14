package com.pg85.otg.paper.api;

import com.pg85.otg.interfaces.IBiomeConfig;
import com.pg85.otg.paper.gen.OTGPaperChunkGen;
import org.bukkit.Location;

import java.util.Optional;

public class OTGPaperAPI {
    public static IBiomeConfig getOTGBiome(Location location) {
        if (location.getWorld().getGenerator() instanceof OTGPaperChunkGen generator) {
            return generator.generator.getCachedBiomeProvider().getBiomeConfig(location.getBlockX(), location.getBlockZ());
        }
        return null;
    }

    public static Optional<IBiomeConfig> getOTGBiomeOptional(Location location) {
        return Optional.ofNullable(getOTGBiome(location));
    }
}
