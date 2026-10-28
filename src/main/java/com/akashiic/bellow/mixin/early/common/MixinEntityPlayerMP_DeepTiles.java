package com.akashiic.bellow.mixin.early.common;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayerMP;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

import com.akashiic.bellow.api.BellowAPI;

/**
 * When chunks are sent, the tile entities whose description packet follows are collected with
 * {@code WorldServer.func_147486_a(x, 0, z, x + 15, 256, z + 15)}, which filters {@code yCoord >= 0}: deep signs,
 * spawners, skulls and machines kept their default client state until their block changed. The lower Y bound becomes
 * the floor (same call in Forge and in Crucible).
 */
@Mixin(EntityPlayerMP.class)
public abstract class MixinEntityPlayerMP_DeepTiles {

    @ModifyArg(
        method = "onUpdate()V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/WorldServer;func_147486_a(IIIIII)Ljava/util/List;"),
        index = 1,
        require = 1,
        allow = 1)
    private int bellow$fromFloor(int minY) {
        return Math.min(minY, BellowAPI.getMinY(((Entity) (Object) this).worldObj));
    }
}
