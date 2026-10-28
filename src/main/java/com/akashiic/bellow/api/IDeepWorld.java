package com.akashiic.bellow.api;

/**
 * Implemented by {@link net.minecraft.world.World} through a mixin.
 * <p>
 * {@code minY} is the lowest valid block Y of the world (inclusive). It is {@code 0} for worlds without deep layers,
 * otherwise a negative multiple of 16 in {@code [DeepConstants.ABSOLUTE_MIN_Y, -16]}.
 */
public interface IDeepWorld {

    int bellow$getMinY();
}
