package com.novapupil.voidmaw.render;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.novapupil.voidmaw.VoidMaw;

import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/** Gson-backed client config; hudX/hudY of -1 means "use the default anchor". */
public class VoidMawConfig {
    public int hudX = -1;
    public int hudY = -1;
    public float hudScale = 1.0f;
    public boolean hudVisible = true;

    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("voidmaw.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static VoidMawConfig load() {
        try {
            if (Files.exists(FILE)) {
                VoidMawConfig config = GSON.fromJson(Files.readString(FILE), VoidMawConfig.class);
                if (config != null) {
                    config.normalize();
                    return config;
                }
            }
        } catch (Exception e) {
            VoidMaw.LOGGER.warn("Failed to read {}, using defaults", FILE, e);
        }
        return new VoidMawConfig();
    }

    public void normalize() {
        hudX = Math.max(-1, hudX);
        hudY = Math.max(-1, hudY);
        hudScale = Float.isFinite(hudScale) ? Math.clamp(hudScale, 0.5f, 4.0f) : 1.0f;
    }

    public static void save(VoidMawConfig config) {
        if (config == null) {
            return;
        }
        config.normalize();
        try {
            Files.createDirectories(FILE.getParent());
            Path tmp = FILE.resolveSibling(FILE.getFileName() + ".tmp");
            Files.writeString(tmp, GSON.toJson(config));
            try {
                Files.move(tmp, FILE, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (java.nio.file.AtomicMoveNotSupportedException e) {
                Files.move(tmp, FILE, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (Exception e) {
            VoidMaw.LOGGER.warn("Failed to write {}", FILE, e);
        }
    }
}
