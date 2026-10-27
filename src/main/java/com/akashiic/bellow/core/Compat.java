package com.akashiic.bellow.core;

/** Environment facts decided at coremod time. Must not reference optional classes. */
public final class Compat {

    /** ArchaicFix's Phosphor mixins are applied (its lighting engine owns World light updates). */
    public static boolean phosphor;

    /** Crucible/Thermos server: CraftBukkit patches reshape some vanilla methods (decided at coremod time). */
    public static boolean crucible;

    /** Client with Angelica's Celeritas renderer (set by the late mixin loader). */
    public static boolean angelica;

    private Compat() {}
}
