package com.lovetropics.lib.entity;

import it.unimi.dsi.fastutil.ints.IntList;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.FireworkExplosion;
import net.minecraft.world.item.component.Fireworks;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

public record FireworkPalette(int[]... palette) {
    private static final RandomSource RANDOM = RandomSource.create();

    static final int CE_YELLOW = 0xF4C434;
    static final int CE_BLUE = 0x65B9DB;
    static final int CE_GREEN = 0xB1D299;
    static final int CE_RED = 0xEA5153;
    static final int CE_ORANGE = 0xF08747;
    static final int CE_PURPLE = 0x8160A6;
    static final int CE_NAVY = 0x394053;

    public static final FireworkPalette DYE_COLORS = new FireworkPalette();

    public static final FireworkPalette PUERTO_RICO = new FireworkPalette(new int[]{0xB3312C, 0xF0F0F0, 0x253192, 0x6ac944, 0xF5C140, 0x0F9684});
    public static final FireworkPalette COOL_EARTH = new FireworkPalette(
            new int[]{CE_YELLOW, CE_BLUE, CE_GREEN},
            new int[]{CE_RED, CE_ORANGE, CE_PURPLE},
            new int[]{CE_YELLOW, CE_BLUE, CE_PURPLE},
            new int[]{CE_BLUE, CE_GREEN, CE_NAVY}
    );
    public static final FireworkPalette OSA_CONSERVATION = new FireworkPalette(
            new int[]{0x89C521, 0xCA813D, 0x0090FF, 0x250E02},
            new int[]{0x89C521, 0xCA813D, 0x0090FF, 0xFEFFEA}
    );

    public static final FireworkPalette ISLAND_ROYALE = new FireworkPalette(
            new int[]{0x000000}
    );

    public static final FireworkPalette SUSTAINABLE_HARVEST = new FireworkPalette(
            new int[]{0xed8b26, 0xffb798, 0xffb85c, 0x0b502f, 0x00000},
            new int[]{0xffb85c, 0xffb85c, 0x5fbfa2, 0xc0f3ed, 0xfffff}
    );

    public static final FireworkPalette SEACOLGY = new FireworkPalette(
            new int[]{0xF4C434, 0x001936, 0x117A8B, 0x20B2AA, 0x4A90E2, 0xF58220},
            new int[]{0xE2583E, 0x00B7EB, 0xF5F5DC, 0x1E4620, 0xE6ECEF, 0x00A86B}
    );

    public static final FireworkPalette CURRENT_PALETTE = SEACOLGY;

    public static FireworkPalette forDye(DyeColor color) {
        return new FireworkPalette(
                new int[]{color.getFireworkColor(), color.getTextColor(), color.getTextureDiffuseColor()}
        );
    }

    public FireworkRocketEntity create(Level level) {
        return this.create(level, BlockPos.ZERO);
    }

    public FireworkRocketEntity create(Level level, BlockPos pos) {
        ItemStack firework = new ItemStack(Items.FIREWORK_ROCKET);

        int[] colors;
        if (this.palette.length == 0) {
            colors = new int[RANDOM.nextInt(8) + 1];
            for (int i = 0; i < colors.length; i++) {
                colors[i] = DyeColor.values()[RANDOM.nextInt(16)].getFireworkColor();
            }
        } else {
            int[] palette = this.palette[RANDOM.nextInt(this.palette.length)];
            colors = new int[palette.length];
            for (int i = 0; i < colors.length; i++) {
                colors[i] = palette[RANDOM.nextInt(palette.length)];
            }
        }

        int type = RANDOM.nextInt(3) + 1;

        // Skip creeper firework type
        if (type == 3) {
            type = 4;
        }

        FireworkExplosion explosion = new FireworkExplosion(FireworkExplosion.Shape.byId(type), IntList.of(colors), IntList.of(), true, true);

        firework.set(DataComponents.FIREWORKS, new Fireworks(1, List.of(explosion)));

        return new FireworkRocketEntity(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, firework);
    }

    public void spawn(BlockPos block, Level level) {
        this.spawn(block, level, 0);
    }

    public void spawn(BlockPos pos, Level level, int range) {
        BlockPos spawnPos = pos;

        // don't bother if there's no randomness at all
        if (range > 0) {
            spawnPos = BlockPos.containing(moveRandomly(spawnPos.getX(), range), spawnPos.getY(), moveRandomly(spawnPos.getZ(), range));
            BlockState bs = level.getBlockState(spawnPos);
            if (!bs.isAir() && !bs.blocksMotion()) {
                return;
            }
        }

        level.addFreshEntity(this.create(level, spawnPos));
    }

    private static double moveRandomly(double base, double range) {
        return base + 0.5 + RANDOM.nextDouble() * range - (range / 2);
    }
}
