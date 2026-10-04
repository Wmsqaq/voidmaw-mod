package com.novapupil.voidmaw.net;

import com.novapupil.voidmaw.VoidMaw;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import java.util.UUID;

/**
 * S2C sync of one player's black hole state so every client can draw the maw.
 */
public record MassSyncPayload(UUID playerId, boolean active, double mass, double radius) implements CustomPayload {
    public static final CustomPayload.Id<MassSyncPayload> ID =
            new CustomPayload.Id<>(Identifier.of(VoidMaw.MOD_ID, "mass_sync"));

    public static final PacketCodec<ByteBuf, MassSyncPayload> CODEC = PacketCodec.of(
            MassSyncPayload::write,
            MassSyncPayload::read
    );

    private static void write(MassSyncPayload payload, ByteBuf buf) {
        buf.writeLong(payload.playerId().getMostSignificantBits());
        buf.writeLong(payload.playerId().getLeastSignificantBits());
        buf.writeBoolean(payload.active());
        buf.writeFloat((float) payload.mass());
        buf.writeFloat((float) payload.radius());
    }

    private static MassSyncPayload read(ByteBuf buf) {
        UUID playerId = new UUID(buf.readLong(), buf.readLong());
        boolean active = buf.readBoolean();
        double mass = buf.readFloat();
        double radius = buf.readFloat();
        return new MassSyncPayload(playerId, active, mass, radius);
    }

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() {
        return ID;
    }
}
