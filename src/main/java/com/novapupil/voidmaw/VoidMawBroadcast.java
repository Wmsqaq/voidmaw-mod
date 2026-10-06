package com.novapupil.voidmaw;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.text.TextColor;
import net.minecraft.util.Formatting;

/**
 * Hardcoded join notice: gradient slogan, version line, author line and a personal
 * greeting. Ported from killstreak-mod's KillstreakBroadcast (voice-remorphed style).
 */
public final class VoidMawBroadcast {
    private VoidMawBroadcast() {
    }

    public static void sendJoinNotice(ServerPlayerEntity player) {
        String version = FabricLoader.getInstance().getModContainer(VoidMaw.MOD_ID)
                .map(container -> container.getMetadata().getVersion().getFriendlyString())
                .orElse("unknown");

        MutableText line1 = gradientText("Void Maw All Rights Reserved.",
                new int[]{0x00BFFF, 0x7C4DFF}, new float[]{0f, 1f});

        MutableText line2 = Text.empty();
        line2.append(literal("当前版本：", Formatting.WHITE, false));
        line2.append(literal(version, Formatting.WHITE, true));
        line2.append(literal("（", Formatting.WHITE, false));
        line2.append(literal("虚空之喉", Formatting.LIGHT_PURPLE, true));
        line2.append(literal("）", Formatting.WHITE, false));

        MutableText line3 = gradientText("This mod was created by novapupil ",
                new int[]{0x8B00FF, 0xFF1493, 0x00BFFF, 0x00FF7F},
                new float[]{0f, 0.33f, 0.66f, 1f});

        MutableText line4 = Text.empty();
        line4.append(literal(player.getName().getString(), Formatting.AQUA, true));
        line4.append(literal(" ，你好！", Formatting.GREEN, false));

        player.sendMessage(line1, false);
        player.sendMessage(line2, false);
        player.sendMessage(line3, false);
        player.sendMessage(line4, false);
    }

    private static MutableText literal(String text, Formatting color, boolean bold) {
        return Text.literal(text).styled(style -> {
            if (bold) {
                return style.withColor(color).withBold(true);
            }
            return style.withColor(color);
        });
    }

    private static MutableText gradientText(String text, int[] colors, float[] stops) {
        MutableText result = Text.empty();
        int length = text.length();
        for (int i = 0; i < length; i++) {
            float ratio = length <= 1 ? 0f : i / (float) (length - 1);
            result.append(Text.literal(String.valueOf(text.charAt(i)))
                    .styled(style -> style.withColor(TextColor.fromRgb(interpolateColor(colors, stops, ratio)))));
        }
        return result;
    }

    private static int interpolateColor(int[] colors, float[] stops, float ratio) {
        if (colors.length == 1) {
            return colors[0];
        }
        if (ratio <= stops[0]) {
            return colors[0];
        }
        if (ratio >= stops[stops.length - 1]) {
            return colors[colors.length - 1];
        }
        int idx = 0;
        while (idx < stops.length - 1 && ratio > stops[idx + 1]) {
            idx++;
        }
        float segStart = stops[idx];
        float segEnd = stops[idx + 1];
        float t = segEnd == segStart ? 0f : (ratio - segStart) / (segEnd - segStart);
        int c1 = colors[idx];
        int c2 = colors[idx + 1];
        int r = (int) ((c1 >> 16 & 0xFF) + ((c2 >> 16 & 0xFF) - (c1 >> 16 & 0xFF)) * t);
        int g = (int) ((c1 >> 8 & 0xFF) + ((c2 >> 8 & 0xFF) - (c1 >> 8 & 0xFF)) * t);
        int b = (int) ((c1 & 0xFF) + ((c2 & 0xFF) - (c1 & 0xFF)) * t);
        return (r << 16) | (g << 8) | b;
    }
}
