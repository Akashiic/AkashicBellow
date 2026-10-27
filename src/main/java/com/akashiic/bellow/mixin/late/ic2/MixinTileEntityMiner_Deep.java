package com.akashiic.bellow.mixin.late.ic2;

import net.minecraft.tileentity.TileEntity;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

import com.akashiic.bellow.api.BellowAPI;

/**
 * IC2 Miner: the pipe column ends at Y 0 ({@code y >= 0}, {@code y > 0}, {@code y < 0}). Lowered to the world floor, so
 * a miner on the surface reaches the deep layer and a miner inside it works at all. Each target is one exact
 * comparison (verified against the 2.2.828 bytecode; {@code withDrawPipe} also has a {@code double} compare that must
 * stay untouched, hence the ordinals).
 */
@Pseudo
@Mixin(targets = "ic2.core.block.machine.tileentity.TileEntityMiner", remap = false)
public abstract class MixinTileEntityMiner_Deep {

    private int bellow$floor() {
        return BellowAPI.getMinY(((TileEntity) (Object) this).getWorldObj());
    }

    /** {@code operationHeight >= 0} */
    @ModifyConstant(
        method = "work",
        constant = @Constant(expandZeroConditions = Constant.Condition.LESS_THAN_ZERO, ordinal = 0),
        require = 1)
    private int bellow$workHasPipe(int zero) {
        return this.bellow$floor();
    }

    /** {@code operationHeight > 0 ? digDown(...)} */
    @ModifyConstant(
        method = "work",
        constant = @Constant(expandZeroConditions = Constant.Condition.LESS_THAN_OR_EQUAL_TO_ZERO, ordinal = 0),
        require = 1)
    private int bellow$workCanDig(int zero) {
        return this.bellow$floor();
    }

    /** {@code for (y = yCoord - 1; y >= 0; y--)} */
    @ModifyConstant(
        method = "getOperationHeight",
        constant = @Constant(expandZeroConditions = Constant.Condition.LESS_THAN_ZERO, ordinal = 0),
        require = 1)
    private int bellow$scanFloor(int zero) {
        return this.bellow$floor();
    }

    /** {@code y < 0 || ...} (the second zero jump is {@code energy >= 3.0}) */
    @ModifyConstant(
        method = "withDrawPipe",
        constant = @Constant(expandZeroConditions = Constant.Condition.LESS_THAN_ZERO, ordinal = 0),
        require = 1)
    private int bellow$withdrawFloor(int zero) {
        return this.bellow$floor();
    }

    /** {@code else if (y < 0) return false} */
    @ModifyConstant(
        method = "digDown",
        constant = @Constant(expandZeroConditions = Constant.Condition.LESS_THAN_ZERO, ordinal = 0),
        require = 1)
    private int bellow$digFloor(int zero) {
        return this.bellow$floor();
    }
}
