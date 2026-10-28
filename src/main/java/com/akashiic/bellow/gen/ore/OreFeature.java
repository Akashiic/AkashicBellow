package com.akashiic.bellow.gen.ore;

import java.util.BitSet;
import java.util.Random;

/**
 * Port of Minecraft 1.18 {@code OreFeature.place/doPlace/canPlaceOre}.
 * <p>
 * A vein is a chain of {@code size} spheres whose centres are evenly spaced on a segment of length {@code size / 4}
 * centred on the origin (random horizontal angle, endpoints jittered by {@code -2..0} in Y). Sphere {@code k} has
 * radius {@code ((sin(pi * k / size) + 1) * q + 1) / 2} with {@code q ~ U[0, size / 16)}; spheres contained in another
 * are discarded, every block is visited once (BitSet), and with {@code discardChanceOnAirExposure} a block touching air
 * is skipped with that probability.
 * <p>
 * The random stream is consumed in the same order as Minecraft (float angle, two {@code nextInt(3)}, one
 * {@code nextDouble} per sphere, then one {@code nextFloat} per matching block when {@code 0 < discard < 1}).
 */
public final class OreFeature {

    /** View of the world being decorated. */
    public interface World {

        /** False outside the writable region or the vertical range of the decorator. */
        boolean canWrite(int x, int y, int z);

        /** Index of the first target whose predicate matches the block at the position, or -1. */
        int matchTarget(int x, int y, int z);

        boolean isAir(int x, int y, int z);

        void place(int x, int y, int z, int targetIndex);
    }

    private OreFeature() {}

    /** @return number of blocks placed. */
    /** Vertical reach of a vein around its origin: blocks lie within {@code [y - reach, y + reach]}. */
    public static int verticalReach(int size) {
        return 2 + ceil((size / 16.0F * 2.0F + 1.0F) / 2.0F);
    }

    public static int place(Random random, int originX, int originY, int originZ, int size, float discardChance,
        World world) {
        float angle = random.nextFloat() * (float) Math.PI;
        float halfLength = size / 8.0F;
        int pad = ceil((size / 16.0F * 2.0F + 1.0F) / 2.0F);
        double x0 = originX + Math.sin(angle) * halfLength;
        double x1 = originX - Math.sin(angle) * halfLength;
        double z0 = originZ + Math.cos(angle) * halfLength;
        double z1 = originZ - Math.cos(angle) * halfLength;
        double y0 = originY + random.nextInt(3) - 2;
        double y1 = originY + random.nextInt(3) - 2;
        int minX = originX - ceil(halfLength) - pad;
        int minY = originY - 2 - pad;
        int minZ = originZ - ceil(halfLength) - pad;
        int width = 2 * (ceil(halfLength) + pad);
        int height = 2 * (2 + pad);
        // Minecraft first checks that the box reaches below the OCEAN_FLOOR_WG heightmap; always true below Y 0.
        return doPlace(world, random, size, discardChance, x0, x1, z0, z1, y0, y1, minX, minY, minZ, width, height);
    }

    private static int doPlace(World world, Random random, int size, float discardChance, double x0, double x1,
        double z0, double z1, double y0, double y1, int minX, int minY, int minZ, int width, int height) {
        int placed = 0;
        BitSet visited = new BitSet(width * height * width);
        double[] spheres = new double[size * 4];

        for (int k = 0; k < size; k++) {
            float t = (float) k / (float) size;
            double cx = lerp(t, x0, x1);
            double cy = lerp(t, y0, y1);
            double cz = lerp(t, z0, z1);
            double q = random.nextDouble() * size / 16.0;
            double radius = ((double) ((float) Math.sin((float) Math.PI * t) + 1.0F) * q + 1.0) / 2.0;
            spheres[k * 4] = cx;
            spheres[k * 4 + 1] = cy;
            spheres[k * 4 + 2] = cz;
            spheres[k * 4 + 3] = radius;
        }

        // Drop spheres fully contained in a bigger one.
        for (int a = 0; a < size - 1; a++) {
            if (spheres[a * 4 + 3] <= 0.0) continue;
            for (int b = a + 1; b < size; b++) {
                if (spheres[b * 4 + 3] <= 0.0) continue;
                double dx = spheres[a * 4] - spheres[b * 4];
                double dy = spheres[a * 4 + 1] - spheres[b * 4 + 1];
                double dz = spheres[a * 4 + 2] - spheres[b * 4 + 2];
                double dr = spheres[a * 4 + 3] - spheres[b * 4 + 3];
                if (dr * dr > dx * dx + dy * dy + dz * dz) {
                    if (dr > 0.0) {
                        spheres[b * 4 + 3] = -1.0;
                    } else {
                        spheres[a * 4 + 3] = -1.0;
                    }
                }
            }
        }

        for (int k = 0; k < size; k++) {
            double r = spheres[k * 4 + 3];
            if (r < 0.0) continue;
            double cx = spheres[k * 4];
            double cy = spheres[k * 4 + 1];
            double cz = spheres[k * 4 + 2];
            int bx0 = Math.max(floor(cx - r), minX);
            int by0 = Math.max(floor(cy - r), minY);
            int bz0 = Math.max(floor(cz - r), minZ);
            int bx1 = Math.max(floor(cx + r), bx0);
            int by1 = Math.max(floor(cy + r), by0);
            int bz1 = Math.max(floor(cz + r), bz0);

            for (int x = bx0; x <= bx1; x++) {
                double fx = (x + 0.5 - cx) / r;
                if (fx * fx >= 1.0) continue;
                for (int y = by0; y <= by1; y++) {
                    double fy = (y + 0.5 - cy) / r;
                    if (fx * fx + fy * fy >= 1.0) continue;
                    for (int z = bz0; z <= bz1; z++) {
                        double fz = (z + 0.5 - cz) / r;
                        if (fx * fx + fy * fy + fz * fz >= 1.0 || !world.canWrite(x, y, z)) continue;
                        int bit = x - minX + (y - minY) * width + (z - minZ) * width * height;
                        if (visited.get(bit)) continue;
                        visited.set(bit);
                        int target = world.matchTarget(x, y, z);
                        if (target < 0) continue;
                        if (!shouldSkipAirCheck(random, discardChance) && isAdjacentToAir(world, x, y, z)) continue;
                        world.place(x, y, z, target);
                        placed++;
                    }
                }
            }
        }
        return placed;
    }

    static boolean shouldSkipAirCheck(Random random, float chance) {
        if (chance <= 0.0F) return true;
        if (chance >= 1.0F) return false;
        return random.nextFloat() >= chance;
    }

    static boolean isAdjacentToAir(World world, int x, int y, int z) {
        return world.isAir(x, y - 1, z) || world.isAir(x, y + 1, z)
            || world.isAir(x - 1, y, z)
            || world.isAir(x + 1, y, z)
            || world.isAir(x, y, z - 1)
            || world.isAir(x, y, z + 1);
    }

    private static double lerp(double t, double a, double b) {
        return a + t * (b - a);
    }

    private static int floor(double v) {
        int i = (int) v;
        return v < i ? i - 1 : i;
    }

    private static int ceil(float v) {
        int i = (int) v;
        return v > i ? i + 1 : i;
    }
}
