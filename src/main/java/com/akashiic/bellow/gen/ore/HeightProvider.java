package com.akashiic.bellow.gen.ore;

import java.util.Random;

/**
 * Ports of 1.18 {@code UniformHeight} and {@code TrapezoidHeight}. Sampling consumes the random stream exactly like
 * Minecraft ({@code Mth.randomBetweenInclusive(r, a, b) = r.nextInt(b - a + 1) + a}).
 */
public abstract class HeightProvider {

    protected final Anchor min;
    protected final Anchor max;

    HeightProvider(Anchor min, Anchor max) {
        this.min = min;
        this.max = max;
    }

    public abstract int sample(Random random, int minY);

    /** Probability mass of a single Y value, for verification. */
    public abstract double probability(int y, int minY);

    public final int minInclusive(int minY) {
        return this.min.resolve(minY);
    }

    public final int maxInclusive(int minY) {
        return this.max.resolve(minY);
    }

    /** True for {@code uniform(bottom, X)}: its span (and expected block count) grows with a deeper floor. */
    public abstract boolean isBottomAnchoredUniform();

    static int between(Random r, int lo, int hi) {
        return r.nextInt(hi - lo + 1) + lo;
    }

    public static HeightProvider uniform(Anchor min, Anchor max) {
        return new Uniform(min, max);
    }

    public static HeightProvider triangle(Anchor min, Anchor max) {
        return new Trapezoid(min, max, 0);
    }

    static final class Uniform extends HeightProvider {

        Uniform(Anchor min, Anchor max) {
            super(min, max);
        }

        @Override
        public int sample(Random random, int minY) {
            int lo = this.min.resolve(minY);
            int hi = this.max.resolve(minY);
            if (lo > hi) return lo; // Minecraft logs "Empty height range" and returns min
            return between(random, lo, hi);
        }

        @Override
        public double probability(int y, int minY) {
            int lo = this.min.resolve(minY);
            int hi = this.max.resolve(minY);
            return y < lo || y > hi ? 0.0 : 1.0 / (hi - lo + 1);
        }

        @Override
        public boolean isBottomAnchoredUniform() {
            return this.min.isBottom();
        }

        @Override
        public String toString() {
            return "uniform(" + this.min + ", " + this.max + ")";
        }
    }

    /**
     * {@code i + U[0, m] + U[0, l]} with {@code k = max - min}, {@code l = (k - plateau) / 2}, {@code m = k - l}: the
     * convolution of two discrete uniforms, i.e. a (discrete) trapezoid / triangle.
     */
    static final class Trapezoid extends HeightProvider {

        private final int plateau;

        Trapezoid(Anchor min, Anchor max, int plateau) {
            super(min, max);
            this.plateau = plateau;
        }

        @Override
        public int sample(Random random, int minY) {
            int i = this.min.resolve(minY);
            int j = this.max.resolve(minY);
            if (i > j) return i;
            int k = j - i;
            if (this.plateau >= k) return between(random, i, j);
            int l = (k - this.plateau) / 2;
            int m = k - l;
            return i + between(random, 0, m) + between(random, 0, l);
        }

        @Override
        public double probability(int y, int minY) {
            int i = this.min.resolve(minY);
            int j = this.max.resolve(minY);
            if (i > j) return y == i ? 1.0 : 0.0;
            int k = j - i;
            if (this.plateau >= k) return y < i || y > j ? 0.0 : 1.0 / (k + 1);
            int l = (k - this.plateau) / 2;
            int m = k - l;
            int s = y - i; // s = a + b, a in [0, m], b in [0, l]
            if (s < 0 || s > m + l) return 0.0;
            int lowA = Math.max(0, s - l);
            int highA = Math.min(m, s);
            return (double) (highA - lowA + 1) / ((double) (m + 1) * (l + 1));
        }

        @Override
        public boolean isBottomAnchoredUniform() {
            return false;
        }

        @Override
        public String toString() {
            return "trapezoid(" + this.min + ", " + this.max + ", plateau=" + this.plateau + ")";
        }
    }
}
