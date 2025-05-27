package com.pg85.otg.paper.gen.carver;

import com.pg85.otg.paper.gen.OTGNoiseChunkGenerator;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.*;
import net.minecraft.world.level.levelgen.carver.CarvingContext;

import java.lang.reflect.Constructor;

import javax.annotation.Nullable;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Optional;
import java.util.function.Function;

public class OTGCarvingContext extends WorldGenerationContext {
    private final RegistryAccess registryAccess;
    private final NoiseChunk noiseChunk;
    private final RandomState randomState;
    private final SurfaceRules.RuleSource surfaceRule;

    public OTGCarvingContext(OTGNoiseChunkGenerator generator, RegistryAccess registryAccess, LevelHeightAccessor level, NoiseChunk noiseChunk, RandomState randomState, SurfaceRules.RuleSource surfaceRule, @Nullable Level serverLevel) {
        super(generator, level, serverLevel);
        this.registryAccess = registryAccess;
        this.noiseChunk = noiseChunk;
        this.randomState = randomState;
        this.surfaceRule = surfaceRule;
    }

    public Optional<BlockState> topMaterial(Function<BlockPos, Holder<Biome>> biomeMapper, ChunkAccess access, BlockPos pos, boolean hasFluid) {
        return internalTopMaterial(this.surfaceRule, this, biomeMapper, access, this.noiseChunk, pos, hasFluid);
    }

    private Optional<BlockState> internalTopMaterial(SurfaceRules.RuleSource rule, OTGCarvingContext context, Function<BlockPos, Holder<Biome>> biomeGetter, ChunkAccess chunk, NoiseChunk noiseChunk, BlockPos pos, boolean hasFluid) {
        try {
            Constructor contextConstructor = SurfaceRules.Context.class.getDeclaredConstructors()[0];
            contextConstructor.setAccessible(true);
            SurfaceRules.Context context1 = (SurfaceRules.Context) contextConstructor.newInstance(this, context.randomState(), chunk, noiseChunk, biomeGetter, this.registryAccess().lookupOrThrow(Registries.BIOME), context);
            SurfaceRules.SurfaceRule surfaceRule = (SurfaceRules.SurfaceRule) rule.apply(context1);
            int x = pos.getX();
            int y = pos.getY();
            int z = pos.getZ();
            Method updateXZ = SurfaceRules.Context.class.getDeclaredMethod("a", int.class, int.class);
            updateXZ.setAccessible(true);
            updateXZ.invoke(context1, x, z);

            Method updateY = SurfaceRules.Context.class.getDeclaredMethod("a", int.class, int.class, int.class, int.class, int.class, int.class);
            updateY.setAccessible(true);
            updateY.invoke(context1, 1, 1, hasFluid ? y + 1 : Integer.MIN_VALUE, x, y, z);
            BlockState blockState = surfaceRule.tryApply(x, y, z);
            return Optional.ofNullable(blockState);
        } catch (InstantiationException e) {
            throw new RuntimeException(e);
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        } catch (InvocationTargetException e) {
            throw new RuntimeException(e);
        } catch (NoSuchMethodException e) {
            throw new RuntimeException(e);
        }
    }

    public RegistryAccess registryAccess() {
        return this.registryAccess;
    }

    public RandomState randomState() {
        return this.randomState;
    }
}
