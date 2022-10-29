package com.lovetropics.lib.codec;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.gson.JsonElement;
import com.google.gson.JsonSyntaxException;
import com.mojang.datafixers.util.Either;
import com.mojang.datafixers.util.Pair;
import com.mojang.datafixers.util.Unit;
import com.mojang.math.Vector3f;
import com.mojang.serialization.*;
import com.mojang.serialization.codecs.OptionalFieldCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.*;
import net.minecraft.ChatFormatting;
import net.minecraft.advancements.critereon.BlockPredicate;
import net.minecraft.core.Registry;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Difficulty;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.stateproviders.BlockStateProvider;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.registries.IForgeRegistry;
import net.minecraftforge.registries.IForgeRegistryEntry;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.function.*;
import java.util.stream.Stream;

public final class MoreCodecs {
    public static final Codec<ItemStack> ITEM_STACK = Codec.either(ItemStack.CODEC, Registry.ITEM.byNameCodec())
            .xmap(either -> either.map(Function.identity(), ItemStack::new), Either::left);

    public static final Codec<BlockState> BLOCK_STATE = Codec.either(BlockState.CODEC, Registry.BLOCK.byNameCodec())
            .xmap(either -> either.map(Function.identity(), Block::defaultBlockState), Either::left);

    public static final Codec<BlockStateProvider> BLOCK_STATE_PROVIDER = Codec.either(BlockStateProvider.CODEC, BLOCK_STATE)
            .xmap(either -> either.map(Function.identity(), BlockStateProvider::simple), Either::left);

    public static final Codec<Component> TEXT = withJson(
            Component.Serializer::toJsonTree,
            json -> {
                Component text = Component.Serializer.fromJson(json);
                return text != null ? DataResult.success(text) : DataResult.error("Malformed text");
            }
    );

    public static final Codec<DyeColor> DYE_COLOR = stringVariants(DyeColor.values(), DyeColor::getSerializedName);

    public static final Codec<EquipmentSlot> EQUIPMENT_SLOT = stringVariants(EquipmentSlot.values(), EquipmentSlot::getName);

    public static final Codec<ChatFormatting> FORMATTING = stringVariants(ChatFormatting.values(), ChatFormatting::getName);

    public static final Codec<TextColor> COLOR = Codec.STRING.comapFlatMap(name -> {
        TextColor color = TextColor.parseColor(name);
        return color != null ? DataResult.success(color) : DataResult.error("Invalid color format");
    }, TextColor::toString);

    public static final Codec<GameType> GAME_TYPE = stringVariants(GameType.values(), GameType::getName);

    public static final Codec<UUID> UUID_STRING = Codec.STRING.comapFlatMap(
            string -> {
                try {
                    return DataResult.success(UUID.fromString(string));
                } catch (IllegalArgumentException e) {
                    return DataResult.error("Malformed UUID!");
                }
            },
            UUID::toString
    );

    public static final Codec<BlockPredicate> BLOCK_PREDICATE = withJson(BlockPredicate::serializeToJson, json -> {
        try {
            return DataResult.success(BlockPredicate.fromJson(json));
        } catch (JsonSyntaxException e) {
            return DataResult.error(e.getMessage());
        }
    });

    public static final Codec<Vec3> VECTOR_3D = Codec.DOUBLE.listOf().comapFlatMap(doubles -> {
        if (doubles.size() == 3) {
            return DataResult.success(new Vec3(doubles.get(0), doubles.get(1), doubles.get(2)));
        } else {
            return DataResult.error("Wrong number of vector components!");
        }
    }, vector -> ImmutableList.of(vector.x, vector.y, vector.z));

    public static final Codec<Vector3f> VECTOR_3F = Codec.FLOAT.listOf().comapFlatMap(floats -> {
        if (floats.size() == 3) {
            return DataResult.success(new Vector3f(floats.get(0), floats.get(1), floats.get(2)));
        } else {
            return DataResult.error("Wrong number of vector components!");
        }
    }, vector -> ImmutableList.of(vector.x(), vector.y(), vector.z()));

    public static final Codec<net.minecraft.world.phys.AABB> AABB = RecordCodecBuilder.create(instance ->
            instance.group(
                    VECTOR_3D.fieldOf("start").forGetter(aabb -> new Vec3(aabb.minX, aabb.minY, aabb.minZ)),
                    VECTOR_3D.fieldOf("end").forGetter(aabb -> new Vec3(aabb.maxX, aabb.maxY, aabb.maxZ))
            ).apply(instance, net.minecraft.world.phys.AABB::new)
    );

    public static final Codec<Difficulty> DIFFICULTY = MoreCodecs.stringVariants(Difficulty.values(), Difficulty::getKey);

    public static final Codec<Potion> POTION = Registry.POTION.byNameCodec();

    private static final Codec<MobEffectInstance> EFFECT_INSTANCE_RECORD = RecordCodecBuilder.create(i -> i.group(
            Registry.MOB_EFFECT.byNameCodec().fieldOf("type").forGetter(MobEffectInstance::getEffect),
            Codec.FLOAT.fieldOf("seconds").forGetter(c -> c.getDuration() / 20.0F),
            Codec.INT.fieldOf("amplifier").forGetter(MobEffectInstance::getAmplifier),
            Codec.BOOL.optionalFieldOf("hide_particles", false).forGetter(c -> !c.isVisible())
    ).apply(i, (type, seconds, amplifier, hideParticles) -> {
        return new MobEffectInstance(type, Math.round(seconds * 20), amplifier, false, hideParticles);
    }));

    public static final Codec<MobEffectInstance> EFFECT_INSTANCE = Codec.either(POTION, EFFECT_INSTANCE_RECORD)
            .comapFlatMap(either -> either.map(potion -> {
                List<MobEffectInstance> effects = potion.getEffects();
                if (effects.size() == 1) {
                    return DataResult.success(effects.get(0));
                } else {
                    return DataResult.error("Potion must have only 1 effect");
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
                        either -> either.map(Function.identity(), MoreCodecs::unitArrayList),
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
            return value != null ? DataResult.success(value) : DataResult.error("No variant with key '" + key + "'");
        }, asKey);
    }

    public static <A> Codec<A> withJson(Function<A, JsonElement> encode, Function<JsonElement, DataResult<A>> decode) {
        return withOps(JsonOps.INSTANCE, encode, decode);
    }

    public static <A> Codec<A> withNbt(Function<A, Tag> encode, Function<Tag, DataResult<A>> decode) {
        return withOps(NbtOps.INSTANCE, encode, decode);
    }

    public static <A> Codec<A> withNbtCompound(BiFunction<A, CompoundTag, CompoundTag> encode, BiConsumer<A, CompoundTag> decode, Supplier<A> factory) {
        return withNbt(
                value -> encode.apply(value, new CompoundTag()),
                nbt -> {
                    if (nbt instanceof CompoundTag compound) {
                        A value = factory.get();
                        decode.accept(value, compound);
                        return DataResult.success(value);
                    }
                    return DataResult.error("Expected compound tag");
                }
        );
    }

    public static <A, T> Codec<A> withOps(DynamicOps<T> ops, Function<A, T> encode, Function<T, DataResult<A>> decode) {
        return new MappedOpsCodec<>(ops, encode, decode);
    }

    public static <N extends Number> Codec<N> numberAsString(Function<String, N> parse) {
        return Codec.STRING.comapFlatMap(
                s -> {
                    try {
                        return DataResult.success(parse.apply(s));
                    } catch (NumberFormatException e) {
                        return DataResult.error("Failed to parse number '" + s + "'");
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

    public static <T> Codec<ResourceKey<T>> resourceKey(ResourceKey<? extends Registry<T>> registry) {
        return ResourceLocation.CODEC.xmap(
                id -> ResourceKey.create(registry, id),
                ResourceKey::location
        );
    }

    public static <T, C extends List<T>> Codec<C> sorted(Codec<C> codec, Comparator<? super T> comparator) {
        return codec.xmap(
                list -> {
                    list.sort(comparator);
                    return list;
                },
                Function.identity()
        );
    }

    public static <K, V> Codec<Map<K, V>> dispatchByMapKey(Codec<K> keyCodec, Function<K, Codec<V>> valueCodec) {
        return new DispatchMapCodec<>(keyCodec, valueCodec);
    }

    public static <T extends IForgeRegistryEntry<T>> Codec<T> ofForgeRegistry(Supplier<IForgeRegistry<T>> registry) {
        return new Codec<T>() {
            @Override
            public <U> DataResult<Pair<T, U>> decode(DynamicOps<U> ops, U input) {
                return ResourceLocation.CODEC.decode(ops, input)
                        .flatMap(pair -> {
                            if (!registry.get().containsKey(pair.getFirst())) {
                                return DataResult.error("Unknown registry key: " + pair.getFirst());
                            }
                            return DataResult.success(pair.mapFirst(registry.get()::getValue));
                        });
            }

            @Override
            public <U> DataResult<U> encode(T input, DynamicOps<U> ops, U prefix) {
                ResourceLocation key = registry.get().getKey(input);
                if (key == null) {
                    return DataResult.error("Unknown registry element " + input);
                }
                return ops.mergeToPrimitive(prefix, ops.createString(key.toString()));
            }
        };
    }

    public static <T> Codec<T> validate(Codec<T> codec, Function<T, DataResult<T>> validate) {
        return codec.flatXmap(validate, validate);
    }

    public static <T> Codec<T> validate(Codec<T> codec, Predicate<T> validate, String error) {
        return validate(codec, value -> {
            if (validate.test(value)) {
                return DataResult.success(value);
            } else {
                return DataResult.error(error);
            }
        });
    }

    public static Codec<LocalDateTime> localDateTime(DateTimeFormatter formatter) {
        return Codec.STRING.comapFlatMap(
                string -> {
                    try {
                        return DataResult.success(LocalDateTime.parse(string, formatter));
                    } catch (DateTimeParseException e) {
                        return DataResult.error("Failed to parse date: " + string);
                    }
                },
                formatter::format
        );
    }

    public static <T> Codec<T> tryFirst(Codec<T> first, Codec<T> second) {
    	return new TryFirstCodec<>(first, second);
    }

    private static <T> List<T> unitArrayList(T t) {
        List<T> list = new ArrayList<>(1);
        list.add(t);
        return list;
    }

    record MappedOpsCodec<A, S>(DynamicOps<S> sourceOps, Function<A, S> encode, Function<S, DataResult<A>> decode) implements Codec<A> {
        @Override
        @SuppressWarnings("unchecked")
        public <T> DataResult<T> encode(A input, DynamicOps<T> ops, T prefix) {
            S sourceData = this.encode.apply(input);
            T targetData = ops == this.sourceOps ? (T) sourceData : this.sourceOps.convertTo(ops, sourceData);
            return ops.getMap(targetData).flatMap(map -> {
                return ops.mergeToMap(prefix, map);
            });
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> DataResult<Pair<A, T>> decode(DynamicOps<T> ops, T input) {
            S sourceData = ops == this.sourceOps ? (S) input : ops.convertTo(this.sourceOps, input);
            return this.decode.apply(sourceData).map(output -> Pair.of(output, input));
        }
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
}
