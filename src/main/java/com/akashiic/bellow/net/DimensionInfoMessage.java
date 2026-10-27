package com.akashiic.bellow.net;

import com.akashiic.bellow.world.ClientFloors;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import io.netty.buffer.ByteBuf;

/** Server -> client: floor of a dimension, sent on login, respawn and dimension change (before its chunks). */
public final class DimensionInfoMessage implements IMessage {

    int dimension;
    int minY;

    public DimensionInfoMessage() {}

    public DimensionInfoMessage(int dimension, int minY) {
        this.dimension = dimension;
        this.minY = minY;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        this.dimension = buf.readInt();
        this.minY = buf.readShort();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(this.dimension);
        buf.writeShort(this.minY);
    }

    /** Runs on the network thread: only records the floor; {@code ClientEvents} applies it on the client thread. */
    public static final class Handler implements IMessageHandler<DimensionInfoMessage, IMessage> {

        @Override
        public IMessage onMessage(DimensionInfoMessage message, MessageContext ctx) {
            ClientFloors.put(message.dimension, message.minY);
            return null;
        }
    }
}
