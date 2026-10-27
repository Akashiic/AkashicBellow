package com.akashiic.bellow.mixin.early.common;

import java.util.List;

import net.minecraft.server.management.PlayerManager;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import com.akashiic.bellow.net.IDeepPlayerManager;

/** Forge: {@code chunkWatcherWithPlayers} is a {@code List}, cleared after each update. */
@Mixin(PlayerManager.class)
public abstract class MixinPlayerManagerQueue_Forge implements IDeepPlayerManager {

    @Shadow
    @Final
    private List<Object> chunkWatcherWithPlayers;

    @Override
    public void bellow$queueForUpdate(Object playerInstance) {
        this.chunkWatcherWithPlayers.add(playerInstance);
    }
}
