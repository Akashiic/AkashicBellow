package com.akashiic.bellow.net;

/** Implemented by {@code PlayerManager$PlayerInstance} through a mixin. */
public interface IDeepPlayerInstance {

    /** Sends the given deep slots to the players that already received this chunk. */
    void bellow$resendDeep(int slotMask);
}
