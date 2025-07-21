package com.pg85.otg.paper.gen;

import com.pg85.otg.paper.util.ObfuscationHelper;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.*;
import net.minecraft.world.level.levelgen.blending.Blender;

import java.lang.reflect.Field;
import java.util.List;

public class PaperNoiseChunkAccess extends NoiseChunk {
    public PaperNoiseChunkAccess(int horizontalCellCount, RandomState noiseConfig, int startBlockX, int startBlockZ, NoiseSettings generationShapeConfig, DensityFunctions.BeardifierOrMarker beardifying, NoiseGeneratorSettings chunkGeneratorSettings, Aquifer.FluidPicker fluidLevelSampler, Blender blender) {
        super(horizontalCellCount, noiseConfig, startBlockX, startBlockZ, generationShapeConfig, beardifying, chunkGeneratorSettings, fluidLevelSampler, blender);
    }

    @Override
    public Climate.Sampler cachedClimateSampler(NoiseRouter router, List<Climate.ParameterPoint> spawnTarget) {
        return super.cachedClimateSampler(router, spawnTarget);
    }

    @Override
    public BlockState getInterpolatedState() {
        return super.getInterpolatedState();
    }
}
