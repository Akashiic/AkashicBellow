package com.akashiic.bellow.mixin.early.common;

import net.minecraft.world.World;
import net.minecraft.world.WorldServer;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import com.akashiic.bellow.api.BellowAPI;
import com.llamalad7.mixinextras.sugar.Local;

/**
 * Tile entities whose description packet follows a chunk send are collected with
 * {@code func_147486_a(x, 0, z, x + 15, 256, z + 15)}, which filters {@code yCoord >= minY}: deep signs, spawners,
 * skulls and machines kept their default client state until their block changed. Vanilla {@code EntityPlayerMP} and
 * Hodgepodge's fast chunk sending both ask for the whole column that way, so a request from Y 0 to the top starts at
 * the floor instead. Other ranges are left as asked. (Crucible's CraftBukkit walks the chunk's tile entity map itself.)
 */
@Mixin(WorldServer.class)
public abstract class MixinWorldServer_DeepTiles {

    @ModifyVariable(method = "func_147486_a(IIIIII)Ljava/util/List;", at = @At("HEAD"), argsOnly = true, ordinal = 1)
    private int bellow$wholeColumnFromFloor(int minY, @Local(argsOnly = true, ordinal = 4) int maxY) {
        return minY == 0 && maxY >= 255 ? BellowAPI.getMinY((World) (Object) this) : minY;
    }
}
