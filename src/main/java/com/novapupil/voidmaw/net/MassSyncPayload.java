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
public record MassSyncPayload(UUID playerId, boolean active, int level, double mass, double radius,
                              double mouthY, int ticksLeft) implements CustomPayload {
    // beta.19 appended a timer to the unversioned channel. Use a new channel so
    // older clients skip an unknown payload instead of failing on trailing bytes.
    public static final CustomPayload.Id<MassSyncPayload> ID =
            new CustomPayload.Id<>(Identifier.of(VoidMaw.MOD_ID, "mass_sync_v2"));
    public static final CustomPayload.Id<MassSyncPayload> LEGACY_ID =
            new CustomPayload.Id<>(Identifier.of(VoidMaw.MOD_ID, "mass_sync"));
    public static final int UNKNOWN_TICKS = -1;

    public static final PacketCodec<ByteBuf, MassSyncPayload> CODEC = PacketCodec.of(
            MassSyncPayload::write,
            MassSyncPayload::read
    );

    /** Receive both the 30-byte beta.18 and 32-byte beta.19 formats. */
    public static final PacketCodec<ByteBuf, MassSyncPayload> LEGACY_CODEC = PacketCodec.of(
            (payload, buf) -> writeBody(payload, buf),
            buf -> readBody(buf, true)
    );

    private static void writeBody(MassSyncPayload payload, ByteBuf buf) {
        buf.writeLong(payload.playerId().getMostSignificantBits());
        buf.writeLong(payload.playerId().getLeastSignificantBits());
        buf.writeBoolean(payload.active());
        buf.writeByte(payload.level());
        buf.writeFloat((float) payload.mass());
        buf.writeFloat((float) payload.radius());
        buf.writeFloat((float) payload.mouthY());
    }

    private static void write(MassSyncPayload payload, ByteBuf buf) {
        writeBody(payload, buf);
        buf.writeShort(payload.ticksLeft());
    }

    private static MassSyncPayload read(ByteBuf buf) {
        return readBody(buf, false);
    }

    private static MassSyncPayload readBody(ByteBuf buf, boolean legacy) {
        UUID playerId = new UUID(buf.readLong(), buf.readLong());
        boolean active = buf.readBoolean();
        int level = buf.readByte();
        double mass = buf.readFloat();
        double radius = buf.readFloat();
        double mouthY = buf.readFloat();
        int ticksLeft = legacy && !buf.isReadable() ? UNKNOWN_TICKS : buf.readUnsignedShort();
        return new MassSyncPayload(playerId, active, level, mass, radius, mouthY, ticksLeft);
    }

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() {
        return ID;
    }
}
