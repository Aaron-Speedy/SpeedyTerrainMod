package com.pg85.otg.paper.gen;

import com.google.gson.JsonSyntaxException;
import com.pg85.otg.constants.Constants;
import com.pg85.otg.core.OTG;
import com.pg85.otg.interfaces.*;
import com.pg85.otg.paper.materials.PaperMaterialData;
import com.pg85.otg.paper.util.JsonToNBT;
import com.pg85.otg.paper.util.PaperNBTHelper;
import com.pg85.otg.util.biome.ReplaceBlockMatrix;
import com.pg85.otg.util.ChunkCoordinate;
import com.pg85.otg.util.FifoMap;
import com.pg85.otg.util.gen.LocalWorldGenRegion;
import com.pg85.otg.util.helpers.PerfHelper;
import com.pg85.otg.util.logging.LogCategory;
import com.pg85.otg.util.logging.LogLevel;
import com.pg85.otg.util.materials.LocalMaterialData;
import com.pg85.otg.util.materials.LocalMaterials;
import com.pg85.otg.util.minecraft.TreeType;
import com.pg85.otg.util.nbt.NamedBinaryTag;

import net.kyori.adventure.text.Component;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.data.worldgen.features.TreeFeatures;
import net.minecraft.data.worldgen.placement.CavePlacements;
import net.minecraft.data.worldgen.placement.EndPlacements;
import net.minecraft.data.worldgen.placement.TreePlacements;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.util.ProblemReporter.Collector;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.levelgen.feature.configurations.FeatureConfiguration;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.WorldGenLevel;

import org.bukkit.Bukkit;
import org.bukkit.craftbukkit.CraftServer;
import org.bukkit.craftbukkit.util.RandomSourceWrapper;
import org.bukkit.event.entity.CreatureSpawnEvent;

import java.text.MessageFormat;
import java.util.Optional;
import java.util.Random;

// TODO: Split up worldgenregion into separate classes, one for decoration/worldgen, one for non-worldgen.
public class PaperWorldGenRegion extends LocalWorldGenRegion {
    protected final WorldGenLevel worldGenRegion;
    private final OTGNoiseChunkGenerator chunkGenerator;
    private static final RegistryAccess registryAccess = ((CraftServer) Bukkit.getServer()).getServer().registryAccess();
    private static final Registry<PlacedFeature> placedRegistry = registryAccess.lookupOrThrow(Registries.PLACED_FEATURE);
    private static final Registry<ConfiguredFeature<?, ?>> configuredFeatureRegistry = registryAccess.lookupOrThrow(Registries.CONFIGURED_FEATURE);

    // BO4 plotting may call hasDefaultStructures on chunks outside the area being decorated, in order to plot large structures.
    // It may query the same chunk multiple times, so use a fixed size cache.
    private final FifoMap<ChunkCoordinate, Boolean> cachedHasDefaultStructureChunks = new FifoMap<>(2048);

    // For accessing protected methods
    // Make sure there are no fields added

    private static class MobAccess extends Mob {
        public MobAccess(EntityType<? extends Mob> arg0, Level arg1) {
            super(arg0, arg1);
        }

        @Override
        public void readAdditionalSaveData(ValueInput arg0) {
            super.readAdditionalSaveData(arg0);
        }
    }

    /**
     * Creates a LocalWorldGenRegion to be used during decoration for OTG worlds.
     */
    public PaperWorldGenRegion(String presetFolderName, IWorldConfig worldConfig, WorldGenRegion worldGenRegion, OTGNoiseChunkGenerator chunkGenerator) {
        super(presetFolderName, OTG.getEngine().getPluginConfig(), worldConfig, OTG.getEngine().getLogger(), worldGenRegion.getCenter().x, worldGenRegion.getCenter().z, chunkGenerator.getCachedBiomeProvider());
        this.worldGenRegion = worldGenRegion;
        this.chunkGenerator = chunkGenerator;
    }

    /**
     * Creates a LocalWorldGenRegion to be used for OTG worlds outside of decoration, only used for /otg spawn/edit/export.
     */
    public PaperWorldGenRegion(String presetFolderName, IWorldConfig worldConfig, WorldGenLevel worldGenRegion, OTGNoiseChunkGenerator chunkGenerator) {
        super(presetFolderName, OTG.getEngine().getPluginConfig(), worldConfig, OTG.getEngine().getLogger());
        this.worldGenRegion = worldGenRegion;
        this.chunkGenerator = chunkGenerator;
    }

    /**
     * Creates a LocalWorldGenRegion to be used for non-OTG worlds outside of decoration, only used for /otg spawn/edit/export.
     */
    public PaperWorldGenRegion(String presetFolderName, IWorldConfig worldConfig, WorldGenLevel worldGenRegion) {
        super(presetFolderName, OTG.getEngine().getPluginConfig(), worldConfig, OTG.getEngine().getLogger());
        this.worldGenRegion = worldGenRegion;
        this.chunkGenerator = null;
    }

    @Override
    public ILogger getLogger() {
        return OTG.getEngine().getLogger();
    }

    @Override
    public long getSeed() {
        return this.worldGenRegion.getSeed();
    }

    public RandomSource getWorldRandom() {
        return this.worldGenRegion.getRandom();
    }

    @Override
    public ICachedBiomeProvider getCachedBiomeProvider() {
        return this.chunkGenerator.getCachedBiomeProvider();
    }

    @Override
    public ChunkCoordinate getSpawnChunk() {
        if (this.getWorldConfig().getSpawnPointSet()) {
            return ChunkCoordinate.fromBlockCoords(this.getWorldConfig().getSpawnPointX(), this.getWorldConfig().getSpawnPointZ());
        } else {
            BlockPos spawnPos = this.worldGenRegion.getMinecraftWorld().getSharedSpawnPos();
            return ChunkCoordinate.fromBlockCoords(spawnPos.getX(), spawnPos.getZ());
        }
    }

    public LevelAccessor getInternal() {
        return this.worldGenRegion;
    }

    @Override
    public IBiome getBiomeForDecoration(int x, int z) {
        // TOOD: Don't use this.decorationArea == null for worldgenregions
        // doing things outside of population, split up worldgenregion
        // into separate classes, one for decoration, one for non-decoration.
        return this.decorationArea != null ? this.decorationBiomeCache.getBiome(x, z) : this.getCachedBiomeProvider().getBiome(x, z);
    }

    @Override
    public IBiomeConfig getBiomeConfigForDecoration(int x, int z) {
        // TOOD: Don't use this.decorationArea == null for worldgenregions
        // doing things outside of population, split up worldgenregion
        // into separate classes, one for decoration, one for non-decoration.
        return this.decorationArea != null ? this.decorationBiomeCache.getBiomeConfig(x, z) : this.getCachedBiomeProvider().getBiomeConfig(x, z);
    }

    @Override
    public double getBiomeBlocksNoiseValue(int xInWorld, int zInWorld) {
        return this.chunkGenerator.getBiomeBlocksNoiseValue(xInWorld, zInWorld);
    }

    // TODO: Only used by resources using 3x3 decoration atm (so icebergs). Align all resources
    // to use 3x3, make them use the decoration cache and remove this method.
    @Override
    public LocalMaterialData getMaterialDirect(int x, int y, int z) {
        return PaperMaterialData.ofBlockData(this.worldGenRegion.getBlockState(new BlockPos(x, y, z)));
    }

    @Override
    public LocalMaterialData getMaterial(int x, int y, int z) {
        if (PerfHelper.isYOutOfWorldBounds(y)) {
            return null;
        }
        return getMaterialDirect(x, y, z);
    }

    @Override
    public int getBlockAboveLiquidHeight(int x, int z) {
        int highestY = getHighestBlockYAt(x, z, false, true, false, false, false);
        if (highestY >= 0) {
            return highestY + 1;
        } else {
            return -1;
        }
    }

    @Override
    public int getBlockAboveSolidHeight(int x, int z) {
        int highestY = getHighestBlockYAt(x, z, true, false, true, true, false);
        if (highestY >= 0) {
            return highestY + 1;
        } else {
            return -1;
        }
    }

    @Override
    public int getHighestBlockAboveYAt(int x, int z) {
        int highestY = getHighestBlockYAt(x, z, true, true, false, false, false);
        if (highestY >= 0) {
            return highestY + 1;
        } else {
            return -1;
        }
    }

    @Override
    public int getHighestBlockAboveYAt(int x, int z, boolean findSolid, boolean findLiquid, boolean ignoreLiquid, boolean ignoreSnow, boolean ignoreLeaves) {
        int highestY = getHighestBlockYAt(x, z, findSolid, findLiquid, ignoreLiquid, ignoreSnow, ignoreLeaves);
        if (highestY >= 0) {
            return highestY + 1;
        } else {
            return -1;
        }
    }

    @Override
    public int getHighestBlockYAt(int x, int z, boolean findSolid, boolean findLiquid, boolean ignoreLiquid, boolean ignoreSnow, boolean ignoreLeaves) {
        ChunkCoordinate chunkCoord = ChunkCoordinate.fromBlockCoords(x, z);

        // If the chunk exists or is inside the area being decorated, fetch it normally.
        ChunkAccess chunk = null;
        // TOOD: Don't use this.decorationArea == null for worldgenregions
        // doing things outside of population, split up worldgenregion
        // into separate classes, one for decoration, one for non-decoration.
        if (this.decorationArea == null || this.decorationArea.isInAreaBeingDecorated(x, z)) {
            chunk = this.worldGenRegion.getChunk(chunkCoord.getChunkX(), chunkCoord.getChunkZ(), ChunkStatus.EMPTY, false);
        } else {
            return -1;
        }

        // Get internal coordinates for block in chunk
        int internalX = x & 0xF;
        int internalZ = z & 0xF;
        int heightMapY = chunk.getHeight(Heightmap.Types.WORLD_SURFACE, internalX, internalZ);
        return getHighestBlockYAt(chunk, internalX, heightMapY, internalZ, findSolid, findLiquid, ignoreLiquid, ignoreSnow, ignoreLeaves);
    }

    protected int getHighestBlockYAt(ChunkAccess chunk, int internalX, int heightMapY, int internalZ, boolean findSolid, boolean findLiquid, boolean ignoreLiquid, boolean ignoreSnow, boolean ignoreLeaves) {
        LocalMaterialData material;
        boolean isSolid;
        boolean isLiquid;
        BlockState blockState;
        Block block;

        for (int i = heightMapY; i >= Constants.WORLD_DEPTH; i--) {
            // TODO: mutable
            blockState = chunk.getBlockState(new BlockPos(internalX, i, internalZ));
            block = blockState.getBlock();
            material = PaperMaterialData.ofBlockData(blockState);
            isLiquid = material.isLiquid();
            isSolid =
                    (
                            (
                                    material.isSolid() &&
                                            (
                                                    !ignoreLeaves ||
                                                            (
                                                                    block != Blocks.ACACIA_LOG &&
                                                                            block != Blocks.BIRCH_LOG &&
                                                                            block != Blocks.DARK_OAK_LOG &&
                                                                            block != Blocks.JUNGLE_LOG &&
                                                                            block != Blocks.OAK_LOG &&
                                                                            block != Blocks.SPRUCE_LOG &&
                                                                            block != Blocks.STRIPPED_ACACIA_LOG &&
                                                                            block != Blocks.STRIPPED_BIRCH_LOG &&
                                                                            block != Blocks.STRIPPED_DARK_OAK_LOG &&
                                                                            block != Blocks.STRIPPED_JUNGLE_LOG &&
                                                                            block != Blocks.STRIPPED_OAK_LOG &&
                                                                            block != Blocks.STRIPPED_SPRUCE_LOG
                                                            )
                                            )
                            )
                                    ||
                                    (
                                            !ignoreLeaves &&
                                                    (
                                                            block == Blocks.ACACIA_LEAVES ||
                                                                    block == Blocks.BIRCH_LEAVES ||
                                                                    block == Blocks.DARK_OAK_LEAVES ||
                                                                    block == Blocks.JUNGLE_LEAVES ||
                                                                    block == Blocks.OAK_LEAVES ||
                                                                    block == Blocks.SPRUCE_LEAVES
                                                    )
                                    ) || (
                                    !ignoreSnow &&
                                            block == Blocks.SNOW
                            )
                    );
            if (!(ignoreLiquid && isLiquid)) {
                if ((findSolid && isSolid) || (findLiquid && isLiquid)) {
                    return i;
                }
                if ((findSolid && isLiquid) || (findLiquid && isSolid)) {
                    return Constants.WORLD_DEPTH - 1;
                }
            }
        }

        // Can happen if this is a chunk filled with air
        return Constants.WORLD_DEPTH - 1;
    }

    @Override
    public int getHeightMapHeight(int x, int z) {
        return this.worldGenRegion.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x, z);
    }

    @Override
    public int getLightLevel(int x, int y, int z) {
        if (PerfHelper.isYOutOfWorldBounds(y)) {
            return -1;
        }

        // Check if the chunk has been lit, otherwise cancel.
        // TODO: Check if this causes problems with BO3 LightChecks.
        // TODO: Make a getLight method based on world.getLight that uses unloaded chunks.
        ChunkCoordinate chunkCoord = ChunkCoordinate.fromBlockCoords(x, z);
        ChunkAccess chunk = this.worldGenRegion.getChunk(chunkCoord.getChunkX(), chunkCoord.getChunkZ(), ChunkStatus.EMPTY, false);
        if (chunk != null && chunk.getPersistedStatus().isOrAfter(ChunkStatus.LIGHT)) {
            // This fetches the block and skylight as if it were day.
            return this.worldGenRegion.getLightEmission(new BlockPos(x, y, z));
        }
        return -1;
    }

    // TODO: Only used by resources using 3x3 decoration atm (so icebergs). Align all resources
    // to use 3x3, make them use the decoration cache and remove this method.
    @Override
    public void setBlockDirect(int x, int y, int z, LocalMaterialData material) {
        IBiomeConfig biomeConfig = this.getCachedBiomeProvider().getBiomeConfig(x, z, true);
        if (biomeConfig.getReplaceBlocks() != null) {
            material = material.parseWithBiomeAndHeight(this.getWorldConfig().getBiomeConfigsHaveReplacement(), biomeConfig.getReplaceBlocks(), y);
        }
        this.worldGenRegion.setBlock(new BlockPos(x, y, z), ((PaperMaterialData) material).internalBlock(), 3);
    }

    @Override
    public void setBlock(int x, int y, int z, LocalMaterialData material) {
        setBlock(x, y, z, material, null, null);
    }

    @Override
    public void setBlock(int x, int y, int z, LocalMaterialData material, NamedBinaryTag nbt) {
        setBlock(x, y, z, material, nbt, null);
    }

    @Override
    public void setBlock(int x, int y, int z, LocalMaterialData material, ReplaceBlockMatrix replaceBlocksMatrix) {
        setBlock(x, y, z, material, null, replaceBlocksMatrix);
    }

    @Override
    public void setBlock(int x, int y, int z, LocalMaterialData material, NamedBinaryTag nbt, ReplaceBlockMatrix replaceBlocksMatrix) {
        if (PerfHelper.isYOutOfWorldBounds(y)) {
            return;
        }

        if (material.isEmpty()) {
            // Happens when configs contain blocks that don't exist.
            // TODO: Catch this earlier up the chain, avoid doing work?
            return;
        }

        // If no decorationArea is present, we're doing something outside of the decoration cycle.
        // If a decorationArea exists, only spawn in the area being decorated.
        // TOOD: Don't use this.decorationArea == null for worldgenregions
        // doing things outside of population, split up worldgenregion
        // into separate classes, one for decoration, one for non-decoration.
        if (this.decorationArea == null || this.decorationArea.isInAreaBeingDecorated(x, z)) {
            if (replaceBlocksMatrix != null) {
                material = material.parseWithBiomeAndHeight(this.getWorldConfig().getBiomeConfigsHaveReplacement(), replaceBlocksMatrix, y);
            }

            BlockPos pos = new BlockPos(x, y, z);
            // Notify world: (2 | 16) == update client, don't update observers
            this.worldGenRegion.setBlock(pos, ((PaperMaterialData) material).internalBlock(), 2 | 16);

            if (material.isLiquid()) {
                this.worldGenRegion.scheduleTick(pos, ((PaperMaterialData) material).internalBlock().getFluidState().getType(), 0);
            } else if (material.isMaterial(LocalMaterials.COMMAND_BLOCK)) {
                this.worldGenRegion.scheduleTick(pos, ((PaperMaterialData) material).internalBlock().getBlock(), 0);
            }

            if (nbt != null) {
                this.attachNBT(x, y, z, nbt);
            }
        }
    }

    protected void attachNBT(int x, int y, int z, NamedBinaryTag nbt) {
        CompoundTag nms = PaperNBTHelper.getNMSFromNBTTagCompound(nbt);
        nms.put("x", IntTag.valueOf(x));
        nms.put("y", IntTag.valueOf(y));
        nms.put("z", IntTag.valueOf(z));

        BlockEntity tileEntity = this.worldGenRegion.getBlockEntity(new BlockPos(x, y, z));
        if (tileEntity != null) {
            try {
                // TODO: Check that this doesn't break anything
                // tileEntity.load(state, nms);
                tileEntity.loadWithComponents(TagValueInput.create(new Collector(), registryAccess, nms));
            } catch (JsonSyntaxException e) {
                if (this.logger.getLogCategoryEnabled(LogCategory.CUSTOM_OBJECTS)) {
                    this.logger.log(
                            LogLevel.ERROR,
                            LogCategory.CUSTOM_OBJECTS,
                            MessageFormat.format(
                                    "Badly formatted json for tile entity with id '{0}' at {1},{2},{3}",
                                    nms.getString("id"),
                                    x, y, z
                            )
                    );
                }
            }
        } else {
            if (this.logger.getLogCategoryEnabled(LogCategory.CUSTOM_OBJECTS)) {
                this.logger.log(
                        LogLevel.ERROR,
                        LogCategory.CUSTOM_OBJECTS,
                        MessageFormat.format(
                                "Skipping tile entity with id {0}, cannot be placed at {1},{2},{3}",
                                nms.getString("id"),
                                x, y, z
                        )
                );
            }
        }
    }

    public BlockEntity getTileEntity(BlockPos blockPos) {
        return worldGenRegion.getBlockEntity(blockPos);
    }

    @Override
    public boolean placeTree(TreeType type, Random rd, int x, int y, int z) {
        RandomSource rand = new RandomSourceWrapper(rd);
        if (PerfHelper.isYOutOfWorldBounds(y)) {
            return false;
        }

        // See explanation in ForgeWorldGenRegion::placeTree
        if (y != this.getHighestBlockAboveYAt(x, z, true, false, false, true, true)) {
            return true;
        }

        BlockPos blockPos = new BlockPos(x, y, z);
        try {
            // Features -> BiomeDecoratorGroups
            // ConfiguredFeature.feature -> WorldGenFeatureConfigured.e
            // ConfiguredFeature.config -> WorldGenFeatureConfigured.f
            PlacedFeature tree = null;
            ConfiguredFeature<?, ?> other = null;
            switch (type) {
                case Acacia:
                    tree = placedRegistry.getValue(TreePlacements.ACACIA_CHECKED);
                    break;
                case BigTree:
                    tree = placedRegistry.getValue(TreePlacements.FANCY_OAK_CHECKED);
                    break;
                case Forest:
                case Birch:
                    tree = placedRegistry.getValue(TreePlacements.BIRCH_CHECKED);
                    break;
                case JungleTree:
                    tree = placedRegistry.getValue(TreePlacements.MEGA_JUNGLE_TREE_CHECKED);
                    break;
                case CocoaTree:
                    tree = placedRegistry.getValue(TreePlacements.JUNGLE_TREE_CHECKED);
                    break;
                case DarkOak:
                    tree = placedRegistry.getValue(TreePlacements.DARK_OAK_CHECKED);
                    break;
                case GroundBush:
                    tree = placedRegistry.getValue(TreePlacements.JUNGLE_BUSH);
                    break;
                case HugeMushroom:
                    if (rand.nextBoolean()) {
                        other = configuredFeatureRegistry.getValue(TreeFeatures.HUGE_BROWN_MUSHROOM);
                    } else {
                        other = configuredFeatureRegistry.getValue(TreeFeatures.HUGE_RED_MUSHROOM);
                    }
                    break;
                case HugeRedMushroom:
                    other = configuredFeatureRegistry.getValue(TreeFeatures.HUGE_RED_MUSHROOM);
                    break;
                case HugeBrownMushroom:
                    other = configuredFeatureRegistry.getValue(TreeFeatures.HUGE_BROWN_MUSHROOM);
                    break;
                case SwampTree:
                    other = configuredFeatureRegistry.getValue(TreeFeatures.SWAMP_OAK);
                    break;
                case Taiga1:
                    tree = placedRegistry.getValue(TreePlacements.PINE_CHECKED);
                    break;
                case Taiga2:
                    tree = placedRegistry.getValue(TreePlacements.SPRUCE_CHECKED);
                    break;
                case HugeTaiga1:
                    tree = placedRegistry.getValue(TreePlacements.MEGA_PINE_CHECKED);
                    break;
                case HugeTaiga2:
                    tree = placedRegistry.getValue(TreePlacements.MEGA_SPRUCE_CHECKED);
                    break;
                case TallBirch:
                    tree = placedRegistry.getValue(TreePlacements.SUPER_BIRCH_BEES_0002);
                    break;
                case Tree:
                    tree = placedRegistry.getValue(TreePlacements.OAK_CHECKED);
                    break;
                case CrimsonFungi:
                    tree = placedRegistry.getValue(TreePlacements.CRIMSON_FUNGI);
                    break;
                case WarpedFungi:
                    tree = placedRegistry.getValue(TreePlacements.WARPED_FUNGI);
                    break;
                case ChorusPlant:
                    tree = placedRegistry.getValue(EndPlacements.CHORUS_PLANT);
                    break;
                case Azalea:
                    other = configuredFeatureRegistry.getValue(TreeFeatures.AZALEA_TREE);
                    break;
                default:
                    throw new RuntimeException("Failed to handle tree of type " + type);
            }
            if (tree != null) tree.place(this.worldGenRegion, this.chunkGenerator, rand, blockPos);
            else other.place(this.worldGenRegion, this.chunkGenerator, rand, blockPos);
            return true;
        } catch (NullPointerException ex) {
            if (this.logger.getLogCategoryEnabled(LogCategory.DECORATION)) {
                this.logger.log(LogLevel.ERROR, LogCategory.DECORATION, "Treegen caused an error: ");
                this.logger.printStackTrace(LogLevel.ERROR, LogCategory.DECORATION, ex);
            }
            // Return true to prevent further attempts.
            return true;
        }
    }

    @Override
    public void spawnEntity(IEntityFunction entityData) {
        if (PerfHelper.isYOutOfWorldBounds(entityData.getY())) {
            if (this.logger.getLogCategoryEnabled(LogCategory.CUSTOM_OBJECTS)) {
                this.logger.log(LogLevel.ERROR, LogCategory.CUSTOM_OBJECTS, "Failed to spawn mob for Entity() " + entityData.makeString() + ", y position out of bounds");
            }
            return;
        }

        // Fetch entity type for Entity() mob name
        Optional<EntityType<?>> optionalType = EntityType.byString(entityData.getResourceLocation());
        EntityType<?> type;
        if (optionalType.isPresent()) {
            type = optionalType.get();
        } else {
            if (this.logger.getLogCategoryEnabled(LogCategory.CUSTOM_OBJECTS)) {
                this.logger.log(LogLevel.ERROR, LogCategory.CUSTOM_OBJECTS, "Could not parse mob for Entity() " + entityData.makeString() + ", mob type could not be found.");
            }
            return;
        }

        // Check for any .txt or .nbt file containing nbt data for the entity
        CompoundTag compoundTag = null;
        if (
                entityData.getNameTagOrNBTFileName() != null &&
                        (
                                entityData.getNameTagOrNBTFileName().toLowerCase().trim().endsWith(".txt")
                                        || entityData.getNameTagOrNBTFileName().toLowerCase().trim().endsWith(".nbt")
                        )
        ) {
            compoundTag = new CompoundTag();
            if (entityData.getNameTagOrNBTFileName().toLowerCase().trim().endsWith(".txt")) {

                compoundTag = JsonToNBT.getTagFromJson(entityData.getMetaData());
                if (compoundTag == null) {
                    if (this.logger.getLogCategoryEnabled(LogCategory.CUSTOM_OBJECTS)) {
                        this.logger.log(LogLevel.ERROR, LogCategory.CUSTOM_OBJECTS, "Could not parse nbt for Entity() " + entityData.makeString() + ", file: " + entityData.getNameTagOrNBTFileName());
                    }
                    return;
                }
                // Specify which type of entity to spawn
                compoundTag.putString("id", entityData.getResourceLocation());
            } else if (entityData.getNBTTag() != null) {
                compoundTag = PaperNBTHelper.getNMSFromNBTTagCompound(entityData.getNBTTag());
            }
        }

        // Create and spawn entities according to group size
        for (int r = 0; r < entityData.getGroupSize(); r++) {
            Entity entity = type.create(this.worldGenRegion.getMinecraftWorld(), EntitySpawnReason.NATURAL);
            if (entity == null) {
                if (this.logger.getLogCategoryEnabled(LogCategory.CUSTOM_OBJECTS)) {
                    this.logger.log(LogLevel.ERROR, LogCategory.CUSTOM_OBJECTS, "Failed to make basic entity for " + entityData.makeString());
                }
                return;
            }
            if (compoundTag != null) {
                entity.load(TagValueInput.create(new Collector(), registryAccess, compoundTag));
            }
            entity.setRot(this.getWorldRandom().nextFloat() * 360.0F, 0.0F);
            entity.setPos(entityData.getX(), entityData.getY(), entityData.getZ());

            // Attach nametag if one was provided via Entity()
            String nameTag = entityData.getNameTagOrNBTFileName();
            if (nameTag != null && !nameTag.toLowerCase().trim().endsWith(".txt") && !nameTag.toLowerCase().trim().endsWith(".nbt")) {
                // I think this will work? It's using some weird classes tho.
                Component customName = net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacySection().deserializeOrNull(nameTag);
                entity.setCustomName(customName != null ? io.papermc.paper.adventure.PaperAdventure.asVanilla(customName) : null);
            }

            // This is a replacement for commented out code below.
            // TODO: Does this work?
            if (!SpawnPlacements.isSpawnPositionOk(entity.getType(), this.worldGenRegion, entity.blockPosition())) {
                continue;
            }

            // TODO: Non-mob entities, aren't those handled via Block(nbt), chests, armor stands etc?
            // if (entity instanceof LivingEntity) {
            //     // If the block is a solid block or entity is a fish out of water, cancel
            //     // TODO: Does this only need to check for fish or does it also need to check for other water mobs?
            //     LocalMaterialData block = PaperMaterialData.ofBlockData(this.worldGenRegion.getBlockState(new BlockPos(entityData.getX(), entityData.getY(), entityData.getZ())));
            //     if (block.isSolid() || ((entity.getCategory() == MobCategory.WATER_AMBIENT) && !block.isLiquid())) {
            //         if (this.logger.getLogCategoryEnabled(LogCategory.CUSTOM_OBJECTS)) {
            //             this.logger.log(LogLevel.ERROR, LogCategory.CUSTOM_OBJECTS, "Could not spawn entity at " + entityData.getX() + " " + entityData.getY() + " " + entityData.getZ() + " for Entity() " + entityData.makeString() + ", a solid block was found or a water mob tried to spawn outside of water.");
            //         }
            //         continue;
            //     }

            // }

            if (entity instanceof MobAccess mobEntity) {
                // Make sure Entity() mobs don't de-spawn, regardless of nbt data
                mobEntity.setPersistenceRequired();
                // mobEntity.readAdditionalSaveData(compoundTag);
                mobEntity.readAdditionalSaveData(TagValueInput.create(new Collector(), registryAccess, compoundTag));
                mobEntity.finalizeSpawn(this.worldGenRegion, this.worldGenRegion.getCurrentDifficultyAt(new BlockPos((int) entityData.getX(), entityData.getY(), (int) entityData.getZ())), EntitySpawnReason.CHUNK_GENERATION, null);
            }
            this.worldGenRegion.addFreshEntity(entity, CreatureSpawnEvent.SpawnReason.DEFAULT);
        }
    }

    @Override
    public void placeDungeon(Random rm, int x, int y, int z) {
        RandomSource random = new RandomSourceWrapper(rm);
        Feature.MONSTER_ROOM.place(FeatureConfiguration.NONE, this.worldGenRegion, this.chunkGenerator, random, new BlockPos(x, y, z));
    }

    @Override
    public void placeFossil(Random rm, int x, int y, int z) {
        RandomSource random = new RandomSourceWrapper(rm);
        if (y >= 0) {
            placedRegistry.getValue(CavePlacements.FOSSIL_UPPER).place(this.worldGenRegion, this.chunkGenerator, random, new BlockPos(x, y, z));
        } else {
            placedRegistry.getValue(CavePlacements.FOSSIL_LOWER).place(this.worldGenRegion, this.chunkGenerator, random, new BlockPos(x, y, z));
        }
    }

    @Override
    public boolean isInsideWorldBorder(ChunkCoordinate chunkCoordinate) {
        // TODO: Implement this.
        return true;
    }

    // Edit command
    // TODO: We already have getMaterial/setBlock, rename/refactor these
    // so it's clear they are/should be used only in a specific context.

    public BlockState getBlockData(BlockPos blockpos) {
        return this.worldGenRegion.getBlockState(blockpos);
    }

    public void setBlockState(BlockPos blockpos, BlockState blockstate1, int i) {
        this.worldGenRegion.setBlock(blockpos, blockstate1, i);
    }

    // Shadowgen

    @Override
    public LocalMaterialData getMaterialWithoutLoading(int x, int y, int z) {
        if (PerfHelper.isYOutOfWorldBounds(y)) {
            return null;
        }

        ChunkPos pos = ChunkPos.minFromRegion(x, z);
        ChunkAccess chunk = null;

        // TOOD: Don't use this.decorationArea == null for worldgenregions
        // doing things outside of population, split up worldgenregion
        // into separate classes, one for decoration, one for non-decoration.
        if (this.decorationArea != null && this.decorationArea.isInAreaBeingDecorated(x, z)) {
            chunk = this.worldGenRegion.hasChunk(pos.x, pos.z) ? this.worldGenRegion.getChunk(pos.x, pos.z, ChunkStatus.CARVERS, false) : null;
        }

        if (chunk == null) {
            // Edited because RandomSource issue
            return this.chunkGenerator.getMaterialInUnloadedChunk(new RandomSourceWrapper.RandomWrapper(this.getWorldRandom()), x, y, z, this.worldGenRegion.getLevel());
        }

        // Get internal coordinates for block in chunk
        int internalX = x & 0xF;
        int internalZ = z & 0xF;
        return PaperMaterialData.ofBlockData(chunk.getBlockState(internalX, y, internalZ));
    }

    @Override
    public int getHighestBlockYAtWithoutLoading(int x, int z, boolean findSolid, boolean findLiquid, boolean ignoreLiquid, boolean ignoreSnow, boolean ignoreLeaves) {
        ChunkAccess chunk = null;
        ChunkPos pos = ChunkPos.minFromRegion(x, z);
        // If the chunk exists or is inside the area being decorated, fetch it normally.
        // TOOD: Don't use this.decorationArea == null for worldgenregions
        // doing things outside of population, split up worldgenregion
        // into separate classes, one for decoration, one for non-decoration.
        if (this.decorationArea != null && this.decorationArea.isInAreaBeingDecorated(x, z)) {
            chunk = this.worldGenRegion.hasChunk(pos.x, pos.z) ? this.worldGenRegion.getChunk(pos.x, pos.z, ChunkStatus.CARVERS, false) : null;
        }

        // If the chunk doesn't exist and we're doing something outside the
        // decoration sequence, return the material without loading the chunk.
        if (chunk == null || !chunk.getPersistedStatus().isOrAfter(ChunkStatus.CARVERS)) {
            Random r = new RandomSourceWrapper.RandomWrapper(this.getWorldRandom());
            return this.chunkGenerator.getHighestBlockYInUnloadedChunk(r, x, z, findSolid, findLiquid, ignoreLiquid, ignoreSnow, this.worldGenRegion.getLevel());
        }

        // Get internal coordinates for block in chunk
        int internalX = x & 0xF;
        int internalZ = z & 0xF;
        int heightMapY = chunk.getHeight(Heightmap.Types.WORLD_SURFACE_WG, internalX, internalZ);
        return getHighestBlockYAt(chunk, internalX, heightMapY, internalZ, findSolid, findLiquid, ignoreLiquid, ignoreSnow, ignoreLeaves);
    }

    @Override
    public boolean chunkHasDefaultStructure(Random worldRandom, ChunkCoordinate chunkCoordinate) {
        Boolean hasDefaultStructure = cachedHasDefaultStructureChunks.get(chunkCoordinate);
        if (hasDefaultStructure != null) {
            return hasDefaultStructure;
        }
        //hasDefaultStructure = this.chunkGenerator.hasFeatureChunkInRange(BuiltinStructureSets.VILLAGES, getSeed(), chunkCoordinate.getChunkX(), chunkCoordinate.getChunkZ(), 4);
        hasDefaultStructure = this.chunkGenerator.checkForVanillaStructure(chunkCoordinate);
        //hasDefaultStructure = this.chunkGenerator.checkHasVanillaStructureWithoutLoading(this.worldGenRegion.getMinecraftWorld(), chunkCoordinate);
        cachedHasDefaultStructureChunks.put(chunkCoordinate, hasDefaultStructure);
        return hasDefaultStructure;
    }
}
