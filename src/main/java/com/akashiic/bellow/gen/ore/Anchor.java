package com.akashiic.bellow.gen.ore;

/** 1.18 {@code VerticalAnchor}: absolute Y, or an offset from the bottom (floor) of the world. */
public final class Anchor {

    private final boolean aboveBottom;
    private final int value;

    private Anchor(boolean aboveBottom, int value) {
        this.aboveBottom = aboveBottom;
        this.value = value;
    }

    public static Anchor absolute(int y) {
        return new Anchor(false, y);
    }

    public static Anchor aboveBottom(int offset) {
        return new Anchor(true, offset);
    }

    public static Anchor bottom() {
        return aboveBottom(0);
    }

    public int resolve(int minY) {
        return this.aboveBottom ? minY + this.value : this.value;
    }

    public boolean isBottom() {
        return this.aboveBottom && this.value == 0;
    }

    @Override
    public String toString() {
        return this.aboveBottom ? "aboveBottom(" + this.value + ")" : "absolute(" + this.value + ")";
    }
}
