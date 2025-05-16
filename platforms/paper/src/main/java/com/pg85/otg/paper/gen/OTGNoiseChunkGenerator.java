package com.pg85.otg.paper.gen;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
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
import com.pg85.otg.paper.presets.PaperPresetLoader;
import com.pg85.otg.paper.util.ObfuscationHelper;
import com.pg85.otg.util.ChunkCoordinate;
import com.pg85.otg.util.gen.ChunkBuffer;
import com.pg85.otg.util.gen.DecorationArea;
import com.pg85.otg.util.gen.JigsawStructureData;
import com.pg85.otg.util.logging.LogCategory;
import com.pg85.otg.util.logging.LogLevel;
import com.pg85.otg.util.materials.LocalMaterialData;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectArraySet;
import it.unimi.dsi.fastutil.objects.ObjectList;
import net.minecraft.SharedConstants;
import net.minecraft.core.*;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.util.Mth;
import net.minecraft.util.random.WeightedRandomList;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.*;
import net.minecraft.world.level.biome.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.*;
import net.minecraft.world.level.levelgen.*;
import net.minecraft.world.level.levelgen.blending.Blender;
import net.minecraft.world.level.levelgen.structure.*;
import net.minecraft.world.level.levelgen.structure.pools.JigsawJunction;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import net.minecraft.world.level.levelgen.synth.NormalNoise;
import net.minecraft.world.level.storage.LevelResource;

import javax.annotation.Nullable;
import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class OTGNoiseChunkGenerator extends ChunkGenerator {
    // Create a codec to serialise/deserialise OTGNoiseChunkGenerator
    public static final Codec<OTGNoiseChunkGenerator> CODEC = RecordCodecBuilder.create(
            (p_236091_0_) -> p_236091_0_
                    .group(
                            Codec.STRING.fieldOf("preset_folder_name").forGetter(p -> p.presetFolderName),
                            BiomeSource.CODEC.fieldOf("biome_source").forGetter(p -> p.biomeSource),
                            RegistryOps.retrieveRegistry(Registry.STRUCTURE_SET_REGISTRY).forGetter(p -> p.structureSets),
                            RegistryOps.retrieveRegistry(Registry.NOISE_REGISTRY).forGetter(p -> p.noises),
                            Codec.LONG.fieldOf("seed").stable().forGetter(p -> p.worldSeed),
                            NoiseGeneratorSettings.CODEC.fieldOf("settings").forGetter(p -> p.generatorSettings)
                    ).apply(
                            p_236091_0_,
                            p_236091_0_.stable(OTGNoiseChunkGenerator::new)
                    )
    );

    private final Holder<NoiseGeneratorSettings> generatorSettings;
    private final long worldSeed;
    private final int noiseHeight;
    protected final BlockState defaultBlock;
    protected final BlockState defaultFluid;

    private final ShadowChunkGenerator shadowChunkGenerator;
    public final OTGChunkGenerator internalGenerator;
    private final OTGChunkDecorator chunkDecorator;
    private final String presetFolderName;
    private final Preset preset;
    private final NoiseRouter router;
    //protected final WorldgenRandom random;

    // TODO: Move this to WorldLoader when ready?
    private CustomStructureCache structureCache;

    // Used to specify which chunk to regen biomes and structures for
    // Necessary because Spigot calls those methods before we have the chance to inject
    private ChunkCoordinate fixBiomesForChunk = null;
    private final Climate.Sampler sampler;
    private final Registry<NormalNoise.NoiseParameters> noises;

    public OTGNoiseChunkGenerator(BiomeSource biomeSource, long seed, Registry<StructureSet> structureSetRegistry, Registry<NormalNoise.NoiseParameters> noiseRegistry, Holder<NoiseGeneratorSettings> generatorSettings) {
        this("default", biomeSource, structureSetRegistry, noiseRegistry, seed, generatorSettings);
    }

    public OTGNoiseChunkGenerator(String presetName, BiomeSource biomeSource, Registry<StructureSet> structureSetRegistry, Registry<NormalNoise.NoiseParameters> noiseRegistry, long seed, Holder<NoiseGeneratorSettings> generatorSettings) {
        this(presetName, biomeSource, biomeSource, structureSetRegistry, noiseRegistry, seed, generatorSettings);
    }

    // Vanilla has two biome sources, where the first is population and the second is runtime. Don't know the practical difference this makes.
    private OTGNoiseChunkGenerator(String presetFolderName, BiomeSource populationSource, BiomeSource runtimeSource, Registry<StructureSet> structureSetRegistry, Registry<NormalNoise.NoiseParameters> noiseRegistry, long seed, Holder<NoiseGeneratorSettings> generatorSettings) {
        // Modified
        //super(structureSetRegistry, Optional.of(getEnabledStructures(structureSetRegistry, presetFolderName)), populationSource, runtimeSource, seed);
        super(structureSetRegistry, Optional.of(getEnabledStructures(structureSetRegistry, presetFolderName)), populationSource);
        if (!(populationSource instanceof ILayerSource)) {
            throw new RuntimeException("OTG has detected an incompatible biome provider- try using otg:otg as the biome source name");
        }

        this.presetFolderName = presetFolderName;
        this.worldSeed = seed;
        NoiseGeneratorSettings settings = generatorSettings.value();
        this.generatorSettings = generatorSettings;
        NoiseSettings noisesettings = settings.noiseSettings();
        this.noiseHeight = noisesettings.height();
        this.noises = noiseRegistry;
        this.defaultBlock = settings.defaultBlock();
        this.defaultFluid = settings.defaultFluid();

        this.preset = OTG.getEngine().getPresetLoader().getPresetByFolderName(presetFolderName);
        this.shadowChunkGenerator = new ShadowChunkGenerator();
        this.internalGenerator = new OTGChunkGenerator(this.preset, seed, (ILayerSource) populationSource, ((PaperPresetLoader) OTG.getEngine().getPresetLoader()).getGlobalIdMapping(presetFolderName), OTG.getEngine().getLogger());
        this.chunkDecorator = new OTGChunkDecorator();

        // Modified for 1.19
        this.router = settings.noiseRouter();
        this.sampler = new Climate.Sampler(this.router.temperature(), this.router.vegetation(), this.router.continents(), this.router.erosion(), this.router.depth(), this.router.ridges(), generatorSettings.value().spawnTarget());

    }

    // Method to remove structures which have been disabled in the world config
    private static HolderSet<StructureSet> getEnabledStructures(Registry<StructureSet> registry, String presetFolderName) {
        Preset preset = OTG.getEngine().getPresetLoader().getPresetByFolderName(presetFolderName);
        IWorldConfig worldConfig = preset.getWorldConfig();
        List<Holder<StructureSet>> holderList = new ArrayList<>();

        if (worldConfig.getRareBuildingsEnabled()) {
            holderList.add(registry.getHolderOrThrow(BuiltinStructureSets.IGLOOS));
            holderList.add(registry.getHolderOrThrow(BuiltinStructureSets.SWAMP_HUTS));
            holderList.add(registry.getHolderOrThrow(BuiltinStructureSets.DESERT_PYRAMIDS));
            holderList.add(registry.getHolderOrThrow(BuiltinStructureSets.JUNGLE_TEMPLES));
        }

        if (worldConfig.getVillagesEnabled())
            holderList.add(registry.getHolderOrThrow(BuiltinStructureSets.VILLAGES));

        if (worldConfig.getPillagerOutpostsEnabled())
            holderList.add(registry.getHolderOrThrow(BuiltinStructureSets.PILLAGER_OUTPOSTS));

        if (worldConfig.getStrongholdsEnabled())
            holderList.add(registry.getHolderOrThrow(BuiltinStructureSets.STRONGHOLDS));

        if (worldConfig.getOceanMonumentsEnabled())
            holderList.add(registry.getHolderOrThrow(BuiltinStructureSets.OCEAN_MONUMENTS));

        if (worldConfig.getEndCitiesEnabled())
            holderList.add(registry.getHolderOrThrow(BuiltinStructureSets.END_CITIES));

        if (worldConfig.getWoodlandMansionsEnabled())
            holderList.add(registry.getHolderOrThrow(BuiltinStructureSets.WOODLAND_MANSIONS));

        if (worldConfig.getBuriedTreasureEnabled())
            holderList.add(registry.getHolderOrThrow(BuiltinStructureSets.BURIED_TREASURES));

        if (worldConfig.getMineshaftsEnabled())
            holderList.add(registry.getHolderOrThrow(BuiltinStructureSets.MINESHAFTS));

        if (worldConfig.getRuinedPortalsEnabled())
            holderList.add(registry.getHolderOrThrow(BuiltinStructureSets.RUINED_PORTALS));

        if (worldConfig.getShipWrecksEnabled())
            holderList.add(registry.getHolderOrThrow(BuiltinStructureSets.SHIPWRECKS));

        if (worldConfig.getOceanRuinsEnabled())
            holderList.add(registry.getHolderOrThrow(BuiltinStructureSets.OCEAN_RUINS));

        if (worldConfig.getBastionRemnantsEnabled())
            holderList.add(registry.getHolderOrThrow(BuiltinStructureSets.NETHER_COMPLEXES));

        if (worldConfig.getNetherFortressesEnabled())
            holderList.add(registry.getHolderOrThrow(BuiltinStructureSets.NETHER_COMPLEXES));

        if (worldConfig.getNetherFossilsEnabled())
            holderList.add(registry.getHolderOrThrow(BuiltinStructureSets.NETHER_FOSSILS));

        HolderSet<StructureSet> holderSet = HolderSet.direct(holderList);
        return holderSet;
    }


    public ICachedBiomeProvider getCachedBiomeProvider() {
        return this.internalGenerator.getCachedBiomeProvider();
    }

    public void saveStructureCache() {
        if (this.chunkDecorator.getIsSaveRequired() && this.structureCache != null) {
            this.structureCache.saveToDisk(OTG.getEngine().getLogger(), this.chunkDecorator);
        }
    }

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

    @Override
    public CompletableFuture<ChunkAccess> fillFromNoise(Executor executor, Blender blender, RandomState noiseConfig, StructureManager accessor, ChunkAccess chunk) {
        buildNoise(accessor, chunk, executor, blender, noiseConfig);

        return CompletableFuture.completedFuture(chunk);
    }

    // Generates the base terrain for a chunk.
    public void buildNoise(StructureManager manager, ChunkAccess chunk, Executor executor, Blender blender, RandomState noiseConfig) {
        // If we've already generated and cached this
        // chunk while it was unloaded, use cached data.
        ChunkCoordinate chunkCoord = ChunkCoordinate.fromChunkCoords(chunk.getPos().x, chunk.getPos().z);

        // Dummy random, as we can't get the level random right now
        Random random = new Random();

        // When generating the spawn area, Spigot will get the structure and biome info for the first chunk before we can inject
        // Therefore, we need to re-do these calls now, for that one chunk
        if (fixBiomesForChunk != null && fixBiomesForChunk.equals(chunkCoord)) {

            // Should only run when first creating the world, on a single chunk
            // TODO: we need a ServerLevel or similar for this
            //this.createStructures(world.getMinecraftWorld().registryAccess(), world.getMinecraftWorld().structureFeatureManager(), chunk, world.getMinecraftWorld().getStructureManager(), worldSeed);
            this.createBiomes(chunk.biomeRegistry, executor, noiseConfig, blender, manager, chunk);
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


    // Replaces surface and ground blocks in base terrain and places bedrock.
    @Override
    public void buildSurface(WorldGenRegion worldGenRegion, StructureManager structures, RandomState noiseConfig, ChunkAccess chunk) {
        // OTG handles surface/ground blocks during base terrain gen. For non-OTG biomes used
    }

    // Carvers: Caves and ravines

    // NOTE: Commenting this out temporarily
    // @Override
    // public void applyCarvers(WorldGenRegion chunkRegion, long seed, RandomState noiseConfig, BiomeManager biomeManager, StructureManager structureAccess, ChunkAccess chunk, GenerationStep.Carving stage) {
    //     if (stage == GenerationStep.Carving.AIR) {
    //         ProtoChunk protoChunk = (ProtoChunk) chunk;
    //         ChunkBuffer chunkBuffer = new PaperChunkBuffer(protoChunk);
    //         /*
    //          * The following code exists as Minecraft 1.18 has a new "carvingMask"
    //          * class that they use instead of BitSet
    //          * However, that class is really just a wrapper that makes it harder
    //          * to access the BitSet inside.
    //          * We simply use reflections to access the BitSet
    //          * Which enables us to send it up into common code.
    //          *
    //          * - Frank
    //          */
    //         CarvingMask carvingMaskRaw = protoChunk.getOrCreateCarvingMask(stage);
    //         try {
    //             Field theRealMask = ObfuscationHelper.getField(CarvingMask.class, "mask", "b");
    //             theRealMask.setAccessible(true);
    //             BitSet carvingMask = (BitSet) theRealMask.get(carvingMaskRaw);

    //             // TODO: Carvers need updating to use sub-0 height, and also to potentially use the new Carving Mask -auth
    //             // Leaving this commented out until at least the sub-0 is implemented.
    //             // this.internalGenerator.carve(chunkBuffer, seed, protoChunk.getPos().x, protoChunk.getPos().z, carvingMask, true, true); //TODO: Don't use hardcoded true
    //         } catch (NoSuchFieldException e) {
    //             if (OTG.getEngine().getLogger().getLogCategoryEnabled(LogCategory.MAIN)) {
    //                 OTG.getEngine().getLogger().log(LogLevel.ERROR, LogCategory.MAIN, "!!! Error obtaining the carving mask! Caves will not generate! Stacktrace:\n" + e.getStackTrace());
    //             }
    //         } catch (IllegalAccessException e) {
    //             if (OTG.getEngine().getLogger().getLogCategoryEnabled(LogCategory.MAIN)) {
    //                 OTG.getEngine().getLogger().log(LogLevel.ERROR, LogCategory.MAIN, "!!! Error obtaining the carving mask! Caves will not generate! Stacktrace:\n" + e.getStackTrace());
    //             }
    //         }
    //     }
    //     // Commenting out as abstract implies it is no longer needed.
    //     // super.applyCarvers(chunkRegion, seed, biomeManager, structureAccess, chunk, stage);
    // }

    // Population / decoration

    @Override
    public void applyBiomeDecoration(WorldGenLevel worldGenLevel, ChunkAccess chunk, StructureManager manager) {
        if (!OTG.getEngine().getPluginConfig().getDecorationEnabled()) {
            return;
        }

        ChunkPos chunkpos = chunk.getPos();
        if (!SharedConstants.debugVoidTerrain(chunkpos)) {
            WorldGenRegion worldGenRegion = ((WorldGenRegion) worldGenLevel);
            SectionPos sectionpos = SectionPos.of(chunkpos, worldGenRegion.getMinSection());
            BlockPos blockpos = sectionpos.origin();
            // NOTE: Whole section removed, updated with vanilla code changes, comment beneath section shows this.
            // Comment beneath written by authvin on March 15th, 2022, so we can assume not much has changed since.
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

            /*
             * For now, we can assume this code is unneeded
             * Thank you, 1.19, for not having to make us handle the messy crap
             */
			/*int length = list.size();

			try {
				Registry<PlacedFeature> placedRegistry = worldGenRegion.registryAccess().registryOrThrow(Registry.PLACED_FEATURE_REGISTRY);
				int steps = Math.max(GenerationStep.Decoration.values().length, length);

				for(int step = 0; step < steps; ++step)
				{
					int n = 0;
					if (manager.shouldGenerateFeatures())
					{
						for(ConfiguredStructureFeature<?, ?> feature : configuredStructureMap.getOrDefault(step, Collections.emptyList()))
						{
							worldgenrandom.setFeatureSeed(decorationSeed, n, step);
							Supplier<String> supplier = () -> structureRegistry
									.getResourceKey(feature)
									.map(Object::toString)
									.orElseGet(feature::toString);

							try {
								worldGenRegion.setCurrentlyGenerating(supplier);
								manager.startsForFeature(sectionpos, feature).forEach(
										start -> start.placeInChunk(worldGenRegion, manager, this, worldgenrandom, getWritableArea(chunk), chunkpos));
							} catch (Exception exception) {
								CrashReport report = CrashReport.forThrowable(exception, "Feature placement");
								report.addCategory("Feature").setDetail("Description", supplier::get);
								throw new ReportedException(report);
							}

							++n;
						}
					}

					if (step < length)
					{
						IntSet intset = new IntArraySet();

						for(Biome biome : set)
						{
							List<HolderSet<PlacedFeature>> holderList = biome.getGenerationSettings().features();
							if (step < holderList.size())
							{
								HolderSet<PlacedFeature> featureHolder = holderList.get(step);
								BiomeSource.StepFeatureData data = list.get(step);
								featureHolder.stream().map(Holder::value).forEach(
										f -> intset.add(data.indexMapping().applyAsInt(f)));
							}
						}

						int biomeCount = intset.size();
						int[] aint = intset.toIntArray();
						Arrays.sort(aint);
						BiomeSource.StepFeatureData biomesource$stepfeaturedata = list.get(step);

						for(int i = 0; i < biomeCount; ++i)
						{
							int j = aint[i];
							PlacedFeature placedfeature = biomesource$stepfeaturedata.features().get(j);
							Supplier<String> supplier1 = () -> {
								return placedRegistry.getResourceKey(placedfeature).map(Object::toString).orElseGet(placedfeature::toString);
							};
							worldgenrandom.setFeatureSeed(decorationSeed, j, step);

							try {
								worldGenRegion.setCurrentlyGenerating(supplier1);
								placedfeature.placeWithBiomeCheck(worldGenRegion, this, worldgenrandom, blockpos);
							} catch (Exception exception1) {
								CrashReport crashreport2 = CrashReport.forThrowable(exception1, "Feature placement");
								crashreport2.addCategory("Feature").setDetail("Description", supplier1::get);
								throw new ReportedException(crashreport2);
							}
						}
					}
				}

				worldGenRegion.setCurrentlyGenerating(null);
			} catch (Exception exception2) {
				CrashReport crashreport = CrashReport.forThrowable(exception2, "Biome decoration");
				crashreport.addCategory("Generation").setDetail("CenterX", chunkpos.x).setDetail("CenterZ", chunkpos.z).setDetail("Seed", decorationSeed);
				throw new ReportedException(crashreport);
			}*/
        }
    }

    // TODO: Do we need this?
    private static BoundingBox getWritableArea(ChunkAccess p_187718_) {
        ChunkPos chunkpos = p_187718_.getPos();
        int i = chunkpos.getMinBlockX();
        int j = chunkpos.getMinBlockZ();
        LevelHeightAccessor levelheightaccessor = p_187718_.getHeightAccessorForGeneration();
        int k = levelheightaccessor.getMinBuildHeight() + 1;
        int l = levelheightaccessor.getMaxBuildHeight() - 1;
        return new BoundingBox(i, k, j, i + 15, l, j + 15);
    }

    // Mob spawning on initial chunk spawn (animals).
    @Override
    public void spawnOriginalMobs(WorldGenRegion region) {
        // We don't respect the mob spawning setting, because we can't access it
        int chunkX = region.getCenter().x;
        int chunkZ = region.getCenter().z;
        IBiome biome = this.internalGenerator.getCachedBiomeProvider().getBiome(chunkX * Constants.CHUNK_SIZE + DecorationArea.DECORATION_OFFSET, chunkZ * Constants.CHUNK_SIZE + DecorationArea.DECORATION_OFFSET);
        WorldgenRandom sharedseedrandom = new WorldgenRandom(new XoroshiroRandomSource(RandomSupport.generateUniqueSeed()));
        sharedseedrandom.setDecorationSeed(region.getSeed(), chunkX << 4, chunkZ << 4);
        NaturalSpawner.spawnMobsForChunkGeneration(region, Holder.direct(((PaperBiome) biome).getBiome()), region.getCenter(), sharedseedrandom);
    }

    // Mob spawning on chunk tick
    @Override
    public WeightedRandomList<MobSpawnSettings.SpawnerData> getMobsAt(Holder<Biome> biome, StructureManager structureManager, MobCategory entityClassification, BlockPos blockPos) {
		/*if (structureManager.getStructureAt(blockPos, StructureFeature.SWAMP_HUT).isValid())
		{
			if (entityClassification == MobCategory.MONSTER)
			{
				return StructureFeature.SWAMP_HUT.getSpecialEnemies();
			}

			if (entityClassification == MobCategory.CREATURE)
			{
				return StructureFeature.SWAMP_HUT.getSpecialAnimals();
			}
		}

		if (entityClassification == MobCategory.MONSTER)
		{
			if (structureManager.getStructureAt(blockPos, false, StructureFeature.PILLAGER_OUTPOST).isValid())
			{
				return StructureFeature.PILLAGER_OUTPOST.getSpecialEnemies();
			}

			if (structureManager.getStructureAt(blockPos, false, StructureFeature.OCEAN_MONUMENT).isValid())
			{
				return StructureFeature.OCEAN_MONUMENT.getSpecialEnemies();
			}

			if (structureManager.getStructureAt(blockPos, true, StructureFeature.NETHER_BRIDGE).isValid())
			{
				return StructureFeature.NETHER_BRIDGE.getSpecialEnemies();
			}
		}

		return entityClassification == MobCategory.UNDERGROUND_WATER_CREATURE && structureManager.getStructureAt(blockPos, false, StructureFeature.OCEAN_MONUMENT).isValid() ? StructureFeature.OCEAN_MONUMENT.getSpecialUndergroundWaterAnimals() : */
        /*
         * Judging by the fact that the methods were removed,
         * I believe the below method will work regardless of structure.
         * - Frank
         */
        return super.getMobsAt(biome, structureManager, entityClassification, blockPos);
    }

    // Noise
    @Override
    public int getBaseHeight(int x, int z, Heightmap.Types heightmap, LevelHeightAccessor world, RandomState noiseConfig) {
        return getBaseHeight(x, z, heightmap, world);
    }

    public int getBaseHeight(int x, int z, Heightmap.Types heightmap, LevelHeightAccessor world) {
        NoiseSettings noiseSettings = this.generatorSettings.value().noiseSettings();
        int minGenY = Math.max(noiseSettings.minY(), world.getMinBuildHeight());
        int maxGenY = Math.min(noiseSettings.minY() + noiseSettings.height(), world.getMaxBuildHeight());
        int cellNoiseMinY = Math.floorDiv(minGenY, noiseSettings.getCellHeight());
        int noiseCellCount = Math.floorDiv(maxGenY - minGenY, noiseSettings.getCellHeight());
        return noiseCellCount <= 0 ?
                world.getMinBuildHeight() :
                this.sampleHeightmap(x, z, null, heightmap.isOpaque(), cellNoiseMinY, noiseCellCount);
    }

    // Provides a sample of the full column for structure generation.
    @Override
    public NoiseColumn getBaseColumn(int x, int z, LevelHeightAccessor world, RandomState noiseConfig) {
        NoiseSettings noiseSettings = this.generatorSettings.value().noiseSettings();
        int minGenY = Math.max(noiseSettings.minY(), world.getMinBuildHeight());
        int maxGenY = Math.min(noiseSettings.minY() + noiseSettings.height(), world.getMaxBuildHeight());
        int cellNoiseMinY = Math.floorDiv(minGenY, noiseSettings.getCellHeight());
        int noiseCellCount = Math.floorDiv(maxGenY - minGenY, noiseSettings.getCellHeight());
        if (noiseCellCount <= 0) {
            return new NoiseColumn(minGenY, new BlockState[0]);
        } else {
            BlockState[] blockStates = new BlockState[noiseCellCount * noiseSettings.getCellHeight()];
            this.sampleHeightmap(x, z, blockStates, null, cellNoiseMinY, noiseCellCount);
            return new NoiseColumn(0, blockStates);
        }
    }

    @Override
    public void addDebugScreenInfo(List<String> text, RandomState noiseConfig, BlockPos pos) {
        // TODO: what does this do? -auth
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

    // No longer used?
	/*@Override
	public ChunkGenerator withSeed(long seed)
	{
		return new OTGNoiseChunkGenerator(this.biomeSource.withSeed(seed), seed, this.structureSets, this.noises, this.generatorSettings);
	}*/

    @Override
    protected Codec<? extends ChunkGenerator> codec() {
        return CODEC;
    }

    // TODO: Comment out if not needed?
    public Climate.Sampler climateSampler() {
        return this.sampler;
    }

    @Override
    public int getGenDepth() {
        return this.noiseHeight;
    }

    @Override
    public int getSeaLevel() {
        return this.generatorSettings.value().seaLevel();
    }

    public Preset getPreset() {
        return preset;
    }

    @Override
    public int getMinY() {
        return generatorSettings.value().noiseSettings().minY();
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
