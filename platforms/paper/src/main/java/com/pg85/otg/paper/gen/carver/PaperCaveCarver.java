package com.pg85.otg.paper.gen.carver;

import com.pg85.otg.paper.materials.PaperMaterialData;
import com.pg85.otg.util.gen.carver.LocalCaveCarver;
import com.pg85.otg.util.materials.LocalMaterialData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.SectionPos;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.util.valueproviders.FloatProvider;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.chunk.CarvingMask;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.Aquifer;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.carver.CarverConfiguration;
import net.minecraft.world.level.levelgen.carver.CaveCarverConfiguration;
import net.minecraft.world.level.levelgen.carver.WorldCarver;
import org.apache.commons.lang3.mutable.MutableBoolean;
import org.bukkit.Material;

import javax.annotation.Nullable;
import java.util.function.Function;

public class PaperCaveCarver extends LocalCaveCarver {

    public PaperCaveCarver() {
        super(PaperMaterialData.ofSpigotMaterial(Material.AIR),
                PaperMaterialData.ofSpigotMaterial(Material.CAVE_AIR),
                PaperMaterialData.ofSpigotMaterial(Material.WATER),
                PaperMaterialData.ofSpigotMaterial(Material.LAVA));
    }

    public boolean carveCaves(OTGCarvingContext context, CaveCarverConfiguration config, ChunkAccess chunk, Function<BlockPos, Holder<Biome>> biomeAccessor, RandomSource random, Aquifer aquifer, ChunkPos chunkPos, CarvingMask carvingMask, FloatProvider floorLevel) {
        int blockPosCoord = SectionPos.sectionToBlockCoord(7);
        int randomInt = random.nextInt(random.nextInt(random.nextInt(this.getCaveBound()) + 1) + 1);

        for(int i = 0; i < randomInt; ++i) {
            double d = (double)chunkPos.getBlockX(random.nextInt(16));
            double d1 = (double)config.y.sample(random, context);
            double d2 = (double)chunkPos.getBlockZ(random.nextInt(16));
            double d3 = (double)config.horizontalRadiusMultiplier.sample(random);
            double d4 = (double)config.verticalRadiusMultiplier.sample(random);
            double d5 = floorLevel.sample(random);  // Using constant value since we can't access the private field
            WorldCarver.CarveSkipChecker carveSkipChecker = (skipContext, relativeX, relativeY, relativeZ, y) -> shouldSkip(relativeX, relativeY, relativeZ, d5);
            int i1 = 1;
            if (random.nextInt(4) == 0) {
                double d6 = (double)config.yScale.sample(random);
                float f = 1.0F + random.nextFloat() * 6.0F;
                this.createRoom(context, config, chunk, biomeAccessor, aquifer, d, d1, d2, f, d6, carvingMask, carveSkipChecker);
                i1 += random.nextInt(4);
            }

            for(int i2 = 0; i2 < i1; ++i2) {
                float f1 = random.nextFloat() * ((float)Math.PI * 2F);
                float f = (random.nextFloat() - 0.5F) / 4.0F;
                float thickness = this.getThickness(random);
                int i3 = blockPosCoord - random.nextInt(blockPosCoord / 4);
                int i4 = 0;
                this.createTunnel(context, config, chunk, biomeAccessor, random.nextLong(), aquifer, d, d1, d2, d3, d4, thickness, f1, f, 0, i3, this.getYScale(), carvingMask, carveSkipChecker);
            }
        }

        return true;
    }

    protected int getCaveBound() {
        return 15;
    }

    protected float getThickness(RandomSource random) {
        float f = random.nextFloat() * 2.0F + random.nextFloat();
        if (random.nextInt(10) == 0) {
            f *= random.nextFloat() * random.nextFloat() * 3.0F + 1.0F;
        }

        return f;
    }

    protected double getYScale() {
        return (double)1.0F;
    }

    protected static boolean canReach(ChunkPos chunkPos, double x, double z, int branchIndex, int branchCount, float width) {
        double d = (double)chunkPos.getMiddleBlockX();
        double d1 = (double)chunkPos.getMiddleBlockZ();
        double d2 = x - d;
        double d3 = z - d1;
        double d4 = (double)(branchCount - branchIndex);
        double d5 = (double)(width + 2.0F + 16.0F);
        return d2 * d2 + d3 * d3 - d4 * d4 <= d5 * d5;
    }

    protected boolean carveEllipsoid(OTGCarvingContext context, CaveCarverConfiguration config, ChunkAccess chunk, Function<BlockPos, Holder<Biome>> biomeAccessor, Aquifer aquifer, double x, double y, double z, double horizontalRadius, double verticalRadius, CarvingMask carvingMask, WorldCarver.CarveSkipChecker skipChecker) {
        ChunkPos pos = chunk.getPos();
        double d = (double)pos.getMiddleBlockX();
        double d1 = (double)pos.getMiddleBlockZ();
        double d2 = (double)16.0F + horizontalRadius * (double)2.0F;
        if (!(Math.abs(x - d) > d2) && !(Math.abs(z - d1) > d2)) {
            int minBlockX = pos.getMinBlockX();
            int minBlockZ = pos.getMinBlockZ();
            int max = Math.max(Mth.floor(x - horizontalRadius) - minBlockX - 1, 0);
            int min = Math.min(Mth.floor(x + horizontalRadius) - minBlockX, 15);
            int max1 = Math.max(Mth.floor(y - verticalRadius) - 1, context.getMinGenY() + 1);
            int i = chunk.isUpgrading() ? 0 : 7;
            int min1 = Math.min(Mth.floor(y + verticalRadius) + 1, context.getMinGenY() + context.getGenDepth() - 1 - i);
            int max2 = Math.max(Mth.floor(z - horizontalRadius) - minBlockZ - 1, 0);
            int min2 = Math.min(Mth.floor(z + horizontalRadius) - minBlockZ, 15);
            boolean flag = false;
            BlockPos.MutableBlockPos mutableBlockPos = new BlockPos.MutableBlockPos();
            BlockPos.MutableBlockPos mutableBlockPos1 = new BlockPos.MutableBlockPos();

            for(int i1 = max; i1 <= min; ++i1) {
                int blockX = pos.getBlockX(i1);
                double d3 = ((double)blockX + (double)0.5F - x) / horizontalRadius;

                for(int i2 = max2; i2 <= min2; ++i2) {
                    int blockZ = pos.getBlockZ(i2);
                    double d4 = ((double)blockZ + (double)0.5F - z) / horizontalRadius;
                    if (!(d3 * d3 + d4 * d4 >= (double)1.0F)) {
                        MutableBoolean mutableBoolean = new MutableBoolean(false);

                        for(int i3 = min1; i3 > max1; --i3) {
                            double d5 = ((double)i3 - (double)0.5F - y) / verticalRadius;
                            // This may cause runtime errors but I'm doing this quick-fix for now to see what happens
                            //if (!skipChecker.shouldSkip(context, d3, d5, d4, i3) && (!carvingMask.get(i1, i3, i2) || isDebugEnabled(config))) {
                                carvingMask.set(i1, i3, i2);
                                mutableBlockPos.set(blockX, i3, blockZ);
                                flag |= this.carveBlock(context, config, chunk, biomeAccessor, carvingMask, mutableBlockPos, mutableBlockPos1, aquifer, mutableBoolean);
                            //}
                        }
                    }
                }
            }

            return flag;
        } else {
            return false;
        }
    }

    protected boolean carveBlock(OTGCarvingContext context, CaveCarverConfiguration config, ChunkAccess chunk, Function<BlockPos, Holder<Biome>> biomeGetter, CarvingMask carvingMask, BlockPos.MutableBlockPos pos, BlockPos.MutableBlockPos checkPos, Aquifer aquifer, MutableBoolean reachedSurface) {
        BlockState blockState = chunk.getBlockState(pos);
        if (blockState.is(Blocks.GRASS_BLOCK) || blockState.is(Blocks.MYCELIUM)) {
            reachedSurface.setTrue();
        }

        if (!this.canReplaceBlock(config, blockState) && !isDebugEnabled(config)) {
            return false;
        } else {
            BlockState carveState = this.getCarveState(context, config, pos, aquifer);
            if (carveState == null) {
                return false;
            } else {
                chunk.setBlockState(pos, carveState);
                if (aquifer.shouldScheduleFluidUpdate() && !carveState.getFluidState().isEmpty()) {
                    chunk.markPosForPostprocessing(pos);
                }

                if (reachedSurface.isTrue()) {
                    checkPos.setWithOffset(pos, Direction.DOWN);
                    if (chunk.getBlockState(checkPos).is(Blocks.DIRT)) {
                        context.topMaterial(biomeGetter, chunk, checkPos, !carveState.getFluidState().isEmpty()).ifPresent((blockState1) -> {
                            chunk.setBlockState(checkPos, blockState1);
                            if (!blockState1.getFluidState().isEmpty()) {
                                chunk.markPosForPostprocessing(checkPos);
                            }

                        });
                    }
                }

                return true;
            }
        }
    }

    protected boolean canReplaceBlock(CaveCarverConfiguration config, BlockState state) {
        return state.is(config.replaceable);
    }

    private static boolean isDebugEnabled(CarverConfiguration config) {
        return config.debugSettings.isDebugMode();
    }

    @Nullable
    private BlockState getCarveState(OTGCarvingContext context, CaveCarverConfiguration config, BlockPos pos, Aquifer aquifer) {
        if (pos.getY() <= config.lavaLevel.resolveY(context)) {
            return ((PaperMaterialData)LAVA).internalBlock();
        } else {
            BlockState blockState = aquifer.computeSubstance(new DensityFunction.SinglePointContext(pos.getX(), pos.getY(), pos.getZ()), (double)0.0F);
            if (blockState == null) {
                return isDebugEnabled(config) ? config.debugSettings.getBarrierState() : null;
            } else {
                return isDebugEnabled(config) ? getDebugState(config, blockState) : blockState;
            }
        }
    }

    private static BlockState getDebugState(CarverConfiguration config, BlockState state) {
        if (state.is(Blocks.AIR)) {
            return config.debugSettings.getAirState();
        } else if (state.is(Blocks.WATER)) {
            BlockState waterState = config.debugSettings.getWaterState();
            return waterState.hasProperty(BlockStateProperties.WATERLOGGED) ? waterState.setValue(BlockStateProperties.WATERLOGGED, true) : waterState;
        } else {
            return state.is(Blocks.LAVA) ? config.debugSettings.getLavaState() : state;
        }
    }

    protected void createRoom(OTGCarvingContext context, CaveCarverConfiguration config, ChunkAccess chunk, Function<BlockPos, Holder<Biome>> biomeAccessor, Aquifer aquifer, double x, double y, double z, float radius, double horizontalVerticalRatio, CarvingMask carvingMask, WorldCarver.CarveSkipChecker skipChecker) {
        double d = (double)1.5F + (double)(Mth.sin(((float)Math.PI / 2F)) * radius);
        double d1 = d * horizontalVerticalRatio;
        this.carveEllipsoid(context, config, chunk, biomeAccessor, aquifer, x + (double)1.0F, y, z, d, d1, carvingMask, skipChecker);
    }

    protected void createTunnel(OTGCarvingContext context, CaveCarverConfiguration config, ChunkAccess chunk, Function<BlockPos, Holder<Biome>> biomeAccessor, long seed, Aquifer aquifer, double x, double y, double z, double horizontalRadiusMultiplier, double verticalRadiusMultiplier, float thickness, float yaw, float pitch, int branchIndex, int branchCount, double horizontalVerticalRatio, CarvingMask carvingMask, WorldCarver.CarveSkipChecker skipChecker) {
        RandomSource randomSource = RandomSource.create(seed);
        int i = randomSource.nextInt(branchCount / 2) + branchCount / 4;
        boolean flag = randomSource.nextInt(6) == 0;
        float f = 0.0F;
        float f1 = 0.0F;

        for(int i1 = branchIndex; i1 < branchCount; ++i1) {
            double d = (double)1.5F + (double)(Mth.sin((float)Math.PI * (float)i1 / (float)branchCount) * thickness);
            double d1 = d * horizontalVerticalRatio;
            float cos = Mth.cos(pitch);
            x += (double)(Mth.cos(yaw) * cos);
            y += (double)Mth.sin(pitch);
            z += (double)(Mth.sin(yaw) * cos);
            pitch *= flag ? 0.92F : 0.7F;
            pitch += f1 * 0.1F;
            yaw += f * 0.1F;
            f1 *= 0.9F;
            f *= 0.75F;
            f1 += (randomSource.nextFloat() - randomSource.nextFloat()) * randomSource.nextFloat() * 2.0F;
            f += (randomSource.nextFloat() - randomSource.nextFloat()) * randomSource.nextFloat() * 4.0F;
            if (i1 == i && thickness > 1.0F) {
                this.createTunnel(context, config, chunk, biomeAccessor, randomSource.nextLong(), aquifer, x, y, z, horizontalRadiusMultiplier, verticalRadiusMultiplier, randomSource.nextFloat() * 0.5F + 0.5F, yaw - ((float)Math.PI / 2F), pitch / 3.0F, i1, branchCount, (double)1.0F, carvingMask, skipChecker);
                this.createTunnel(context, config, chunk, biomeAccessor, randomSource.nextLong(), aquifer, x, y, z, horizontalRadiusMultiplier, verticalRadiusMultiplier, randomSource.nextFloat() * 0.5F + 0.5F, yaw + ((float)Math.PI / 2F), pitch / 3.0F, i1, branchCount, (double)1.0F, carvingMask, skipChecker);
                return;
            }

            if (randomSource.nextInt(4) != 0) {
                if (!canReach(chunk.getPos(), x, z, i1, branchCount, thickness)) {
                    return;
                }

                this.carveEllipsoid(context, config, chunk, biomeAccessor, aquifer, x, y, z, d * horizontalRadiusMultiplier, d1 * verticalRadiusMultiplier, carvingMask, skipChecker);
            }
        }

    }

    private static boolean shouldSkip(double relative, double relativeY, double relativeZ, double minrelativeY) {
        return relativeY <= minrelativeY || relative * relative + relativeY * relativeY + relativeZ * relativeZ >= (double)1.0F;
    }
}
