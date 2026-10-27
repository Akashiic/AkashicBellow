package com.akashiic.bellow.mixin.early.common;

import net.minecraft.world.World;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

import com.akashiic.bellow.api.IDeepWorld;
import com.akashiic.bellow.compat.archaic.DeepLightingInstaller;
import com.akashiic.bellow.core.Compat;
import com.akashiic.bellow.world.ClientFloors;
import com.akashiic.bellow.world.DeepWorlds;
import com.llamalad7.mixinextras.expression.Definition;
import com.llamalad7.mixinextras.expression.Expression;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;

/**
 * Lowers the hard-coded floor of {@link World}.
 * <p>
 * The targets are matched as expressions over the {@code y} parameter ({@code y < 0}, {@code y >= 0}, {@code y = 0}),
 * not by constant ordinal: callbacks injected by other mods add {@code iconst_0/1} instructions and would shift
 * ordinals. For worlds without deep layers the floor is 0 and every handler returns the original value.
 * Every injector has an exact {@code require}/{@code allow}, so a changed target fails at startup.
 */
@Mixin(value = World.class, priority = 1100)
public abstract class MixinWorld_Deep implements IDeepWorld {

    @Unique
    private static final int bellow$UNRESOLVED = Integer.MIN_VALUE;

    @Unique
    private volatile int bellow$minY = bellow$UNRESOLVED;

    @Override
    public int bellow$getMinY() {
        int v = this.bellow$minY;
        if (v == bellow$UNRESOLVED) {
            v = DeepWorlds.resolve((World) (Object) this);
            this.bellow$minY = v;
        }
        return v;
    }

    @Override
    public void bellow$setMinY(int minY) {
        int old = this.bellow$minY;
        this.bellow$minY = minY;
        if (old != minY && ((World) (Object) this).isRemote) ClientFloors.markRendererDirty();
        if (minY < 0 && Compat.phosphor) DeepLightingInstaller.install((World) (Object) this);
    }

    // -------------------------------------------------------------- y >= 0

    @Definition(id = "y", local = @Local(type = int.class, argsOnly = true, ordinal = 1))
    @Expression("y >= 0")
    @ModifyExpressionValue(
        method = "getBlock(III)Lnet/minecraft/block/Block;",
        at = @At("MIXINEXTRAS:EXPRESSION"),
        require = 1,
        allow = 1)
    private boolean bellow$atLeastFloor0(boolean original, @Local(argsOnly = true, ordinal = 1) int y) {
        return y >= this.bellow$getMinY();
    }

    @Definition(id = "y", local = @Local(type = int.class, argsOnly = true, ordinal = 1))
    @Expression("y >= 0")
    @ModifyExpressionValue(method = "blockExists(III)Z", at = @At("MIXINEXTRAS:EXPRESSION"), require = 1, allow = 1)
    private boolean bellow$atLeastFloor1(boolean original, @Local(argsOnly = true, ordinal = 1) int y) {
        return y >= this.bellow$getMinY();
    }

    @Definition(id = "y", local = @Local(type = int.class, argsOnly = true, ordinal = 1))
    @Expression("y >= 0")
    @ModifyExpressionValue(
        method = "setLightValue(Lnet/minecraft/world/EnumSkyBlock;IIII)V",
        at = @At("MIXINEXTRAS:EXPRESSION"),
        require = 1,
        allow = 1)
    private boolean bellow$atLeastFloor2(boolean original, @Local(argsOnly = true, ordinal = 1) int y) {
        return y >= this.bellow$getMinY();
    }

    @Definition(id = "y", local = @Local(type = int.class, argsOnly = true, ordinal = 1))
    @Expression("y >= 0")
    @ModifyExpressionValue(
        method = "getTileEntity(III)Lnet/minecraft/tileentity/TileEntity;",
        at = @At("MIXINEXTRAS:EXPRESSION"),
        require = 1,
        allow = 1)
    private boolean bellow$atLeastFloor3(boolean original, @Local(argsOnly = true, ordinal = 1) int y) {
        return y >= this.bellow$getMinY();
    }

    // checkChunksExist(minX, minY, minZ, maxX, maxY, maxZ): if (maxY >= 0 && minY < 256)
    @Definition(id = "maxY", local = @Local(type = int.class, argsOnly = true, ordinal = 4))
    @Expression("maxY >= 0")
    @ModifyExpressionValue(
        method = "checkChunksExist(IIIIII)Z",
        at = @At("MIXINEXTRAS:EXPRESSION"),
        require = 1,
        allow = 1)
    private boolean bellow$rangeReachesFloor(boolean original, @Local(argsOnly = true, ordinal = 4) int maxY) {
        return maxY >= this.bellow$getMinY();
    }

    // -------------------------------------------------------------- y < 0

    @Definition(id = "y", local = @Local(type = int.class, argsOnly = true, ordinal = 1))
    @Expression("y < 0")
    @ModifyExpressionValue(
        method = "setBlock(IIILnet/minecraft/block/Block;II)Z",
        at = @At("MIXINEXTRAS:EXPRESSION"),
        require = 1,
        allow = 1)
    private boolean bellow$belowFloor0(boolean original, @Local(argsOnly = true, ordinal = 1) int y) {
        return y < this.bellow$getMinY();
    }

    @Definition(id = "y", local = @Local(type = int.class, argsOnly = true, ordinal = 1))
    @Expression("y < 0")
    @ModifyExpressionValue(
        method = "getBlockMetadata(III)I",
        at = @At("MIXINEXTRAS:EXPRESSION"),
        require = 1,
        allow = 1)
    private boolean bellow$belowFloor1(boolean original, @Local(argsOnly = true, ordinal = 1) int y) {
        return y < this.bellow$getMinY();
    }

    @Definition(id = "y", local = @Local(type = int.class, argsOnly = true, ordinal = 1))
    @Expression("y < 0")
    @ModifyExpressionValue(
        method = "setBlockMetadataWithNotify(IIIII)Z",
        at = @At("MIXINEXTRAS:EXPRESSION"),
        require = 1,
        allow = 1)
    private boolean bellow$belowFloor2(boolean original, @Local(argsOnly = true, ordinal = 1) int y) {
        return y < this.bellow$getMinY();
    }

    @Definition(id = "y", local = @Local(type = int.class, argsOnly = true, ordinal = 1))
    @Expression("y < 0")
    @ModifyExpressionValue(
        method = "getFullBlockLightValue(III)I",
        at = @At("MIXINEXTRAS:EXPRESSION"),
        require = 1,
        allow = 1)
    private boolean bellow$belowFloor3(boolean original, @Local(argsOnly = true, ordinal = 1) int y) {
        return y < this.bellow$getMinY();
    }

    @Definition(id = "y", local = @Local(type = int.class, argsOnly = true, ordinal = 1))
    @Expression("y < 0")
    @ModifyExpressionValue(
        method = "getBlockLightValue_do(IIIZ)I",
        at = @At("MIXINEXTRAS:EXPRESSION"),
        require = 1,
        allow = 1)
    private boolean bellow$belowFloor4(boolean original, @Local(argsOnly = true, ordinal = 1) int y) {
        return y < this.bellow$getMinY();
    }

    @Definition(id = "y", local = @Local(type = int.class, argsOnly = true, ordinal = 1))
    @Expression("y < 0")
    @ModifyExpressionValue(
        method = "getSavedLightValue(Lnet/minecraft/world/EnumSkyBlock;III)I",
        at = @At("MIXINEXTRAS:EXPRESSION"),
        require = 1,
        allow = 1)
    private boolean bellow$belowFloor5(boolean original, @Local(argsOnly = true, ordinal = 1) int y) {
        return y < this.bellow$getMinY();
    }

    // Forge's getBlockLightOpacity: if (y < 0 || y >= 256) return 0;
    @Definition(id = "y", local = @Local(type = int.class, argsOnly = true, ordinal = 1))
    @Expression("y < 0")
    @ModifyExpressionValue(
        method = "getBlockLightOpacity(III)I",
        at = @At("MIXINEXTRAS:EXPRESSION"),
        remap = false,
        require = 1,
        allow = 1)
    private boolean bellow$opacityBelowFloor(boolean original, @Local(argsOnly = true, ordinal = 1) int y) {
        return y < this.bellow$getMinY();
    }

    // getSavedLightValue: if (y < 0) y = 0; -> clamp to the floor instead of 0
    @Definition(id = "y", local = @Local(type = int.class, argsOnly = true, ordinal = 1))
    @Expression("y = @(0)")
    @ModifyExpressionValue(
        method = "getSavedLightValue(Lnet/minecraft/world/EnumSkyBlock;III)I",
        at = @At("MIXINEXTRAS:EXPRESSION"),
        require = 1,
        allow = 1)
    private int bellow$clampToFloor(int zero) {
        return this.bellow$getMinY();
    }
}
