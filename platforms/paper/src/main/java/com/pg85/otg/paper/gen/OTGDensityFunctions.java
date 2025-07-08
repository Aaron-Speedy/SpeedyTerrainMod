/* Copied exactly from net.minecraft.world.level.levelgen.DensityFunctions except for
   changing BeardifierMarker from protected to public and renaming the class. */

package com.pg85.otg.paper.gen;

import com.mojang.datafixers.util.Either;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.HashCommon;
import it.unimi.dsi.fastutil.doubles.Double2DoubleFunction;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.WeakHashMap;
import java.util.function.BiFunction;
import java.util.function.Function;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.CubicSpline;
import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.util.ToFloatFunction;
import net.minecraft.util.VisibleForDebug;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraft.world.level.levelgen.NoiseRouterData;
import net.minecraft.world.level.levelgen.synth.BlendedNoise;
import net.minecraft.world.level.levelgen.synth.NormalNoise;
import net.minecraft.world.level.levelgen.synth.SimplexNoise;
import org.slf4j.Logger;

public final class OTGDensityFunctions {
    private static final Codec<DensityFunction> CODEC;
    protected static final double MAX_REASONABLE_NOISE_VALUE = (double)1000000.0F;
    static final Codec<Double> NOISE_VALUE_CODEC;
    public static final Codec<DensityFunction> DIRECT_CODEC;

    public static MapCodec<? extends DensityFunction> bootstrap(Registry<MapCodec<? extends DensityFunction>> registry) {
        register(registry, "blend_alpha", OTGDensityFunctions.BlendAlpha.CODEC);
        register(registry, "blend_offset", OTGDensityFunctions.BlendOffset.CODEC);
        register(registry, "beardifier", OTGDensityFunctions.BeardifierMarker.CODEC);
        register(registry, "old_blended_noise", BlendedNoise.CODEC);

        for(OTGDensityFunctions.Marker.Type type : OTGDensityFunctions.Marker.Type.values()) {
            register(registry, type.getSerializedName(), type.codec);
        }

        register(registry, "noise", OTGDensityFunctions.Noise.CODEC);
        register(registry, "end_islands", OTGDensityFunctions.EndIslandDensityFunction.CODEC);
        register(registry, "weird_scaled_sampler", OTGDensityFunctions.WeirdScaledSampler.CODEC);
        register(registry, "shifted_noise", OTGDensityFunctions.ShiftedNoise.CODEC);
        register(registry, "range_choice", OTGDensityFunctions.RangeChoice.CODEC);
        register(registry, "shift_a", OTGDensityFunctions.ShiftA.CODEC);
        register(registry, "shift_b", OTGDensityFunctions.ShiftB.CODEC);
        register(registry, "shift", OTGDensityFunctions.Shift.CODEC);
        register(registry, "blend_density", OTGDensityFunctions.BlendDensity.CODEC);
        register(registry, "clamp", OTGDensityFunctions.Clamp.CODEC);

        for(OTGDensityFunctions.Mapped.Type type1 : OTGDensityFunctions.Mapped.Type.values()) {
            register(registry, type1.getSerializedName(), type1.codec);
        }

        for(OTGDensityFunctions.TwoArgumentSimpleFunction.Type type2 : OTGDensityFunctions.TwoArgumentSimpleFunction.Type.values()) {
            register(registry, type2.getSerializedName(), type2.codec);
        }

        register(registry, "spline", OTGDensityFunctions.Spline.CODEC);
        register(registry, "constant", OTGDensityFunctions.Constant.CODEC);
        return register(registry, "y_clamped_gradient", OTGDensityFunctions.YClampedGradient.CODEC);
    }

    private static MapCodec<? extends DensityFunction> register(Registry<MapCodec<? extends DensityFunction>> registry, String name, KeyDispatchDataCodec<? extends DensityFunction> codec) {
        return (MapCodec)Registry.register(registry, name, codec.codec());
    }

    static <A, O> KeyDispatchDataCodec<O> singleArgumentCodec(Codec<A> codec, Function<A, O> fromFunction, Function<O, A> toFunction) {
        return KeyDispatchDataCodec.of(codec.fieldOf("argument").xmap(fromFunction, toFunction));
    }

    static <O> KeyDispatchDataCodec<O> singleFunctionArgumentCodec(Function<DensityFunction, O> fromFunction, Function<O, DensityFunction> toFunction) {
        return singleArgumentCodec(DensityFunction.HOLDER_HELPER_CODEC, fromFunction, toFunction);
    }

    static <O> KeyDispatchDataCodec<O> doubleFunctionArgumentCodec(BiFunction<DensityFunction, DensityFunction, O> fromFunction, Function<O, DensityFunction> primary, Function<O, DensityFunction> secondary) {
        return KeyDispatchDataCodec.of(RecordCodecBuilder.mapCodec((instance) -> instance.group(DensityFunction.HOLDER_HELPER_CODEC.fieldOf("argument1").forGetter(primary), DensityFunction.HOLDER_HELPER_CODEC.fieldOf("argument2").forGetter(secondary)).apply(instance, fromFunction)));
    }

    static <O> KeyDispatchDataCodec<O> makeCodec(MapCodec<O> mapCodec) {
        return KeyDispatchDataCodec.of(mapCodec);
    }

    private OTGDensityFunctions() {
    }

    public static DensityFunction interpolated(DensityFunction wrapped) {
        return new OTGDensityFunctions.Marker(OTGDensityFunctions.Marker.Type.Interpolated, wrapped);
    }

    public static DensityFunction flatCache(DensityFunction wrapped) {
        return new OTGDensityFunctions.Marker(OTGDensityFunctions.Marker.Type.FlatCache, wrapped);
    }

    public static DensityFunction cache2d(DensityFunction wrapped) {
        return new OTGDensityFunctions.Marker(OTGDensityFunctions.Marker.Type.Cache2D, wrapped);
    }

    public static DensityFunction cacheOnce(DensityFunction wrapped) {
        return new OTGDensityFunctions.Marker(OTGDensityFunctions.Marker.Type.CacheOnce, wrapped);
    }

    public static DensityFunction cacheAllInCell(DensityFunction wrapped) {
        return new OTGDensityFunctions.Marker(OTGDensityFunctions.Marker.Type.CacheAllInCell, wrapped);
    }

    public static DensityFunction mappedNoise(Holder<NormalNoise.NoiseParameters> noiseData, @Deprecated double xzScale, double yScale, double fromY, double toY) {
        return mapFromUnitTo(new OTGDensityFunctions.Noise(new DensityFunction.NoiseHolder(noiseData), xzScale, yScale), fromY, toY);
    }

    public static DensityFunction mappedNoise(Holder<NormalNoise.NoiseParameters> noiseData, double yScale, double fromY, double toY) {
        return mappedNoise(noiseData, (double)1.0F, yScale, fromY, toY);
    }

    public static DensityFunction mappedNoise(Holder<NormalNoise.NoiseParameters> noiseData, double fromY, double toY) {
        return mappedNoise(noiseData, (double)1.0F, (double)1.0F, fromY, toY);
    }

    public static DensityFunction shiftedNoise2d(DensityFunction shiftX, DensityFunction shiftZ, double xzScale, Holder<NormalNoise.NoiseParameters> noiseData) {
        return new OTGDensityFunctions.ShiftedNoise(shiftX, zero(), shiftZ, xzScale, (double)0.0F, new DensityFunction.NoiseHolder(noiseData));
    }

    public static DensityFunction noise(Holder<NormalNoise.NoiseParameters> noiseData) {
        return noise(noiseData, (double)1.0F, (double)1.0F);
    }

    public static DensityFunction noise(Holder<NormalNoise.NoiseParameters> noiseData, double xzScale, double yScale) {
        return new OTGDensityFunctions.Noise(new DensityFunction.NoiseHolder(noiseData), xzScale, yScale);
    }

    public static DensityFunction noise(Holder<NormalNoise.NoiseParameters> noiseData, double yScale) {
        return noise(noiseData, (double)1.0F, yScale);
    }

    public static DensityFunction rangeChoice(DensityFunction input, double minInclusive, double maxExclusive, DensityFunction whenInRange, DensityFunction whenOutOfRange) {
        return new OTGDensityFunctions.RangeChoice(input, minInclusive, maxExclusive, whenInRange, whenOutOfRange);
    }

    public static DensityFunction shiftA(Holder<NormalNoise.NoiseParameters> noiseData) {
        return new OTGDensityFunctions.ShiftA(new DensityFunction.NoiseHolder(noiseData));
    }

    public static DensityFunction shiftB(Holder<NormalNoise.NoiseParameters> noiseData) {
        return new OTGDensityFunctions.ShiftB(new DensityFunction.NoiseHolder(noiseData));
    }

    public static DensityFunction shift(Holder<NormalNoise.NoiseParameters> noiseData) {
        return new OTGDensityFunctions.Shift(new DensityFunction.NoiseHolder(noiseData));
    }

    public static DensityFunction blendDensity(DensityFunction input) {
        return new OTGDensityFunctions.BlendDensity(input);
    }

    public static DensityFunction endIslands(long seed) {
        return new OTGDensityFunctions.EndIslandDensityFunction(seed);
    }

    public static DensityFunction weirdScaledSampler(DensityFunction input, Holder<NormalNoise.NoiseParameters> noiseData, OTGDensityFunctions.WeirdScaledSampler.RarityValueMapper rarityValueMapper) {
        return new OTGDensityFunctions.WeirdScaledSampler(input, new DensityFunction.NoiseHolder(noiseData), rarityValueMapper);
    }

    public static DensityFunction add(DensityFunction argument1, DensityFunction argument2) {
        return OTGDensityFunctions.TwoArgumentSimpleFunction.create(OTGDensityFunctions.TwoArgumentSimpleFunction.Type.ADD, argument1, argument2);
    }

    public static DensityFunction mul(DensityFunction argument1, DensityFunction argument2) {
        return OTGDensityFunctions.TwoArgumentSimpleFunction.create(OTGDensityFunctions.TwoArgumentSimpleFunction.Type.MUL, argument1, argument2);
    }

    public static DensityFunction min(DensityFunction argument1, DensityFunction argument2) {
        return OTGDensityFunctions.TwoArgumentSimpleFunction.create(OTGDensityFunctions.TwoArgumentSimpleFunction.Type.MIN, argument1, argument2);
    }

    public static DensityFunction max(DensityFunction argument1, DensityFunction argument2) {
        return OTGDensityFunctions.TwoArgumentSimpleFunction.create(OTGDensityFunctions.TwoArgumentSimpleFunction.Type.MAX, argument1, argument2);
    }

    public static DensityFunction spline(CubicSpline<OTGDensityFunctions.Spline.Point, OTGDensityFunctions.Spline.Coordinate> spline) {
        return new OTGDensityFunctions.Spline(spline);
    }

    public static DensityFunction zero() {
        return OTGDensityFunctions.Constant.ZERO;
    }

    public static DensityFunction constant(double value) {
        return new OTGDensityFunctions.Constant(value);
    }

    public static DensityFunction yClampedGradient(int fromY, int toY, double fromValue, double toValue) {
        return new OTGDensityFunctions.YClampedGradient(fromY, toY, fromValue, toValue);
    }

    public static DensityFunction map(DensityFunction input, OTGDensityFunctions.Mapped.Type type) {
        return OTGDensityFunctions.Mapped.create(type, input);
    }

    private static DensityFunction mapFromUnitTo(DensityFunction densityFunction, double fromY, double toY) {
        double d = (fromY + toY) * (double)0.5F;
        double d1 = (toY - fromY) * (double)0.5F;
        return add(constant(d), mul(constant(d1), densityFunction));
    }

    public static DensityFunction blendAlpha() {
        return OTGDensityFunctions.BlendAlpha.INSTANCE;
    }

    public static DensityFunction blendOffset() {
        return OTGDensityFunctions.BlendOffset.INSTANCE;
    }

    public static DensityFunction lerp(DensityFunction deltaFunction, DensityFunction minFunction, DensityFunction maxFunction) {
        if (minFunction instanceof OTGDensityFunctions.Constant constant) {
            return lerp(deltaFunction, constant.value, maxFunction);
        } else {
            DensityFunction densityFunction = cacheOnce(deltaFunction);
            DensityFunction densityFunction1 = add(mul(densityFunction, constant((double)-1.0F)), constant((double)1.0F));
            return add(mul(minFunction, densityFunction1), mul(maxFunction, densityFunction));
        }
    }

    public static DensityFunction lerp(DensityFunction deltaFunction, double min, DensityFunction maxFunction) {
        return add(mul(deltaFunction, add(maxFunction, constant(-min))), constant(min));
    }

    static {
        CODEC = BuiltInRegistries.DENSITY_FUNCTION_TYPE.byNameCodec().dispatch((densityFunction) -> densityFunction.codec().codec(), Function.identity());
        NOISE_VALUE_CODEC = Codec.doubleRange((double)-1000000.0F, (double)1000000.0F);
        DIRECT_CODEC = Codec.either(NOISE_VALUE_CODEC, CODEC).xmap((either) -> (DensityFunction)either.map(OTGDensityFunctions::constant, Function.identity()), (densityFunction) -> {
            Either var10000;
            if (densityFunction instanceof OTGDensityFunctions.Constant constant) {
                var10000 = Either.left(constant.value());
            } else {
                var10000 = Either.right(densityFunction);
            }

            return var10000;
        });
    }

    static record Ap2(OTGDensityFunctions.TwoArgumentSimpleFunction.Type type, DensityFunction argument1, DensityFunction argument2, double minValue, double maxValue) implements OTGDensityFunctions.TwoArgumentSimpleFunction {
        public double compute(DensityFunction.FunctionContext context) {
            double d = this.argument1.compute(context);
            double var10000;
            switch (this.type.ordinal()) {
                case 0 -> var10000 = d + this.argument2.compute(context);
                case 1 -> var10000 = d == (double)0.0F ? (double)0.0F : d * this.argument2.compute(context);
                case 2 -> var10000 = d < this.argument2.minValue() ? d : Math.min(d, this.argument2.compute(context));
                case 3 -> var10000 = d > this.argument2.maxValue() ? d : Math.max(d, this.argument2.compute(context));
                default -> throw new MatchException((String)null, (Throwable)null);
            }

            return var10000;
        }

        public void fillArray(double[] array, DensityFunction.ContextProvider contextProvider) {
            this.argument1.fillArray(array, contextProvider);
            switch (this.type.ordinal()) {
                case 0:
                    double[] doubles = new double[array.length];
                    this.argument2.fillArray(doubles, contextProvider);

                    for(int i = 0; i < array.length; ++i) {
                        array[i] += doubles[i];
                    }
                    break;
                case 1:
                    for(int i1 = 0; i1 < array.length; ++i1) {
                        double d = array[i1];
                        array[i1] = d == (double)0.0F ? (double)0.0F : d * this.argument2.compute(contextProvider.forIndex(i1));
                    }
                    break;
                case 2:
                    double d1 = this.argument2.minValue();

                    for(int i2 = 0; i2 < array.length; ++i2) {
                        double d2 = array[i2];
                        array[i2] = d2 < d1 ? d2 : Math.min(d2, this.argument2.compute(contextProvider.forIndex(i2)));
                    }
                    break;
                case 3:
                    double d0 = this.argument2.maxValue();

                    for(int i2 = 0; i2 < array.length; ++i2) {
                        double d2 = array[i2];
                        array[i2] = d2 > d0 ? d2 : Math.max(d2, this.argument2.compute(contextProvider.forIndex(i2)));
                    }
            }

        }

        public DensityFunction mapAll(DensityFunction.Visitor visitor) {
            return visitor.apply(OTGDensityFunctions.TwoArgumentSimpleFunction.create(this.type, this.argument1.mapAll(visitor), this.argument2.mapAll(visitor)));
        }
    }

    protected static enum BeardifierMarker implements OTGDensityFunctions.BeardifierOrMarker {
        INSTANCE;

        public double compute(DensityFunction.FunctionContext context) {
            return (double)0.0F;
        }

        public void fillArray(double[] array, DensityFunction.ContextProvider contextProvider) {
            Arrays.fill(array, (double)0.0F);
        }

        public double minValue() {
            return (double)0.0F;
        }

        public double maxValue() {
            return (double)0.0F;
        }
    }

    public interface BeardifierOrMarker extends DensityFunction.SimpleFunction {
        KeyDispatchDataCodec<DensityFunction> CODEC = KeyDispatchDataCodec.of(MapCodec.unit(OTGDensityFunctions.BeardifierMarker.INSTANCE));

        default KeyDispatchDataCodec<? extends DensityFunction> codec() {
            return CODEC;
        }
    }

    protected static enum BlendAlpha implements DensityFunction.SimpleFunction {
        INSTANCE;

        public static final KeyDispatchDataCodec<DensityFunction> CODEC = KeyDispatchDataCodec.of(MapCodec.unit(INSTANCE));

        public double compute(DensityFunction.FunctionContext context) {
            return (double)1.0F;
        }

        public void fillArray(double[] array, DensityFunction.ContextProvider contextProvider) {
            Arrays.fill(array, (double)1.0F);
        }

        public double minValue() {
            return (double)1.0F;
        }

        public double maxValue() {
            return (double)1.0F;
        }

        public KeyDispatchDataCodec<? extends DensityFunction> codec() {
            return CODEC;
        }
    }

    static record BlendDensity(DensityFunction input) implements OTGDensityFunctions.TransformerWithContext {
        static final KeyDispatchDataCodec<OTGDensityFunctions.BlendDensity> CODEC = OTGDensityFunctions.<OTGDensityFunctions.BlendDensity>singleFunctionArgumentCodec(OTGDensityFunctions.BlendDensity::new, OTGDensityFunctions.BlendDensity::input);

        public double transform(DensityFunction.FunctionContext context, double value) {
            return context.getBlender().blendDensity(context, value);
        }

        public DensityFunction mapAll(DensityFunction.Visitor visitor) {
            return visitor.apply(new OTGDensityFunctions.BlendDensity(this.input.mapAll(visitor)));
        }

        public double minValue() {
            return Double.NEGATIVE_INFINITY;
        }

        public double maxValue() {
            return Double.POSITIVE_INFINITY;
        }

        public KeyDispatchDataCodec<? extends DensityFunction> codec() {
            return CODEC;
        }
    }

    protected static enum BlendOffset implements DensityFunction.SimpleFunction {
        INSTANCE;

        public static final KeyDispatchDataCodec<DensityFunction> CODEC = KeyDispatchDataCodec.of(MapCodec.unit(INSTANCE));

        public double compute(DensityFunction.FunctionContext context) {
            return (double)0.0F;
        }

        public void fillArray(double[] array, DensityFunction.ContextProvider contextProvider) {
            Arrays.fill(array, (double)0.0F);
        }

        public double minValue() {
            return (double)0.0F;
        }

        public double maxValue() {
            return (double)0.0F;
        }

        public KeyDispatchDataCodec<? extends DensityFunction> codec() {
            return CODEC;
        }
    }

    protected static record Clamp(DensityFunction input, double minValue, double maxValue) implements OTGDensityFunctions.PureTransformer {
        private static final MapCodec<OTGDensityFunctions.Clamp> DATA_CODEC = RecordCodecBuilder.mapCodec((instance) -> instance.group(DensityFunction.DIRECT_CODEC.fieldOf("input").forGetter(OTGDensityFunctions.Clamp::input), OTGDensityFunctions.NOISE_VALUE_CODEC.fieldOf("min").forGetter(OTGDensityFunctions.Clamp::minValue), OTGDensityFunctions.NOISE_VALUE_CODEC.fieldOf("max").forGetter(OTGDensityFunctions.Clamp::maxValue)).apply(instance, OTGDensityFunctions.Clamp::new));
        public static final KeyDispatchDataCodec<OTGDensityFunctions.Clamp> CODEC;

        public double transform(double value) {
            return Mth.clamp(value, this.minValue, this.maxValue);
        }

        public DensityFunction mapAll(DensityFunction.Visitor visitor) {
            return new OTGDensityFunctions.Clamp(this.input.mapAll(visitor), this.minValue, this.maxValue);
        }

        public KeyDispatchDataCodec<? extends DensityFunction> codec() {
            return CODEC;
        }

        static {
            CODEC = KeyDispatchDataCodec.of(DATA_CODEC);
        }
    }

    static record Constant(double value) implements DensityFunction.SimpleFunction {
        static final KeyDispatchDataCodec<OTGDensityFunctions.Constant> CODEC;
        static final OTGDensityFunctions.Constant ZERO;

        public double compute(DensityFunction.FunctionContext context) {
            return this.value;
        }

        public void fillArray(double[] array, DensityFunction.ContextProvider contextProvider) {
            Arrays.fill(array, this.value);
        }

        public double minValue() {
            return this.value;
        }

        public double maxValue() {
            return this.value;
        }

        public KeyDispatchDataCodec<? extends DensityFunction> codec() {
            return CODEC;
        }

        static {
            CODEC = OTGDensityFunctions.singleArgumentCodec(OTGDensityFunctions.NOISE_VALUE_CODEC, OTGDensityFunctions.Constant::new, OTGDensityFunctions.Constant::value);
            ZERO = new OTGDensityFunctions.Constant((double)0.0F);
        }
    }

    protected static final class EndIslandDensityFunction implements DensityFunction.SimpleFunction {
        public static final KeyDispatchDataCodec<OTGDensityFunctions.EndIslandDensityFunction> CODEC = KeyDispatchDataCodec.of(MapCodec.unit(new OTGDensityFunctions.EndIslandDensityFunction(0L)));
        private static final float ISLAND_THRESHOLD = -0.9F;
        private final SimplexNoise islandNoise;
        private static final ThreadLocal<Map<SimplexNoise, OTGDensityFunctions.EndIslandDensityFunction.NoiseCache>> noiseCache = ThreadLocal.withInitial(WeakHashMap::new);

        public EndIslandDensityFunction(long seed) {
            RandomSource randomSource = new LegacyRandomSource(seed);
            randomSource.consumeCount(17292);
            this.islandNoise = new SimplexNoise(randomSource);
        }

        private static float getHeightValue(SimplexNoise noise, int x, int z) {
            int i = x / 2;
            int i1 = z / 2;
            int i2 = x % 2;
            int i3 = z % 2;
            float f = 100.0F - Mth.sqrt((float)((long)x * (long)x + (long)z * (long)z)) * 8.0F;
            f = Mth.clamp(f, -100.0F, 80.0F);
            OTGDensityFunctions.EndIslandDensityFunction.NoiseCache cache = (OTGDensityFunctions.EndIslandDensityFunction.NoiseCache)((Map)noiseCache.get()).computeIfAbsent(noise, (noiseKey) -> new OTGDensityFunctions.EndIslandDensityFunction.NoiseCache());

            for(int i4 = -12; i4 <= 12; ++i4) {
                for(int i5 = -12; i5 <= 12; ++i5) {
                    long l = (long)(i + i4);
                    int chunkX = (int)l;
                    long l1 = (long)(i1 + i5);
                    int chunkZ = (int)l1;
                    long chunkKey = ChunkPos.asLong(chunkX, chunkZ);
                    int cacheIndex = (int) HashCommon.mix(chunkKey) & 8191;
                    float f1 = Float.MIN_VALUE;
                    if (cache.keys[cacheIndex] == chunkKey) {
                        f1 = cache.values[cacheIndex];
                    } else {
                        if (l * l + l1 * l1 > 4096L && noise.getValue((double)l, (double)l1) < (double)-0.9F) {
                            f1 = (Mth.abs((float)l) * 3439.0F + Mth.abs((float)l1) * 147.0F) % 13.0F + 9.0F;
                        }

                        cache.keys[cacheIndex] = chunkKey;
                        cache.values[cacheIndex] = f1;
                    }

                    if (f1 != Float.MIN_VALUE) {
                        float f2 = (float)(i2 - i4 * 2);
                        float f3 = (float)(i3 - i5 * 2);
                        float f4 = 100.0F - Mth.sqrt(f2 * f2 + f3 * f3) * f1;
                        f4 = Mth.clamp(f4, -100.0F, 80.0F);
                        f = Math.max(f, f4);
                    }
                }
            }

            return f;
        }

        public double compute(DensityFunction.FunctionContext context) {
            return ((double)getHeightValue(this.islandNoise, context.blockX() / 8, context.blockZ() / 8) - (double)8.0F) / (double)128.0F;
        }

        public double minValue() {
            return (double)-0.84375F;
        }

        public double maxValue() {
            return (double)0.5625F;
        }

        public KeyDispatchDataCodec<? extends DensityFunction> codec() {
            return CODEC;
        }

        private static final class NoiseCache {
            public long[] keys = new long[8192];
            public float[] values = new float[8192];

            public NoiseCache() {
                Arrays.fill(this.keys, Long.MIN_VALUE);
            }
        }
    }

    @VisibleForDebug
    public static record HolderHolder(Holder<DensityFunction> function) implements DensityFunction {
        public double compute(DensityFunction.FunctionContext context) {
            return ((DensityFunction)this.function.value()).compute(context);
        }

        public void fillArray(double[] array, DensityFunction.ContextProvider contextProvider) {
            ((DensityFunction)this.function.value()).fillArray(array, contextProvider);
        }

        public DensityFunction mapAll(DensityFunction.Visitor visitor) {
            return visitor.apply(new OTGDensityFunctions.HolderHolder(new Holder.Direct(((DensityFunction)this.function.value()).mapAll(visitor))));
        }

        public double minValue() {
            return this.function.isBound() ? ((DensityFunction)this.function.value()).minValue() : Double.NEGATIVE_INFINITY;
        }

        public double maxValue() {
            return this.function.isBound() ? ((DensityFunction)this.function.value()).maxValue() : Double.POSITIVE_INFINITY;
        }

        public KeyDispatchDataCodec<? extends DensityFunction> codec() {
            throw new UnsupportedOperationException("Calling .codec() on HolderHolder");
        }
    }

    protected static record Mapped(OTGDensityFunctions.Mapped.Type type, DensityFunction input, double minValue, double maxValue) implements OTGDensityFunctions.PureTransformer {
        public static OTGDensityFunctions.Mapped create(OTGDensityFunctions.Mapped.Type type, DensityFunction input) {
            double d = input.minValue();
            double d1 = transform(type, d);
            double d2 = transform(type, input.maxValue());
            return type != OTGDensityFunctions.Mapped.Type.ABS && type != OTGDensityFunctions.Mapped.Type.SQUARE ? new OTGDensityFunctions.Mapped(type, input, d1, d2) : new OTGDensityFunctions.Mapped(type, input, Math.max((double)0.0F, d), Math.max(d1, d2));
        }

        private static double transform(OTGDensityFunctions.Mapped.Type type, double value) {
            double var10000;
            switch (type.ordinal()) {
                case 0:
                    var10000 = Math.abs(value);
                    break;
                case 1:
                    var10000 = value * value;
                    break;
                case 2:
                    var10000 = value * value * value;
                    break;
                case 3:
                    var10000 = value > (double)0.0F ? value : value * (double)0.5F;
                    break;
                case 4:
                    var10000 = value > (double)0.0F ? value : value * (double)0.25F;
                    break;
                case 5:
                    double d = Mth.clamp(value, (double)-1.0F, (double)1.0F);
                    var10000 = d / (double)2.0F - d * d * d / (double)24.0F;
                    break;
                default:
                    throw new MatchException((String)null, (Throwable)null);
            }

            return var10000;
        }

        public double transform(double value) {
            return transform(this.type, value);
        }

        public OTGDensityFunctions.Mapped mapAll(DensityFunction.Visitor visitor) {
            return create(this.type, this.input.mapAll(visitor));
        }

        public KeyDispatchDataCodec<? extends DensityFunction> codec() {
            return this.type.codec;
        }

        static enum Type implements StringRepresentable {
            ABS("abs"),
            SQUARE("square"),
            CUBE("cube"),
            HALF_NEGATIVE("half_negative"),
            QUARTER_NEGATIVE("quarter_negative"),
            SQUEEZE("squeeze");

            private final String name;
            final KeyDispatchDataCodec<OTGDensityFunctions.Mapped> codec = OTGDensityFunctions.<OTGDensityFunctions.Mapped>singleFunctionArgumentCodec((input) -> OTGDensityFunctions.Mapped.create(this, input), OTGDensityFunctions.Mapped::input);

            private Type(final String name) {
                this.name = name;
            }

            public String getSerializedName() {
                return this.name;
            }
        }
    }

    protected static record Marker(OTGDensityFunctions.Marker.Type type, DensityFunction wrapped) implements OTGDensityFunctions.MarkerOrMarked {
        public double compute(DensityFunction.FunctionContext context) {
            return this.wrapped.compute(context);
        }

        public void fillArray(double[] array, DensityFunction.ContextProvider contextProvider) {
            this.wrapped.fillArray(array, contextProvider);
        }

        public double minValue() {
            return this.wrapped.minValue();
        }

        public double maxValue() {
            return this.wrapped.maxValue();
        }

        static enum Type implements StringRepresentable {
            Interpolated("interpolated"),
            FlatCache("flat_cache"),
            Cache2D("cache_2d"),
            CacheOnce("cache_once"),
            CacheAllInCell("cache_all_in_cell");

            private final String name;
            final KeyDispatchDataCodec<OTGDensityFunctions.MarkerOrMarked> codec = OTGDensityFunctions.<OTGDensityFunctions.MarkerOrMarked>singleFunctionArgumentCodec((function) -> new OTGDensityFunctions.Marker(this, function), OTGDensityFunctions.MarkerOrMarked::wrapped);

            private Type(final String name) {
                this.name = name;
            }

            public String getSerializedName() {
                return this.name;
            }
        }
    }

    public interface MarkerOrMarked extends DensityFunction {
        OTGDensityFunctions.Marker.Type type();

        DensityFunction wrapped();

        default KeyDispatchDataCodec<? extends DensityFunction> codec() {
            return this.type().codec;
        }

        default DensityFunction mapAll(DensityFunction.Visitor visitor) {
            return visitor.apply(new OTGDensityFunctions.Marker(this.type(), this.wrapped().mapAll(visitor)));
        }
    }

    static record MulOrAdd(OTGDensityFunctions.MulOrAdd.Type specificType, DensityFunction input, double minValue, double maxValue, double argument) implements OTGDensityFunctions.PureTransformer, OTGDensityFunctions.TwoArgumentSimpleFunction {
        public OTGDensityFunctions.TwoArgumentSimpleFunction.Type type() {
            return this.specificType == OTGDensityFunctions.MulOrAdd.Type.MUL ? OTGDensityFunctions.TwoArgumentSimpleFunction.Type.MUL : OTGDensityFunctions.TwoArgumentSimpleFunction.Type.ADD;
        }

        public DensityFunction argument1() {
            return OTGDensityFunctions.constant(this.argument);
        }

        public DensityFunction argument2() {
            return this.input;
        }

        public double transform(double value) {
            double var10000;
            switch (this.specificType.ordinal()) {
                case 0 -> var10000 = value * this.argument;
                case 1 -> var10000 = value + this.argument;
                default -> throw new MatchException((String)null, (Throwable)null);
            }

            return var10000;
        }

        public DensityFunction mapAll(DensityFunction.Visitor visitor) {
            DensityFunction densityFunction = this.input.mapAll(visitor);
            double d = densityFunction.minValue();
            double d1 = densityFunction.maxValue();
            double d2;
            double d3;
            if (this.specificType == OTGDensityFunctions.MulOrAdd.Type.ADD) {
                d2 = d + this.argument;
                d3 = d1 + this.argument;
            } else if (this.argument >= (double)0.0F) {
                d2 = d * this.argument;
                d3 = d1 * this.argument;
            } else {
                d2 = d1 * this.argument;
                d3 = d * this.argument;
            }

            return new OTGDensityFunctions.MulOrAdd(this.specificType, densityFunction, d2, d3, this.argument);
        }

        static enum Type {
            MUL,
            ADD;
        }
    }

    protected static record Noise(DensityFunction.NoiseHolder noise, double xzScale, double yScale) implements DensityFunction {
        public static final MapCodec<OTGDensityFunctions.Noise> DATA_CODEC = RecordCodecBuilder.mapCodec((instance) -> instance.group(NoiseHolder.CODEC.fieldOf("noise").forGetter(OTGDensityFunctions.Noise::noise), Codec.DOUBLE.fieldOf("xz_scale").forGetter(OTGDensityFunctions.Noise::xzScale), Codec.DOUBLE.fieldOf("y_scale").forGetter(OTGDensityFunctions.Noise::yScale)).apply(instance, OTGDensityFunctions.Noise::new));
        public static final KeyDispatchDataCodec<OTGDensityFunctions.Noise> CODEC;

        protected Noise(DensityFunction.NoiseHolder noise, @Deprecated double xzScale, double yScale) {
            this.noise = noise;
            this.xzScale = xzScale;
            this.yScale = yScale;
        }

        public double compute(DensityFunction.FunctionContext context) {
            return this.noise.getValue((double)context.blockX() * this.xzScale, (double)context.blockY() * this.yScale, (double)context.blockZ() * this.xzScale);
        }

        public void fillArray(double[] array, DensityFunction.ContextProvider contextProvider) {
            contextProvider.fillAllDirectly(array, this);
        }

        public DensityFunction mapAll(DensityFunction.Visitor visitor) {
            return visitor.apply(new OTGDensityFunctions.Noise(visitor.visitNoise(this.noise), this.xzScale, this.yScale));
        }

        public double minValue() {
            return -this.maxValue();
        }

        public double maxValue() {
            return this.noise.maxValue();
        }

        public KeyDispatchDataCodec<? extends DensityFunction> codec() {
            return CODEC;
        }

        /** @deprecated */
        @Deprecated
        public double xzScale() {
            return this.xzScale;
        }

        static {
            CODEC = KeyDispatchDataCodec.of(DATA_CODEC);
        }
    }

    interface PureTransformer extends DensityFunction {
        DensityFunction input();

        default double compute(DensityFunction.FunctionContext context) {
            return this.transform(this.input().compute(context));
        }

        default void fillArray(double[] array, DensityFunction.ContextProvider contextProvider) {
            this.input().fillArray(array, contextProvider);

            for(int i = 0; i < array.length; ++i) {
                array[i] = this.transform(array[i]);
            }

        }

        double transform(double var1);
    }

    static record RangeChoice(DensityFunction input, double minInclusive, double maxExclusive, DensityFunction whenInRange, DensityFunction whenOutOfRange) implements DensityFunction {
        public static final MapCodec<OTGDensityFunctions.RangeChoice> DATA_CODEC = RecordCodecBuilder.mapCodec((instance) -> instance.group(DensityFunction.HOLDER_HELPER_CODEC.fieldOf("input").forGetter(OTGDensityFunctions.RangeChoice::input), OTGDensityFunctions.NOISE_VALUE_CODEC.fieldOf("min_inclusive").forGetter(OTGDensityFunctions.RangeChoice::minInclusive), OTGDensityFunctions.NOISE_VALUE_CODEC.fieldOf("max_exclusive").forGetter(OTGDensityFunctions.RangeChoice::maxExclusive), DensityFunction.HOLDER_HELPER_CODEC.fieldOf("when_in_range").forGetter(OTGDensityFunctions.RangeChoice::whenInRange), DensityFunction.HOLDER_HELPER_CODEC.fieldOf("when_out_of_range").forGetter(OTGDensityFunctions.RangeChoice::whenOutOfRange)).apply(instance, OTGDensityFunctions.RangeChoice::new));
        public static final KeyDispatchDataCodec<OTGDensityFunctions.RangeChoice> CODEC;

        public double compute(DensityFunction.FunctionContext context) {
            double d = this.input.compute(context);
            return d >= this.minInclusive && d < this.maxExclusive ? this.whenInRange.compute(context) : this.whenOutOfRange.compute(context);
        }

        public void fillArray(double[] array, DensityFunction.ContextProvider contextProvider) {
            this.input.fillArray(array, contextProvider);

            for(int i = 0; i < array.length; ++i) {
                double d = array[i];
                if (d >= this.minInclusive && d < this.maxExclusive) {
                    array[i] = this.whenInRange.compute(contextProvider.forIndex(i));
                } else {
                    array[i] = this.whenOutOfRange.compute(contextProvider.forIndex(i));
                }
            }

        }

        public DensityFunction mapAll(DensityFunction.Visitor visitor) {
            return visitor.apply(new OTGDensityFunctions.RangeChoice(this.input.mapAll(visitor), this.minInclusive, this.maxExclusive, this.whenInRange.mapAll(visitor), this.whenOutOfRange.mapAll(visitor)));
        }

        public double minValue() {
            return Math.min(this.whenInRange.minValue(), this.whenOutOfRange.minValue());
        }

        public double maxValue() {
            return Math.max(this.whenInRange.maxValue(), this.whenOutOfRange.maxValue());
        }

        public KeyDispatchDataCodec<? extends DensityFunction> codec() {
            return CODEC;
        }

        static {
            CODEC = KeyDispatchDataCodec.of(DATA_CODEC);
        }
    }

    protected static record Shift(DensityFunction.NoiseHolder offsetNoise) implements OTGDensityFunctions.ShiftNoise {
        static final KeyDispatchDataCodec<OTGDensityFunctions.Shift> CODEC;

        public double compute(DensityFunction.FunctionContext context) {
            return this.compute((double)context.blockX(), (double)context.blockY(), (double)context.blockZ());
        }

        public DensityFunction mapAll(DensityFunction.Visitor visitor) {
            return visitor.apply(new OTGDensityFunctions.Shift(visitor.visitNoise(this.offsetNoise)));
        }

        public KeyDispatchDataCodec<? extends DensityFunction> codec() {
            return CODEC;
        }

        static {
            CODEC = OTGDensityFunctions.singleArgumentCodec(NoiseHolder.CODEC, OTGDensityFunctions.Shift::new, OTGDensityFunctions.Shift::offsetNoise);
        }
    }

    protected static record ShiftA(DensityFunction.NoiseHolder offsetNoise) implements OTGDensityFunctions.ShiftNoise {
        static final KeyDispatchDataCodec<OTGDensityFunctions.ShiftA> CODEC;

        public double compute(DensityFunction.FunctionContext context) {
            return this.compute((double)context.blockX(), (double)0.0F, (double)context.blockZ());
        }

        public DensityFunction mapAll(DensityFunction.Visitor visitor) {
            return visitor.apply(new OTGDensityFunctions.ShiftA(visitor.visitNoise(this.offsetNoise)));
        }

        public KeyDispatchDataCodec<? extends DensityFunction> codec() {
            return CODEC;
        }

        static {
            CODEC = OTGDensityFunctions.singleArgumentCodec(NoiseHolder.CODEC, OTGDensityFunctions.ShiftA::new, OTGDensityFunctions.ShiftA::offsetNoise);
        }
    }

    protected static record ShiftB(DensityFunction.NoiseHolder offsetNoise) implements OTGDensityFunctions.ShiftNoise {
        static final KeyDispatchDataCodec<OTGDensityFunctions.ShiftB> CODEC;

        public double compute(DensityFunction.FunctionContext context) {
            return this.compute((double)context.blockZ(), (double)context.blockX(), (double)0.0F);
        }

        public DensityFunction mapAll(DensityFunction.Visitor visitor) {
            return visitor.apply(new OTGDensityFunctions.ShiftB(visitor.visitNoise(this.offsetNoise)));
        }

        public KeyDispatchDataCodec<? extends DensityFunction> codec() {
            return CODEC;
        }

        static {
            CODEC = OTGDensityFunctions.singleArgumentCodec(NoiseHolder.CODEC, OTGDensityFunctions.ShiftB::new, OTGDensityFunctions.ShiftB::offsetNoise);
        }
    }

    interface ShiftNoise extends DensityFunction {
        DensityFunction.NoiseHolder offsetNoise();

        default double minValue() {
            return -this.maxValue();
        }

        default double maxValue() {
            return this.offsetNoise().maxValue() * (double)4.0F;
        }

        default double compute(double x, double y, double z) {
            return this.offsetNoise().getValue(x * (double)0.25F, y * (double)0.25F, z * (double)0.25F) * (double)4.0F;
        }

        default void fillArray(double[] array, DensityFunction.ContextProvider contextProvider) {
            contextProvider.fillAllDirectly(array, this);
        }
    }

    protected static record ShiftedNoise(DensityFunction shiftX, DensityFunction shiftY, DensityFunction shiftZ, double xzScale, double yScale, DensityFunction.NoiseHolder noise) implements DensityFunction {
        private static final MapCodec<OTGDensityFunctions.ShiftedNoise> DATA_CODEC = RecordCodecBuilder.mapCodec((instance) -> instance.group(DensityFunction.HOLDER_HELPER_CODEC.fieldOf("shift_x").forGetter(OTGDensityFunctions.ShiftedNoise::shiftX), DensityFunction.HOLDER_HELPER_CODEC.fieldOf("shift_y").forGetter(OTGDensityFunctions.ShiftedNoise::shiftY), DensityFunction.HOLDER_HELPER_CODEC.fieldOf("shift_z").forGetter(OTGDensityFunctions.ShiftedNoise::shiftZ), Codec.DOUBLE.fieldOf("xz_scale").forGetter(OTGDensityFunctions.ShiftedNoise::xzScale), Codec.DOUBLE.fieldOf("y_scale").forGetter(OTGDensityFunctions.ShiftedNoise::yScale), NoiseHolder.CODEC.fieldOf("noise").forGetter(OTGDensityFunctions.ShiftedNoise::noise)).apply(instance, OTGDensityFunctions.ShiftedNoise::new));
        public static final KeyDispatchDataCodec<OTGDensityFunctions.ShiftedNoise> CODEC;

        public double compute(DensityFunction.FunctionContext context) {
            double d = (double)context.blockX() * this.xzScale + this.shiftX.compute(context);
            double d1 = (double)context.blockY() * this.yScale + this.shiftY.compute(context);
            double d2 = (double)context.blockZ() * this.xzScale + this.shiftZ.compute(context);
            return this.noise.getValue(d, d1, d2);
        }

        public void fillArray(double[] array, DensityFunction.ContextProvider contextProvider) {
            contextProvider.fillAllDirectly(array, this);
        }

        public DensityFunction mapAll(DensityFunction.Visitor visitor) {
            return visitor.apply(new OTGDensityFunctions.ShiftedNoise(this.shiftX.mapAll(visitor), this.shiftY.mapAll(visitor), this.shiftZ.mapAll(visitor), this.xzScale, this.yScale, visitor.visitNoise(this.noise)));
        }

        public double minValue() {
            return -this.maxValue();
        }

        public double maxValue() {
            return this.noise.maxValue();
        }

        public KeyDispatchDataCodec<? extends DensityFunction> codec() {
            return CODEC;
        }

        static {
            CODEC = KeyDispatchDataCodec.of(DATA_CODEC);
        }
    }

    public static record Spline(CubicSpline<OTGDensityFunctions.Spline.Point, OTGDensityFunctions.Spline.Coordinate> spline) implements DensityFunction {
        private static final Codec<CubicSpline<OTGDensityFunctions.Spline.Point, OTGDensityFunctions.Spline.Coordinate>> SPLINE_CODEC;
        private static final MapCodec<OTGDensityFunctions.Spline> DATA_CODEC;
        public static final KeyDispatchDataCodec<OTGDensityFunctions.Spline> CODEC;

        public double compute(DensityFunction.FunctionContext context) {
            return (double)this.spline.apply(new OTGDensityFunctions.Spline.Point(context));
        }

        public double minValue() {
            return (double)this.spline.minValue();
        }

        public double maxValue() {
            return (double)this.spline.maxValue();
        }

        public void fillArray(double[] array, DensityFunction.ContextProvider contextProvider) {
            contextProvider.fillAllDirectly(array, this);
        }

        public DensityFunction mapAll(DensityFunction.Visitor visitor) {
            return visitor.apply(new OTGDensityFunctions.Spline(this.spline.mapAll((coordinate) -> coordinate.mapAll(visitor))));
        }

        public KeyDispatchDataCodec<? extends DensityFunction> codec() {
            return CODEC;
        }

        static {
            SPLINE_CODEC = CubicSpline.codec(OTGDensityFunctions.Spline.Coordinate.CODEC);
            DATA_CODEC = SPLINE_CODEC.fieldOf("spline").xmap(OTGDensityFunctions.Spline::new, OTGDensityFunctions.Spline::spline);
            CODEC = KeyDispatchDataCodec.of(DATA_CODEC);
        }

        public static record Coordinate(Holder<DensityFunction> function) implements ToFloatFunction<OTGDensityFunctions.Spline.Point> {
            public static final Codec<OTGDensityFunctions.Spline.Coordinate> CODEC;

            public String toString() {
                Optional<ResourceKey<DensityFunction>> optional = this.function.unwrapKey();
                if (optional.isPresent()) {
                    ResourceKey<DensityFunction> resourceKey = (ResourceKey)optional.get();
                    if (resourceKey == NoiseRouterData.CONTINENTS) {
                        return "continents";
                    }

                    if (resourceKey == NoiseRouterData.EROSION) {
                        return "erosion";
                    }

                    if (resourceKey == NoiseRouterData.RIDGES) {
                        return "weirdness";
                    }

                    if (resourceKey == NoiseRouterData.RIDGES_FOLDED) {
                        return "ridges";
                    }
                }

                return "Coordinate[" + String.valueOf(this.function) + "]";
            }

            public float apply(OTGDensityFunctions.Spline.Point object) {
                return (float)((DensityFunction)this.function.value()).compute(object.context());
            }

            public float minValue() {
                return this.function.isBound() ? (float)((DensityFunction)this.function.value()).minValue() : Float.NEGATIVE_INFINITY;
            }

            public float maxValue() {
                return this.function.isBound() ? (float)((DensityFunction)this.function.value()).maxValue() : Float.POSITIVE_INFINITY;
            }

            public OTGDensityFunctions.Spline.Coordinate mapAll(DensityFunction.Visitor visitor) {
                return new OTGDensityFunctions.Spline.Coordinate(new Holder.Direct(((DensityFunction)this.function.value()).mapAll(visitor)));
            }

            static {
                CODEC = DensityFunction.CODEC.xmap(OTGDensityFunctions.Spline.Coordinate::new, OTGDensityFunctions.Spline.Coordinate::function);
            }
        }

        public static record Point(DensityFunction.FunctionContext context) {
        }
    }

    interface TransformerWithContext extends DensityFunction {
        DensityFunction input();

        default double compute(DensityFunction.FunctionContext context) {
            return this.transform(context, this.input().compute(context));
        }

        default void fillArray(double[] array, DensityFunction.ContextProvider contextProvider) {
            this.input().fillArray(array, contextProvider);

            for(int i = 0; i < array.length; ++i) {
                array[i] = this.transform(contextProvider.forIndex(i), array[i]);
            }

        }

        double transform(DensityFunction.FunctionContext var1, double var2);
    }

    interface TwoArgumentSimpleFunction extends DensityFunction {
        Logger LOGGER = LogUtils.getLogger();

        static OTGDensityFunctions.TwoArgumentSimpleFunction create(OTGDensityFunctions.TwoArgumentSimpleFunction.Type type, DensityFunction argument1, DensityFunction argument2) {
            double d = argument1.minValue();
            double d1 = argument2.minValue();
            double d2 = argument1.maxValue();
            double d3 = argument2.maxValue();
            if (type == OTGDensityFunctions.TwoArgumentSimpleFunction.Type.MIN || type == OTGDensityFunctions.TwoArgumentSimpleFunction.Type.MAX) {
                boolean flag = d >= d3;
                boolean flag1 = d1 >= d2;
                if (flag || flag1) {
                    Logger var10000 = LOGGER;
                    String var10001 = String.valueOf(type);
                    var10000.warn("Creating a " + var10001 + " function between two non-overlapping inputs: " + String.valueOf(argument1) + " and " + String.valueOf(argument2));
                }
            }

            double var18;
            switch (type.ordinal()) {
                case 0 -> var18 = d + d1;
                case 1 -> var18 = d > (double)0.0F && d1 > (double)0.0F ? d * d1 : (d2 < (double)0.0F && d3 < (double)0.0F ? d2 * d3 : Math.min(d * d3, d2 * d1));
                case 2 -> var18 = Math.min(d, d1);
                case 3 -> var18 = Math.max(d, d1);
                default -> throw new MatchException((String)null, (Throwable)null);
            }

            double d4 = var18;
            switch (type.ordinal()) {
                case 0 -> var18 = d2 + d3;
                case 1 -> var18 = d > (double)0.0F && d1 > (double)0.0F ? d2 * d3 : (d2 < (double)0.0F && d3 < (double)0.0F ? d * d1 : Math.max(d * d1, d2 * d3));
                case 2 -> var18 = Math.min(d2, d3);
                case 3 -> var18 = Math.max(d2, d3);
                default -> throw new MatchException((String)null, (Throwable)null);
            }

            double d5 = var18;
            if (type == OTGDensityFunctions.TwoArgumentSimpleFunction.Type.MUL || type == OTGDensityFunctions.TwoArgumentSimpleFunction.Type.ADD) {
                if (argument1 instanceof OTGDensityFunctions.Constant) {
                    OTGDensityFunctions.Constant constant = (OTGDensityFunctions.Constant)argument1;
                    return new OTGDensityFunctions.MulOrAdd(type == OTGDensityFunctions.TwoArgumentSimpleFunction.Type.ADD ? OTGDensityFunctions.MulOrAdd.Type.ADD : OTGDensityFunctions.MulOrAdd.Type.MUL, argument2, d4, d5, constant.value);
                }

                if (argument2 instanceof OTGDensityFunctions.Constant) {
                    OTGDensityFunctions.Constant constant = (OTGDensityFunctions.Constant)argument2;
                    return new OTGDensityFunctions.MulOrAdd(type == OTGDensityFunctions.TwoArgumentSimpleFunction.Type.ADD ? OTGDensityFunctions.MulOrAdd.Type.ADD : OTGDensityFunctions.MulOrAdd.Type.MUL, argument1, d4, d5, constant.value);
                }
            }

            return new OTGDensityFunctions.Ap2(type, argument1, argument2, d4, d5);
        }

        OTGDensityFunctions.TwoArgumentSimpleFunction.Type type();

        DensityFunction argument1();

        DensityFunction argument2();

        default KeyDispatchDataCodec<? extends DensityFunction> codec() {
            return this.type().codec;
        }

        public static enum Type implements StringRepresentable {
            ADD("add"),
            MUL("mul"),
            MIN("min"),
            MAX("max");

            final KeyDispatchDataCodec<OTGDensityFunctions.TwoArgumentSimpleFunction> codec = OTGDensityFunctions.<OTGDensityFunctions.TwoArgumentSimpleFunction>doubleFunctionArgumentCodec((from, to) -> OTGDensityFunctions.TwoArgumentSimpleFunction.create(this, from, to), OTGDensityFunctions.TwoArgumentSimpleFunction::argument1, OTGDensityFunctions.TwoArgumentSimpleFunction::argument2);
            private final String name;

            private Type(final String name) {
                this.name = name;
            }

            public String getSerializedName() {
                return this.name;
            }
        }
    }

    protected static record WeirdScaledSampler(DensityFunction input, DensityFunction.NoiseHolder noise, OTGDensityFunctions.WeirdScaledSampler.RarityValueMapper rarityValueMapper) implements OTGDensityFunctions.TransformerWithContext {
        private static final MapCodec<OTGDensityFunctions.WeirdScaledSampler> DATA_CODEC = RecordCodecBuilder.mapCodec((instance) -> instance.group(DensityFunction.HOLDER_HELPER_CODEC.fieldOf("input").forGetter(OTGDensityFunctions.WeirdScaledSampler::input), NoiseHolder.CODEC.fieldOf("noise").forGetter(OTGDensityFunctions.WeirdScaledSampler::noise), OTGDensityFunctions.WeirdScaledSampler.RarityValueMapper.CODEC.fieldOf("rarity_value_mapper").forGetter(OTGDensityFunctions.WeirdScaledSampler::rarityValueMapper)).apply(instance, OTGDensityFunctions.WeirdScaledSampler::new));
        public static final KeyDispatchDataCodec<OTGDensityFunctions.WeirdScaledSampler> CODEC;

        public double transform(DensityFunction.FunctionContext context, double value) {
            double d = this.rarityValueMapper.mapper.get(value);
            return d * Math.abs(this.noise.getValue((double)context.blockX() / d, (double)context.blockY() / d, (double)context.blockZ() / d));
        }

        public DensityFunction mapAll(DensityFunction.Visitor visitor) {
            return visitor.apply(new OTGDensityFunctions.WeirdScaledSampler(this.input.mapAll(visitor), visitor.visitNoise(this.noise), this.rarityValueMapper));
        }

        public double minValue() {
            return (double)0.0F;
        }

        public double maxValue() {
            return this.rarityValueMapper.maxRarity * this.noise.maxValue();
        }

        public KeyDispatchDataCodec<? extends DensityFunction> codec() {
            return CODEC;
        }

        static {
            CODEC = KeyDispatchDataCodec.of(DATA_CODEC);
        }

        public static enum RarityValueMapper implements StringRepresentable {
            TYPE1("type_1", OTGNoiseRouterData.QuantizedSpaghettiRarity::getSpaghettiRarity3D, (double)2.0F),
            TYPE2("type_2", OTGNoiseRouterData.QuantizedSpaghettiRarity::getSphaghettiRarity2D, (double)3.0F);

            public static final Codec<OTGDensityFunctions.WeirdScaledSampler.RarityValueMapper> CODEC = StringRepresentable.fromEnum(OTGDensityFunctions.WeirdScaledSampler.RarityValueMapper::values);
            private final String name;
            final Double2DoubleFunction mapper;
            final double maxRarity;

            private RarityValueMapper(final String name, final Double2DoubleFunction mapper, final double maxRarity) {
                this.name = name;
                this.mapper = mapper;
                this.maxRarity = maxRarity;
            }

            public String getSerializedName() {
                return this.name;
            }
        }
    }

    static record YClampedGradient(int fromY, int toY, double fromValue, double toValue) implements DensityFunction.SimpleFunction {
        private static final MapCodec<OTGDensityFunctions.YClampedGradient> DATA_CODEC = RecordCodecBuilder.mapCodec((codec) -> codec.group(Codec.intRange(-4064, 4062).fieldOf("from_y").forGetter(OTGDensityFunctions.YClampedGradient::fromY), Codec.intRange(-4064, 4062).fieldOf("to_y").forGetter(OTGDensityFunctions.YClampedGradient::toY), OTGDensityFunctions.NOISE_VALUE_CODEC.fieldOf("from_value").forGetter(OTGDensityFunctions.YClampedGradient::fromValue), OTGDensityFunctions.NOISE_VALUE_CODEC.fieldOf("to_value").forGetter(OTGDensityFunctions.YClampedGradient::toValue)).apply(codec, OTGDensityFunctions.YClampedGradient::new));
        public static final KeyDispatchDataCodec<OTGDensityFunctions.YClampedGradient> CODEC;

        public double compute(DensityFunction.FunctionContext context) {
            return Mth.clampedMap((double)context.blockY(), (double)this.fromY, (double)this.toY, this.fromValue, this.toValue);
        }

        public double minValue() {
            return Math.min(this.fromValue, this.toValue);
        }

        public double maxValue() {
            return Math.max(this.fromValue, this.toValue);
        }

        public KeyDispatchDataCodec<? extends DensityFunction> codec() {
            return CODEC;
        }

        static {
            CODEC = KeyDispatchDataCodec.of(DATA_CODEC);
        }
    }
}
