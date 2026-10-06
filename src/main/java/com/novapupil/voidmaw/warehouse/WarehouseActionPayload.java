package com.novapupil.voidmaw.warehouse;

import com.novapupil.voidmaw.VoidMaw;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * C2S warehouse actions the vanilla click packet cannot express: ctrl+left takes a
 * full stack and ctrl+right destroys the whole type. The slot is page-relative
 * (0..44); the server resolves it against the handler's current page.
 */
public record WarehouseActionPayload(int slot, int action) implements CustomPayload {
    public static final int TAKE_STACK = 0;
    public static final int DESTROY_ALL = 1;

    public static final CustomPayload.Id<WarehouseActionPayload> ID =
            new CustomPayload.Id<>(Identifier.of(VoidMaw.MOD_ID, "warehouse_action"));

    public static final PacketCodec<net.minecraft.network.RegistryByteBuf, WarehouseActionPayload> CODEC =
            PacketCodec.tuple(
                    PacketCodecs.VAR_INT, WarehouseActionPayload::slot,
                    PacketCodecs.VAR_INT, WarehouseActionPayload::action,
                    WarehouseActionPayload::new);

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() {
        return ID;
    }
}
