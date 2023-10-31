package com.lovetropics.lib.codec;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.gson.JsonSyntaxException;
import com.mojang.datafixers.util.Either;
import com.mojang.datafixers.util.Pair;
import com.mojang.datafixers.util.Unit;
import com.mojang.serialization.*;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.*;
import net.minecraft.SharedConstants;
import net.minecraft.advancements.critereon.BlockPredicate;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.stateproviders.BlockStateProvider;
import net.minecraft.world.phys.Vec3;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.function.Function;
import java.util.function.IntFunction;
import java.util.stream.Stream;

public final class MoreCodecs {
    public static final Codec<ItemStack> ITEM_STACK = Codec.either(ItemStack.CODEC, BuiltInRegistries.ITEM.byNameCodec())
            .xmap(either -> either.map(Function.identity(), ItemStack::new), Either::left);

    public static final Codec<BlockState> BLOCK_STATE = Codec.either(BlockState.CODEC, BuiltInRegistries.BLOCK.byNameCodec())
            .xmap(either -> either.map(Function.identity(), Block::defaultBlockState), Either::left);

    public static final Codec<BlockStateProvider> BLOCK_STATE_PROVIDER = Codec.either(BlockStateProvider.CODEC, BLOCK_STATE)
            .xmap(either -> either.map(Function.identity(), BlockStateProvider::simple), Either::left);

    public static final Codec<EquipmentSlot> EQUIPMENT_SLOT = stringVariants(EquipmentSlot.values(), EquipmentSlot::getName);

    public static final Codec<BlockPredicate> BLOCK_PREDICATE = ExtraCodecs.JSON.comapFlatMap(json -> {
        try {
            return DataResult.success(BlockPredicate.fromJson(json));
        } catch (JsonSyntaxException e) {
            return DataResult.error(e::getMessage);
        }
    }, BlockPredicate::serializeToJson);

    public static final Codec<net.minecraft.world.phys.AABB> AABB = RecordCodecBuilder.create(i -> i.group(
            Vec3.CODEC.fieldOf("start").forGetter(aabb -> new Vec3(aabb.minX, aabb.minY, aabb.minZ)),
            Vec3.CODEC.fieldOf("end").forGetter(aabb -> new Vec3(aabb.maxX, aabb.maxY, aabb.maxZ))
    ).apply(i, net.minecraft.world.phys.AABB::new));

    public static final Codec<Potion> POTION = BuiltInRegistries.POTION.byNameCodec();

    private static final Codec<MobEffectInstance> EFFECT_INSTANCE_RECORD = RecordCodecBuilder.create(i -> i.group(
            BuiltInRegistries.MOB_EFFECT.byNameCodec().fieldOf("type").forGetter(MobEffectInstance::getEffect),
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
                    return DataResult.success(effects.get(0));
                } else {
                    return DataResult.error(() -> "Potion must have only 1 effect");
                }
            }, DataResult::success), Either::right);

    public static <T> MapCodec<T> inputOptionalFieldOf(Codec<T> codec, String name, T fallback) {
        return Codec.optionalField(name, codec).xmap(
                o -> o.orElse(fallback),
                Optional::of
        );
    }

    public static <T> Codec<T[]> arrayOrUnit(Codec<T> codec, IntFunction<T[]> factory) {
        return listToArray(listOrUnit(codec), factory);
    }

    public static <T> Codec<List<T>> listOrUnit(Codec<T> codec) {
        return Codec.either(codec.listOf(), codec)
                .xmap(
                        either -> either.map(Function.identity(), List::of),
                        list -> list.size() == 1 ? Either.right(list.get(0)) : Either.left(list)
                );
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

    @Deprecated
    public static <T, C extends List<T>> Codec<C> sorted(Codec<C> codec, Comparator<? super T> comparator) {
        return codec.xmap(
                list -> {
                    list.sort(comparator);
                    return list;
                },
                Function.identity()
        );
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

    public static <K, V> Codec<Map<K, V>> dispatchByMapKey(Codec<K> keyCodec, Function<K, Codec<V>> valueCodec) {
        return new DispatchMapCodec<>(keyCodec, valueCodec);
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

    public static <T> Codec<T> tryFirst(Codec<T> first, Codec<T> second) {
        return new TryFirstCodec<>(first, second);
    }

    record DispatchMapCodec<K, V>(Codec<K> keyCodec, Function<K, Codec<V>> valueCodec) implements Codec<Map<K, V>> {
        @Override
        public <T> DataResult<Pair<Map<K, V>, T>> decode(DynamicOps<T> ops, T input) {
            return ops.getMap(input).flatMap(mapInput -> {
                ImmutableMap.Builder<K, V> read = ImmutableMap.builder();
                ImmutableList.Builder<Pair<T, T>> failed = ImmutableList.builder();

                DataResult<Unit> result = mapInput.entries().reduce(
                        DataResult.success(Unit.INSTANCE, Lifecycle.stable()),
                        (r, pair) -> this.keyCodec.parse(ops, pair.getFirst()).flatMap(key -> {
                            DataResult<Pair<K, V>> entry = this.valueCodec.apply(key).parse(ops, pair.getSecond())
                                    .map(value -> Pair.of(key, value));
                            entry.error().ifPresent(e -> failed.add(pair));

                            return r.apply2stable((u, p) -> {
                                read.put(p.getFirst(), p.getSecond());
                                return u;
                            }, entry);
                        }),
                        (r1, r2) -> r1.apply2stable((u1, u2) -> u1, r2)
                );

                Map<K, V> elements = read.build();
                T errors = ops.createMap(failed.build().stream());

                return result.map(unit -> Pair.of(elements, input))
                        .setPartial(Pair.of(elements, input))
                        .mapError(e -> e + " missed input: " + errors);
            });
        }

        @Override
        public <T> DataResult<T> encode(Map<K, V> input, DynamicOps<T> ops, T prefix) {
            RecordBuilder<T> map = ops.mapBuilder();
            for (Map.Entry<K, V> entry : input.entrySet()) {
                K key = entry.getKey();
                V value = entry.getValue();
                map.add(this.keyCodec.encodeStart(ops, key), this.valueCodec.apply(key).encodeStart(ops, value));
            }
            return map.build(prefix);
        }
    }

    record TryFirstCodec<T>(Codec<T> first, Codec<T> second) implements Codec<T> {
        @Override
        public <R> DataResult<Pair<T, R>> decode(final DynamicOps<R> ops, final R input) {
            final DataResult<Pair<T, R>> firstRead = first.decode(ops, input);
            if (firstRead.result().isPresent()) {
                return firstRead;
            }
            return second.decode(ops, input);
        }

        @Override
        public <R> DataResult<R> encode(final T input, final DynamicOps<R> ops, final R prefix) {
            return second.encode(input, ops, prefix);
        }
    }

    public static <A> MapCodec<Optional<A>> strictOptionalFieldOf(final Codec<A> codec, final String name) {
        return new MapCodec<>() {
            @Override
            public <T> DataResult<Optional<A>> decode(final DynamicOps<T> ops, final MapLike<T> input) {
                final T value = input.get(name);
                if (value == null) {
                    return DataResult.success(Optional.empty());
                }
                return codec.parse(ops, value).map(Optional::of);
            }

            @Override
            public <T> RecordBuilder<T> encode(final Optional<A> input, final DynamicOps<T> ops, final RecordBuilder<T> prefix) {
                if (input.isPresent()) {
                    return prefix.add(name, codec.encodeStart(ops, input.get()));
                }
                return prefix;
            }

            @Override
            public <T> Stream<T> keys(final DynamicOps<T> ops) {
                return Stream.of(ops.createString(name));
            }
        };
    }

    public static <A> MapCodec<A> strictOptionalFieldOf(final Codec<A> codec, final String name, final A defaultValue) {
        return strictOptionalFieldOf(codec, name).xmap(
                value -> value.orElse(defaultValue),
                value -> Objects.equals(value, defaultValue) ? Optional.empty() : Optional.of(value)
        );
    }
}
