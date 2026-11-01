package com.lovetropics.lib.item;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.Items;

import java.util.List;

public record StackedHat(
        ItemStackTemplate item,
        float width,
        float height,
        float bounciness
) {
    public static final Codec<StackedHat> CODEC = RecordCodecBuilder.create(i -> i.group(
            ItemStackTemplate.MAP_CODEC.forGetter(StackedHat::item),
            Codec.FLOAT.optionalFieldOf("width", 1.0f).forGetter(StackedHat::width),
            Codec.FLOAT.optionalFieldOf("height", 1.0f).forGetter(StackedHat::height),
            Codec.FLOAT.optionalFieldOf("bounciness", 1.0f).forGetter(StackedHat::bounciness)
    ).apply(i, StackedHat::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, StackedHat> STREAM_CODEC = StreamCodec.composite(
            ItemStackTemplate.STREAM_CODEC, StackedHat::item,
            ByteBufCodecs.FLOAT, StackedHat::width,
            ByteBufCodecs.FLOAT, StackedHat::height,
            ByteBufCodecs.FLOAT, StackedHat::bounciness,
            StackedHat::new
    );

    private static Access access = Access.NONE;

    public static ItemStack create(List<StackedHat> hats) {
        return access.create(hats);
    }

    public static void set(ItemStack item, List<StackedHat> hats) {
        access.set(item, hats);
    }

    public static List<StackedHat> get(ItemStack item) {
        return access.get(item);
    }

    public static List<StackedHat> getEntityHats(Entity entity) {
        return access.getEntityHats(entity);
    }

    public static void setEntityHats(Entity entity, List<StackedHat> hats) {
        access.setEntityHats(entity, hats);
    }

    public static void addHatToEntity(Entity entity, StackedHat hat) {
        access.addHatToEntity(entity, hat);
    }

    public static void removeHatFromEntity(Entity entity, StackedHat hat) {
        access.removeHatFromEntity(entity, hat);
    }

    public static void bindAccess(Access access) {
        StackedHat.access = access;
    }

    public interface Access {
        Access NONE = new Access() {
            @Override
            public ItemStack create(List<StackedHat> hats) {
                return new ItemStack(Items.LEATHER_HELMET);
            }

            @Override
            public void set(ItemStack item, List<StackedHat> hats) {
            }

            @Override
            public List<StackedHat> get(ItemStack item) {
                return List.of();
            }

            @Override
            public List<StackedHat> getEntityHats(Entity entity) {
                return List.of();
            }

            @Override
            public void setEntityHats(Entity entity, List<StackedHat> hats) {
            }

            @Override
            public void addHatToEntity(Entity entity, StackedHat hat) {
            }

            @Override
            public void removeHatFromEntity(Entity entity, StackedHat hat) {
            }
        };

        ItemStack create(List<StackedHat> hats);

        void set(ItemStack item, List<StackedHat> hats);

        List<StackedHat> get(ItemStack item);

        List<StackedHat> getEntityHats(Entity entity);

        void setEntityHats(Entity entity, List<StackedHat> hats);

        void addHatToEntity(Entity entity, StackedHat hat);

        void removeHatFromEntity(Entity entity, StackedHat hat);
    }
}
