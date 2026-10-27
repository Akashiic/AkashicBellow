package com.akashiic.bellow.compat.angelica;

import net.minecraft.tileentity.TileEntity;

import com.gtnewhorizons.angelica.utils.AwaitingDescriptor;

/** Only loaded when {@link com.akashiic.bellow.core.Compat#angelica} is set. */
public final class AngelicaTiles {

    private AngelicaTiles() {}

    /** Same marking Angelica applies to the tile entities it creates in {@code Chunk.fillChunk}. */
    public static void awaitDescriptor(TileEntity te) {
        AwaitingDescriptor.begin(te);
    }
}
