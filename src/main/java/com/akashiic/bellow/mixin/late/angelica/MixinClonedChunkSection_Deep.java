package com.akashiic.bellow.mixin.late.angelica;

import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.akashiic.bellow.api.BellowAPI;
import com.gtnewhorizons.angelica.compat.mojang.ChunkSectionPos;

/** Copies of sections for the meshing threads: read deep sections from the deep storage. */
@Pseudo
@Mixin(targets = "com.gtnewhorizons.angelica.rendering.celeritas.world.cloned.ClonedChunkSection", remap = false)
public abstract class MixinClonedChunkSection_Deep {

    @Inject(method = "isOutOfBuildLimitVertically", at = @At("HEAD"), cancellable = true, require = 1)
    private static void bellow$limits(World world, int y, CallbackInfoReturnable<Boolean> cir) {
        int floor = BellowAPI.getMinY(world);
        if (floor < 0) cir.setReturnValue(y < floor || y >= 256);
    }

    @Inject(method = "getChunkSection", at = @At("HEAD"), cancellable = true, require = 1)
    private void bellow$deepSection(Chunk chunk, ChunkSectionPos pos,
        CallbackInfoReturnable<ExtendedBlockStorage> cir) {
        if (pos.y >= 0) return;
        cir.setReturnValue(
            pos.y << 4 < BellowAPI.getMinY(chunk.worldObj) ? null : BellowAPI.getDeepSection(chunk, pos.y));
    }
}
