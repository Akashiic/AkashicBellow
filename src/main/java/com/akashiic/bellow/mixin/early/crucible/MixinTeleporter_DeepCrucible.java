package com.akashiic.bellow.mixin.early.crucible;

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
 * Crucible: CraftBukkit moves the scans of {@code placeInExistingPortal}/{@code makePortal} into
 * {@code findPortal(DDDI)} and {@code createPortal(DDDI)}; same loops and fix as {@code MixinTeleporter_Deep}.
 */
@Mixin(Teleporter.class)
public abstract class MixinTeleporter_DeepCrucible {

    @Shadow
    @Final
    private WorldServer worldServerInstance;

    @ModifyConstant(
        method = "findPortal(DDDI)Lnet/minecraft/util/ChunkCoordinates;",
        constant = @Constant(expandZeroConditions = Constant.Condition.LESS_THAN_ZERO, ordinal = 0),
        slice = @Slice(
            from = @At(value = "INVOKE", target = "Lnet/minecraft/world/WorldServer;getActualHeight()I", remap = true)),
        remap = false,
        require = 1,
        allow = 1)
    private int bellow$searchToFloor(int zero) {
        return BellowAPI.getMinY(this.worldServerInstance);
    }

    @ModifyConstant(
        method = "createPortal(DDDI)Z",
        constant = @Constant(expandZeroConditions = Constant.Condition.LESS_THAN_ZERO, ordinal = 0),
        slice = @Slice(
            from = @At(
                value = "INVOKE",
                target = "Lnet/minecraft/world/WorldServer;getActualHeight()I",
                ordinal = 0,
                remap = true)),
        remap = false,
        require = 1,
        allow = 1)
    private int bellow$placeToFloor(int zero) {
        return BellowAPI.getMinY(this.worldServerInstance);
    }

    @ModifyConstant(
        method = "createPortal(DDDI)Z",
        constant = @Constant(expandZeroConditions = Constant.Condition.LESS_THAN_ZERO, ordinal = 0),
        slice = @Slice(
            from = @At(
                value = "INVOKE",
                target = "Lnet/minecraft/world/WorldServer;getActualHeight()I",
                ordinal = 1,
                remap = true)),
        remap = false,
        require = 1,
        allow = 1)
    private int bellow$placeToFloorRelaxed(int zero) {
        return BellowAPI.getMinY(this.worldServerInstance);
    }
}
