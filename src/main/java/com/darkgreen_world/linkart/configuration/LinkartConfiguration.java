package com.darkgreen_world.linkart.configuration;

import com.darkgreen_world.linkart.Linkart;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import net.fabricmc.loader.api.FabricLoader;

public class LinkartConfiguration {
    public static int pathfindingDistance = 6;
    public static int collisionDepth = 4;
    public static double distance = 1.2d;
    public static double breakLoadPerCart = 0.02d;
    public static boolean chunkloading = false;
    public static int chunkloadingRadius = 3;

    // Reads the settings, then rewrites the file with every setting and current comments.
    public static void load() {
        Path file = FabricLoader.getInstance().getConfigDir().resolve("linkart.toml");
        Map<String, String> values = new HashMap<>();

        try {
            if (Files.exists(file)) {
                // Flat key = value lines only
                for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                    int comment = line.indexOf('#');
                    if (comment >= 0) line = line.substring(0, comment);

                    int equals = line.indexOf('=');
                    if (equals >= 0) values.put(line.substring(0, equals).trim(), line.substring(equals + 1).trim());
                }
            }
        } catch (IOException e) {
            Linkart.LOGGER.error("Could not read the Linkart config. Leaving the file and the settings as they are", e);
            return;
        }

        pathfindingDistance = integer(values, "pathfindingDistance", pathfindingDistance);
        collisionDepth = integer(values, "collisionDepth", collisionDepth);
        distance = decimal(values, "distance", distance);
        breakLoadPerCart = decimal(values, "breakLoadPerCart", breakLoadPerCart);
        chunkloading = flag(values, "chunkloading", chunkloading);
        chunkloadingRadius = integer(values, "chunkloadingRadius", chunkloadingRadius);

        try {
            Files.writeString(file, toml(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            Linkart.LOGGER.error("Could not write the Linkart config", e);
        }
    }

    private static String toml() {
        StringBuilder out = new StringBuilder();

        comment(out,
                "Linkart settings. Edit, then restart or run /linkart config reload.",
                "This file is rewritten whenever it is read: values are kept, anything else is not.");

        setting(out, "pathfindingDistance", pathfindingDistance,
                "How far out of place, in blocks, a cart may get before its link breaks, and how far apart two carts",
                "may be when you link them. A safety net for teleports, portals and stuck carts: ordinary running",
                "never gets near it.",
                "Default 6, recommended 4 to 8.");

        setting(out, "collisionDepth", collisionDepth,
                "How many links away the carts of one train ignore each other: no pushing, no getting in the way.",
                "Default 4, recommended 2 to 8. Below 2, neighbouring carts shove each other and the train stutters.");

        setting(out, "distance", distance,
                "Spacing between the centres of neighbouring carts, in blocks. A minecart is 0.98 long.",
                "Default 1.2, recommended 1.0 to 1.5.");

        setting(out, "breakLoadPerCart", breakLoadPerCart,
                "How easily trains come apart. A train of N carts holds as long as",
                "breakLoadPerCart * N * speed * speed stays within 940, with the speed in blocks per second.",
                "This is tested when a cart rounds a bend, comes down from a flight, or the front cart is stopped dead.",
                "Going straight never breaks a link. A train that is too long lets go of the extra carts at its back.",
                "Longest train that gets round a bend whole, by speed:",
                "             8    16    32    48    64    80",
                "   0.01    any   367    91    40    22    14",
                "   0.02    734   183    45    20    11     7",
                "   0.04    367    91    22    10     5     3",
                "Default 0.02, recommended 0.01 to 0.04. 0 makes links unbreakable.");

        setting(out, "chunkloading", chunkloading,
                "Whether moving trains keep the chunks around them loaded, also after a restart. Takes a train of at",
                "least three carts, and costs the server every chunk it holds loaded.",
                "Default false.");

        setting(out, "chunkloadingRadius", chunkloadingRadius,
                "How many chunks around each cart are kept loaded, with chunkloading on.",
                "Default 3, recommended 2 or 3. Below 2 the train itself stops running.");

        return out.toString();
    }

    private static void comment(StringBuilder out, String... lines) {
        for (String line : lines) out.append("# ").append(line).append('\n');
    }

    private static void setting(StringBuilder out, String key, Object value, String... lines) {
        out.append('\n');
        comment(out, lines);
        out.append(key).append(" = ").append(value).append('\n');
    }

    private static int integer(Map<String, String> values, String key, int fallback) {
        String value = values.get(key);
        if (value == null) return fallback;

        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return invalid(key, value, "a whole number", fallback);
        }
    }

    private static double decimal(Map<String, String> values, String key, double fallback) {
        String value = values.get(key);
        if (value == null) return fallback;

        try {
            double number = Double.parseDouble(value);
            return Double.isFinite(number) ? number : invalid(key, value, "a number", fallback);
        } catch (NumberFormatException e) {
            return invalid(key, value, "a number", fallback);
        }
    }

    private static boolean flag(Map<String, String> values, String key, boolean fallback) {
        String value = values.get(key);
        if (value == null) return fallback;
        if (value.equals("true")) return true;
        if (value.equals("false")) return false;
        return invalid(key, value, "true or false", fallback);
    }

    private static <T> T invalid(String key, String value, String expected, T fallback) {
        Linkart.LOGGER.warn("Linkart config: {} = {} is not {}. Using {}", key, value, expected, fallback);
        return fallback;
    }
}
