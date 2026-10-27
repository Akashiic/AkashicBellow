package com.akashiic.bellow.mixin.early.crucible;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

/**
 * CraftBlock reads type, data and light through the chunk with {@code this.y & 0xFF}; see
 * {@link MixinCraftWorld_Deep}. The chunk handle resolves negative Y through the deep sections.
 */
@Pseudo
@Mixin(targets = "org.bukkit.craftbukkit.v1_7_R4.block.CraftBlock", remap = false)
public abstract class MixinCraftBlock_Deep {

    @Shadow
    @Final
    private int y;

    @ModifyConstant(
        method = { "getData()B", "getTypeId()I", "getLightFromSky()B", "getLightFromBlocks()B" },
        constant = @Constant(intValue = 255),
        require = 4,
        allow = 4)
    private int bellow$keepNegativeY(int mask) {
        return this.y < 0 ? -1 : mask;
    }
}
