package com.novapupil.voidmaw.blackhole;

import com.novapupil.voidmaw.net.MassSyncPayload;
import io.netty.buffer.Unpooled;
import java.util.UUID;

public final class AbsorptionRegressionTest {
    public static void main(String[] args) {
        assert !HoleGeometry.contains(2.4, 2.4, 64, 64, 2.5) : "square corners must not be absorbed";
        assert !HoleGeometry.contains(0, 0, 63.5, 64, 2.5) : "entities below the disc must survive";
        assert HoleGeometry.contains(2, 0, 64, 64, 2.5);
        assert HoleGeometry.firstBlockY(64.5) == 65 : "slab underfoot must survive";
        assert HoleGeometry.firstBlockY(-63.5) == -63;
        assert HoleGeometry.firstBlockY(64) == 64;
        assert HoleGeometry.suctionY(70, 64, false) < 0 : "high mobs must move downward";
        assert HoleGeometry.suctionY(64, 64, true) > 0;
        assert HoleLevel.levelFor(99.9) == 1;
        assert HoleLevel.levelFor(100) == 2;
        assert HoleLevel.levelFor(3000) == 5;
        assert HoleLevel.radiusFor(0) == HoleLevel.radiusFor(1);
        assert HoleLevel.radiusFor(99) == HoleLevel.radiusFor(5);
        var state = new BlackHoleState();
        state.tick();
        assert state.ticksLeft() == 1199;
        state.addMass(10000);
        assert state.ticksLeft() == Balance.MAX_DURATION_TICKS;
        var payload = new MassSyncPayload(UUID.randomUUID(), true, 3, 400, 4.5, -63.5, 6000);
        var buffer = Unpooled.buffer();
        try {
            MassSyncPayload.CODEC.encode(buffer, payload);
            assert payload.equals(MassSyncPayload.CODEC.decode(buffer));
            assert !buffer.isReadable();
            MassSyncPayload.LEGACY_CODEC.encode(buffer, payload);
            assert MassSyncPayload.LEGACY_CODEC.decode(buffer).ticksLeft() == MassSyncPayload.UNKNOWN_TICKS;
            MassSyncPayload.LEGACY_CODEC.encode(buffer, payload);
            buffer.writeShort(1200);
            assert MassSyncPayload.LEGACY_CODEC.decode(buffer).ticksLeft() == 1200;
        } finally {
            buffer.release();
        }
        System.out.println("Absorption geometry, timer, level and network regressions passed");
    }
}
