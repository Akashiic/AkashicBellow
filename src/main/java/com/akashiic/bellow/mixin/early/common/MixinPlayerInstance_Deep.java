package com.akashiic.bellow.mixin.early.common;

import net.minecraft.network.Packet;
import net.minecraft.server.management.PlayerManager;
import net.minecraft.world.ChunkCoordIntPair;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.ForgeModContainer;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.akashiic.bellow.api.BellowAPI;
import com.akashiic.bellow.net.DeepChanges;
import com.akashiic.bellow.net.DeepNet;
import com.akashiic.bellow.net.IDeepPlayerInstance;
import com.akashiic.bellow.net.IDeepPlayerManager;

/**
 * Keeps block changes below Y 0 out of the vanilla short-packed change list and flushes them with the regular chunk
 * update of the player manager.
 */
@Mixin(targets = "net.minecraft.server.management.PlayerManager$PlayerInstance", priority = 1100)
public abstract class MixinPlayerInstance_Deep implements IDeepPlayerInstance, DeepChanges.Sink {

    @Shadow(remap = false)
    @Final
    PlayerManager this$0;

    @Shadow
    @Final
    private ChunkCoordIntPair chunkLocation;

    @Shadow
    private int numberOfTilesToUpdate;

    @Shadow
    public abstract void sendToAllPlayersWatchingChunk(Packet packet);

    @Unique
    private DeepChanges bellow$changes;

    @Inject(method = "flagChunkForUpdate(III)V", at = @At("HEAD"), cancellable = true, require = 1)
    private void bellow$flagDeep(int localX, int y, int localZ, CallbackInfo ci) {
        if (y >= 0) return;
        ci.cancel();
        WorldServer world = this.this$0.getWorldServer();
        if (y < BellowAPI.getMinY(world)) return;
        DeepChanges changes = this.bellow$changes;
        if (changes == null) changes = this.bellow$changes = new DeepChanges();
        if (changes.isEmpty() && this.numberOfTilesToUpdate == 0) {
            ((IDeepPlayerManager) this.this$0).bellow$queueForUpdate(this);
        }
        changes.add(localX, y, localZ);
    }

    @Inject(method = "sendChunkUpdate()V", at = @At("HEAD"), require = 1)
    private void bellow$sendDeep(CallbackInfo ci) {
        DeepChanges changes = this.bellow$changes;
        if (changes == null || changes.isEmpty()) return;
        changes.flush(
            this.this$0.getWorldServer(),
            this.chunkLocation.chunkXPos,
            this.chunkLocation.chunkZPos,
            ForgeModContainer.clumpingThreshold,
            this);
    }

    @Override
    public void bellow$send(Packet packet) {
        this.sendToAllPlayersWatchingChunk(packet);
    }

    @Override
    public void bellow$resendDeep(int slotMask) {
        WorldServer world = this.this$0.getWorldServer();
        this.sendToAllPlayersWatchingChunk(
            DeepNet.deepOnlyPacket(
                world.getChunkFromChunkCoords(this.chunkLocation.chunkXPos, this.chunkLocation.chunkZPos),
                slotMask));
    }

}
