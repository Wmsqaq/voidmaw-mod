package com.novapupil.voidmaw.net;

import com.novapupil.voidmaw.VoidMaw;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * C2S request fired by the warehouse hotkey; the server opens the sender's warehouse.
 */
public record OpenWarehousePayload() implements CustomPayload {
    public static final OpenWarehousePayload INSTANCE = new OpenWarehousePayload();

    public static final CustomPayload.Id<OpenWarehousePayload> ID =
            new CustomPayload.Id<>(Identifier.of(VoidMaw.MOD_ID, "open_warehouse"));

    public static final PacketCodec<ByteBuf, OpenWarehousePayload> CODEC = PacketCodec.unit(INSTANCE);

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() {
        return ID;
    }
}
