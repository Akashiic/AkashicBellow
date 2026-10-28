package com.akashiic.bellow.mixin.early.common;

import net.minecraft.world.Teleporter;
import net.minecraft.world.WorldServer;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.Slice;

import com.akashiic.bellow.api.BellowAPI;

/**
 * Forge. Portal search and placement scan {@code for (y = getActualHeight() - 1; y >= 0; --y)}: portals below Y 0
 * were never found (every trip back built a new one above 0). The loop bound is the first zero test after each
 * {@code getActualHeight()} call (javac puts the condition at the top of the loop); relative {@code >= 0} tests of
 * the frame offsets come later and are left alone. Crucible: {@code MixinTeleporter_DeepCrucible}.
 */
@Mixin(Teleporter.class)
public abstract class MixinTeleporter_Deep {

    @Shadow
    @Final
    private WorldServer worldServerInstance;

    @ModifyConstant(
        method = "placeInExistingPortal(Lnet/minecraft/entity/Entity;DDDF)Z",
        constant = @Constant(expandZeroConditions = Constant.Condition.LESS_THAN_ZERO, ordinal = 0),
        slice = @Slice(from = @At(value = "INVOKE", target = "Lnet/minecraft/world/WorldServer;getActualHeight()I")),
        require = 1,
        allow = 1)
    private int bellow$searchToFloor(int zero) {
        return BellowAPI.getMinY(this.worldServerInstance);
    }

    @ModifyConstant(
        method = "makePortal(Lnet/minecraft/entity/Entity;)Z",
        constant = @Constant(expandZeroConditions = Constant.Condition.LESS_THAN_ZERO, ordinal = 0),
        slice = @Slice(
            from = @At(value = "INVOKE", target = "Lnet/minecraft/world/WorldServer;getActualHeight()I", ordinal = 0)),
        require = 1,
        allow = 1)
    private int bellow$placeToFloor(int zero) {
        return BellowAPI.getMinY(this.worldServerInstance);
    }

    @ModifyConstant(
        method = "makePortal(Lnet/minecraft/entity/Entity;)Z",
        constant = @Constant(expandZeroConditions = Constant.Condition.LESS_THAN_ZERO, ordinal = 0),
        slice = @Slice(
            from = @At(value = "INVOKE", target = "Lnet/minecraft/world/WorldServer;getActualHeight()I", ordinal = 1)),
        require = 1,
        allow = 1)
    private int bellow$placeToFloorRelaxed(int zero) {
        return BellowAPI.getMinY(this.worldServerInstance);
    }
}
