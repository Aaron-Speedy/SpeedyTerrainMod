package com.pg85.otg.paper.biome;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.Lifecycle;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import com.pg85.otg.core.OTG;
import com.pg85.otg.gen.biome.layers.BiomeLayers;
import com.pg85.otg.gen.biome.layers.util.CachingLayerSampler;
import com.pg85.otg.util.logging.*;

import com.pg85.otg.interfaces.IBiome;
import com.pg85.otg.interfaces.IBiomeConfig;
import com.pg85.otg.interfaces.ILayerSource;
import com.pg85.otg.paper.presets.PaperPresetLoader;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Climate;

import org.bukkit.Bukkit;
import org.bukkit.craftbukkit.CraftServer;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

public class OTGBiomeProvider extends BiomeSource implements ILayerSource {
    public final String presetFolderName;
    private final long seed;
    private final boolean legacyBiomeInitLayer;
    private final boolean largeBiomes;
    // private final Registry<Biome> registry;
    // private final HolderSet<Biome> biomes;

    private final ThreadLocal<CachingLayerSampler> layer;
    private final Int2ObjectMap<Holder<Biome>> keyLookup;

    private static final RegistryAccess registryAccess = ((CraftServer) Bukkit.getServer()).getServer().registryAccess();

    public static final MapCodec<OTGBiomeProvider> CODEC = RecordCodecBuilder.mapCodec(
        instance -> instance.group(
            Codec.STRING.fieldOf("preset_name").stable().forGetter(x -> x.presetFolderName),
            Codec.LONG.fieldOf("seed").stable().forGetter(x -> x.seed),
            Codec.BOOL.optionalFieldOf("legacy_biome_init_layer", Boolean.FALSE, Lifecycle.stable()).forGetter(x -> x.legacyBiomeInitLayer),
            Codec.BOOL.fieldOf("large_biomes").orElse(false).stable().forGetter(x -> x.largeBiomes)
            // Registry.byNameCodec().fieldOf("registry").forGetter(x -> x.registry)
            // registryAccess.lookupOrThrow(Registries.BIOME).byNameCodec().fieldOf("registry").forGetter(x -> x.registry)
            // Biome.LIST_CODEC.fieldOf("biomes").forGetter(x -> x.biomes)
            // RegistryLookupCodec.create(Registry.BIOME_REGISTRY).forGetter(x -> x.registry)
        ).apply(instance, instance.stable(OTGBiomeProvider::new))
    );

    public OTGBiomeProvider(String presetFolderName, long seed, boolean legacyBiomeInitLayer, boolean largeBiomes) {
        // super(getAllBiomesByPreset(presetFolderName));
        this.presetFolderName = presetFolderName;
        this.seed = seed;
        this.legacyBiomeInitLayer = legacyBiomeInitLayer;
        this.largeBiomes = largeBiomes;
        this.layer = ThreadLocal.withInitial(() -> BiomeLayers.create(seed, ((PaperPresetLoader) OTG.getEngine().getPresetLoader()).getPresetGenerationData().get(presetFolderName), OTG.getEngine().getLogger()));
        this.keyLookup = new Int2ObjectOpenHashMap<Holder<Biome>>();

        // Default to let us know if we did anything wrong
        // this.keyLookup.defaultReturnValue(Biomes.OCEAN);

        IBiome[] biomeLookup = ((PaperPresetLoader) OTG.getEngine().getPresetLoader()).getGlobalIdMapping(presetFolderName);
        if (biomeLookup == null) {
            throw new RuntimeException("No OTG preset found with name \"" + presetFolderName + "\". Install the correct preset or update your server.properties.");
        }

        // for (int biomeId = 0; biomeId < biomeLookup.length; biomeId++) {
            // IBiomeConfig config = biomeLookup[biomeId].getBiomeConfig();

            // ResourceKey<Biome> key = ResourceKey.create(Registries.BIOME, new ResourceLocation(config.getRegistryKey().toResourceLocationString()));
            // this.keyLookup.put(biomeId, key);
        // }
    }

    @Override
    protected Stream<Holder<Biome>> collectPossibleBiomes() {
        var biomes = OTG.getEngine().getPresetLoader().getGlobalIdMapping(presetFolderName);
        if (biomes == null) {
            OTG.getEngine().getLogger().log(LogLevel.ERROR, LogCategory.BIOME_REGISTRY,
                    "Biome mapping for preset " + presetFolderName + " is null.");
            return Stream.empty();
        }
        for (int id = 0; id < biomes.length; id++) {
            keyLookup.put(id, ((PaperBiome) biomes[id]).getBiomeHolder());
        }
        return Stream.of(biomes).map(biome -> ((PaperBiome) biome).getBiomeHolder());
    }

    protected MapCodec<? extends BiomeSource> codec() {
        return CODEC;
    }

    // TODO: This is only used by MC internally, OTG fetches all biomes via CachedBiomeProvider.
    // Since it's useless, we're returning every biome as plains.
    // Could make this use the cache too?
    @Override
    public Holder<Biome> getNoiseBiome(int biomeX, int biomeY, int biomeZ, Climate.Sampler sampler) {
        return Holder.direct(registryAccess.lookupOrThrow(Registries.BIOME).getValue(Biomes.PLAINS));
    }

    @Override
    public CachingLayerSampler getSampler() {
        return this.layer.get();
    }

    // No longer used?
    /*@Override
    public BiomeSource withSeed(long seed)
    {
        return new OTGBiomeProvider(this.presetFolderName, seed, this.legacyBiomeInitLayer, this.largeBiomes, this.registry);
    }*/
}
