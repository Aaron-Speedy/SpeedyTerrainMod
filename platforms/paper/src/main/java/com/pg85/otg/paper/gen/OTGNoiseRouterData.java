package com.pg85.otg.paper.gen;

import java.util.stream.Stream;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.worldgen.BootstrapContext;
import net.minecraft.data.worldgen.TerrainProvider;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;

import com.pg85.otg.paper.gen.OTGDensityFunctions.WeirdScaledSampler.RarityValueMapper;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.NoiseRouter;
import net.minecraft.world.level.levelgen.Noises;
import com.pg85.otg.paper.gen.OTGOreVeinifier.VeinType;
import net.minecraft.world.level.levelgen.synth.BlendedNoise;
import net.minecraft.world.level.levelgen.synth.NormalNoise;

public class OTGNoiseRouterData {
    public static final float GLOBAL_OFFSET = -0.50375F;
    private static final float ORE_THICKNESS = 0.08F;
    private static final double VEININESS_FREQUENCY = (double)1.5F;
    private static final double NOODLE_SPACING_AND_STRAIGHTNESS = (double)1.5F;
    private static final double SURFACE_DENSITY_THRESHOLD = (double)1.5625F;
    private static final double CHEESE_NOISE_TARGET = (double)-0.703125F;
    public static final int ISLAND_CHUNK_DISTANCE = 64;
    public static final long ISLAND_CHUNK_DISTANCE_SQR = 4096L;
    private static final DensityFunction BLENDING_FACTOR = OTGDensityFunctions.constant((double)10.0F);
    private static final DensityFunction BLENDING_JAGGEDNESS = OTGDensityFunctions.zero();
    private static final ResourceKey<DensityFunction> ZERO = createKey("zero");
    private static final ResourceKey<DensityFunction> Y = createKey("y");
    private static final ResourceKey<DensityFunction> SHIFT_X = createKey("shift_x");
    private static final ResourceKey<DensityFunction> SHIFT_Z = createKey("shift_z");
    private static final ResourceKey<DensityFunction> BASE_3D_NOISE_OVERWORLD = createKey("overworld/base_3d_noise");
    private static final ResourceKey<DensityFunction> BASE_3D_NOISE_NETHER = createKey("nether/base_3d_noise");
    private static final ResourceKey<DensityFunction> BASE_3D_NOISE_END = createKey("end/base_3d_noise");
    public static final ResourceKey<DensityFunction> CONTINENTS = createKey("overworld/continents");
    public static final ResourceKey<DensityFunction> EROSION = createKey("overworld/erosion");
    public static final ResourceKey<DensityFunction> RIDGES = createKey("overworld/ridges");
    public static final ResourceKey<DensityFunction> RIDGES_FOLDED = createKey("overworld/ridges_folded");
    public static final ResourceKey<DensityFunction> OFFSET = createKey("overworld/offset");
    public static final ResourceKey<DensityFunction> FACTOR = createKey("overworld/factor");
    public static final ResourceKey<DensityFunction> JAGGEDNESS = createKey("overworld/jaggedness");
    public static final ResourceKey<DensityFunction> DEPTH = createKey("overworld/depth");
    private static final ResourceKey<DensityFunction> SLOPED_CHEESE = createKey("overworld/sloped_cheese");
    public static final ResourceKey<DensityFunction> CONTINENTS_LARGE = createKey("overworld_large_biomes/continents");
    public static final ResourceKey<DensityFunction> EROSION_LARGE = createKey("overworld_large_biomes/erosion");
    private static final ResourceKey<DensityFunction> OFFSET_LARGE = createKey("overworld_large_biomes/offset");
    private static final ResourceKey<DensityFunction> FACTOR_LARGE = createKey("overworld_large_biomes/factor");
    private static final ResourceKey<DensityFunction> JAGGEDNESS_LARGE = createKey("overworld_large_biomes/jaggedness");
    private static final ResourceKey<DensityFunction> DEPTH_LARGE = createKey("overworld_large_biomes/depth");
    private static final ResourceKey<DensityFunction> SLOPED_CHEESE_LARGE = createKey("overworld_large_biomes/sloped_cheese");
    private static final ResourceKey<DensityFunction> OFFSET_AMPLIFIED = createKey("overworld_amplified/offset");
    private static final ResourceKey<DensityFunction> FACTOR_AMPLIFIED = createKey("overworld_amplified/factor");
    private static final ResourceKey<DensityFunction> JAGGEDNESS_AMPLIFIED = createKey("overworld_amplified/jaggedness");
    private static final ResourceKey<DensityFunction> DEPTH_AMPLIFIED = createKey("overworld_amplified/depth");
    private static final ResourceKey<DensityFunction> SLOPED_CHEESE_AMPLIFIED = createKey("overworld_amplified/sloped_cheese");
    private static final ResourceKey<DensityFunction> SLOPED_CHEESE_END = createKey("end/sloped_cheese");
    private static final ResourceKey<DensityFunction> SPAGHETTI_ROUGHNESS_FUNCTION = createKey("overworld/caves/spaghetti_roughness_function");
    private static final ResourceKey<DensityFunction> ENTRANCES = createKey("overworld/caves/entrances");
    private static final ResourceKey<DensityFunction> NOODLE = createKey("overworld/caves/noodle");
    private static final ResourceKey<DensityFunction> PILLARS = createKey("overworld/caves/pillars");
    private static final ResourceKey<DensityFunction> SPAGHETTI_2D_THICKNESS_MODULATOR = createKey("overworld/caves/spaghetti_2d_thickness_modulator");
    private static final ResourceKey<DensityFunction> SPAGHETTI_2D = createKey("overworld/caves/spaghetti_2d");

    private static ResourceKey<DensityFunction> createKey(String location) {
        return ResourceKey.create(Registries.DENSITY_FUNCTION, ResourceLocation.withDefaultNamespace(location));
    }

    public static Holder<? extends DensityFunction> bootstrap(BootstrapContext<DensityFunction> context) {
        HolderGetter<NormalNoise.NoiseParameters> holderGetter = context.lookup(Registries.NOISE);
        HolderGetter<DensityFunction> holderGetter1 = context.lookup(Registries.DENSITY_FUNCTION);
        context.register(ZERO, OTGDensityFunctions.zero());
        int i = -4064;
        int i1 = 4062;
        context.register(Y, OTGDensityFunctions.yClampedGradient(i, i1, (double)i, (double)i1));
        DensityFunction densityFunction = registerAndWrap(context, SHIFT_X, OTGDensityFunctions.flatCache(OTGDensityFunctions.cache2d(OTGDensityFunctions.shiftA(holderGetter.getOrThrow(Noises.SHIFT)))));
        DensityFunction densityFunction1 = registerAndWrap(context, SHIFT_Z, OTGDensityFunctions.flatCache(OTGDensityFunctions.cache2d(OTGDensityFunctions.shiftB(holderGetter.getOrThrow(Noises.SHIFT)))));
        context.register(BASE_3D_NOISE_OVERWORLD, BlendedNoise.createUnseeded((double)0.25F, (double)0.125F, (double)80.0F, (double)160.0F, (double)8.0F));
        context.register(BASE_3D_NOISE_NETHER, BlendedNoise.createUnseeded((double)0.25F, (double)0.375F, (double)80.0F, (double)60.0F, (double)8.0F));
        context.register(BASE_3D_NOISE_END, BlendedNoise.createUnseeded((double)0.25F, (double)0.25F, (double)80.0F, (double)160.0F, (double)4.0F));
        Holder<DensityFunction> holder = context.register(CONTINENTS, OTGDensityFunctions.flatCache(OTGDensityFunctions.shiftedNoise2d(densityFunction, densityFunction1, (double)0.25F, holderGetter.getOrThrow(Noises.CONTINENTALNESS))));
        Holder<DensityFunction> holder1 = context.register(EROSION, OTGDensityFunctions.flatCache(OTGDensityFunctions.shiftedNoise2d(densityFunction, densityFunction1, (double)0.25F, holderGetter.getOrThrow(Noises.EROSION))));
        DensityFunction densityFunction2 = registerAndWrap(context, RIDGES, OTGDensityFunctions.flatCache(OTGDensityFunctions.shiftedNoise2d(densityFunction, densityFunction1, (double)0.25F, holderGetter.getOrThrow(Noises.RIDGE))));
        context.register(RIDGES_FOLDED, peaksAndValleys(densityFunction2));
        DensityFunction densityFunction3 = OTGDensityFunctions.noise(holderGetter.getOrThrow(Noises.JAGGED), (double)1500.0F, (double)0.0F);
        registerTerrainNoises(context, holderGetter1, densityFunction3, holder, holder1, OFFSET, FACTOR, JAGGEDNESS, DEPTH, SLOPED_CHEESE, false);
        Holder<DensityFunction> holder2 = context.register(CONTINENTS_LARGE, OTGDensityFunctions.flatCache(OTGDensityFunctions.shiftedNoise2d(densityFunction, densityFunction1, (double)0.25F, holderGetter.getOrThrow(Noises.CONTINENTALNESS_LARGE))));
        Holder<DensityFunction> holder3 = context.register(EROSION_LARGE, OTGDensityFunctions.flatCache(OTGDensityFunctions.shiftedNoise2d(densityFunction, densityFunction1, (double)0.25F, holderGetter.getOrThrow(Noises.EROSION_LARGE))));
        registerTerrainNoises(context, holderGetter1, densityFunction3, holder2, holder3, OFFSET_LARGE, FACTOR_LARGE, JAGGEDNESS_LARGE, DEPTH_LARGE, SLOPED_CHEESE_LARGE, false);
        registerTerrainNoises(context, holderGetter1, densityFunction3, holder, holder1, OFFSET_AMPLIFIED, FACTOR_AMPLIFIED, JAGGEDNESS_AMPLIFIED, DEPTH_AMPLIFIED, SLOPED_CHEESE_AMPLIFIED, true);
        context.register(SLOPED_CHEESE_END, OTGDensityFunctions.add(OTGDensityFunctions.endIslands(0L), getFunction(holderGetter1, BASE_3D_NOISE_END)));
        context.register(SPAGHETTI_ROUGHNESS_FUNCTION, spaghettiRoughnessFunction(holderGetter));
        context.register(SPAGHETTI_2D_THICKNESS_MODULATOR, OTGDensityFunctions.cacheOnce(OTGDensityFunctions.mappedNoise(holderGetter.getOrThrow(Noises.SPAGHETTI_2D_THICKNESS), (double)2.0F, (double)1.0F, -0.6, -1.3)));
        context.register(SPAGHETTI_2D, spaghetti2D(holderGetter1, holderGetter));
        context.register(ENTRANCES, entrances(holderGetter1, holderGetter));
        context.register(NOODLE, noodle(holderGetter1, holderGetter));
        return context.register(PILLARS, pillars(holderGetter));
    }

    private static void registerTerrainNoises(BootstrapContext<DensityFunction> context, HolderGetter<DensityFunction> densityFunctionRegistry, DensityFunction jaggedNoise, Holder<DensityFunction> continentalness, Holder<DensityFunction> erosion, ResourceKey<DensityFunction> offsetKey, ResourceKey<DensityFunction> factorKey, ResourceKey<DensityFunction> jaggednessKey, ResourceKey<DensityFunction> depthKey, ResourceKey<DensityFunction> slopedCheeseKey, boolean amplified) {
        OTGDensityFunctions.Spline.Coordinate coordinate = new OTGDensityFunctions.Spline.Coordinate(continentalness);
        OTGDensityFunctions.Spline.Coordinate coordinate1 = new OTGDensityFunctions.Spline.Coordinate(erosion);
        OTGDensityFunctions.Spline.Coordinate coordinate2 = new OTGDensityFunctions.Spline.Coordinate(densityFunctionRegistry.getOrThrow(RIDGES));
        OTGDensityFunctions.Spline.Coordinate coordinate3 = new OTGDensityFunctions.Spline.Coordinate(densityFunctionRegistry.getOrThrow(RIDGES_FOLDED));
        DensityFunction densityFunction = registerAndWrap(context, offsetKey, splineWithBlending(OTGDensityFunctions.add(OTGDensityFunctions.constant((double)-0.50375F), OTGDensityFunctions.spline(TerrainProvider.overworldOffset(coordinate, coordinate1, coordinate3, amplified))), OTGDensityFunctions.blendOffset()));
        DensityFunction densityFunction1 = registerAndWrap(context, factorKey, splineWithBlending(OTGDensityFunctions.spline(TerrainProvider.overworldFactor(coordinate, coordinate1, coordinate2, coordinate3, amplified)), BLENDING_FACTOR));
        DensityFunction densityFunction2 = registerAndWrap(context, depthKey, OTGDensityFunctions.add(OTGDensityFunctions.yClampedGradient(-64, 320, (double)1.5F, (double)-1.5F), densityFunction));
        DensityFunction densityFunction3 = registerAndWrap(context, jaggednessKey, splineWithBlending(OTGDensityFunctions.spline(TerrainProvider.overworldJaggedness(coordinate, coordinate1, coordinate2, coordinate3, amplified)), BLENDING_JAGGEDNESS));
        DensityFunction densityFunction4 = OTGDensityFunctions.mul(densityFunction3, jaggedNoise.halfNegative());
        DensityFunction densityFunction5 = noiseGradientDensity(densityFunction1, OTGDensityFunctions.add(densityFunction2, densityFunction4));
        context.register(slopedCheeseKey, OTGDensityFunctions.add(densityFunction5, getFunction(densityFunctionRegistry, BASE_3D_NOISE_OVERWORLD)));
    }

    private static DensityFunction registerAndWrap(BootstrapContext<DensityFunction> context, ResourceKey<DensityFunction> key, DensityFunction value) {
        return new OTGDensityFunctions.HolderHolder(context.register(key, value));
    }

    private static DensityFunction getFunction(HolderGetter<DensityFunction> densityFunctionRegistry, ResourceKey<DensityFunction> key) {
        return new OTGDensityFunctions.HolderHolder(densityFunctionRegistry.getOrThrow(key));
    }

    private static DensityFunction peaksAndValleys(DensityFunction densityFunction) {
        return OTGDensityFunctions.mul(OTGDensityFunctions.add(OTGDensityFunctions.add(densityFunction.abs(), OTGDensityFunctions.constant(-0.6666666666666666)).abs(), OTGDensityFunctions.constant(-0.3333333333333333)), OTGDensityFunctions.constant((double)-3.0F));
    }

    public static float peaksAndValleys(float weirdness) {
        return -(Math.abs(Math.abs(weirdness) - 0.6666667F) - 0.33333334F) * 3.0F;
    }

    private static DensityFunction spaghettiRoughnessFunction(HolderGetter<NormalNoise.NoiseParameters> noiseParameters) {
        DensityFunction densityFunction = OTGDensityFunctions.noise(noiseParameters.getOrThrow(Noises.SPAGHETTI_ROUGHNESS));
        DensityFunction densityFunction1 = OTGDensityFunctions.mappedNoise(noiseParameters.getOrThrow(Noises.SPAGHETTI_ROUGHNESS_MODULATOR), (double)0.0F, -0.1);
        return OTGDensityFunctions.cacheOnce(OTGDensityFunctions.mul(densityFunction1, OTGDensityFunctions.add(densityFunction.abs(), OTGDensityFunctions.constant(-0.4))));
    }

    private static DensityFunction entrances(HolderGetter<DensityFunction> densityFunctionRegistry, HolderGetter<NormalNoise.NoiseParameters> noiseParameters) {
        DensityFunction densityFunction = OTGDensityFunctions.cacheOnce(OTGDensityFunctions.noise(noiseParameters.getOrThrow(Noises.SPAGHETTI_3D_RARITY), (double)2.0F, (double)1.0F));
        DensityFunction densityFunction1 = OTGDensityFunctions.mappedNoise(noiseParameters.getOrThrow(Noises.SPAGHETTI_3D_THICKNESS), -0.065, -0.088);
        DensityFunction densityFunction2 = OTGDensityFunctions.weirdScaledSampler(densityFunction, noiseParameters.getOrThrow(Noises.SPAGHETTI_3D_1), RarityValueMapper.TYPE1);
        DensityFunction densityFunction3 = OTGDensityFunctions.weirdScaledSampler(densityFunction, noiseParameters.getOrThrow(Noises.SPAGHETTI_3D_2), RarityValueMapper.TYPE1);
        DensityFunction densityFunction4 = OTGDensityFunctions.add(OTGDensityFunctions.max(densityFunction2, densityFunction3), densityFunction1).clamp((double)-1.0F, (double)1.0F);
        DensityFunction function = getFunction(densityFunctionRegistry, SPAGHETTI_ROUGHNESS_FUNCTION);
        DensityFunction densityFunction5 = OTGDensityFunctions.noise(noiseParameters.getOrThrow(Noises.CAVE_ENTRANCE), (double)0.75F, (double)0.5F);
        DensityFunction densityFunction6 = OTGDensityFunctions.add(OTGDensityFunctions.add(densityFunction5, OTGDensityFunctions.constant(0.37)), OTGDensityFunctions.yClampedGradient(-10, 30, 0.3, (double)0.0F));
        return OTGDensityFunctions.cacheOnce(OTGDensityFunctions.min(densityFunction6, OTGDensityFunctions.add(function, densityFunction4)));
    }

    private static DensityFunction noodle(HolderGetter<DensityFunction> densityFunctions, HolderGetter<NormalNoise.NoiseParameters> noiseParameters) {
        DensityFunction function = getFunction(densityFunctions, Y);
        int i = -64;
        int i1 = -60;
        int i2 = 320;
        DensityFunction densityFunction = yLimitedInterpolatable(function, OTGDensityFunctions.noise(noiseParameters.getOrThrow(Noises.NOODLE), (double)1.0F, (double)1.0F), -60, 320, -1);
        DensityFunction densityFunction1 = yLimitedInterpolatable(function, OTGDensityFunctions.mappedNoise(noiseParameters.getOrThrow(Noises.NOODLE_THICKNESS), (double)1.0F, (double)1.0F, -0.05, -0.1), -60, 320, 0);
        double d = 2.6666666666666665;
        DensityFunction densityFunction2 = yLimitedInterpolatable(function, OTGDensityFunctions.noise(noiseParameters.getOrThrow(Noises.NOODLE_RIDGE_A), 2.6666666666666665, 2.6666666666666665), -60, 320, 0);
        DensityFunction densityFunction3 = yLimitedInterpolatable(function, OTGDensityFunctions.noise(noiseParameters.getOrThrow(Noises.NOODLE_RIDGE_B), 2.6666666666666665, 2.6666666666666665), -60, 320, 0);
        DensityFunction densityFunction4 = OTGDensityFunctions.mul(OTGDensityFunctions.constant((double)1.5F), OTGDensityFunctions.max(densityFunction2.abs(), densityFunction3.abs()));
        return OTGDensityFunctions.rangeChoice(densityFunction, (double)-1000000.0F, (double)0.0F, OTGDensityFunctions.constant((double)64.0F), OTGDensityFunctions.add(densityFunction1, densityFunction4));
    }

    private static DensityFunction pillars(HolderGetter<NormalNoise.NoiseParameters> noiseParameters) {
        double d = (double)25.0F;
        double d1 = 0.3;
        DensityFunction densityFunction = OTGDensityFunctions.noise(noiseParameters.getOrThrow(Noises.PILLAR), (double)25.0F, 0.3);
        DensityFunction densityFunction1 = OTGDensityFunctions.mappedNoise(noiseParameters.getOrThrow(Noises.PILLAR_RARENESS), (double)0.0F, (double)-2.0F);
        DensityFunction densityFunction2 = OTGDensityFunctions.mappedNoise(noiseParameters.getOrThrow(Noises.PILLAR_THICKNESS), (double)0.0F, 1.1);
        DensityFunction densityFunction3 = OTGDensityFunctions.add(OTGDensityFunctions.mul(densityFunction, OTGDensityFunctions.constant((double)2.0F)), densityFunction1);
        return OTGDensityFunctions.cacheOnce(OTGDensityFunctions.mul(densityFunction3, densityFunction2.cube()));
    }

    private static DensityFunction spaghetti2D(HolderGetter<DensityFunction> densityFunctionRegistry, HolderGetter<NormalNoise.NoiseParameters> noiseParameters) {
        DensityFunction densityFunction = OTGDensityFunctions.noise(noiseParameters.getOrThrow(Noises.SPAGHETTI_2D_MODULATOR), (double)2.0F, (double)1.0F);
        DensityFunction densityFunction1 = OTGDensityFunctions.weirdScaledSampler(densityFunction, noiseParameters.getOrThrow(Noises.SPAGHETTI_2D), RarityValueMapper.TYPE2);
        DensityFunction densityFunction2 = OTGDensityFunctions.mappedNoise(noiseParameters.getOrThrow(Noises.SPAGHETTI_2D_ELEVATION), (double)0.0F, (double)Math.floorDiv(-64, 8), (double)8.0F);
        DensityFunction function = getFunction(densityFunctionRegistry, SPAGHETTI_2D_THICKNESS_MODULATOR);
        DensityFunction densityFunction3 = OTGDensityFunctions.add(densityFunction2, OTGDensityFunctions.yClampedGradient(-64, 320, (double)8.0F, (double)-40.0F)).abs();
        DensityFunction densityFunction4 = OTGDensityFunctions.add(densityFunction3, function).cube();
        double d = 0.083;
        DensityFunction densityFunction5 = OTGDensityFunctions.add(densityFunction1, OTGDensityFunctions.mul(OTGDensityFunctions.constant(0.083), function));
        return OTGDensityFunctions.max(densityFunction5, densityFunction4).clamp((double)-1.0F, (double)1.0F);
    }

    private static DensityFunction underground(HolderGetter<DensityFunction> densityFunctionRegistry, HolderGetter<NormalNoise.NoiseParameters> noiseParameters, DensityFunction slopedCheese) {
        DensityFunction function = getFunction(densityFunctionRegistry, SPAGHETTI_2D);
        DensityFunction function1 = getFunction(densityFunctionRegistry, SPAGHETTI_ROUGHNESS_FUNCTION);
        DensityFunction densityFunction = OTGDensityFunctions.noise(noiseParameters.getOrThrow(Noises.CAVE_LAYER), (double)8.0F);
        DensityFunction densityFunction1 = OTGDensityFunctions.mul(OTGDensityFunctions.constant((double)4.0F), densityFunction.square());
        DensityFunction densityFunction2 = OTGDensityFunctions.noise(noiseParameters.getOrThrow(Noises.CAVE_CHEESE), 0.6666666666666666);
        DensityFunction densityFunction3 = OTGDensityFunctions.add(OTGDensityFunctions.add(OTGDensityFunctions.constant(0.27), densityFunction2).clamp((double)-1.0F, (double)1.0F), OTGDensityFunctions.add(OTGDensityFunctions.constant((double)1.5F), OTGDensityFunctions.mul(OTGDensityFunctions.constant(-0.64), slopedCheese)).clamp((double)0.0F, (double)0.5F));
        DensityFunction densityFunction4 = OTGDensityFunctions.add(densityFunction1, densityFunction3);
        DensityFunction densityFunction5 = OTGDensityFunctions.min(OTGDensityFunctions.min(densityFunction4, getFunction(densityFunctionRegistry, ENTRANCES)), OTGDensityFunctions.add(function, function1));
        DensityFunction function2 = getFunction(densityFunctionRegistry, PILLARS);
        DensityFunction densityFunction6 = OTGDensityFunctions.rangeChoice(function2, (double)-1000000.0F, 0.03, OTGDensityFunctions.constant((double)-1000000.0F), function2);
        return OTGDensityFunctions.max(densityFunction5, densityFunction6);
    }

    private static DensityFunction postProcess(DensityFunction densityFunction) {
        DensityFunction densityFunction1 = OTGDensityFunctions.blendDensity(densityFunction);
        return OTGDensityFunctions.mul(OTGDensityFunctions.interpolated(densityFunction1), OTGDensityFunctions.constant(0.64)).squeeze();
    }

    protected static NoiseRouter overworld(HolderGetter<DensityFunction> densityFunctionRegistry, HolderGetter<NormalNoise.NoiseParameters> noiseParameters, boolean large, boolean amplified) {
        DensityFunction densityFunction = OTGDensityFunctions.noise(noiseParameters.getOrThrow(Noises.AQUIFER_BARRIER), (double)0.5F);
        DensityFunction densityFunction1 = OTGDensityFunctions.noise(noiseParameters.getOrThrow(Noises.AQUIFER_FLUID_LEVEL_FLOODEDNESS), 0.67);
        DensityFunction densityFunction2 = OTGDensityFunctions.noise(noiseParameters.getOrThrow(Noises.AQUIFER_FLUID_LEVEL_SPREAD), 0.7142857142857143);
        DensityFunction densityFunction3 = OTGDensityFunctions.noise(noiseParameters.getOrThrow(Noises.AQUIFER_LAVA));
        DensityFunction function = getFunction(densityFunctionRegistry, SHIFT_X);
        DensityFunction function1 = getFunction(densityFunctionRegistry, SHIFT_Z);
        DensityFunction densityFunction4 = OTGDensityFunctions.shiftedNoise2d(function, function1, (double)0.25F, noiseParameters.getOrThrow(large ? Noises.TEMPERATURE_LARGE : Noises.TEMPERATURE));
        DensityFunction densityFunction5 = OTGDensityFunctions.shiftedNoise2d(function, function1, (double)0.25F, noiseParameters.getOrThrow(large ? Noises.VEGETATION_LARGE : Noises.VEGETATION));
        DensityFunction function2 = getFunction(densityFunctionRegistry, large ? FACTOR_LARGE : (amplified ? FACTOR_AMPLIFIED : FACTOR));
        DensityFunction function3 = getFunction(densityFunctionRegistry, large ? DEPTH_LARGE : (amplified ? DEPTH_AMPLIFIED : DEPTH));
        DensityFunction densityFunction6 = noiseGradientDensity(OTGDensityFunctions.cache2d(function2), function3);
        DensityFunction function4 = getFunction(densityFunctionRegistry, large ? SLOPED_CHEESE_LARGE : (amplified ? SLOPED_CHEESE_AMPLIFIED : SLOPED_CHEESE));
        DensityFunction densityFunction7 = OTGDensityFunctions.min(function4, OTGDensityFunctions.mul(OTGDensityFunctions.constant((double)5.0F), getFunction(densityFunctionRegistry, ENTRANCES)));
        DensityFunction densityFunction8 = OTGDensityFunctions.rangeChoice(function4, (double)-1000000.0F, (double)1.5625F, densityFunction7, underground(densityFunctionRegistry, noiseParameters, function4));
        DensityFunction densityFunction9 = OTGDensityFunctions.min(postProcess(slideOverworld(amplified, densityFunction8)), getFunction(densityFunctionRegistry, NOODLE));
        DensityFunction function5 = getFunction(densityFunctionRegistry, Y);
        int i = Stream.of(VeinType.values()).mapToInt((veinType) -> veinType.minY).min().orElse(4064);
        int i1 = Stream.of(VeinType.values()).mapToInt((veinType) -> veinType.maxY).max().orElse(4064);
        DensityFunction densityFunction10 = yLimitedInterpolatable(function5, OTGDensityFunctions.noise(noiseParameters.getOrThrow(Noises.ORE_VEININESS), (double)1.5F, (double)1.5F), i, i1, 0);
        float f = 4.0F;
        DensityFunction densityFunction11 = yLimitedInterpolatable(function5, OTGDensityFunctions.noise(noiseParameters.getOrThrow(Noises.ORE_VEIN_A), (double)4.0F, (double)4.0F), i, i1, 0).abs();
        DensityFunction densityFunction12 = yLimitedInterpolatable(function5, OTGDensityFunctions.noise(noiseParameters.getOrThrow(Noises.ORE_VEIN_B), (double)4.0F, (double)4.0F), i, i1, 0).abs();
        DensityFunction densityFunction13 = OTGDensityFunctions.add(OTGDensityFunctions.constant((double)-0.08F), OTGDensityFunctions.max(densityFunction11, densityFunction12));
        DensityFunction densityFunction14 = OTGDensityFunctions.noise(noiseParameters.getOrThrow(Noises.ORE_GAP));
        return new NoiseRouter(densityFunction, densityFunction1, densityFunction2, densityFunction3, densityFunction4, densityFunction5, getFunction(densityFunctionRegistry, large ? CONTINENTS_LARGE : CONTINENTS), getFunction(densityFunctionRegistry, large ? EROSION_LARGE : EROSION), function3, getFunction(densityFunctionRegistry, RIDGES), slideOverworld(amplified, OTGDensityFunctions.add(densityFunction6, OTGDensityFunctions.constant((double)-0.703125F)).clamp((double)-64.0F, (double)64.0F)), densityFunction9, densityFunction10, densityFunction13, densityFunction14);
    }

    private static NoiseRouter noNewCaves(HolderGetter<DensityFunction> densityFunctions, HolderGetter<NormalNoise.NoiseParameters> noiseParameters, DensityFunction postProccessor) {
        DensityFunction function = getFunction(densityFunctions, SHIFT_X);
        DensityFunction function1 = getFunction(densityFunctions, SHIFT_Z);
        DensityFunction densityFunction = OTGDensityFunctions.shiftedNoise2d(function, function1, (double)0.25F, noiseParameters.getOrThrow(Noises.TEMPERATURE));
        DensityFunction densityFunction1 = OTGDensityFunctions.shiftedNoise2d(function, function1, (double)0.25F, noiseParameters.getOrThrow(Noises.VEGETATION));
        DensityFunction densityFunction2 = postProcess(postProccessor);
        return new NoiseRouter(OTGDensityFunctions.zero(), OTGDensityFunctions.zero(), OTGDensityFunctions.zero(), OTGDensityFunctions.zero(), densityFunction, densityFunction1, OTGDensityFunctions.zero(), OTGDensityFunctions.zero(), OTGDensityFunctions.zero(), OTGDensityFunctions.zero(), OTGDensityFunctions.zero(), densityFunction2, OTGDensityFunctions.zero(), OTGDensityFunctions.zero(), OTGDensityFunctions.zero());
    }

    private static DensityFunction slideOverworld(boolean amplified, DensityFunction densityFunction) {
        return slide(densityFunction, -64, 384, amplified ? 16 : 80, amplified ? 0 : 64, (double)-0.078125F, 0, 24, amplified ? 0.4 : (double)0.1171875F);
    }

    private static DensityFunction slideNetherLike(HolderGetter<DensityFunction> OTGDensityFunctions, int minY, int height) {
        return slide(getFunction(OTGDensityFunctions, BASE_3D_NOISE_NETHER), minY, height, 24, 0, (double)0.9375F, -8, 24, (double)2.5F);
    }

    private static DensityFunction slideEndLike(DensityFunction densityFunction, int minY, int height) {
        return slide(densityFunction, minY, height, 72, -184, (double)-23.4375F, 4, 32, (double)-0.234375F);
    }

    protected static NoiseRouter nether(HolderGetter<DensityFunction> OTGDensityFunctions, HolderGetter<NormalNoise.NoiseParameters> noiseParameters) {
        return noNewCaves(OTGDensityFunctions, noiseParameters, slideNetherLike(OTGDensityFunctions, 0, 128));
    }

    protected static NoiseRouter caves(HolderGetter<DensityFunction> OTGDensityFunctions, HolderGetter<NormalNoise.NoiseParameters> noiseParameters) {
        return noNewCaves(OTGDensityFunctions, noiseParameters, slideNetherLike(OTGDensityFunctions, -64, 192));
    }

    protected static NoiseRouter floatingIslands(HolderGetter<DensityFunction> densityFunction, HolderGetter<NormalNoise.NoiseParameters> noiseParameters) {
        return noNewCaves(densityFunction, noiseParameters, slideEndLike(getFunction(densityFunction, BASE_3D_NOISE_END), 0, 256));
    }

    private static DensityFunction slideEnd(DensityFunction densityFunction) {
        return slideEndLike(densityFunction, 0, 128);
    }

    protected static NoiseRouter end(HolderGetter<DensityFunction> densityFunctions) {
        DensityFunction densityFunction = OTGDensityFunctions.cache2d(OTGDensityFunctions.endIslands(0L));
        DensityFunction densityFunction1 = postProcess(slideEnd(getFunction(densityFunctions, SLOPED_CHEESE_END)));
        return new NoiseRouter(OTGDensityFunctions.zero(), OTGDensityFunctions.zero(), OTGDensityFunctions.zero(), OTGDensityFunctions.zero(), OTGDensityFunctions.zero(), OTGDensityFunctions.zero(), OTGDensityFunctions.zero(), densityFunction, OTGDensityFunctions.zero(), OTGDensityFunctions.zero(), slideEnd(OTGDensityFunctions.add(densityFunction, OTGDensityFunctions.constant((double)-0.703125F))), densityFunction1, OTGDensityFunctions.zero(), OTGDensityFunctions.zero(), OTGDensityFunctions.zero());
    }

    protected static NoiseRouter none() {
        return new NoiseRouter(OTGDensityFunctions.zero(), OTGDensityFunctions.zero(), OTGDensityFunctions.zero(), OTGDensityFunctions.zero(), OTGDensityFunctions.zero(), OTGDensityFunctions.zero(), OTGDensityFunctions.zero(), OTGDensityFunctions.zero(), OTGDensityFunctions.zero(), OTGDensityFunctions.zero(), OTGDensityFunctions.zero(), OTGDensityFunctions.zero(), OTGDensityFunctions.zero(), OTGDensityFunctions.zero(), OTGDensityFunctions.zero());
    }

    private static DensityFunction splineWithBlending(DensityFunction minFunction, DensityFunction maxFunction) {
        DensityFunction densityFunction = OTGDensityFunctions.lerp(OTGDensityFunctions.blendAlpha(), maxFunction, minFunction);
        return OTGDensityFunctions.flatCache(OTGDensityFunctions.cache2d(densityFunction));
    }

    private static DensityFunction noiseGradientDensity(DensityFunction minFunction, DensityFunction maxFunction) {
        DensityFunction densityFunction = OTGDensityFunctions.mul(maxFunction, minFunction);
        return OTGDensityFunctions.mul(OTGDensityFunctions.constant((double)4.0F), densityFunction.quarterNegative());
    }

    private static DensityFunction yLimitedInterpolatable(DensityFunction input, DensityFunction whenInRange, int minY, int maxY, int whenOutOfRange) {
        return OTGDensityFunctions.interpolated(OTGDensityFunctions.rangeChoice(input, (double)minY, (double)(maxY + 1), whenInRange, OTGDensityFunctions.constant((double)whenOutOfRange)));
    }

    private static DensityFunction slide(DensityFunction input, int minY, int height, int topStartOffset, int topEndOffset, double topDelta, int bottomStartOffset, int bottomEndOffset, double bottomDelta) {
        DensityFunction densityFunction1 = OTGDensityFunctions.yClampedGradient(minY + height - topStartOffset, minY + height - topEndOffset, (double)1.0F, (double)0.0F);
        DensityFunction densityFunction = OTGDensityFunctions.lerp(densityFunction1, topDelta, input);
        DensityFunction densityFunction2 = OTGDensityFunctions.yClampedGradient(minY + bottomStartOffset, minY + bottomEndOffset, (double)0.0F, (double)1.0F);
        return OTGDensityFunctions.lerp(densityFunction2, bottomDelta, densityFunction);
    }

    protected static final class QuantizedSpaghettiRarity {
        protected static double getSphaghettiRarity2D(double value) {
            if (value < (double)-0.75F) {
                return (double)0.5F;
            } else if (value < (double)-0.5F) {
                return (double)0.75F;
            } else if (value < (double)0.5F) {
                return (double)1.0F;
            } else {
                return value < (double)0.75F ? (double)2.0F : (double)3.0F;
            }
        }

        protected static double getSpaghettiRarity3D(double value) {
            if (value < (double)-0.5F) {
                return (double)0.75F;
            } else if (value < (double)0.0F) {
                return (double)1.0F;
            } else {
                return value < (double)0.5F ? (double)1.5F : (double)2.0F;
            }
        }
    }
}

