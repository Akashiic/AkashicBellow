package com.akashiic.bellow.mixin.early.common;

import net.minecraft.world.World;
import net.minecraft.world.WorldServer;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.akashiic.bellow.compat.archaic.DeepLightingInstaller;

/**
 * Selected only when ArchaicFix's Phosphor is active. Client worlds are handled when the server announces the floor.
 */
@Mixin(value = World.class, priority = 1100)
public abstract class MixinWorld_DeepPhosphor {

    @Inject(method = "<init>", at = @At("RETURN"), require = 1)
    private void bellow$installDeepLighting(CallbackInfo ci) {
        if ((Object) this instanceof WorldServer) DeepLightingInstaller.install((World) (Object) this);
    }
}
