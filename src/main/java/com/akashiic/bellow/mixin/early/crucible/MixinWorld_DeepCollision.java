package com.akashiic.bellow.mixin.early.crucible;

import net.minecraft.world.World;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

import com.akashiic.bellow.api.BellowAPI;

/**
 * Crucible only. Spigot rewrote {@code getCollidingBoundingBoxes} to walk chunk by chunk and starts the Y loop at
 * {@code ystart = (k - 1 < 0) ? 0 : k - 1}: without this, nothing below Y 0 collides on the server, so items, mobs,
 * minecarts and falling blocks drop through the deep layer into the void. Both the test and the clamp use the floor.
 */
@Mixin(World.class)
public abstract class MixinWorld_DeepCollision {

    @ModifyConstant(
        method = "getCollidingBoundingBoxes(Lnet/minecraft/entity/Entity;Lnet/minecraft/util/AxisAlignedBB;)Ljava/util/List;",
        constant = @Constant(expandZeroConditions = Constant.Condition.LESS_THAN_ZERO, ordinal = 0),
        require = 1,
        allow = 1)
    private int bellow$startTest(int zero) {
        return BellowAPI.getMinY((World) (Object) this);
    }

    @ModifyConstant(
        method = "getCollidingBoundingBoxes(Lnet/minecraft/entity/Entity;Lnet/minecraft/util/AxisAlignedBB;)Ljava/util/List;",
        constant = @Constant(intValue = 0, ordinal = 0),
        require = 1,
        allow = 1)
    private int bellow$startClamp(int zero) {
        return BellowAPI.getMinY((World) (Object) this);
    }
}
