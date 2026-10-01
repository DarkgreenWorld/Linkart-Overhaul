package com.darkgreen_world.linkart.configuration;

import com.darkgreen_world.linkart.Linkart;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import net.fabricmc.loader.api.FabricLoader;

public class LinkartConfiguration {
    public static int pathfindingDistance = 6;
    public static int collisionDepth = 8;
    public static double distance = 1.2d;
    public static double breakSpeed = 70d;
    public static double breakLoadPerCart = 0.02d;
    public static boolean chunkloading = false;
    public static int chunkloadingRadius = 3;

    private static final String FILE = "linkart.toml";
    // Where the settings were kept before, without a word of explanation
    private static final String OLD_FILE = "linkart.json";

    // Reads the settings, then writes the file back out so that it has every setting and this version's comments.
    public static void load() {
        Path directory = FabricLoader.getInstance().getConfigDir();
        Path file = directory.resolve(FILE);
        Path oldFile = directory.resolve(OLD_FILE);
        Map<String, String> values = new HashMap<>();

        try {
            if (Files.exists(file)) {
                readToml(file, values);
            } else if (Files.exists(oldFile)) {
                readJson(oldFile, values);
            }
        } catch (Exception e) {
            Linkart.LOGGER.error("Could not read the Linkart config. Leaving the file and the settings as they are", e);
            return;
        }

        pathfindingDistance = integer(values, "pathfindingDistance", pathfindingDistance);
        collisionDepth = integer(values, "collisionDepth", collisionDepth);
        distance = decimal(values, "distance", distance);
        breakSpeed = decimal(values, "breakSpeed", breakSpeed);
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
                "Linkart settings. Change the values and restart, or run /linkart config reload.",
                "This file is written anew every time it is read: the values are kept, anything else is not.");

        setting(out, "pathfindingDistance", pathfindingDistance,
                "How far out of place, in blocks, a cart may get before its link breaks. Also how far apart two carts",
                "may be when you link them.",
                "Carts follow the track exactly, so in ordinary running none gets anywhere near this. It is what lets go",
                "of a cart that was teleported, went through a portal or got stuck. Off the rails, twice the train's",
                "speed is allowed on top of it.",
                "Default: 6. Recommended: 4 to 8.",
                "Lower: links break when a cart is briefly held up, such as on unpowered powered rails at speed.",
                "Higher: a cart that is held up stays linked for longer and jumps back into place from further away.");

        setting(out, "collisionDepth", collisionDepth,
                "How many links away, in either direction, the carts of one train ignore each other: they don't push",
                "each other, and on rails they pass through each other.",
                "Default: 8. Recommended: 8 or more.",
                "Lower: carts of the same train bump into each other where the track doubles back next to itself, or",
                "when the train bunches up, and the train slows down or stops.",
                "Higher: no drawback worth mentioning.");

        setting(out, "distance", distance,
                "The spacing kept between the centres of neighbouring carts, in blocks. A minecart is 0.98 long.",
                "Default: 1.2. Recommended: 1.0 to 1.5.",
                "Lower: below 0.98 the carts overlap.",
                "Higher: wider gaps between the carts, and a longer train.");

        setting(out, "breakSpeed", breakSpeed,
                "The speed, in blocks per second, at which a link that is pulling a single cart snaps. See",
                "breakLoadPerCart for links pulling more than one.",
                "A link is put to this test in three situations:",
                " - a cart is swung round a bend (a quarter turn or more within one tick; a 45 degree kink counts half)",
                " - a cart comes down from a flight, at the speed it comes down with",
                " - the front cart is stopped dead, by the speed it lost in that tick",
                "Going straight never breaks a link, however fast.",
                "Default: 70.0. 0 makes links unbreakable.",
                "Recommended: somewhat above the speed your trains take their bends at, such as 70 for lines run at 60.",
                "Lower: trains shed carts in bends and hard stops they used to survive.",
                "Higher: only really fast trains come apart.");

        setting(out, "breakLoadPerCart", breakLoadPerCart,
                "How much every further cart behind a link adds to its load. A link pulling N carts snaps at",
                "breakSpeed / (1 + breakLoadPerCart * (N - 1)).",
                "A train that is too long for its speed lets go of the carts that are too many, at its back, in one piece.",
                "With the defaults (70 and 0.02), the longest train that gets round a bend whole is 39 carts at 40",
                "blocks per second, 22 at 50, 10 at 60, 5 at 65 and 2 at 70.",
                "Default: 0.02. Recommended: 0.01 to 0.05. 0 makes the length of the train not matter.",
                "Lower: long trains can go nearly as fast as short ones.",
                "Higher: long trains have to slow down for bends, or be split up.");

        setting(out, "chunkloading", chunkloading,
                "Whether moving trains keep the chunks around them loaded, so that they keep running with no player",
                "near, and start running again after a restart.",
                "Only carts with a cart linked on both sides load chunks, so a train needs at least three carts.",
                "Default: false. Recommended: false, unless you need trains that run unattended.",
                "true: every moving train costs the server the chunks it holds loaded.");

        setting(out, "chunkloadingRadius", chunkloadingRadius,
                "How many chunks around each of those carts are kept loaded. Only matters with chunkloading on.",
                "Default: 3. Recommended: 2 or 3.",
                "Lower: below 2 the cart's own chunk stops running entities, and the train stops with it.",
                "Higher: more chunks loaded per train.");

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

    // Nothing here is a string or a table, so a line is a key, a value and perhaps a comment after it
    private static void readToml(Path file, Map<String, String> values) throws IOException {
        for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
            int comment = line.indexOf('#');
            if (comment >= 0) line = line.substring(0, comment);

            int equals = line.indexOf('=');
            if (equals >= 0) values.put(line.substring(0, equals).trim(), line.substring(equals + 1).trim());
        }
    }

    private static void readJson(Path file, Map<String, String> values) throws IOException {
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            for (Map.Entry<String, JsonElement> entry : JsonParser.parseReader(reader).getAsJsonObject().entrySet()) {
                if (entry.getValue().isJsonPrimitive()) values.put(entry.getKey(), entry.getValue().getAsString());
            }
        }
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
