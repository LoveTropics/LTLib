package com.lovetropics.lib.codec;

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2DoubleMap;
import it.unimi.dsi.fastutil.objects.Object2DoubleOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2FloatMap;
import it.unimi.dsi.fastutil.objects.Object2FloatOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import net.minecraft.SharedConstants;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.stateproviders.BlockStateProvider;
import net.minecraft.world.phys.Vec3;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.IntFunction;

public final class MoreCodecs {
    public static final Codec<ItemStack> ITEM_STACK = Codec.either(ItemStack.CODEC, BuiltInRegistries.ITEM.byNameCodec())
            .xmap(either -> either.map(Function.identity(), ItemStack::new), Either::left);

    public static final Codec<BlockState> BLOCK_STATE = Codec.either(BlockState.CODEC, BuiltInRegistries.BLOCK.byNameCodec())
            .xmap(either -> either.map(Function.identity(), Block::defaultBlockState), Either::left);

    public static final Codec<BlockStateProvider> BLOCK_STATE_PROVIDER = Codec.either(BlockStateProvider.CODEC, BLOCK_STATE)
            .xmap(either -> either.map(Function.identity(), BlockStateProvider::simple), Either::left);

    public static final Codec<net.minecraft.world.phys.AABB> AABB = RecordCodecBuilder.create(i -> i.group(
            Vec3.CODEC.fieldOf("start").forGetter(aabb -> new Vec3(aabb.minX, aabb.minY, aabb.minZ)),
            Vec3.CODEC.fieldOf("end").forGetter(aabb -> new Vec3(aabb.maxX, aabb.maxY, aabb.maxZ))
    ).apply(i, net.minecraft.world.phys.AABB::new));

    public static final Codec<Potion> POTION = BuiltInRegistries.POTION.byNameCodec();

    private static final Codec<MobEffectInstance> EFFECT_INSTANCE_RECORD = RecordCodecBuilder.create(i -> i.group(
            BuiltInRegistries.MOB_EFFECT.holderByNameCodec().fieldOf("type").forGetter(MobEffectInstance::getEffect),
            Codec.FLOAT.optionalFieldOf("seconds").forGetter(c -> c.isInfiniteDuration() ? Optional.empty() : Optional.of((float) c.getDuration() / SharedConstants.TICKS_PER_SECOND)),
            Codec.INT.fieldOf("amplifier").forGetter(MobEffectInstance::getAmplifier),
            Codec.BOOL.optionalFieldOf("ambient", false).forGetter(MobEffectInstance::isAmbient),
            Codec.BOOL.optionalFieldOf("particles", true).forGetter(MobEffectInstance::isVisible),
            Codec.BOOL.optionalFieldOf("show_icon", true).forGetter(MobEffectInstance::showIcon)
    ).apply(i, (type, seconds, amplifier, ambient, hideParticles, showIcon) -> {
        final int ticks = seconds.map(s -> Math.round(s * SharedConstants.TICKS_PER_SECOND)).orElse(MobEffectInstance.INFINITE_DURATION);
        return new MobEffectInstance(type, ticks, amplifier, ambient, hideParticles, showIcon);
    }));

    public static final Codec<MobEffectInstance> EFFECT_INSTANCE = Codec.either(POTION, EFFECT_INSTANCE_RECORD)
            .comapFlatMap(either -> either.map(potion -> {
                List<MobEffectInstance> effects = potion.getEffects();
                if (effects.size() == 1) {
                    return DataResult.success(effects.getFirst());
                } else {
                    return DataResult.error(() -> "Potion must have only 1 effect");
                }
            }, DataResult::success), Either::right);

    public static <T> MapCodec<T> inputOptionalFieldOf(Codec<T> codec, String name, T fallback) {
        return Codec.optionalField(name, codec, false).xmap(
                o -> o.orElse(fallback),
                Optional::of
        );
    }

    public static <T> Codec<T[]> arrayOrUnit(Codec<T> codec, IntFunction<T[]> factory) {
        return listToArray(listOrUnit(codec), factory);
    }

    /**
     * @deprecated Use {@link ExtraCodecs#compactListCodec(Codec)}
     */
    @Deprecated
    public static <T> Codec<List<T>> listOrUnit(Codec<T> codec) {
        return ExtraCodecs.compactListCodec(codec);
    }

    public static <T> Codec<T[]> listToArray(Codec<List<T>> codec, IntFunction<T[]> factory) {
        return codec.xmap(list -> list.toArray(factory.apply(0)), Arrays::asList);
    }

    public static <A> Codec<A> stringVariants(A[] values, Function<A, String> asName) {
        return keyedVariants(values, asName, Codec.STRING);
    }

    public static <A, K> Codec<A> keyedVariants(A[] values, Function<A, K> asKey, Codec<K> keyCodec) {
        Map<K, A> byKey = new Object2ObjectOpenHashMap<>();
        for (A value : values) {
            byKey.put(asKey.apply(value), value);
        }

        return keyCodec.comapFlatMap(key -> {
            A value = byKey.get(key);
            return value != null ? DataResult.success(value) : DataResult.error(() -> "No variant with key '" + key + "'");
        }, asKey);
    }

    public static <N extends Number> Codec<N> numberAsString(Function<String, N> parse) {
        return Codec.STRING.comapFlatMap(
                s -> {
                    try {
                        return DataResult.success(parse.apply(s));
                    } catch (NumberFormatException e) {
                        return DataResult.error(() -> "Failed to parse number '" + s + "'");
                    }
                },
                Object::toString
        );
    }

    public static <V> Codec<Long2ObjectMap<V>> long2Object(Codec<V> codec) {
        return Codec.unboundedMap(numberAsString(Long::parseLong), codec).xmap(Long2ObjectOpenHashMap::new, HashMap::new);
    }

    public static <K> Codec<Object2FloatMap<K>> object2Float(Codec<K> codec) {
        return Codec.unboundedMap(codec, Codec.FLOAT).xmap(Object2FloatOpenHashMap::new, HashMap::new);
    }

    public static <K> Codec<Object2DoubleMap<K>> object2Double(Codec<K> codec) {
        return Codec.unboundedMap(codec, Codec.DOUBLE).xmap(Object2DoubleOpenHashMap::new, HashMap::new);
    }

    public static <T> Codec<List<T>> sortedList(Codec<T> codec, Comparator<? super T> comparator) {
        return codec.listOf().xmap(
                list -> {
                    list = new ArrayList<>(list);
                    list.sort(comparator);
                    return List.copyOf(list);
                },
                Function.identity()
        );
    }

    public static Codec<LocalDateTime> localDateTime(DateTimeFormatter formatter) {
        return Codec.STRING.comapFlatMap(
                string -> {
                    try {
                        return DataResult.success(LocalDateTime.parse(string, formatter));
                    } catch (DateTimeParseException e) {
                        return DataResult.error(() -> "Failed to parse date: " + string);
                    }
                },
                formatter::format
        );
    }

    public static Codec<Instant> instantCodec(DateTimeFormatter formatter) {
        return MoreCodecs.localDateTime(formatter).xmap(
                localTime -> localTime.atOffset(ZoneOffset.UTC).toInstant(),
                instant -> instant.atOffset(ZoneOffset.UTC).toLocalDateTime()
        );
    }

    public static final Codec<Instant> TIME_CODEC = Codec.withAlternative(
            instantCodec(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSSSSS")),
            // Why can we receive this one too? No idea! But we get it now
            instantCodec(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
    );

    /**
     * @deprecated Use {@link Codec#withAlternative(Codec, Codec)}
     */
    @Deprecated
    public static <T> Codec<T> tryFirst(Codec<T> first, Codec<T> second) {
        return Codec.withAlternative(first, second);
    }
}
