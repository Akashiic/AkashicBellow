package com.akashiic.bellow.mixin.early.client;

import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.renderer.RenderGlobal;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

import com.akashiic.bellow.api.BellowAPI;
import com.akashiic.bellow.api.DeepConstants;
import com.llamalad7.mixinextras.expression.Definition;
import com.llamalad7.mixinextras.expression.Expression;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;

/**
 * Vanilla terrain renderer: the vertical grid of {@link net.minecraft.client.renderer.WorldRenderer}s becomes
 * {@code 16 + N} sections tall, starting at the floor of the world. Renderer {@code j} covers
 * {@code [minY + 16j, minY + 16j + 16)}. Not applied when Angelica (Celeritas) replaces terrain rendering.
 */
@Mixin(value = RenderGlobal.class, priority = 1100)
public abstract class MixinRenderGlobal_Deep {

    @Shadow
    private WorldClient theWorld;

    private int bellow$floor() {
        return BellowAPI.getMinY(this.theWorld);
    }

    /**
     * The constructor reserves display lists and occlusion queries for {@code 34 * 34 * 16} renderers, before any world
     * (and floor) is known; the taller grid indexes past them (IndexOutOfBounds on the query buffer at high render
     * distances, overwritten display lists without occlusion). Reserve for the deepest floor: these are only GL names.
     */
    @ModifyConstant(method = "<init>", constant = @Constant(intValue = 16), require = 1, allow = 1)
    private int bellow$reserveDeepRows(int sixteen) {
        return sixteen + DeepConstants.MAX_DEEP_SECTIONS;
    }

    @Definition(id = "renderChunksTall", field = "Lnet/minecraft/client/renderer/RenderGlobal;renderChunksTall:I")
    @Expression("this.renderChunksTall = @(16)")
    @ModifyExpressionValue(method = "loadRenderers()V", at = @At("MIXINEXTRAS:EXPRESSION"), require = 1, allow = 1)
    private int bellow$tallerGrid(int sixteen) {
        return sixteen + (-this.bellow$floor() >> 4);
    }

    @ModifyArg(
        method = "loadRenderers()V",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/WorldRenderer;<init>(Lnet/minecraft/world/World;Ljava/util/List;IIII)V"),
        index = 3,
        require = 1,
        allow = 1)
    private int bellow$rendererY(int y) {
        return y + this.bellow$floor();
    }

    @ModifyArg(
        method = "markRenderersForNewPosition(III)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/WorldRenderer;setPosition(III)V"),
        index = 1,
        require = 1,
        allow = 1)
    private int bellow$repositionY(int y) {
        return y + this.bellow$floor();
    }

    // markBlocksForUpdate(x1, y1, z1, x2, y2, z2): bucketInt(y1) and bucketInt(y2) become grid rows.
    @ModifyArg(
        method = "markBlocksForUpdate(IIIIII)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/util/MathHelper;bucketInt(II)I", ordinal = 1),
        index = 0,
        require = 1,
        allow = 1)
    private int bellow$rowMin(int y) {
        return y - this.bellow$floor();
    }

    @ModifyArg(
        method = "markBlocksForUpdate(IIIIII)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/util/MathHelper;bucketInt(II)I", ordinal = 4),
        index = 0,
        require = 1,
        allow = 1)
    private int bellow$rowMax(int y) {
        return y - this.bellow$floor();
    }
}
