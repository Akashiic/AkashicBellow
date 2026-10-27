package com.akashiic.bellow.gen.ore;

import java.util.Random;

/**
 * One 1.18-style placed feature: {@code count (or rarity) -> in square -> height -> [biome] -> ore vein}.
 */
public final class DeepFeature {

    public enum Kind {
        /** Ore with a stone variant and a deepslate variant (chosen by the replaced block). */
        ORE,
        /** Single block replacing any base stone (stone, deepslate, tuff): gravel, tuff. */
        BLOB
    }

    public final String name;
    public final Kind kind;
    /** Registry name of the block (modid:name) and meta. */
    public final String block;
    public final int meta;
    public final int size;
    public final float discardOnAir;
    public final HeightProvider height;
    /** Fixed count, or {@code countMax > countMin} for {@code UniformInt(countMin, countMax)}. */
    public final int countMin;
    public final int countMax;
    /** {@code RarityFilter.onAverageOnceEvery(rarity)}; 0 when unused. */
    public final int rarity;
    public final boolean mountainsOnly;

    private DeepFeature(String name, Kind kind, String block, int meta, int size, float discardOnAir,
        HeightProvider height, int countMin, int countMax, int rarity, boolean mountainsOnly) {
        this.name = name;
        this.kind = kind;
        this.block = block;
        this.meta = meta;
        this.size = size;
        this.discardOnAir = discardOnAir;
        this.height = height;
        this.countMin = countMin;
        this.countMax = countMax;
        this.rarity = rarity;
        this.mountainsOnly = mountainsOnly;
    }

    public static DeepFeature ore(String name, String block, int size, float discard, int count,
        HeightProvider height) {
        return new DeepFeature(name, Kind.ORE, block, 0, size, discard, height, count, count, 0, false);
    }

    public static DeepFeature blob(String name, String block, int size, int count, HeightProvider height) {
        return new DeepFeature(name, Kind.BLOB, block, 0, size, 0.0F, height, count, count, 0, false);
    }

    public DeepFeature withCountRange(int min, int max) {
        return new DeepFeature(
            this.name,
            this.kind,
            this.block,
            this.meta,
            this.size,
            this.discardOnAir,
            this.height,
            min,
            max,
            0,
            this.mountainsOnly);
    }

    public DeepFeature withRarity(int oneIn) {
        return new DeepFeature(
            this.name,
            this.kind,
            this.block,
            this.meta,
            this.size,
            this.discardOnAir,
            this.height,
            1,
            1,
            oneIn,
            this.mountainsOnly);
    }

    public DeepFeature inMountains() {
        return new DeepFeature(
            this.name,
            this.kind,
            this.block,
            this.meta,
            this.size,
            this.discardOnAir,
            this.height,
            this.countMin,
            this.countMax,
            this.rarity,
            true);
    }

    /**
     * Expected number of placement attempts per chunk before height clipping, including the optional density scaling
     * for deeper floors: for {@code uniform(bottom, X)} the span is {@code X - minY + 1} instead of {@code X + 65}.
     */
    public double expectedAttempts(int minY, boolean scaleBottomAnchored, double multiplier) {
        double base = this.rarity > 0 ? 1.0 / this.rarity : (this.countMin + this.countMax) / 2.0;
        return base * this.densityScale(minY, scaleBottomAnchored) * multiplier;
    }

    public double densityScale(int minY, boolean scaleBottomAnchored) {
        if (!scaleBottomAnchored || minY >= -64 || !this.height.isBottomAnchoredUniform()) return 1.0;
        int top = this.height.maxInclusive(minY);
        return (double) (top - minY + 1) / (double) (top + 64 + 1);
    }

    /**
     * Number of placement attempts for one chunk. With the defaults (floor -64, multiplier 1) this consumes the random
     * stream exactly like Minecraft: {@code CountPlacement} with a constant consumes nothing, {@code UniformInt} one
     * {@code nextInt}, {@code RarityFilter} one {@code nextFloat}. A fractional expected count adds one Bernoulli draw
     * so
     * that the expectation is exact.
     */
    public int attempts(Random random, int minY, boolean scaleBottomAnchored, double multiplier) {
        int n;
        if (this.rarity > 0) {
            n = random.nextFloat() < 1.0F / this.rarity ? 1 : 0;
        } else if (this.countMax > this.countMin) {
            n = random.nextInt(this.countMax - this.countMin + 1) + this.countMin;
        } else {
            n = this.countMin;
        }
        double scale = this.densityScale(minY, scaleBottomAnchored) * multiplier;
        if (scale == 1.0 || n == 0) return n;
        double expected = n * scale;
        int whole = (int) Math.floor(expected);
        double frac = expected - whole;
        return frac > 0.0 && random.nextDouble() < frac ? whole + 1 : whole;
    }
}
