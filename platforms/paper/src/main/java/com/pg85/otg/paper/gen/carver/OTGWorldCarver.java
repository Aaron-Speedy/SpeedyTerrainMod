package com.pg85.otg.paper.gen.carver;

import com.mojang.serialization.Codec;
import net.minecraft.SharedConstants;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.RegistryFileCodec;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.util.valueproviders.UniformFloat;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.CarvingMask;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.Aquifer;
import net.minecraft.world.level.levelgen.VerticalAnchor;
import net.minecraft.world.level.levelgen.carver.*;
import net.minecraft.world.level.levelgen.heightproviders.UniformHeight;

import java.util.function.Function;

public class OTGWorldCarver {
    public boolean carve(OTGCarvingContext context, ChunkAccess chunk, Function<BlockPos, Holder<Biome>> biomeAccessor, RandomSource random, Aquifer aquifer, ChunkPos chunkPos, CarvingMask carvingMask) {
        CaveCarverConfiguration caveConfig = new CaveCarverConfiguration(
                0.15F,
                UniformHeight.of(VerticalAnchor.aboveBottom(8), VerticalAnchor.absolute(180)),
                UniformFloat.of(0.1F, 0.9F),
                VerticalAnchor.aboveBottom(8),
                CarverDebugSettings.of(false, Blocks.CRIMSON_BUTTON.defaultBlockState()),
                BuiltInRegistries.BLOCK.getOrThrow(BlockTags.OVERWORLD_CARVER_REPLACEABLES),
                UniformFloat.of(0.7F, 1.4F),
                UniformFloat.of(0.8F, 1.3F),
                UniformFloat.of(-1.0F, -0.4F)
        );
        OTGCaveCarver caveCarver = new OTGCaveCarver();
        return !SharedConstants.debugVoidTerrain(chunk.getPos()) && caveCarver.carveCaves(context, caveConfig, chunk, biomeAccessor, random, aquifer, chunkPos, carvingMask, UniformFloat.of(-1.0F, -0.4F));
    }
}
