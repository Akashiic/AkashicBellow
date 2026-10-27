package com.akashiic.bellow.mixin.late.angelica;

import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.akashiic.bellow.api.BellowAPI;
import com.llamalad7.mixinextras.sugar.Local;

/**
 * Celeritas allocates render sections for {@code [minSection, maxSection)} of every column. Start at the floor, and
 * report deep sections as empty or not from the deep storage (vanilla code would read {@code storageArrays}).
 */
@Pseudo
@Mixin(targets = "com.gtnewhorizons.angelica.rendering.celeritas.AngelicaRenderSectionManager", remap = false)
public abstract class MixinAngelicaRenderSectionManager_Deep {

    @ModifyArg(
        method = "create",
        at = @At(
            value = "INVOKE",
            target = "Lcom/gtnewhorizons/angelica/rendering/celeritas/AngelicaRenderSectionManager;<init>"),
        index = 4,
        require = 1,
        allow = 1)
    private static int bellow$minSection(int minSection, @Local(argsOnly = true) WorldClient world) {
        int floor = BellowAPI.getMinY(world);
        return floor < 0 ? Math.min(minSection, floor >> 4) : minSection;
    }

    @Inject(method = "isSectionVisuallyEmpty(III)Z", at = @At("HEAD"), cancellable = true, require = 1)
    private void bellow$deepEmpty(int x, int y, int z, CallbackInfoReturnable<Boolean> cir) {
        if (y >= 0) return;
        WorldClient world = net.minecraft.client.Minecraft.getMinecraft().theWorld;
        if (world == null || y << 4 < BellowAPI.getMinY(world)) {
            cir.setReturnValue(true);
            return;
        }
        Chunk chunk = world.getChunkFromChunkCoords(x, z);
        ExtendedBlockStorage ebs = chunk.isEmpty() ? null : BellowAPI.getDeepSection(chunk, y);
        cir.setReturnValue(ebs == null || ebs.isEmpty());
    }
}
