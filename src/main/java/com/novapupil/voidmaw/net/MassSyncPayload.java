package com.novapupil.voidmaw.net;

import com.novapupil.voidmaw.VoidMaw;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import java.util.UUID;

public record MassSyncPayload(UUID playerId, boolean active, int level, double mass, double radius,
                              double mouthY, int ticksLeft) implements CustomPayload {
    public static final CustomPayload.Id<MassSyncPayload> ID =
            new CustomPayload.Id<>(Identifier.of(VoidMaw.MOD_ID, "mass_sync_v3"));
    public static final CustomPayload.Id<MassSyncPayload> V2_ID =
            new CustomPayload.Id<>(Identifier.of(VoidMaw.MOD_ID, "mass_sync_v2"));
    public static final CustomPayload.Id<MassSyncPayload> LEGACY_ID =
            new CustomPayload.Id<>(Identifier.of(VoidMaw.MOD_ID, "mass_sync"));
    public static final int UNKNOWN_TICKS = -1;
    public static final PacketCodec<ByteBuf, MassSyncPayload> CODEC = PacketCodec.of(
            MassSyncPayload::write, MassSyncPayload::read);
    public static final PacketCodec<ByteBuf, MassSyncPayload> LEGACY_CODEC = PacketCodec.of(
            MassSyncPayload::writeLegacy, MassSyncPayload::readLegacy);

    private static void write(MassSyncPayload payload, ByteBuf buf) {
        writeIdentity(payload, buf);
        buf.writeInt(payload.level());
        buf.writeDouble(payload.mass());
        buf.writeDouble(payload.radius());
        buf.writeDouble(payload.mouthY());
        buf.writeInt(payload.ticksLeft());
    }

    private static void writeIdentity(MassSyncPayload payload, ByteBuf buf) {
        buf.writeLong(payload.playerId().getMostSignificantBits());
        buf.writeLong(payload.playerId().getLeastSignificantBits());
        buf.writeBoolean(payload.active());
    }

    private static void writeLegacy(MassSyncPayload payload, ByteBuf buf) {
        writeIdentity(payload, buf);
        buf.writeByte(payload.level());
        buf.writeFloat((float) payload.mass());
        buf.writeFloat((float) payload.radius());
        buf.writeFloat((float) payload.mouthY());
    }

    private static MassSyncPayload read(ByteBuf buf) {
        var id = new UUID(buf.readLong(), buf.readLong());
        return new MassSyncPayload(id, buf.readBoolean(), buf.readInt(), buf.readDouble(),
                buf.readDouble(), buf.readDouble(), buf.readInt());
    }

    private static MassSyncPayload readLegacy(ByteBuf buf) {
        var id = new UUID(buf.readLong(), buf.readLong());
        boolean active = buf.readBoolean();
        int level = buf.readUnsignedByte();
        double mass = buf.readFloat();
        double radius = buf.readFloat();
        double mouth = buf.readFloat();
        int ticks = buf.isReadable() ? buf.readUnsignedShort() : UNKNOWN_TICKS;
        return new MassSyncPayload(id, active, level, mass, radius, mouth, ticks);
    }

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() {
        return ID;
    }
}
