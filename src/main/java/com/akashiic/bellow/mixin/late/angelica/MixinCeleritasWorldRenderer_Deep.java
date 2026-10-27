package com.akashiic.bellow.mixin.late.angelica;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.akashiic.bellow.compat.angelica.AngelicaFloor;

/** The section graph, culling and block-update scheduling ask the renderer for the bottom of the world. */
@Pseudo
@Mixin(targets = "com.gtnewhorizons.angelica.rendering.celeritas.CeleritasWorldRenderer", remap = false)
public abstract class MixinCeleritasWorldRenderer_Deep {

    @Inject(method = "getMinimumBuildHeight()I", at = @At("HEAD"), cancellable = true, require = 1)
    private void bellow$floor(CallbackInfoReturnable<Integer> cir) {
        int floor = AngelicaFloor.current();
        if (floor < 0) cir.setReturnValue(floor);
    }
}
