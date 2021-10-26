package com.lovetropics.lib;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.vector.Vector3d;

import javax.annotation.Nullable;
import java.util.Iterator;
import java.util.Random;

public final class BlockBox implements Iterable<BlockPos> {
    public static final Codec<BlockBox> CODEC = RecordCodecBuilder.create(instance -> {
        return instance.group(
                BlockPos.CODEC.fieldOf("min").forGetter(c -> c.min),
                BlockPos.CODEC.fieldOf("max").forGetter(c -> c.max)
        ).apply(instance, BlockBox::of);
    });

    public final BlockPos min;
    public final BlockPos max;

    private BlockBox(BlockPos min, BlockPos max) {
        this.min = min;
        this.max = max;
    }

    public static BlockBox of(BlockPos pos) {
        return new BlockBox(pos, pos);
    }

    public static BlockBox of(BlockPos a, BlockPos b) {
        return new BlockBox(BlockBox.min(a, b), BlockBox.max(a, b));
    }

    public static BlockBox ofChunk(int chunkX, int chunkZ) {
        return new BlockBox(
                new BlockPos(chunkX << 4, 0, chunkZ << 4),
                new BlockPos((chunkX << 4) + 15, 256, (chunkZ << 16) + 15)
        );
    }

    public static BlockBox ofChunk(ChunkPos chunkPos) {
        return ofChunk(chunkPos.x, chunkPos.z);
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

    public BlockBox offset(double x, double y, double z) {
        return new BlockBox(
                this.min.add(x, y, z),
                this.max.add(x, y, z)
        );
    }

    public Vector3d getCenter() {
        return new Vector3d(
                (this.min.getX() + this.max.getX() + 1.0) / 2.0,
                (this.min.getY() + this.max.getY() + 1.0) / 2.0,
                (this.min.getZ() + this.max.getZ() + 1.0) / 2.0
        );
    }

    public BlockPos getCenterBlock() {
        return new BlockPos(
                (this.min.getX() + this.max.getX() + 1) / 2,
                (this.min.getY() + this.max.getY() + 1) / 2,
                (this.min.getZ() + this.max.getZ() + 1) / 2
        );
    }

    public BlockPos getSize() {
        return new BlockPos(
                this.max.getX() - this.min.getX() + 1,
                this.max.getY() - this.min.getY() + 1,
                this.max.getZ() - this.min.getZ() + 1
        );
    }

    public long getVolume() {
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

    public boolean contains(Vector3d pos) {
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

    public boolean intersects(AxisAlignedBB aabb) {
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

    public AxisAlignedBB asAabb() {
        return new AxisAlignedBB(
                this.min.getX(), this.min.getY(), this.min.getZ(),
                this.max.getX() + 1.0, this.max.getY() + 1.0, this.max.getZ() + 1.0
        );
    }

    @Override
    public Iterator<BlockPos> iterator() {
        return BlockPos.getAllInBoxMutable(this.min, this.max).iterator();
    }

    public LongSet asChunks() {
        LongSet chunks = new LongOpenHashSet();

        int minChunkX = this.min.getX() >> 4;
        int minChunkZ = this.min.getZ() >> 4;
        int maxChunkX = this.max.getX() >> 4;
        int maxChunkZ = this.max.getZ() >> 4;

        for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
            for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
                chunks.add(ChunkPos.asLong(chunkX, chunkZ));
            }
        }

        return chunks;
    }

    public CompoundNBT write(CompoundNBT root) {
        root.put("min", writeBlockPos(this.min, new CompoundNBT()));
        root.put("max", writeBlockPos(this.max, new CompoundNBT()));
        return root;
    }

    public static BlockBox read(CompoundNBT root) {
        BlockPos min = readBlockPos(root.getCompound("min"));
        BlockPos max = readBlockPos(root.getCompound("max"));
        return new BlockBox(min, max);
    }

    public void write(PacketBuffer buffer) {
        buffer.writeBlockPos(this.min);
        buffer.writeBlockPos(this.max);
    }

    public static BlockBox read(PacketBuffer buffer) {
        BlockPos min = buffer.readBlockPos();
        BlockPos max = buffer.readBlockPos();
        return new BlockBox(min, max);
    }

    private static CompoundNBT writeBlockPos(BlockPos pos, CompoundNBT root) {
        root.putInt("x", pos.getX());
        root.putInt("y", pos.getY());
        root.putInt("z", pos.getZ());
        return root;
    }

    private static BlockPos readBlockPos(CompoundNBT root) {
        return new BlockPos(root.getInt("x"), root.getInt("y"), root.getInt("z"));
    }
}
