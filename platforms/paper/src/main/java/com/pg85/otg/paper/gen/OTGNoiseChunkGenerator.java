package com.pg85.otg.paper.gen;

import com.pg85.otg.constants.Constants;
import com.pg85.otg.constants.SettingsEnums;
import com.pg85.otg.core.OTG;
import com.pg85.otg.core.gen.OTGChunkDecorator;
import com.pg85.otg.core.gen.OTGChunkGenerator;
import com.pg85.otg.core.presets.Preset;
import com.pg85.otg.customobject.structures.CustomStructureCache;
import com.pg85.otg.interfaces.IBiome;
import com.pg85.otg.interfaces.ICachedBiomeProvider;
import com.pg85.otg.interfaces.ILayerSource;
import com.pg85.otg.interfaces.IWorldConfig;
import com.pg85.otg.paper.biome.PaperBiome;
import com.pg85.otg.paper.gen.carver.OTGCarvingContext;
import com.pg85.otg.paper.gen.carver.OTGWorldCarver;
import com.pg85.otg.paper.presets.PaperPresetLoader;
import com.pg85.otg.util.ChunkCoordinate;
import com.pg85.otg.util.gen.ChunkBuffer;
import com.pg85.otg.util.gen.DecorationArea;
import com.pg85.otg.util.gen.JigsawStructureData;
import com.pg85.otg.util.materials.LocalMaterialData;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectArraySet;
import it.unimi.dsi.fastutil.objects.ObjectList;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Suppliers;
import com.google.common.collect.Sets;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.mojang.serialization.codecs.RecordCodecBuilder.Instance;
import com.mojang.serialization.Codec;
import java.text.DecimalFormat;
import java.util.List;
import java.util.OptionalInt;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Predicate;
import java.util.function.Supplier;
import javax.annotation.Nullable;
import net.minecraft.SharedConstants;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.NaturalSpawner;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeGenerationSettings;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.biome.BiomeResolver;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.CarvingMask;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.levelgen.blending.Blender;
import net.minecraft.world.level.levelgen.carver.CarvingContext;
import net.minecraft.world.level.levelgen.carver.ConfiguredWorldCarver;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.Aquifer;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.NoiseChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.Heightmap.Types;
import net.minecraft.world.level.levelgen.BelowZeroRetrogen;
import org.apache.commons.lang3.mutable.MutableObject;

import javax.annotation.Nullable;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public class OTGNoiseChunkGenerator extends ChunkGenerator {
    // Create a codec to serialise/deserialise OTGNoiseChunkGenerator
    public static final MapCodec<OTGNoiseChunkGenerator> CODEC = RecordCodecBuilder.mapCodec(
        instance -> instance.group(
            Codec.STRING.fieldOf("preset_folder_name").forGetter(x -> x.presetFolderName),
            BiomeSource.CODEC.fieldOf("biome_source").forGetter(x -> x.biomeSource),
            // RegistryOps.retrieveRegistry(Registries.STRUCTURE_SET).forGetter(x -> x.structureSets),
            // RegistryOps.retrieveRegistry(Registries.NOISE).forGetter(x -> x.noises),
            // Codec.LONG.fieldOf("seed").stable().forGetter(x -> x.worldSeed),
            NoiseGeneratorSettings.CODEC.fieldOf("settings").forGetter(x -> x.settings)
        ).apply(instance, instance.stable(OTGNoiseChunkGenerator::new))
    );

    // private final Holder<NoiseGeneratorSettings> generatorSettings;
    // private final long worldSeed;
    // private final int noiseHeight;
    // protected final BlockState defaultBlock;
    // protected final BlockState defaultFluid;

    // private final ShadowChunkGenerator shadowChunkGenerator;
    // public final OTGChunkGenerator internalGenerator;
    // private final OTGChunkDecorator chunkDecorator;
    // private final NoiseRouter router;
    //protected final WorldgenRandom random;

    // private final Supplier<Aquifer.FluidPicker> globalFluidPicker;

    // TODO: Move this to WorldLoader when ready?
    // private CustomStructureCache structureCache;

    // Used to specify which chunk to regen biomes and structures for
    // Necessary because Spigot calls those methods before we have the chance to inject
    // private ChunkCoordinate fixBiomesForChunk = null;
    // private final Climate.Sampler sampler;
    // private final Registry<NormalNoise.NoiseParameters> noises;

    private static final BlockState AIR = Blocks.AIR.defaultBlockState();
    private final Holder<NoiseGeneratorSettings> settings;
    private final Supplier<Aquifer.FluidPicker> globalFluidPicker;

    private final String presetFolderName;
    private final Preset preset;

    public OTGNoiseChunkGenerator(BiomeSource source, Holder<NoiseGeneratorSettings> settings) {
        this("default", source, settings);
    }

    public ICachedBiomeProvider getCachedBiomeProvider() {
        return this.internalGenerator.getCachedBiomeProvider();
    }

    public OTGNoiseChunkGenerator(String presetFolderName, BiomeSource source, Holder<NoiseGeneratorSettings> settings) {
        super(source);

        this.presetFolderName = presetFolderName;
        this.preset = OTG.getEngine().getPresetLoader().getPresetByFolderName(presetFolderName);

        this.settings = settings;
        this.globalFluidPicker = Suppliers.memoize(() -> createFluidPicker((NoiseGeneratorSettings) settings.value()));
    }

    public void saveStructureCache() {
        if (this.chunkDecorator.getIsSaveRequired() && this.structureCache != null) {
            this.structureCache.saveToDisk(OTG.getEngine().getLogger(), this.chunkDecorator);
        }
    }

    // TODO: Investigate this
    // Method to remove structures which have been disabled in the world config
    // private static HolderSet<StructureSet> getEnabledStructures(Registry<StructureSet> registry, String presetFolderName) {
    //     Preset preset = OTG.getEngine().getPresetLoader().getPresetByFolderName(presetFolderName);
    //     IWorldConfig worldConfig = preset.getWorldConfig();
    //     List<Holder<StructureSet>> holderList = new ArrayList<>();

    //     if (worldConfig.getRareBuildingsEnabled()) {
    //         holderList.add(registry.getOrThrow(BuiltinStructureSets.IGLOOS));
    //         holderList.add(registry.getOrThrow(BuiltinStructureSets.SWAMP_HUTS));
    //         holderList.add(registry.getOrThrow(BuiltinStructureSets.DESERT_PYRAMIDS));
    //         holderList.add(registry.getOrThrow(BuiltinStructureSets.JUNGLE_TEMPLES));
    //     }

    //     if (worldConfig.getVillagesEnabled())
    //         holderList.add(registry.getOrThrow(BuiltinStructureSets.VILLAGES));

    //     if (worldConfig.getPillagerOutpostsEnabled())
    //         holderList.add(registry.getOrThrow(BuiltinStructureSets.PILLAGER_OUTPOSTS));

    //     if (worldConfig.getStrongholdsEnabled())
    //         holderList.add(registry.getOrThrow(BuiltinStructureSets.STRONGHOLDS));

    //     if (worldConfig.getOceanMonumentsEnabled())
    //         holderList.add(registry.getOrThrow(BuiltinStructureSets.OCEAN_MONUMENTS));

    //     if (worldConfig.getEndCitiesEnabled())
    //         holderList.add(registry.getOrThrow(BuiltinStructureSets.END_CITIES));

    //     if (worldConfig.getWoodlandMansionsEnabled())
    //         holderList.add(registry.getOrThrow(BuiltinStructureSets.WOODLAND_MANSIONS));

    //     if (worldConfig.getBuriedTreasureEnabled())
    //         holderList.add(registry.getOrThrow(BuiltinStructureSets.BURIED_TREASURES));

    //     if (worldConfig.getMineshaftsEnabled())
    //         holderList.add(registry.getOrThrow(BuiltinStructureSets.MINESHAFTS));

    //     if (worldConfig.getRuinedPortalsEnabled())
    //         holderList.add(registry.getOrThrow(BuiltinStructureSets.RUINED_PORTALS));

    //     if (worldConfig.getShipWrecksEnabled())
    //         holderList.add(registry.getOrThrow(BuiltinStructureSets.SHIPWRECKS));

    //     if (worldConfig.getOceanRuinsEnabled())
    //         holderList.add(registry.getOrThrow(BuiltinStructureSets.OCEAN_RUINS));

    //     if (worldConfig.getBastionRemnantsEnabled())
    //         holderList.add(registry.getOrThrow(BuiltinStructureSets.NETHER_COMPLEXES));

    //     if (worldConfig.getNetherFortressesEnabled())
    //         holderList.add(registry.getOrThrow(BuiltinStructureSets.NETHER_COMPLEXES));

    //     if (worldConfig.getNetherFossilsEnabled())
    //         holderList.add(registry.getOrThrow(BuiltinStructureSets.NETHER_FOSSILS));

    //     HolderSet<StructureSet> holderSet = HolderSet.direct(holderList);
    //     return holderSet;
    // }

    private static Aquifer.FluidPicker createFluidPicker(NoiseGeneratorSettings settings) {
        Aquifer.FluidStatus fluidStatus = new Aquifer.FluidStatus(-54, Blocks.LAVA.defaultBlockState());
        int seaLevel = settings.seaLevel();
        Aquifer.FluidStatus fluidStatus1 = new Aquifer.FluidStatus(seaLevel, settings.defaultFluid());
        Aquifer.FluidStatus fluidStatus2 = new Aquifer.FluidStatus(DimensionType.MIN_Y * 2, Blocks.AIR.defaultBlockState()); // Is this used?
        return (x, y, z) -> y < Math.min(-54, seaLevel) ? fluidStatus : fluidStatus1;
    }

    @Override
    public CompletableFuture<ChunkAccess> createBiomes(RandomState rand, Blender blender, StructureManager structureManager, ChunkAccess chunkAccess) {
        return CompletableFuture.supplyAsync(() -> {
            this.doCreateBiomes(blender, rand, structureManager, chunkAccess);
            return chunkAccess;
        }, Util.backgroundExecutor().forName("init_biomes"));
    }

    private void doCreateBiomes(Blender blender, RandomState rand, StructureManager structureManager, ChunkAccess chunkAccess) {
        NoiseChunk chunk = chunkAccess.getOrCreateNoiseChunk(x -> this.createNoiseChunk(x, structureManager, blender, rand));
        BiomeResolver resolver = BelowZeroRetrogen.getBiomeResolver(blender.getBiomeResolver(this.biomeSource), chunkAccess);
        chunkAccess.fillBiomesFromNoise(resolver, chunk.cachedClimateSampler(rand.router(), ((NoiseGeneratorSettings) this.settings.value()).spawnTarget()));
    }

    private NoiseChunk createNoiseChunk(ChunkAccess chunkAccess, StructureManager structureManager, Blender blender, RandomState rand) {
        return NoiseChunk.forChunk(
            chunkAccess,
            rand,
            Beardifier.forStructuresInChunk(structureManager, chunkAccess.getPos()),
            (NoiseGeneratorSettings) this.settings.value(),
            (Aquifer.FluidPicker) this.globalFluidPicker.get(),
            blender
        );
    }

    @Override
    protected MapCodec<? extends ChunkGenerator> codec() {
        return CODEC;
    }

    public Holder<NoiseGeneratorSettings> generatorSettings() {
        return this.settings;
    }

    public boolean stable(ResourceKey<NoiseGeneratorSettings> key) {
        return this.settings.is(key);
    }

    @Override
    public int getBaseHeight(int x, int y, Heightmap.Types heightMap, LevelHeightAccessor world, RandomState noiseConfig) {
        return this.iterateNoiseColumn(world, noiseConfig, x, y, null, heightMap.isOpaque()).orElse(world.getMinY());
    }

    @Override
    public NoiseColumn getBaseColumn(int x, int z, LevelHeightAccessor world, RandomState noiseConfig) {
        MutableObject<NoiseColumn> column = new MutableObject<>();
        this.iterateNoiseColumn(world, noiseConfig, x, z, column, null);
        return column.getValue();
    }

    @Override
    public void addDebugScreenInfo(List<String> text, RandomState noiseConfig, BlockPos pos) {
        // TODO: what does this do? - auth (unknown)
    }

    // TODO: label variables
    private OptionalInt iterateNoiseColumn(LevelHeightAccessor world, RandomState noiseConfig, int d1, int d2, @Nullable MutableObject<NoiseColumn> column, @Nullable Predicate<BlockState> pbs) {
        NoiseSettings settings = ((NoiseGeneratorSettings) this.settings.value()).noiseSettings().clampToHeightAccessor(world);
        int cellHeight = settings.getCellHeight();
        int minY = settings.minY();
        int $$9 = Mth.floorDiv(minY, cellHeight);
        int $$10 = Mth.floorDiv(settings.height(), cellHeight);
        if ($$10 <= 0) {
            return OptionalInt.empty();
        } else {
            BlockState[] $$11;
            if (column == null) {
                $$11 = null;
            } else {
                $$11 = new BlockState[settings.height()];
                column.setValue(new NoiseColumn(minY, $$11));
            }

            int $$13 = settings.getCellWidth();
            int $$14 = Math.floorDiv(d1, $$13);
            int $$15 = Math.floorDiv(d2, $$13);
            int $$16 = Math.floorMod(d1, $$13);
            int $$17 = Math.floorMod(d2, $$13);
            int $$18 = $$14 * $$13;
            int $$19 = $$15 * $$13;
            double $$20 = (double) $$16 / (double) $$13;
            double $$21 = (double) $$17 / (double) $$13;
            NoiseChunk $$22 = new NoiseChunk(
                1,
                noiseConfig,
                $$18,
                $$19,
                settings,
                DensityFunctions.BeardifierMarker.INSTANCE,
                (NoiseGeneratorSettings) this.settings.value(),
                (Aquifer.FluidPicker) this.globalFluidPicker.get(),
                Blender.empty()
            );
            $$22.initializeForFirstCellX();
            $$22.advanceCellX(0);

            for (int $$23 = $$10 - 1; $$23 >= 0; --$$23) {
                $$22.selectCellYZ($$23, 0);

                for (int $$24 = cellHeight - 1; $$24 >= 0; --$$24) {
                    int $$25 = ($$9 + $$23) * cellHeight + $$24;
                    double $$26 = (double) $$24 / (double) cellHeight;
                    $$22.updateForY($$25, $$26);
                    $$22.updateForX(d1, $$20);
                    $$22.updateForZ(d2, $$21);
                    BlockState $$27 = $$22.getInterpolatedState();
                    BlockState $$28 = $$27 == null ? ((NoiseGeneratorSettings) this.settings.value()).defaultBlock() : $$27;
                    if ($$11 != null) {
                        int $$29 = $$23 * cellHeight + $$24;
                        $$11[$$29] = $$28;
                    }

                    if (pbs != null && pbs.test($$28)) {
                        $$22.stopInterpolation();
                        return OptionalInt.of($$25 + 1);
                    }
                }
            }

            $$22.stopInterpolation();
            return OptionalInt.empty();
        }
    }

    @Override
    public void buildSurface(WorldGenRegion worldGenRegion, StructureManager structures, RandomState noiseConfig, ChunkAccess chunk) {
        // OTG handles surface/ground blocks during base terrain gen. For non-OTG biomes used
    }

    // Is this needed?
    // @VisibleForTesting
    // public void buildSurface(ChunkAccess $$0, WorldGenerationContext $$1, RandomState $$2, StructureManager $$3, BiomeManager $$4, Registry<Biome> $$5, Blender $$6) {
    //     NoiseChunk $$7 = $$0.getOrCreateNoiseChunk($$3x -> this.createNoiseChunk($$3x, $$3, $$6, $$2));
    //     NoiseGeneratorSettings $$8 = (NoiseGeneratorSettings) this.settings.value();
    //     $$2.surfaceSystem().buildSurface($$2, $$4, $$5, $$8.useLegacyRandomSource(), $$1, $$0, $$7, $$8.surfaceRule());
    // }

    // Carvers: Caves and ravines
    // TODO: Re-implement carvers, or find some way to get new (much more complex) vanilla cavegen to do the work for us

    // NOTE: Commenting this out temporarily
    // @Override
    // public void applyCarvers(WorldGenRegion chunkRegion, long seed, RandomState noiseConfig, BiomeManager biomeManager, StructureManager structureAccess, ChunkAccess chunk) {
    //     BiomeManager biomeManager1 = biomeManager.withDifferentSource((x, y, z) -> super.biomeSource.getNoiseBiome(x, y, z, noiseConfig.sampler()));
    //     WorldgenRandom worldgenRandom = new WorldgenRandom(new LegacyRandomSource(RandomSupport.generateUniqueSeed()));
    //     int i = 8;
    //     ChunkPos pos = chunk.getPos();
    //     NoiseChunk noiseChunk = chunk.getOrCreateNoiseChunk((chunkAccess) -> this.createNoiseChunk(chunkAccess, structureAccess, Blender.of(chunkRegion), noiseConfig));
    //     Aquifer aquifer = noiseChunk.aquifer();
    //     OTGCarvingContext carvingContext = new OTGCarvingContext(this, structureAccess.level.registryAccess(), chunk.getHeightAccessorForGeneration(), noiseChunk, noiseConfig, generatorSettings.value().surfaceRule(), structureAccess.level.getMinecraftWorld());
    //     CarvingMask carvingMask = ((ProtoChunk)chunk).getOrCreateCarvingMask();

    //     for(int i1 = -8; i1 <= 8; ++i1) {
    //         for(int i2 = -8; i2 <= 8; ++i2) {
    //             ChunkPos chunkPos = new ChunkPos(pos.x + i1, pos.z + i2);
    //             ChunkAccess chunk1 = structureAccess.level.getChunk(chunkPos.x, chunkPos.z);
    //             BiomeGenerationSettings biomeGenerationSettings = chunk1.carverBiome(() -> this.getBiomeGenerationSettings(super.biomeSource.getNoiseBiome(QuartPos.fromBlock(chunkPos.getMinBlockX()), 0, QuartPos.fromBlock(chunkPos.getMinBlockZ()), noiseConfig.sampler())));
    //             Iterable<Holder<ConfiguredWorldCarver<?>>> carvers = biomeGenerationSettings.getCarvers();
    //             int i3 = 0;

    //             for(Holder<ConfiguredWorldCarver<?>> holder : carvers) {
    //                 ConfiguredWorldCarver<?> configuredWorldCarver = (ConfiguredWorldCarver)holder.value();
    //                 worldgenRandom.setLargeFeatureSeed(seed + (long)i3, chunkPos.x, chunkPos.z);
    //                 if (configuredWorldCarver.isStartChunk(worldgenRandom)) {
    //                     Objects.requireNonNull(biomeManager1);
    //                     OTGWorldCarver worldCarver = new OTGWorldCarver();
    //                     worldCarver.carve(carvingContext, chunk, biomeManager1::getBiome, worldgenRandom, aquifer, chunkPos, carvingMask);
    //                 }

    //                 ++i3;
    //             }
    //         }
    //     }
     // }

    // TODO: conform buildNoise
    @Override
    public CompletableFuture<ChunkAccess> fillFromNoise(Blender blender, RandomState noiseConfig, StructureManager accessor, ChunkAccess chunk) {
        buildNoise(accessor, chunk, blender, noiseConfig);

        return CompletableFuture.completedFuture(chunk);
    }

    @Override
    public int getGenDepth() {
        return ((NoiseGeneratorSettings) this.settings.value()).noiseSettings().height();
    }

    @Override
    public int getSeaLevel() {
        return ((NoiseGeneratorSettings) this.settings.value()).seaLevel();
    }

    @Override
    public int getMinY() {
        return ((NoiseGeneratorSettings) this.settings.value()).noiseSettings().minY();
    }

    // TODO: conform this
    // Mob spawning on initial chunk spawn (animals).
    @Override
    public void spawnOriginalMobs(WorldGenRegion region) {
        // Check if mob spawning is enabled
        if (!region.getServer().getLevel(region.getLevel().dimension()).getGameRules().getBoolean(GameRules.RULE_DOMOBSPAWNING)) {
            return;
        }

        int chunkX = region.getCenter().x;
        int chunkZ = region.getCenter().z;
        IBiome biome = this.internalGenerator.getCachedBiomeProvider().getBiome(chunkX * Constants.CHUNK_SIZE + DecorationArea.DECORATION_OFFSET, chunkZ * Constants.CHUNK_SIZE + DecorationArea.DECORATION_OFFSET);
        WorldgenRandom sharedseedrandom = new WorldgenRandom(new XoroshiroRandomSource(RandomSupport.generateUniqueSeed()));
        sharedseedrandom.setDecorationSeed(region.getSeed(), chunkX << 4, chunkZ << 4);
        NaturalSpawner.spawnMobsForChunkGeneration(region, Holder.direct(((PaperBiome) biome).getBiome()), region.getCenter(), sharedseedrandom);
    }

    // @Override
    // public void spawnOriginalMobs(WorldGenRegion region) {
    //     if (!((NoiseGeneratorSettings) this.settings.value()).disableMobGeneration()) {
    //         ChunkPos $$1 = region.getCenter();
    //         Holder<Biome> $$2 = region.getBiome($$1.getWorldPosition().atY(region.getMaxY()));
    //         WorldgenRandom $$3 = new WorldgenRandom(new LegacyRandomSource(RandomSupport.generateUniqueSeed()));
    //         $$3.setDecorationSeed(region.getSeed(), $$1.getMinBlockX(), $$1.getMinBlockZ());
    //         NaturalSpawner.spawnMobsForChunkGeneration(region, $$2, $$1, $$3);
    //     }
    // }


    // Base terrain gen

    // Generates the base terrain for a chunk. Spigot compatible.
    public void buildNoiseSpigot(ServerLevel world, org.bukkit.generator.ChunkGenerator.ChunkData chunk, ChunkCoordinate chunkCoord, Random random) {
        ChunkBuffer buffer = new PaperChunkBuffer(chunk, chunkCoord);
        ChunkAccess cachedChunk = this.shadowChunkGenerator.getChunkFromCache(chunkCoord);
        if (cachedChunk != null) {
            this.shadowChunkGenerator.fillWorldGenChunkFromShadowChunk(chunkCoord, chunk, cachedChunk);
        } else {
            // Setup jigsaw data
            ObjectList<JigsawStructureData> structures = new ObjectArrayList<>(10);
            ObjectList<JigsawStructureData> junctions = new ObjectArrayList<>(32);

            ChunkAccess chunkAccess = world.getChunk(chunkCoord.getChunkX(), chunkCoord.getChunkZ());
            ChunkPos pos = new ChunkPos(chunkCoord.getChunkX(), chunkCoord.getChunkZ());
            findNoiseStructures(pos, chunkAccess, world.structureManager(), structures, junctions);

            this.internalGenerator.populateNoise(this.preset.getWorldConfig().getWorldHeightCap(), random, buffer, buffer.getChunkCoordinate(), structures, junctions);
            this.shadowChunkGenerator.setChunkGenerated(chunkCoord);
        }
    }

    public static void findNoiseStructures(ChunkPos pos, ChunkAccess chunk, StructureManager manager, ObjectList<JigsawStructureData> structures, ObjectList<JigsawStructureData> junctions) {
        int chunkX = pos.x;
        int chunkZ = pos.z;
        int startX = chunkX << 4;
        int startZ = chunkZ << 4;

        // Iterate through all of the jigsaw structures (villages, pillager outposts, nether fossils)

        // Get all structure starts in this chunk
        List<StructureStart> structureStarts = manager.startsForStructure(
                chunk.getPos(),
                // Last line I ain't too sure about
                (struct) -> struct.terrainAdaptation().equals(TerrainAdjustment.NONE));

        for (StructureStart start : structureStarts) {
            // Iterate through the pieces in the structure
            for (StructurePiece piece : start.getPieces()) {
                // Check if it intersects with this chunk
                if (piece.isCloseToChunk(pos, 12)) {
                    BoundingBox box = piece.getBoundingBox();

                    if (piece instanceof PoolElementStructurePiece villagePiece) {
                        // Add to the list if it's a rigid piece
                        if (villagePiece.getElement().getProjection() == StructureTemplatePool.Projection.RIGID) {
                            structures.add(new JigsawStructureData(box.minX(), box.minY(), box.minZ(), box.maxX(), villagePiece.getGroundLevelDelta(), box.maxZ(), true, 0, 0, 0));
                        }

                        // Get all the junctions in this piece
                        for (JigsawJunction junction : villagePiece.getJunctions()) {
                            int sourceX = junction.getSourceX();
                            int sourceZ = junction.getSourceZ();

                            // If the junction is in this chunk, then add to list
                            if (sourceX > startX - 12 && sourceZ > startZ - 12 && sourceX < startX + 15 + 12 && sourceZ < startZ + 15 + 12) {
                                junctions.add(new JigsawStructureData(0, 0, 0, 0, 0, 0, false, junction.getSourceX(), junction.getSourceGroundY(), junction.getSourceZ()));
                            }
                        }
                    } else {
                        structures.add(new JigsawStructureData(box.minX(), box.minY(), box.minZ(), box.maxX(), 0, box.maxZ(), false, 0, 0, 0));
                    }
                }
            }
        }
    }

    // Generates the base terrain for a chunk.
    public void buildNoise(StructureManager manager, ChunkAccess chunk, Blender blender, RandomState noiseConfig) {
        // If we've already generated and cached this
        // chunk while it was unloaded, use cached data.
        ChunkCoordinate chunkCoord = ChunkCoordinate.fromChunkCoords(chunk.getPos().x, chunk.getPos().z);

        // Dummy random, as we can't get the level random right now
        Random random = new Random();

        // When generating the spawn area, Spigot will get the structure and biome info for the first chunk before we can inject
        // Therefore, we need to re-do these calls now, for that one chunk
        if (fixBiomesForChunk != null && fixBiomesForChunk.equals(chunkCoord)) {
            HolderLookup structureSetLookup = manager.level.getMinecraftWorld().registryAccess().lookupOrThrow(Registries.STRUCTURE_SET);
            this.createStructures(
                    manager.level.getMinecraftWorld().registryAccess(),
                    ChunkGeneratorStructureState.createForNormal(noiseConfig, worldSeed, biomeSource, structureSetLookup, manager.level.getMinecraftWorld().spigotConfig),
                    manager,
                    chunk,
                    manager.level.getMinecraftWorld().getStructureManager(),
                    Level.OVERWORLD
            );
            this.createBiomes(noiseConfig, blender, manager, chunk);
            fixBiomesForChunk = null;
        }
        ChunkBuffer buffer = new PaperChunkBuffer(chunk);
        ChunkAccess cachedChunk = this.shadowChunkGenerator.getChunkFromCache(chunkCoord);
        if (cachedChunk != null) {
            this.shadowChunkGenerator.fillWorldGenChunkFromShadowChunk(chunkCoord, chunk, cachedChunk);
        } else {
            // Setup jigsaw data
            ObjectList<JigsawStructureData> structures = new ObjectArrayList<>(10);
            ObjectList<JigsawStructureData> junctions = new ObjectArrayList<>(32);

            ChunkPos pos = new ChunkPos(chunkCoord.getChunkX(), chunkCoord.getChunkZ());

            findNoiseStructures(pos, chunk, manager, structures, junctions);

            this.internalGenerator.populateNoise(this.preset.getWorldConfig().getWorldHeightCap(), random, buffer, buffer.getChunkCoordinate(), structures, junctions);
            this.shadowChunkGenerator.setChunkGenerated(chunkCoord);
        }
    }

    // Population / decoration

    @Override
    public void applyBiomeDecoration(WorldGenLevel worldGenLevel, ChunkAccess chunk, StructureManager manager) {
        if (!OTG.getEngine().getPluginConfig().getDecorationEnabled()) {
            return;
        }

        ChunkPos chunkpos = chunk.getPos();
        if (!SharedConstants.debugVoidTerrain(chunkpos)) {
            WorldGenRegion worldGenRegion = ((WorldGenRegion) worldGenLevel);
            SectionPos sectionpos = SectionPos.of(chunkpos, worldGenRegion.getMinSectionY());
            org.bukkit.World world = worldGenLevel.getMinecraftWorld().getWorld();
            // only call when a populator is present (prevents unnecessary entity conversion)
            if (!world.getPopulators().isEmpty()) {
                org.bukkit.craftbukkit.generator.CraftLimitedRegion limitedRegion = new org.bukkit.craftbukkit.generator.CraftLimitedRegion(worldGenLevel, chunk.getPos());
                int x = chunk.getPos().x;
                int z = chunk.getPos().z;
                for (org.bukkit.generator.BlockPopulator populator : world.getPopulators()) {
                    WorldgenRandom seededrandom = new WorldgenRandom(new net.minecraft.world.level.levelgen.LegacyRandomSource(worldGenLevel.getSeed()));
                    seededrandom.setDecorationSeed(worldGenLevel.getSeed(), x, z);
                    populator.populate(world, new org.bukkit.craftbukkit.util.RandomSourceWrapper.RandomWrapper(seededrandom), x, z, limitedRegion);
                }
                limitedRegion.saveEntities();
                limitedRegion.breakLink();
            }

            // This section is the only part that diverges from vanilla, but it probably has to stay this way for now
            //
            int worldX = worldGenRegion.getCenter().x * Constants.CHUNK_SIZE;
            int worldZ = worldGenRegion.getCenter().z * Constants.CHUNK_SIZE;
            ChunkCoordinate chunkBeingDecorated = ChunkCoordinate.fromBlockCoords(worldX, worldZ);
            IBiome noiseBiome = this.internalGenerator.getCachedBiomeProvider().getNoiseBiome((worldGenRegion.getCenter().x << 2) + 2, (worldGenRegion.getCenter().z << 2) + 2);
            PaperWorldGenRegion forgeWorldGenRegion = new PaperWorldGenRegion(this.preset.getFolderName(), this.preset.getWorldConfig(), worldGenRegion, this);
            // World save folder name may not be identical to level name, fetch it.
            Path worldSaveFolder = worldGenRegion.getServer().getWorldPath(LevelResource.PLAYER_DATA_DIR).getParent();
            this.chunkDecorator.decorate(this.preset.getFolderName(), chunkBeingDecorated, forgeWorldGenRegion, noiseBiome.getBiomeConfig(), getStructureCache(worldSaveFolder));

            Set<Biome> set = new ObjectArraySet<>();
            ChunkPos.rangeClosed(sectionpos.chunk(), 1).forEach((pos) ->
            {
                ChunkAccess chunkaccess = worldGenLevel.getChunk(pos.x, pos.z);
                for (LevelChunkSection levelchunksection : chunkaccess.getSections()) {
                    levelchunksection.getBiomes().getAll((b) -> set.add(b.value()));
                }
            });
            set.retainAll(this.biomeSource.possibleBiomes().stream().map(Holder::value).collect(Collectors.toSet()));
        }
    }

    // Samples the noise at a column and provides a view of the blockstates, or fills a heightmap.
    private int sampleHeightmap(int x, int z, @Nullable BlockState[] blockStates, @Nullable Predicate<BlockState> predicate, int cellNoiseMinY, int noiseCellCount) {
        NoiseSettings noisesettings = this.generatorSettings.value().noiseSettings();
        int cellWidth = noisesettings.getCellWidth();
        // Get all of the coordinate starts and positions
        int xStart = Math.floorDiv(x, cellWidth);
        int zStart = Math.floorDiv(z, cellWidth);
        int xProgress = Math.floorMod(x, cellWidth);
        int zProgress = Math.floorMod(z, cellWidth);
        double xLerp = (double) xProgress / cellWidth;
        double zLerp = (double) zProgress / cellWidth;
        // Create the noise data in a 2 * 2 * 32 grid for interpolation.
        double[][] noiseData = new double[4][this.internalGenerator.getNoiseSizeY() + 1];

        // Initialize noise array.
        for (int i = 0; i < noiseData.length; i++) {
            noiseData[i] = new double[this.internalGenerator.getNoiseSizeY() + 1];
        }

        // Sample all 4 nearby columns.
        this.internalGenerator.getNoiseColumn(noiseData[0], xStart, zStart);
        this.internalGenerator.getNoiseColumn(noiseData[1], xStart, zStart + 1);
        this.internalGenerator.getNoiseColumn(noiseData[2], xStart + 1, zStart);
        this.internalGenerator.getNoiseColumn(noiseData[3], xStart + 1, zStart + 1);

        //IBiomeConfig biomeConfig = this.internalGenerator.getCachedBiomeProvider().getBiomeConfig(x, z);

        BlockState state;
        double x0z0y0;
        double x0z1y0;
        double x1z0y0;
        double x1z1y0;
        double x0z0y1;
        double x0z1y1;
        double x1z0y1;
        double x1z1y1;
        double yLerp;
        double density;
        int y;
        // [0, 32] -> noise chunks
        for (int noiseY = this.internalGenerator.getNoiseSizeY() - 1; noiseY >= 0; --noiseY) {
            // Gets all the noise in a 2x2x2 cube and interpolates it together.
            // Lower pieces
            x0z0y0 = noiseData[0][noiseY];
            x0z1y0 = noiseData[1][noiseY];
            x1z0y0 = noiseData[2][noiseY];
            x1z1y0 = noiseData[3][noiseY];
            // Upper pieces
            x0z0y1 = noiseData[0][noiseY + 1];
            x0z1y1 = noiseData[1][noiseY + 1];
            x1z0y1 = noiseData[2][noiseY + 1];
            x1z1y1 = noiseData[3][noiseY + 1];

            // [0, 8] -> noise pieces
            for (int pieceY = 7; pieceY >= 0; --pieceY) {
                yLerp = (double) pieceY / 8.0;
                // Density at this position given the current y interpolation
                // used to have yLerp and xLerp switched, which seemed wrong? -auth
                density = Mth.lerp3(xLerp, yLerp, zLerp, x0z0y0, x0z0y1, x1z0y0, x1z0y1, x0z1y0, x0z1y1, x1z1y0, x1z1y1);

                // Get the real y position (translate noise chunk and noise piece)
                y = (noiseY * 8) + pieceY;

                //state = this.getBlockState(density, y, biomeConfig);
                state = this.getBlockState(density, y);
                if (blockStates != null) {
                    blockStates[y] = state;
                }

                // return y if it fails the check
                if (predicate != null && predicate.test(state)) {
                    return y + 1;
                }
            }
        }

        return 0;
    }

    private BlockState getBlockState(double density, int y) {
        if (density > 0.0D) {
            return this.defaultBlock;
        } else if (y < this.getSeaLevel()) {
            return this.defaultFluid;
        } else {
            return Blocks.AIR.defaultBlockState();
        }
    }

    // Getters / misc

    public Preset getPreset() {
        return preset;
    }

    public CustomStructureCache getStructureCache(Path worldSaveFolder) {
        if (this.structureCache == null) {
            this.structureCache = OTG.getEngine().createCustomStructureCache(this.preset.getFolderName(), worldSaveFolder, this.worldSeed, this.preset.getWorldConfig().getCustomStructureType() == SettingsEnums.CustomStructureType.BO4);
        }
        return this.structureCache;
    }

    double getBiomeBlocksNoiseValue(int blockX, int blockZ) {
        return this.internalGenerator.getBiomeBlocksNoiseValue(blockX, blockZ);
    }

    public void fixBiomes(int chunkX, int chunkZ) {
        this.fixBiomesForChunk = ChunkCoordinate.fromChunkCoords(chunkX, chunkZ);
    }

    // Shadowgen

    public Boolean checkHasVanillaStructureWithoutLoading(ServerLevel world, ChunkCoordinate chunkCoord) {
        // This method needs updating to 1.18.2 in the right way. For now, has been replaced by this.checkForVanillaStructure()
        return false;
        //return this.shadowChunkGenerator.checkHasVanillaStructureWithoutLoading(world, this, this.biomeSource, this., chunkCoord, this.internalGenerator.getCachedBiomeProvider(), false);
    }

    public int getHighestBlockYInUnloadedChunk(Random worldRandom, int x, int z, boolean findSolid, boolean findLiquid, boolean ignoreLiquid, boolean ignoreSnow, ServerLevel level) {
        return this.shadowChunkGenerator.getHighestBlockYInUnloadedChunk(this.internalGenerator, this.preset.getWorldConfig().getWorldHeightCap(), worldRandom, x, z, findSolid, findLiquid, ignoreLiquid, ignoreSnow, level);
    }

    public LocalMaterialData getMaterialInUnloadedChunk(Random worldRandom, int x, int y, int z, ServerLevel level) {
        return this.shadowChunkGenerator.getMaterialInUnloadedChunk(this.internalGenerator, this.preset.getWorldConfig().getWorldHeightCap(), worldRandom, x, y, z, level);
    }

    public PaperChunkBuffer getChunkWithoutLoadingOrCaching(Random random, ChunkCoordinate chunkCoord, ServerLevel level) {
        return this.shadowChunkGenerator.getChunkWithoutLoadingOrCaching(this.internalGenerator, this.preset.getWorldConfig().getWorldHeightCap(), random, chunkCoord, level);
    }

    // Uses the vanilla method of checking if there is a vanilla structure in range
    // Might be slower than old solution in ShadowChunkGenerator
    public boolean checkForVanillaStructure(ChunkCoordinate chunkCoordinate) {
        // TODO: This is jank, but we'll do this temporarily
        // We should try to implement the older solution present in ShadowChunkGenerator
        return false;
		/*
		int x = chunkCoordinate.getChunkX();
		int z = chunkCoordinate.getChunkZ();
		// Structures with a radius of 4
		PaperBiome biome = (PaperBiome) getCachedBiomeProvider().getNoiseBiome((x << 2) + 2, (z << 2) + 2);
		if (biome.getBiomeConfig().getVillageType() != SettingsEnums.VillageType.disabled)
			if (this.hasFeatureChunkInRange(BuiltinStructureSets.VILLAGES, worldSeed, x, z, 4))
				return true;
		if (biome.getBiomeConfig().getBastionRemnantEnabled())
			if (this.hasFeatureChunkInRange(BuiltinStructureSets.NETHER_COMPLEXES, worldSeed, x, z, 4))
				return true;
		if (biome.getBiomeConfig().getEndCityEnabled())
			if (this.hasFeatureChunkInRange(BuiltinStructureSets.END_CITIES, worldSeed, x, z, 4))
				return true;
		if (biome.getBiomeConfig().getOceanMonumentsEnabled())
			if (this.hasFeatureChunkInRange(BuiltinStructureSets.OCEAN_MONUMENTS, worldSeed, x, z, 4))
				return true;
		if (biome.getBiomeConfig().getWoodlandMansionsEnabled())
			if (this.hasFeatureChunkInRange(BuiltinStructureSets.WOODLAND_MANSIONS, worldSeed, x, z, 4))
				return true;
		switch (biome.getBiomeConfig().getRareBuildingType()) {
			case disabled -> {}
			case desertPyramid -> {
				if (this.hasFeatureChunkInRange(BuiltinStructureSets.DESERT_PYRAMIDS, worldSeed, x, z, 1))
					return true;
			}
			case jungleTemple -> {
				if (this.hasFeatureChunkInRange(BuiltinStructureSets.JUNGLE_TEMPLES, worldSeed, x, z, 1))
					return true;
			}
			case swampHut -> {
				if (this.hasFeatureChunkInRange(BuiltinStructureSets.SWAMP_HUTS, worldSeed, x, z, 1))
					return true;
			}
			case igloo -> {
				if (this.hasFeatureChunkInRange(BuiltinStructureSets.IGLOOS, worldSeed, x, z, 1))
					return true;
			}
		}
		if (biome.getBiomeConfig().getShipWreckEnabled() || biome.getBiomeConfig().getShipWreckBeachedEnabled())
			if (this.hasFeatureChunkInRange(BuiltinStructureSets.SHIPWRECKS, worldSeed, x, z, 1))
				return true;
		if (biome.getBiomeConfig().getPillagerOutpostEnabled())
			if (this.hasFeatureChunkInRange(BuiltinStructureSets.PILLAGER_OUTPOSTS, worldSeed, x, z, 1))
				return true;
		if (biome.getBiomeConfig().getOceanRuinsType() != SettingsEnums.OceanRuinsType.disabled)
			return this.hasFeatureChunkInRange(BuiltinStructureSets.OCEAN_RUINS, worldSeed, x, z, 1);
		return false;*/
    }
}
