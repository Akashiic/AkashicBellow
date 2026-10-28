package com.akashiic.bellow.world;

/**
 * The writable side of {@link com.akashiic.bellow.api.IDeepWorld}, implemented by {@link net.minecraft.world.World}.
 */
public interface IDeepWorldInternal {

    /** Client side only: the server announces the floor of the dimension. */
    void bellow$setMinY(int minY);
}
