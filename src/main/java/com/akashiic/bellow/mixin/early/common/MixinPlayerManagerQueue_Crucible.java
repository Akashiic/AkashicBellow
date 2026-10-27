package com.akashiic.bellow.mixin.early.common;

import java.util.Queue;

import net.minecraft.server.management.PlayerManager;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import com.akashiic.bellow.net.IDeepPlayerManager;

/**
 * Crucible: {@code chunkWatcherWithPlayers} ({@code field_72697_d}) is a {@code ConcurrentLinkedQueue} whose entries
 * are removed as they are processed. Named by its SRG name: the type differs from the development mappings.
 */
@Mixin(PlayerManager.class)
public abstract class MixinPlayerManagerQueue_Crucible implements IDeepPlayerManager {

    @Shadow(remap = false)
    @Final
    private Queue<Object> field_72697_d;

    @Override
    public void bellow$queueForUpdate(Object playerInstance) {
        this.field_72697_d.add(playerInstance);
    }
}
