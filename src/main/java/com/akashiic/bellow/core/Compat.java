package com.akashiic.bellow.core;

/** Environment facts decided at coremod time. Must not reference optional classes. */
public final class Compat {

    /** ArchaicFix's Phosphor mixins are applied (its lighting engine owns World light updates). */
    public static boolean phosphor;

    private Compat() {}
}
