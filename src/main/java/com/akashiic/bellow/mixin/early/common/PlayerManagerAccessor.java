package com.akashiic.bellow.mixin.early.common;

import net.minecraft.server.management.PlayerManager;
import net.minecraft.util.LongHashMap;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(PlayerManager.class)
public interface PlayerManagerAccessor {

    @Accessor("playerInstances")
    LongHashMap bellow$getPlayerInstances();
}
