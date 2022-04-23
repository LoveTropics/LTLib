package com.lovetropics.lib.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nonnull;
import java.util.Random;

public final class FireworkPalette {
	private static final Random RANDOM = new Random();

	static final int CE_YELLOW = 0xF4C434;
	static final int CE_BLUE = 0x65B9DB;
	static final int CE_GREEN = 0xB1D299;
	static final int CE_RED = 0xEA5153;
	static final int CE_ORANGE = 0xF08747;
	static final int CE_PURPLE = 0x8160A6;
	static final int CE_NAVY = 0x394053;

	public static final FireworkPalette DYE_COLORS = new FireworkPalette();

	public static final FireworkPalette PUERTO_RICO = new FireworkPalette(new int[] { 0xB3312C, 0xF0F0F0, 0x253192, 0x6ac944, 0xF5C140, 0x0F9684 });
	public static final FireworkPalette COOL_EARTH = new FireworkPalette(
			new int[] { CE_YELLOW, CE_BLUE, CE_GREEN },
			new int[] { CE_RED, CE_ORANGE, CE_PURPLE },
			new int[] { CE_YELLOW, CE_BLUE, CE_PURPLE },
			new int[] { CE_BLUE, CE_GREEN, CE_NAVY }
	);
	public static final FireworkPalette OSA_CONSERVATION = new FireworkPalette(
			new int[] { 0x89C521, 0xCA813D, 0x0090FF, 0x250E02 },
			new int[] { 0x89C521, 0xCA813D, 0x0090FF, 0xFEFFEA }
	);

	public static final FireworkPalette ISLAND_ROYALE = new FireworkPalette(
			new int[] { 0x000000 }
	);

	private final int[][] palette;

	private FireworkPalette(int[]... palette) {
		this.palette = palette;
	}

	public int[][] getPalette() {
		return this.palette;
	}

	public @Nonnull FireworkRocketEntity create(@Nonnull Level level) {
		return this.create(level, BlockPos.ZERO);
	}

	public @Nonnull FireworkRocketEntity create(@Nonnull Level level, @Nonnull BlockPos pos) {
		ItemStack firework = new ItemStack(Items.FIREWORK_ROCKET);
		firework.setTag(new CompoundTag());

		CompoundTag explosion = new CompoundTag();
		explosion.putBoolean("Flicker", true);
		explosion.putBoolean("Trail", true);

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

		explosion.putIntArray("Colors", colors);
		byte type = (byte) (RANDOM.nextInt(3) + 1);
		type = type == 3 ? 4 : type;
		explosion.putByte("Type", type);

		ListTag explosions = new ListTag();
		explosions.add(explosion);

		CompoundTag fireworkTag = new CompoundTag();
		fireworkTag.put("Explosions", explosions);
		fireworkTag.putByte("Flight", (byte) 1);
		firework.addTagElement("Fireworks", fireworkTag);

		return new FireworkRocketEntity(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, firework);
	}

	public void spawn(@Nonnull BlockPos block, Level level) {
		this.spawn(block, level, 0);
	}

	public void spawn(@Nonnull BlockPos pos, Level level, int range) {
		BlockPos spawnPos = pos;

		// don't bother if there's no randomness at all
		if (range > 0) {
			spawnPos = new BlockPos(moveRandomly(spawnPos.getX(), range), spawnPos.getY(), moveRandomly(spawnPos.getZ(), range));
			BlockState bs = level.getBlockState(spawnPos);

			int tries = -1;
			while (!level.isEmptyBlock(new BlockPos(spawnPos)) && !bs.getMaterial().blocksMotion()) {
				tries++;
				if (tries > 100) {
					return;
				}
			}
		}

		level.addFreshEntity(this.create(level, spawnPos));
	}

	private static double moveRandomly(double base, double range) {
		return base + 0.5 + RANDOM.nextDouble() * range - (range / 2);
	}
}
