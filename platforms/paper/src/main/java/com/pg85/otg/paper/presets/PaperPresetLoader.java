package com.pg85.otg.paper.presets;

import com.mojang.serialization.Lifecycle;
import com.pg85.otg.config.biome.BiomeConfigFinder;
import com.pg85.otg.config.biome.BiomeGroup;
import com.pg85.otg.constants.Constants;
import com.pg85.otg.constants.SettingsEnums.BiomeMode;
import com.pg85.otg.core.OTG;
import com.pg85.otg.core.config.world.WorldConfig;
import com.pg85.otg.core.presets.LocalPresetLoader;
import com.pg85.otg.core.presets.Preset;
import com.pg85.otg.gen.biome.BiomeData;
import com.pg85.otg.gen.biome.layers.BiomeLayerData;
import com.pg85.otg.gen.biome.layers.NewBiomeGroup;
import com.pg85.otg.interfaces.*;
import com.pg85.otg.paper.biome.PaperBiome;
import com.pg85.otg.paper.materials.PaperMaterialReader;
import com.pg85.otg.paper.networking.BiomeSettingSyncWrapper;
import com.pg85.otg.paper.networking.OTGClientSyncManager;
import com.pg85.otg.paper.util.MobSpawnGroupHelper;
import com.pg85.otg.paper.util.ObfuscationHelper;
import com.pg85.otg.util.biome.OTGBiomeResourceLocation;
import com.pg85.otg.util.logging.LogCategory;
import com.pg85.otg.util.logging.LogLevel;
import com.pg85.otg.util.minecraft.EntityCategory;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.WritableRegistry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.dedicated.DedicatedServer;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.biome.Biome;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.craftbukkit.CraftServer;

import java.io.File;
import java.lang.reflect.Field;
import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class PaperPresetLoader extends LocalPresetLoader {
    private final Map<String, List<ResourceKey<Biome>>> biomesByPresetFolderName = new LinkedHashMap<>();
    private final ConcurrentHashMap<String, IBiome[]> globalIdMapping = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, BiomeLayerData> presetGenerationData = new ConcurrentHashMap<>();
    // We have to store biomes, since Spigot doesn't expose registry key on BiomeBase.
    private final ConcurrentMap<Biome, IBiomeConfig> biomeConfigsByBiome = new ConcurrentHashMap<>();

    private final ResourceKey<Registry<Biome>> BIOME_KEY = Registries.BIOME;

    public PaperPresetLoader(File otgRootFolder) {
        super(otgRootFolder.toPath());
    }

    // Creates a preset-specific materialreader, have to do this
    // only when loading each preset since each preset may have
    // its own block fallbacks / block dictionaries.
    @Override
    public IMaterialReader createMaterialReader() {
        return new PaperMaterialReader();
    }

    @Override
    public void registerBiomes() {
        DedicatedServer server = ((CraftServer) Bukkit.getServer()).getServer();
        WritableRegistry<Biome> biomeRegistry = (WritableRegistry<Biome>) server.registryAccess().ownedRegistryOrThrow(BIOME_KEY);

        Field frozen;
        try {
            frozen = ObfuscationHelper.getField(MappedRegistry.class, "frozen", "ca");
            // Make the frozen boolean accessible
            frozen.setAccessible(true);
            // Set the 'frozen' boolean to false for this registry
            frozen.set(biomeRegistry, false);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            OTG.getEngine().getLogger().log(LogLevel.ERROR, LogCategory.BIOME_REGISTRY, "Failed to unfreeze registry");
            e.printStackTrace();
        }

        for (Preset preset : this.presets.values()) {
            registerBiomesForPreset(false, preset, biomeRegistry);
        }

        try {
            frozen = ObfuscationHelper.getField(MappedRegistry.class, "frozen", "ca");
            // Set the 'frozen' boolean to true for this registry
            frozen.setAccessible(true);
            frozen.set(biomeRegistry, true);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            OTG.getEngine().getLogger().log(LogLevel.ERROR, LogCategory.BIOME_REGISTRY, "Failed to re-freeze registry");
            e.printStackTrace();
        }
    }

    private void registerBiomesForPreset(boolean refresh, Preset preset, WritableRegistry<Biome> biomeRegistry) {
        // Index BiomeColors for FromImageMode and /otg map
        HashMap<Integer, Integer> biomeColorMap = new HashMap<>();

        // Start at 1, 0 is the fallback for the biome generator (the world's ocean biome).
        int currentId = 1;

        List<ResourceKey<Biome>> presetBiomes = new ArrayList<>();
        this.biomesByPresetFolderName.put(preset.getFolderName(), presetBiomes);

        IWorldConfig worldConfig = preset.getWorldConfig();
        IBiomeConfig oceanBiomeConfig = null;
        int[] oceanTemperatures = new int[]{0, 0, 0, 0};

        List<IBiomeConfig> biomeConfigs = preset.getAllBiomeConfigs();

        ConcurrentMap<Integer, List<BiomeData>> isleBiomesAtDepth = new ConcurrentHashMap<>();
        ConcurrentMap<Integer, List<BiomeData>> borderBiomesAtDepth = new ConcurrentHashMap<>();

        ConcurrentMap<String, List<Integer>> worldBiomes = new ConcurrentHashMap<>();
        ConcurrentMap<String, IBiomeConfig> biomeConfigsByName = new ConcurrentHashMap<>();

        // Create registry keys for each biomeconfig, create template
        // biome configs for any modded biomes using TemplateForBiome.
        Map<IBiomeResourceLocation, IBiomeConfig> biomeConfigsByResourceLocation = new LinkedHashMap<>();
        for (IBiomeConfig biomeConfig : biomeConfigs) {
            if (!biomeConfig.getIsTemplateForBiome()) {
                IBiomeResourceLocation otgLocation = new OTGBiomeResourceLocation(preset.getPresetFolder(), preset.getShortPresetName(), preset.getMajorVersion(), biomeConfig.getName());
                biomeConfigsByResourceLocation.put(otgLocation, biomeConfig);
                biomeConfigsByName.put(biomeConfig.getName(), biomeConfig);
            }
        }

        IBiome[] presetIdMapping = new IBiome[biomeConfigsByResourceLocation.entrySet().size()];
        for (Entry<IBiomeResourceLocation, IBiomeConfig> biomeConfig : biomeConfigsByResourceLocation.entrySet()) {
            boolean isOceanBiome = false;
            // Biome id 0 is reserved for ocean, used when a land column has
            // no biome assigned, which can happen due to biome group rarity.
            if (biomeConfig.getValue().getName().equals(worldConfig.getDefaultOceanBiome())) {
                oceanBiomeConfig = biomeConfig.getValue();
                isOceanBiome = true;
            }

            int otgBiomeId = isOceanBiome ? 0 : currentId;

            // When using TemplateForBiome, we'll fetch the non-OTG biome from the registry, including any settings registered to it.
            // For normal biomes we create our own new OTG biome and apply settings from the biome config.
            ResourceLocation resourceLocation = new ResourceLocation(biomeConfig.getKey().toResourceLocationString());
            ResourceKey<Biome> registryKey;
            Biome biome;
            if (!(biomeConfig.getKey() instanceof OTGBiomeResourceLocation)) {
                if (OTG.getEngine().getLogger().getLogCategoryEnabled(LogCategory.BIOME_REGISTRY)) {
                    OTG.getEngine().getLogger().log(LogLevel.ERROR, LogCategory.BIOME_REGISTRY, "Could not process template biomeconfig " + biomeConfig.getValue().getName() + ", did you set TemplateForBiome:true in the BiomeConfig?");
                }
                continue;
            }
            biomeConfig.getValue().setRegistryKey(biomeConfig.getKey());
            biomeConfig.getValue().setOTGBiomeId(otgBiomeId);
            registryKey = ResourceKey.create(BIOME_KEY, resourceLocation);
            presetBiomes.add(registryKey);
            biome = PaperBiome.createOTGBiome(isOceanBiome, preset.getWorldConfig(), biomeConfig.getValue(), ((CraftServer) Bukkit.getServer()).getServer().registryHolder);

            if (!refresh) {
                biomeRegistry.register(registryKey, biome, Lifecycle.experimental());
            } else {
                biomeRegistry.registerOrOverride(OptionalInt.empty(), registryKey, biome, Lifecycle.experimental());
            }

            // Populate our map for syncing
            OTGClientSyncManager.getSyncedData().put(resourceLocation.toString(), new BiomeSettingSyncWrapper(biomeConfig.getValue()));

            // Ocean temperature mappings. Probably a better way to do this?
            if (biomeConfig.getValue().getName().equals(worldConfig.getDefaultWarmOceanBiome())) {
                oceanTemperatures[0] = otgBiomeId;
            }
            if (biomeConfig.getValue().getName().equals(worldConfig.getDefaultLukewarmOceanBiome())) {
                oceanTemperatures[1] = otgBiomeId;
            }
            if (biomeConfig.getValue().getName().equals(worldConfig.getDefaultColdOceanBiome())) {
                oceanTemperatures[2] = otgBiomeId;
            }
            if (biomeConfig.getValue().getName().equals(worldConfig.getDefaultFrozenOceanBiome())) {
                oceanTemperatures[3] = otgBiomeId;
            }

//			if(biomeConfig.getKey() instanceof OTGBiomeResourceLocation)
//			{
//				// Add biome dictionary tags for Forge
//				biomeConfig.getValue().getBiomeDictTags().forEach(biomeDictId -> {
//					if(biomeDictId != null && biomeDictId.trim().length() > 0)
//					{
//						BiomeDictionary.addTypes(registryKey, BiomeDictionary.Type.getType(biomeDictId.trim()));
//					}
//				});
//			}

            IBiome otgBiome = new PaperBiome(biome, biomeConfig.getValue());
            if (otgBiomeId >= presetIdMapping.length) {
                OTG.getEngine().getLogger().log(LogLevel.FATAL, LogCategory.CONFIGS, "Fatal error while registering OTG biome id's for preset " + preset.getFolderName() + ", most likely you've assigned a DefaultOceanBiome that doesn't exist.");
                throw new RuntimeException("Fatal error while registering OTG biome id's for preset " + preset.getFolderName() + ", most likely you've assigned a DefaultOceanBiome that doesn't exist.");
            }
            presetIdMapping[otgBiomeId] = otgBiome;

            List<Integer> idsForBiome = worldBiomes.computeIfAbsent(biomeConfig.getValue().getName(), k -> new ArrayList<>());
            idsForBiome.add(otgBiomeId);

            // Make a list of isle and border biomes per generation depth
            if (biomeConfig.getValue().isIsleBiome()) {
                // Make or get a list for this group depth, then add
                List<BiomeData> biomesAtDepth = isleBiomesAtDepth.getOrDefault(worldConfig.getBiomeMode() == BiomeMode.NoGroups ? biomeConfig.getValue().getBiomeSize() : biomeConfig.getValue().getBiomeSizeWhenIsle(), new ArrayList<>());
                biomesAtDepth.add(
                        new BiomeData(
                                otgBiomeId,
                                worldConfig.getBiomeMode() == BiomeMode.NoGroups ? biomeConfig.getValue().getBiomeRarity() : biomeConfig.getValue().getBiomeRarityWhenIsle(),
                                worldConfig.getBiomeMode() == BiomeMode.NoGroups ? biomeConfig.getValue().getBiomeSize() : biomeConfig.getValue().getBiomeSizeWhenIsle(),
                                biomeConfig.getValue().getBiomeTemperature(),
                                biomeConfig.getValue().getIsleInBiomes(),
                                biomeConfig.getValue().getBorderInBiomes(),
                                biomeConfig.getValue().getOnlyBorderNearBiomes(),
                                biomeConfig.getValue().getNotBorderNearBiomes()
                        )
                );
                isleBiomesAtDepth.put(worldConfig.getBiomeMode() == BiomeMode.NoGroups ? biomeConfig.getValue().getBiomeSize() : biomeConfig.getValue().getBiomeSizeWhenIsle(), biomesAtDepth);
            }

            if (biomeConfig.getValue().isBorderBiome()) {
                // Make or get a list for this group depth, then add
                List<BiomeData> biomesAtDepth = borderBiomesAtDepth.getOrDefault(worldConfig.getBiomeMode() == BiomeMode.NoGroups ? biomeConfig.getValue().getBiomeSize() : biomeConfig.getValue().getBiomeSizeWhenBorder(), new ArrayList<>());
                biomesAtDepth.add(
                        new BiomeData(
                                otgBiomeId,
                                biomeConfig.getValue().getBiomeRarity(),
                                worldConfig.getBiomeMode() == BiomeMode.NoGroups ? biomeConfig.getValue().getBiomeSize() : biomeConfig.getValue().getBiomeSizeWhenBorder(),
                                biomeConfig.getValue().getBiomeTemperature(),
                                biomeConfig.getValue().getIsleInBiomes(),
                                biomeConfig.getValue().getBorderInBiomes(),
                                biomeConfig.getValue().getOnlyBorderNearBiomes(),
                                biomeConfig.getValue().getNotBorderNearBiomes()
                        )
                );
                borderBiomesAtDepth.put(worldConfig.getBiomeMode() == BiomeMode.NoGroups ? biomeConfig.getValue().getBiomeSize() : biomeConfig.getValue().getBiomeSizeWhenBorder(), biomesAtDepth);
            }

            // Index BiomeColor for FromImageMode and /otg map
            biomeColorMap.put(biomeConfig.getValue().getBiomeColor(), otgBiomeId);

            if (OTG.getEngine().getLogger().getLogCategoryEnabled(LogCategory.BIOME_REGISTRY)) {
                OTG.getEngine().getLogger().log(LogLevel.INFO, LogCategory.BIOME_REGISTRY, "Registered biome " + resourceLocation + " | " + biomeConfig.getValue().getName() + " with OTG id " + otgBiomeId);
            }

            currentId += isOceanBiome ? 0 : 1;
        }

        // If the ocean config is null, shift the array downwards to fill id 0
        if (oceanBiomeConfig == null) {
            System.arraycopy(presetIdMapping, 1, presetIdMapping, 0, presetIdMapping.length - 1);
        }

        this.globalIdMapping.put(preset.getFolderName(), presetIdMapping);

        // Set the base data
        BiomeLayerData data = new BiomeLayerData(preset.getPresetFolder(), worldConfig, oceanBiomeConfig, oceanTemperatures);

        Set<Integer> biomeDepths = new HashSet<>();
        ConcurrentMap<Integer, List<NewBiomeGroup>> groupDepths = new ConcurrentHashMap<>();

        int genDepth = worldConfig.getGenerationDepth();

        // Iterate through the groups and add it to the layer data
        // TODO: Refactor BiomeGroupManager to IBiomeGroupManager/IBiomeGroup to avoid WorldConfig cast?
        for (BiomeGroup group : ((WorldConfig) worldConfig).getBiomeGroupManager().getGroups()) {
            // Initialize biome group data
            NewBiomeGroup bg = new NewBiomeGroup();
            bg.id = group.getGroupId();
            bg.rarity = group.getGroupRarity();

            // init to genDepth as it will have one value per depth
            bg.totalDepthRarity = new int[genDepth + 1];
            bg.maxRarityPerDepth = new int[genDepth + 1];

            float totalTemp = 0;

            HashMap<String, IBiomeConfig> groupBiomes = new LinkedHashMap<>();
            for (String biomeEntry : group.getBiomes()) {
                IBiomeConfig config = biomeConfigsByName.get(biomeEntry);
                if (config != null) {
                    groupBiomes.put(biomeEntry, config);
                }
            }

            // Add each biome to the group
            for (Entry<String, IBiomeConfig> biome : groupBiomes.entrySet()) {
                if (biome.getValue() != null) {
                    IBiomeConfig config = biome.getValue();
                    // Make and add the generation data
                    BiomeData newBiomeData = new BiomeData(
                            config.getOTGBiomeId(),
                            config.getBiomeRarity(),
                            config.getBiomeSize(),
                            config.getBiomeTemperature(),
                            config.getIsleInBiomes(),
                            config.getBorderInBiomes(),
                            config.getOnlyBorderNearBiomes(),
                            config.getNotBorderNearBiomes()
                    );
                    bg.biomes.add(newBiomeData);

                    // Add the biome size- if it's already there, nothing is done
                    biomeDepths.add(config.getBiomeSize());

                    totalTemp += config.getBiomeTemperature();
                    bg.totalGroupRarity += config.getBiomeRarity();

                    // Add this biome's rarity to the total for its depth in the group
                    bg.totalDepthRarity[config.getBiomeSize()] += config.getBiomeRarity();
                }
            }

            // We have filled out the biome group's totalDepthRarity array, use it to fill the maxRarityPerDepth array
            for (int depth = 0; depth < bg.totalDepthRarity.length; depth++) {
                // maxRarityPerDepth is the sum of totalDepthRarity for this and subsequent depths
                for (int j = depth; j < bg.totalDepthRarity.length; j++) {
                    bg.maxRarityPerDepth[depth] += bg.totalDepthRarity[j];
                }
            }

            bg.avgTemp = totalTemp / group.getBiomes().size();

            int groupSize = group.getGenerationDepth();

            // Make or get a list for this group depth, then add
            List<NewBiomeGroup> groupsAtDepth = groupDepths.getOrDefault(groupSize, new ArrayList<>());
            groupsAtDepth.add(bg);

            // Replace entry
            groupDepths.put(groupSize, groupsAtDepth);

            // Register group id
            data.groupRegistry.put(bg.id, bg);
        }

        // Add the data and process isle/border biomes
        data.init(biomeDepths, groupDepths, isleBiomesAtDepth, borderBiomesAtDepth, worldBiomes, biomeColorMap, presetIdMapping);

        // Set data for this preset
        this.presetGenerationData.put(preset.getFolderName(), data);
    }

    public IBiomeConfig getBiomeConfig(Biome biome) {
        // This map is never filled?
        return this.biomeConfigsByBiome.get(biome);
    }

    public List<ResourceKey<Biome>> getBiomeRegistryKeys(String presetFolderName) {
        return this.biomesByPresetFolderName.get(presetFolderName);
    }

    public IBiome[] getGlobalIdMapping(String presetFolderName) {
        return globalIdMapping.get(presetFolderName);
    }

    public ConcurrentMap<String, BiomeLayerData> getPresetGenerationData() {
        ConcurrentMap<String, BiomeLayerData> clonedData = new ConcurrentHashMap<>();
        for (Map.Entry<String, BiomeLayerData> entry : this.presetGenerationData.entrySet()) {
            clonedData.put(entry.getKey(), new BiomeLayerData(entry.getValue()));
        }
        return clonedData;
    }

    @Override
    protected void mergeVanillaBiomeMobSpawnSettings(BiomeConfigFinder.BiomeConfigStub biomeConfigStub, String biomeResourceLocation) {
        String[] resourceLocationArr = biomeResourceLocation.split(":");
        String resourceDomain = resourceLocationArr.length > 1 ? resourceLocationArr[0] : "minecraft";
        String resourceLocation = resourceLocationArr.length > 1 ? resourceLocationArr[1] : resourceLocationArr[0];

        NamespacedKey location = null;
        try {
            location = new NamespacedKey(resourceDomain, resourceLocation);
        } catch (IllegalArgumentException ex) {
            // Can happen when input is invalid.
        }

        if (location != null) {
            org.bukkit.block.Biome biome = org.bukkit.Registry.BIOME.get(location);
            Biome biomeBase = null;
            if (biome != null) {
                biomeBase = BuiltInRegistries.BIOME.get(new ResourceLocation(biome.getKey().toString()));
            }
            if (biomeBase != null) {
                // Merge the vanilla biome's mob spawning lists with the mob spawning lists from the BiomeConfig.
                // Mob spawning settings for the same creature will not be inherited (so BiomeConfigs can override vanilla mob spawning settings).
                // We also inherit any mobs that have been added to vanilla biomes' mob spawning lists by other mods.
                biomeConfigStub.mergeMobs(MobSpawnGroupHelper.getListFromMinecraftBiome(biomeBase, MobCategory.MONSTER), EntityCategory.MONSTER);
                biomeConfigStub.mergeMobs(MobSpawnGroupHelper.getListFromMinecraftBiome(biomeBase, MobCategory.AMBIENT), EntityCategory.AMBIENT);
                biomeConfigStub.mergeMobs(MobSpawnGroupHelper.getListFromMinecraftBiome(biomeBase, MobCategory.UNDERGROUND_WATER_CREATURE), EntityCategory.UNDERGROUND_WATER_CREATURE);
                biomeConfigStub.mergeMobs(MobSpawnGroupHelper.getListFromMinecraftBiome(biomeBase, MobCategory.CREATURE), EntityCategory.CREATURE);
                biomeConfigStub.mergeMobs(MobSpawnGroupHelper.getListFromMinecraftBiome(biomeBase, MobCategory.WATER_AMBIENT), EntityCategory.WATER_AMBIENT);
                biomeConfigStub.mergeMobs(MobSpawnGroupHelper.getListFromMinecraftBiome(biomeBase, MobCategory.WATER_CREATURE), EntityCategory.WATER_CREATURE);
                biomeConfigStub.mergeMobs(MobSpawnGroupHelper.getListFromMinecraftBiome(biomeBase, MobCategory.MISC), EntityCategory.MISC);
                return;
            }
        }
        if (OTG.getEngine().getLogger().getLogCategoryEnabled(LogCategory.MOBS)) {
            OTG.getEngine().getLogger().log(LogLevel.ERROR, LogCategory.MOBS, "Could not inherit mobs for unrecognised biome \"" + biomeResourceLocation + "\" in " + biomeConfigStub.getBiomeName() + Constants.BiomeConfigFileExtension);
        }
    }
}
