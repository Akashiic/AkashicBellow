package com.akashiic.bellow.mixin.early.common;

import java.util.Set;

import net.minecraft.block.Block;
import net.minecraft.profiler.Profiler;
import net.minecraft.world.ChunkCoordIntPair;
import net.minecraft.world.World;
import net.minecraft.world.WorldProvider;
import net.minecraft.world.WorldServer;
import net.minecraft.world.WorldSettings;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;
import net.minecraft.world.storage.ISaveHandler;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.akashiic.bellow.api.BellowAPI;
import com.akashiic.bellow.api.DeepConstants;
import com.akashiic.bellow.api.IDeepChunk;

/**
 * Random ticks for deep sections. Vanilla only walks {@code chunk.getBlockStorageArray()}; this runs the same
 * algorithm (3 picks per section, or the {@code randomTickSpeed} game rule when a mod such as Et Futurum adds it) over
 * the deep sections of the same active chunk set.
 */
@Mixin(value = WorldServer.class, priority = 1100)
public abstract class MixinWorldServer_DeepTicks extends World {

    @Unique
    private int bellow$lcg = 0x5DEECE66;

    private MixinWorldServer_DeepTicks() {
        super((ISaveHandler) null, null, (WorldSettings) null, (WorldProvider) null, (Profiler) null);
    }

    // Right after super.func_147456_g() (which rebuilds activeChunkSet): Crucible/Spigot clears the set at the end
    // of the method, so TAIL would see it empty there.
    @Inject(
        method = "func_147456_g()V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/World;func_147456_g()V", shift = At.Shift.AFTER),
        require = 1,
        allow = 1)
    private void bellow$tickDeepSections(CallbackInfo ci) {
        int floor = BellowAPI.getMinY(this);
        if (floor >= 0) return;
        int picks = 3;
        if (this.getGameRules()
            .hasRule("randomTickSpeed")) {
            try {
                picks = Integer.parseInt(
                    this.getGameRules()
                        .getGameRuleStringValue("randomTickSpeed")
                        .trim());
            } catch (NumberFormatException e) {
                picks = 3;
            }
            if (picks <= 0) return;
        }
        int firstSlot = (floor >> 4) + DeepConstants.MAX_DEEP_SECTIONS;
        @SuppressWarnings("unchecked")
        Set<ChunkCoordIntPair> active = (Set<ChunkCoordIntPair>) this.activeChunkSet;
        for (ChunkCoordIntPair pos : active) {
            if (!this.chunkExists(pos.chunkXPos, pos.chunkZPos)) continue;
            Chunk chunk = this.getChunkFromChunkCoords(pos.chunkXPos, pos.chunkZPos);
            ExtendedBlockStorage[] deep = ((IDeepChunk) chunk).bellow$peekDeepSections();
            if (deep == null) continue;
            int baseX = pos.chunkXPos << 4;
            int baseZ = pos.chunkZPos << 4;
            for (int slot = firstSlot; slot < deep.length; slot++) {
                ExtendedBlockStorage ebs = deep[slot];
                if (ebs == null || !ebs.getNeedsRandomTick()) continue;
                for (int i = 0; i < picks; i++) {
                    this.bellow$lcg = this.bellow$lcg * 3 + 1013904223;
                    int r = this.bellow$lcg >> 2;
                    int x = r & 15;
                    int z = r >> 8 & 15;
                    int y = r >> 16 & 15;
                    Block block = ebs.getBlockByExtId(x, y, z);
                    if (block.getTickRandomly()) {
                        block.updateTick(this, baseX + x, ebs.getYLocation() + y, baseZ + z, this.rand);
                    }
                }
            }
        }
    }
}
