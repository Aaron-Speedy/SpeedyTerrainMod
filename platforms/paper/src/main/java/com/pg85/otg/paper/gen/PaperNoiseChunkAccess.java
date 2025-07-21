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

    // Since casting is not possible, ugly solution. If it can be any better, please change
    public static PaperNoiseChunkAccess create(NoiseChunk chunk, RandomState random, NoiseSettings generationShapeConfig, NoiseGeneratorSettings chunkGeneratorSettings, Aquifer.FluidPicker fluidLevelSampler) throws NoSuchFieldException, IllegalAccessException {
        Field cellCountXZField = ObfuscationHelper.getField(NoiseChunk.class, "cellCountXZ", "b");
        cellCountXZField.setAccessible(true);
        int cellCountXZ = cellCountXZField.getInt(chunk);

        // NoiseChunk will attempt to quartify these values upon init, so reverse that so init can happen again.
        Field firstNoiseXField =  ObfuscationHelper.getField(NoiseChunk.class, "firstNoiseX", "g");
        firstNoiseXField.setAccessible(true);
        int firstNoiseX = (firstNoiseXField.getInt(chunk) << 2);

        Field firstNoiseZField =   ObfuscationHelper.getField(NoiseChunk.class, "firstNoiseZ", "h");
        firstNoiseZField.setAccessible(true);
        int firstNoiseZ = (firstNoiseZField.getInt(chunk) << 2);

        Field beardifierField = ObfuscationHelper.getField(NoiseChunk.class, "beardifier", "s");
        beardifierField.setAccessible(true);
        DensityFunctions.BeardifierOrMarker beardifier = (DensityFunctions.BeardifierOrMarker)beardifierField.get(chunk);

        Field blenderField = ObfuscationHelper.getField(NoiseChunk.class, "blender", "p");
        blenderField.setAccessible(true);
        Blender blender = (Blender)blenderField.get(chunk);

        return new PaperNoiseChunkAccess(cellCountXZ, random, firstNoiseX, firstNoiseZ, generationShapeConfig,
                beardifier, chunkGeneratorSettings, fluidLevelSampler, blender);
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
