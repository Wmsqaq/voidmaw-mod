package com.novapupil.voidmaw.blackhole;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.novapupil.voidmaw.VoidMaw;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.WorldSavePath;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.UUID;

/** Persistent growth is separate from the temporary active form and its timer. */
public final class HoleProgress {
    private static final Map<MinecraftServer, Map<UUID, HoleProgress>> LOADED = new IdentityHashMap<>();
    private final Path file;
    private double mass;
    private boolean dirty;
    private boolean loadFailed;

    HoleProgress(Path file) {
        this.file = file;
        load();
    }

    public static HoleProgress get(MinecraftServer server, UUID player) {
        return LOADED.computeIfAbsent(server, ignored -> new HashMap<>()).computeIfAbsent(player,
                id -> new HoleProgress(server.getSavePath(WorldSavePath.ROOT)
                        .resolve("data/voidmaw/progress/" + id + ".json")));
    }

    public double mass() {
        if (loadFailed) {
            throw new IllegalStateException("Cannot use unreadable black-hole progress: " + file);
        }
        return mass;
    }

    public void retain(double currentMass) {
        if (!Double.isFinite(currentMass) || currentMass < mass || loadFailed) {
            return;
        }
        if (mass != currentMass) {
            mass = currentMass;
            dirty = true;
        }
    }

    /** Detonation and death disperse the growth; only ever touches a readable file. */
    public void reset() {
        if (loadFailed || mass == 0) {
            return;
        }
        mass = 0;
        dirty = true;
    }

    private void load() {
        if (!Files.exists(file)) {
            return;
        }
        try {
            var root = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
            double saved = root.get("mass").getAsDouble();
            if (!Double.isFinite(saved) || saved < 0) {
                throw new IllegalArgumentException("Invalid saved mass");
            }
            mass = saved;
        } catch (IOException | RuntimeException failure) {
            loadFailed = true;
            VoidMaw.LOGGER.error("Cannot load hole progress {}; preserving original file", file, failure);
        }
    }

    public void flush() {
        if (!dirty || loadFailed) {
            return;
        }
        Path temporary = null;
        try {
            Files.createDirectories(file.getParent());
            var root = new JsonObject();
            root.addProperty("mass", mass);
            root.addProperty("level", HoleLevel.levelFor(mass));
            temporary = Files.createTempFile(file.getParent(), "progress-", ".tmp");
            Files.writeString(temporary, root.toString());
            try {
                Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
            }
            dirty = false;
        } catch (IOException | RuntimeException failure) {
            VoidMaw.LOGGER.error("Cannot save hole progress {}; will retry", file, failure);
        } finally {
            if (temporary != null) {
                try {
                    Files.deleteIfExists(temporary);
                } catch (IOException failure) {
                    VoidMaw.LOGGER.warn("Cannot remove progress temp file {}", temporary, failure);
                }
            }
        }
    }

    public static void flushAll(MinecraftServer server) {
        var entries = LOADED.get(server);
        if (entries != null) {
            entries.values().forEach(HoleProgress::flush);
        }
    }

    public static void unload(MinecraftServer server) {
        flushAll(server);
        LOADED.remove(server);
    }
}
