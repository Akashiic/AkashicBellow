package com.akashiic.bellow.mixin.early.common;

import net.minecraft.world.World;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.expression.Definition;
import com.llamalad7.mixinextras.expression.Expression;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;

/**
 * {@code Chunk.addEntity} files entities below Y 0 in entity list 0 ({@code chunkCoordY} clamped to 0), but the
 * per-tick check compares it with {@code floor(posY / 16)}: every deep entity was removed from and re-added to its
 * chunk each tick, posting {@code EntityEvent.EnteringChunk} each time. The computed row gets the same clamp.
 */
@Mixin(World.class)
public abstract class MixinWorld_DeepEntityChunk {

    @Definition(id = "floor_double", method = "Lnet/minecraft/util/MathHelper;floor_double(D)I")
    @Definition(id = "posY", field = "Lnet/minecraft/entity/Entity;posY:D")
    @Expression("floor_double(?.posY / 16.0)")
    @ModifyExpressionValue(
        method = "updateEntityWithOptionalForce(Lnet/minecraft/entity/Entity;Z)V",
        at = @At("MIXINEXTRAS:EXPRESSION"),
        require = 1,
        allow = 1)
    private int bellow$entityRow(int row) {
        return Math.max(row, 0);
    }
}
