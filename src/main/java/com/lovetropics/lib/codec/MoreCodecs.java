package com.lovetropics.lib.codec;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.gson.JsonElement;
import com.google.gson.JsonSyntaxException;
import com.mojang.datafixers.util.Either;
import com.mojang.datafixers.util.Pair;
import com.mojang.datafixers.util.Unit;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.Lifecycle;
import com.mojang.serialization.RecordBuilder;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2DoubleMap;
import it.unimi.dsi.fastutil.objects.Object2DoubleOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2FloatMap;
import it.unimi.dsi.fastutil.objects.Object2FloatOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import net.minecraft.advancements.criterion.BlockPredicate;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.inventory.EquipmentSlotType;
import net.minecraft.item.DyeColor;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.nbt.INBT;
import net.minecraft.nbt.NBTDynamicOps;
import net.minecraft.util.RegistryKey;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.vector.Vector3d;
import net.minecraft.util.math.vector.Vector3f;
import net.minecraft.util.registry.Registry;
import net.minecraft.util.text.Color;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.Difficulty;
import net.minecraft.world.GameType;
import net.minecraft.world.gen.blockstateprovider.BlockStateProvider;
import net.minecraft.world.gen.blockstateprovider.SimpleBlockStateProvider;
import net.minecraftforge.registries.IForgeRegistry;
import net.minecraftforge.registries.IForgeRegistryEntry;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.IntFunction;
import java.util.function.Predicate;
import java.util.function.Supplier;

public final class MoreCodecs {
    public static final Codec<ItemStack> ITEM_STACK = Codec.either(ItemStack.CODEC, Registry.ITEM)
            .xmap(either -> either.map(Function.identity(), ItemStack::new), Either::left);

    public static final Codec<BlockState> BLOCK_STATE = Codec.either(BlockState.CODEC, Registry.BLOCK)
            .xmap(either -> either.map(Function.identity(), Block::getDefaultState), Either::left);

    public static final Codec<BlockStateProvider> BLOCK_STATE_PROVIDER = Codec.either(BlockStateProvider.CODEC, BLOCK_STATE)
            .xmap(either -> either.map(Function.identity(), SimpleBlockStateProvider::new), Either::left);

    public static final Codec<ITextComponent> TEXT = withJson(
            ITextComponent.Serializer::toJsonTree,
            json -> {
                ITextComponent text = ITextComponent.Serializer.getComponentFromJson(json);
                return text != null ? DataResult.success(text) : DataResult.error("Malformed text");
            }
    );

    public static final Codec<DyeColor> DYE_COLOR = stringVariants(DyeColor.values(), DyeColor::getString);

    public static final Codec<EquipmentSlotType> EQUIPMENT_SLOT = stringVariants(EquipmentSlotType.values(), EquipmentSlotType::getName);

    public static final Codec<TextFormatting> FORMATTING = stringVariants(TextFormatting.values(), TextFormatting::getFriendlyName);

    public static final Codec<Color> COLOR = Codec.STRING.comapFlatMap(name -> {
        Color color = Color.fromHex(name);
        return color != null ? DataResult.success(color) : DataResult.error("Invalid color format");
    }, Color::toString);

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

    public static final Codec<BlockPredicate> BLOCK_PREDICATE = withJson(BlockPredicate::serialize, json -> {
        try {
            return DataResult.success(BlockPredicate.deserialize(json));
        } catch (JsonSyntaxException e) {
            return DataResult.error(e.getMessage());
        }
    });

    public static final Codec<Vector3d> VECTOR_3D = Codec.DOUBLE.listOf().comapFlatMap(doubles -> {
        if (doubles.size() == 3) {
            return DataResult.success(new Vector3d(doubles.get(0), doubles.get(1), doubles.get(2)));
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
    }, vector -> ImmutableList.of(vector.getX(), vector.getY(), vector.getZ()));

    public static final Codec<AxisAlignedBB> AABB = RecordCodecBuilder.create(instance ->
            instance.group(
                    VECTOR_3D.fieldOf("start").forGetter(aabb -> new Vector3d(aabb.minX, aabb.minY, aabb.minZ)),
                    VECTOR_3D.fieldOf("end").forGetter(aabb -> new Vector3d(aabb.maxX, aabb.maxY, aabb.maxZ))
            ).apply(instance, AxisAlignedBB::new)
    );

    public static final Codec<Difficulty> DIFFICULTY = MoreCodecs.stringVariants(Difficulty.values(), Difficulty::getTranslationKey);

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

    public static <A> Codec<A> withNbt(Function<A, INBT> encode, Function<INBT, DataResult<A>> decode) {
        return withOps(NBTDynamicOps.INSTANCE, encode, decode);
    }

    public static <A> Codec<A> withNbtCompound(BiFunction<A, CompoundNBT, CompoundNBT> encode, BiConsumer<A, CompoundNBT> decode, Supplier<A> factory) {
        return withNbt(
                value -> encode.apply(value, new CompoundNBT()),
                nbt -> {
                    if (nbt instanceof CompoundNBT) {
                        A value = factory.get();
                        decode.accept(value, (CompoundNBT) nbt);
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

    public static <T> Codec<RegistryKey<T>> registryKey(RegistryKey<? extends Registry<T>> registry) {
        return ResourceLocation.CODEC.xmap(
                id -> RegistryKey.getOrCreateKey(registry, id),
                RegistryKey::getLocation
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

    private static <T> List<T> unitArrayList(T t) {
        List<T> list = new ArrayList<>(1);
        list.add(t);
        return list;
    }

    static final class MappedOpsCodec<A, S> implements Codec<A> {
        private final DynamicOps<S> sourceOps;
        private final Function<A, S> encode;
        private final Function<S, DataResult<A>> decode;

        MappedOpsCodec(DynamicOps<S> sourceOps, Function<A, S> encode, Function<S, DataResult<A>> decode) {
            this.sourceOps = sourceOps;
            this.encode = encode;
            this.decode = decode;
        }

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

    static final class DispatchMapCodec<K, V> implements Codec<Map<K, V>> {
        private final Codec<K> keyCodec;
        private final Function<K, Codec<V>> valueCodec;

        DispatchMapCodec(Codec<K> keyCodec, Function<K, Codec<V>> valueCodec) {
            this.keyCodec = keyCodec;
            this.valueCodec = valueCodec;
        }

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
}
