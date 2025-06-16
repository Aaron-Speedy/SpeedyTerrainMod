package com.pg85.otg.paper.util;

import com.pg85.otg.util.biome.WeightedMobSpawnGroup;

import net.minecraft.util.random.Weighted;
import net.minecraft.util.random.WeightedList;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.MobSpawnSettings.SpawnerData;

import java.util.ArrayList;
import java.util.List;

public class MobSpawnGroupHelper {
    public static List<WeightedMobSpawnGroup> getListFromMinecraftBiome(Biome biome, MobCategory type) {
        WeightedList<SpawnerData> mobList = biome.getMobSettings().getMobs(type);
        List<WeightedMobSpawnGroup> result = new ArrayList<WeightedMobSpawnGroup>();
        for (Weighted<SpawnerData> wSpawner : mobList.unwrap()) {
            // Removing "entities/" since the key returned is "minecraft:entities/chicken" for vanilla biomes/mobs.
            // in 1.17, this is now "minecraft:entity.minecraft.chicken"
            // TODO: Make sure this works for all mobs.
            SpawnerData spawner = wSpawner.value();
            WeightedMobSpawnGroup wMSG = new WeightedMobSpawnGroup(spawner.type().getDescriptionId().replace("entity.minecraft.", ""), wSpawner.weight(), spawner.minCount(), spawner.maxCount());
            result.add(wMSG);
        }
        return result;
    }
}
