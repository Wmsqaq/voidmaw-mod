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
        assert HoleLevel.radiusFor(99) > HoleLevel.radiusFor(5);
        assert HoleLevel.levelFor(5700) == 6;
        assert HoleLevel.levelFor(HoleLevel.levelThreshold(1000)) == 1000;
        assert HoleLevel.levelFor(HoleLevel.levelThreshold(1000) - 1) == 999;
        assert HoleLevel.nextLevelMass(1000) > HoleLevel.levelThreshold(1000);
        assert HoleLevel.explosionPowerFor(1) == 3.0f;
        assert HoleLevel.explosionPowerFor(2) == 3.75f;
        assert Math.abs(HoleLevel.explosionPowerFor(5) - 5.12132f) < 0.001f;
        assert Math.abs(HoleLevel.explosionPowerFor(10) - 6.89711f) < 0.001f;
        assert HoleLevel.explosionPowerFor(100) - HoleLevel.explosionPowerFor(99)
                < HoleLevel.explosionPowerFor(2) - HoleLevel.explosionPowerFor(1);
        assert HoleLevel.detonationPowerFor(1, 0) == 3.0f;
        assert HoleLevel.detonationPowerFor(5, 3000) > HoleLevel.detonationPowerFor(5, 1000)
                : "stored mass must add punch";
        assert HoleLevel.detonationPowerFor(1, 10_000_000) == 15.0f
                : "stored mass adds at most 12 on top of the level floor";
        assert HoleLevel.detonationPowerFor(100, 10_000_000) == 24.0f : "total power is capped";
        assert Double.isFinite(HoleLevel.radiusFor(Integer.MAX_VALUE));
        assert DiscSweep.minChunk(8.0, 2.5) == 0 && DiscSweep.maxChunk(8.0, 2.5) == 0;
        assert DiscSweep.minChunk(0.0, 2.5) == -1 && DiscSweep.maxChunk(0.0, 2.5) == 0
                : "a disc over a chunk border spans two chunks";
        assert DiscSweep.volume(-1, 0, -1, 0) == 4;
        assert DiscSweep.chunkX(3, -1, 2) == 0 && DiscSweep.chunkZ(3, -1, 2) == 0;
        assert DiscSweep.chunkX(0, -1, 2) == -1 && DiscSweep.chunkZ(0, -1, 2) == -1;
        DiscSweep.Row center = DiscSweep.rowRange(0, 0, 8, 8.0, 8.0, 2.5);
        assert !center.empty() && center.start() <= 6 && center.endInclusive() >= 9
                : "the column through the center must cover the circle";
        DiscSweep.Row outside = DiscSweep.rowRange(0, 0, 0, 8.0, 8.0, 2.5);
        assert outside.empty() : "columns beyond the radius must be skipped";
        DiscSweep.Row rim = DiscSweep.rowRange(0, 0, 6, 8.0, 8.0, 2.5);
        assert !rim.empty() : "the rim column at the radius edge is still inside";
        // Asymmetric chunk coords: the z bounds must come from the chunk's Z coord,
        // not its X coord - mixing them collapses the disc into a line along X.
        DiscSweep.Row shifted = DiscSweep.rowRange(1, -2, 8, 24.5, -30.5, 2.5);
        assert !shifted.empty() && shifted.start() <= 1 && shifted.endInclusive() >= 2
                : "the center column must be covered when the chunk spans differ";
        for (int lz = shifted.start(); lz <= shifted.endInclusive(); lz++) {
            double worldZ = ((-2) << 4) + lz + 0.5 - (-30.5);
            assert worldZ * worldZ <= (2.5 + 0.5) * (2.5 + 0.5)
                    : "every row cell must sit within the circle's generous rim";
        }
        assert DiscSweep.rowRange(1, -2, 0, 24.5, -30.5, 2.5).empty()
                : "columns beyond the radius must be skipped in shifted chunks too";
        assert MassTables.liquidMass(net.minecraft.block.Blocks.WATER.getDefaultState()) == 0.0
                : "water must be free";
        assert MassTables.liquidMass(net.minecraft.block.Blocks.LAVA.getDefaultState()) == 1.2;
        assert MassTables.liquidMass(net.minecraft.block.Blocks.LAVA.getDefaultState())
                > MassTables.liquidMass(net.minecraft.block.Blocks.WATER.getDefaultState())
                : "lava must outweigh water";
        try {
            var file = java.nio.file.Files.createTempDirectory("voidmaw-progress-test-").resolve("test.json");
            var growth = new HoleProgress(file);
            var firstForm = new BlackHoleState(growth);
            firstForm.addMass(5700);
            firstForm.retainProgress();
            var nextForm = new BlackHoleState(growth);
            assert nextForm.mass() == 5700;
            assert nextForm.level() == 6;
            assert nextForm.ticksLeft() == Balance.BASE_DURATION_TICKS;
            var reload = new HoleProgress(file);
            assert reload.mass() == 5700;
            reload.retain(1);
            assert reload.mass() == 5700 : "retain must never lower saved mass";
            reload.reset();
            reload.flush();
            var cleared = new HoleProgress(file);
            assert cleared.mass() == 0 : "reset must disperse growth on disk";
            java.nio.file.Files.delete(file);
            java.nio.file.Files.delete(file.getParent());
        } catch (java.io.IOException failure) {
            throw new AssertionError(failure);
        }
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
            var large = new MassSyncPayload(UUID.randomUUID(), true, 1000, 900_000_000.125,
                    HoleLevel.radiusFor(1000), -63.5, 6000);
            MassSyncPayload.CODEC.encode(buffer, large);
            assert large.equals(MassSyncPayload.CODEC.decode(buffer));
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
