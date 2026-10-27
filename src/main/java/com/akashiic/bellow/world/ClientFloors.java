package com.akashiic.bellow.world;

import java.util.concurrent.ConcurrentHashMap;

/** Floors announced by the server, keyed by dimension. Plain data: safe to reference on a dedicated server. */
public final class ClientFloors {

    private static final ConcurrentHashMap<Integer, Integer> FLOORS = new ConcurrentHashMap<>();

    private ClientFloors() {}

    public static void put(int dimension, int minY) {
        FLOORS.put(dimension, minY);
    }

    /** @return the announced floor, or null when the server has not announced this dimension. */
    public static Integer get(int dimension) {
        return FLOORS.get(dimension);
    }

    public static void clear() {
        FLOORS.clear();
    }
}
