package com.akashiic.bellow.mixin.early.common;

import java.util.Random;

import net.minecraft.world.SpawnerAnimals;
import net.minecraft.world.World;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import com.akashiic.bellow.BellowConfig;
import com.akashiic.bellow.api.BellowAPI;

/**
 * Spawn attempts pick {@code y = rand(topFilledSegment + 15)}; extend the range down to the floor (1.18 behaviour),
 * keeping the distribution uniform over the whole column.
 */
@Mixin(value = SpawnerAnimals.class, priority = 1100)
public abstract class MixinSpawnerAnimals_Deep {

    @Redirect(
        method = "func_151350_a(Lnet/minecraft/world/World;II)Lnet/minecraft/world/ChunkPosition;",
        at = @At(value = "INVOKE", target = "Ljava/util/Random;nextInt(I)I", ordinal = 2),
        require = 1)
    private static int bellow$spawnHeight(Random random, int bound, World world, int chunkX, int chunkZ) {
        int floor = BellowConfig.spawnMobsInDeep ? BellowAPI.getMinY(world) : 0;
        return floor + random.nextInt(bound - floor);
    }
}
