package com.akashiic.bellow.gen;

/**
 * Stateless randomness keyed by position, so the result never depends on generation order (1.18 uses positional
 * random factories for the same reason). SplitMix64 finalizer over the combined key.
 */
public final class PositionalRandom {

    private PositionalRandom() {}

    public static long mix(long z) {
        z = (z ^ z >>> 30) * 0xBF58476D1CE4E5B9L;
        z = (z ^ z >>> 27) * 0x94D049BB133111EBL;
        return z ^ z >>> 31;
    }

    public static long hash(long seed, int x, int y, int z) {
        long h = mix(seed ^ 0x9E3779B97F4A7C15L);
        h = mix(h ^ x * 0x632BE59BD9B4E019L);
        h = mix(h ^ y * 0x8CB92BA72F3D8DD7L);
        return mix(h ^ z * 0xD6E8FEB86659FD93L);
    }

    /** Uniform float in [0, 1) with 24 bits of precision (same resolution as {@code Random.nextFloat}). */
    public static float nextFloat(long seed, int x, int y, int z) {
        return (hash(seed, x, y, z) >>> 40) * 0x1.0p-24f;
    }

    /** Seed for the decoration of one chunk and one feature (independent streams per feature). */
    public static long featureSeed(long worldSeed, int chunkX, int chunkZ, int featureIndex) {
        return hash(worldSeed ^ 0x6A09E667F3BCC909L, chunkX, featureIndex, chunkZ);
    }
}
