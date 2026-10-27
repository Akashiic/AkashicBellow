package com.akashiic.bellow.mixin.late.angelica;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.akashiic.bellow.compat.angelica.AngelicaFloor;

/** Light lookups clamp Y to 0..255: clamp to the floor instead. */
@Pseudo
@Mixin(targets = "com.gtnewhorizons.angelica.compat.mojang.CompatMathHelper", remap = false)
public abstract class MixinCompatMathHelper_Deep {

    @Inject(method = "clampHeight(I)I", at = @At("HEAD"), cancellable = true, require = 1)
    private static void bellow$clamp(int y, CallbackInfoReturnable<Integer> cir) {
        if (y >= 0) return;
        int floor = AngelicaFloor.current();
        if (floor < 0) cir.setReturnValue(Math.max(y, floor));
    }
}
