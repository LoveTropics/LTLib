package com.lovetropics.lib;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.Iterator;
import java.util.Random;

public record BlockBox(BlockPos min, BlockPos max) implements Iterable<BlockPos> {
    public static final Codec<BlockBox> CODEC = RecordCodecBuilder.create(i -> i.group(
            BlockPos.CODEC.fieldOf("min").forGetter(c -> c.min),
            BlockPos.CODEC.fieldOf("max").forGetter(c -> c.max)
    ).apply(i, BlockBox::of));

    public static BlockBox of(BlockPos pos) {
        return new BlockBox(pos, pos);
    }

    public static BlockBox of(BlockPos a, BlockPos b) {
        return new BlockBox(BlockBox.min(a, b), BlockBox.max(a, b));
    }

    public static BlockBox ofChunk(LevelHeightAccessor level, int chunkX, int chunkZ) {
        return new BlockBox(
                new BlockPos(SectionPos.sectionToBlockCoord(chunkX), level.getMinBuildHeight(), SectionPos.sectionToBlockCoord(chunkZ)),
                new BlockPos(SectionPos.sectionToBlockCoord(chunkX, SectionPos.SECTION_MAX_INDEX), level.getMaxBuildHeight(), SectionPos.sectionToBlockCoord(chunkZ, SectionPos.SECTION_MAX_INDEX))
        );
    }

    public static BlockBox ofChunk(LevelHeightAccessor level, ChunkPos chunkPos) {
        return ofChunk(level, chunkPos.x, chunkPos.z);
    }

    public static BlockPos min(BlockPos a, BlockPos b) {
        return new BlockPos(
                Math.min(a.getX(), b.getX()),
                Math.min(a.getY(), b.getY()),
                Math.min(a.getZ(), b.getZ())
        );
    }

    public static BlockPos max(BlockPos a, BlockPos b) {
        return new BlockPos(
                Math.max(a.getX(), b.getX()),
                Math.max(a.getY(), b.getY()),
                Math.max(a.getZ(), b.getZ())
        );
    }

    public BlockBox withMin(BlockPos min) {
        return BlockBox.of(min, this.max);
    }

    public BlockBox withMax(BlockPos max) {
        return BlockBox.of(this.min, max);
    }

    public BlockBox offset(int x, int y, int z) {
        return new BlockBox(
                this.min.offset(x, y, z),
                this.max.offset(x, y, z)
        );
    }

    public Vec3 center() {
        return new Vec3(
                (this.min.getX() + this.max.getX() + 1.0) / 2.0,
                (this.min.getY() + this.max.getY() + 1.0) / 2.0,
                (this.min.getZ() + this.max.getZ() + 1.0) / 2.0
        );
    }

    public BlockPos centerBlock() {
        return new BlockPos(
                (this.min.getX() + this.max.getX() + 1) / 2,
                (this.min.getY() + this.max.getY() + 1) / 2,
                (this.min.getZ() + this.max.getZ() + 1) / 2
        );
    }

    public BlockPos size() {
        return new BlockPos(
                this.max.getX() - this.min.getX() + 1,
                this.max.getY() - this.min.getY() + 1,
                this.max.getZ() - this.min.getZ() + 1
        );
    }

    public long volume() {
        long sizeX = this.max.getX() - this.min.getX() + 1;
        long sizeY = this.max.getY() - this.min.getY() + 1;
        long sizeZ = this.max.getZ() - this.min.getZ() + 1;
        return sizeX * sizeY * sizeZ;
    }

    public BlockPos sample(Random random) {
        return new BlockPos(
                this.min.getX() + random.nextInt(this.max.getX() - this.min.getX() + 1),
                this.min.getY() + random.nextInt(this.max.getY() - this.min.getY() + 1),
                this.min.getZ() + random.nextInt(this.max.getZ() - this.min.getZ() + 1)
        );
    }

    public boolean contains(BlockPos pos) {
        return this.contains(pos.getX(), pos.getY(), pos.getZ());
    }

    public boolean contains(Vec3 pos) {
        return this.contains(pos.x, pos.y, pos.z);
    }

    public boolean contains(double x, double y, double z) {
        BlockPos min = this.min;
        BlockPos max = this.max;
        return x >= min.getX() && y >= min.getY() && z >= min.getZ()
                && x < max.getX() + 1.0 && y < max.getY() + 1.0 && z < max.getZ() + 1.0;
    }

    public boolean contains(int x, int y, int z) {
        BlockPos min = this.min;
        BlockPos max = this.max;
        return x >= min.getX() && y >= min.getY() && z >= min.getZ()
                && x <= max.getX() && y <= max.getY() && z <= max.getZ();
    }

    public boolean intersects(AABB aabb) {
        return aabb.intersects(this.min.getX(), this.min.getY(), this.min.getZ(), this.max.getX() + 1.0, this.max.getY() + 1.0, this.max.getZ() + 1.0);
    }

    public boolean intersects(BlockBox other) {
        return this.max.getX() >= other.min.getX() && this.min.getX() <= other.max.getX()
                && this.max.getY() >= other.min.getY() && this.min.getY() <= other.max.getY()
                && this.max.getZ() >= other.min.getZ() && this.min.getZ() <= other.max.getZ();
    }

    @Nullable
    public BlockBox intersection(BlockBox other) {
        BlockPos min = max(this.min, other.min);
        BlockPos max = min(this.max, other.max);
        if (min.getX() >= max.getX() || min.getY() >= max.getY() || min.getZ() >= max.getZ()) {
            return null;
        }
        return new BlockBox(min, max);
    }

    public AABB asAabb() {
        return new AABB(
                this.min.getX(), this.min.getY(), this.min.getZ(),
                this.max.getX() + 1.0, this.max.getY() + 1.0, this.max.getZ() + 1.0
        );
    }

    @Override
    public Iterator<BlockPos> iterator() {
        return BlockPos.betweenClosed(this.min, this.max).iterator();
    }

    public LongSet asChunks() {
        LongSet chunks = new LongOpenHashSet();

        int minChunkX = SectionPos.blockToSectionCoord(this.min.getX());
        int minChunkZ = SectionPos.blockToSectionCoord(this.min.getZ());
        int maxChunkX = SectionPos.blockToSectionCoord(this.max.getX());
        int maxChunkZ = SectionPos.blockToSectionCoord(this.max.getZ());

        for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
            for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
                chunks.add(ChunkPos.asLong(chunkX, chunkZ));
            }
        }

        return chunks;
    }

    public CompoundTag write(CompoundTag root) {
        root.put("min", writeBlockPos(this.min, new CompoundTag()));
        root.put("max", writeBlockPos(this.max, new CompoundTag()));
        return root;
    }

    public static BlockBox read(CompoundTag root) {
        BlockPos min = readBlockPos(root.getCompound("min"));
        BlockPos max = readBlockPos(root.getCompound("max"));
        return new BlockBox(min, max);
    }

    public void write(FriendlyByteBuf buffer) {
        buffer.writeBlockPos(this.min);
        buffer.writeBlockPos(this.max);
    }

    public static BlockBox read(FriendlyByteBuf buffer) {
        BlockPos min = buffer.readBlockPos();
        BlockPos max = buffer.readBlockPos();
        return new BlockBox(min, max);
    }

    private static CompoundTag writeBlockPos(BlockPos pos, CompoundTag root) {
        root.putInt("x", pos.getX());
        root.putInt("y", pos.getY());
        root.putInt("z", pos.getZ());
        return root;
    }

    private static BlockPos readBlockPos(CompoundTag root) {
        return new BlockPos(root.getInt("x"), root.getInt("y"), root.getInt("z"));
    }
}
