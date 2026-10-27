package com.akashiic.bellow.mixin.late.ic2;

import net.minecraft.tileentity.TileEntity;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

import com.akashiic.bellow.api.BellowAPI;

/**
 * IC2 Magnetizer: the downward iron fence scan stops at {@code y > 0}; lowered to the floor (fence lifts into a base).
 */
@Pseudo
@Mixin(targets = "ic2.core.block.machine.tileentity.TileEntityMagnetizer", remap = false)
public abstract class MixinTileEntityMagnetizer_Deep {

    @ModifyConstant(
        method = "updateEntityServer",
        constant = @Constant(expandZeroConditions = Constant.Condition.LESS_THAN_OR_EQUAL_TO_ZERO, ordinal = 0),
        require = 1)
    private int bellow$fenceFloor(int zero) {
        return BellowAPI.getMinY(((TileEntity) (Object) this).getWorldObj());
    }
}
