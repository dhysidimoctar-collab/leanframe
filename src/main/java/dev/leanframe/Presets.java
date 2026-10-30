package dev.leanframe;

import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;

/**
 * Settings presets. Every preset only ever makes the game CHEAPER to run: it never raises a value
 * you already set lower. The original values are saved the first time they change, so
 * "/leanframe restore" can put everything back exactly as it was.
 */
final class Presets {
    enum Kind {
        /** Change the setting if it is not already this value. */
        SET,
        /** Lower a number to this value, but never raise it. */
        CAP,
        /** Change an option to this value only if it is currently one of the listed (more expensive) values. */
        FROM
    }

    record Setting(String accessor, Object value, Kind kind, List<String> from) {
        static Setting set(String accessor, Object value) {
            return new Setting(accessor, value, Kind.SET, List.of());
        }

        static Setting cap(String accessor, Number max) {
            return new Setting(accessor, max, Kind.CAP, List.of());
        }

        static Setting downgrade(String accessor, String target, String... replace) {
            return new Setting(accessor, target, Kind.FROM, List.of(replace));
        }
    }

    record Preset(String name, List<Setting> settings) {
    }

    record Result(List<String> changed, List<String> skipped) {
    }

    /** Look-neutral: the picture stays exactly vanilla, only invisible CPU work is reduced. First-start default. */
    static final Preset VANILLA = new Preset("Vanilla-look", List.of(
            Setting.cap("simulationDistance", 8)));

    /** Shorter distances: the game still looks almost normal. Opt-in. */
    static final Preset LOWEND = new Preset("Low-end", List.of(
            Setting.cap("renderDistance", 8),
            Setting.cap("simulationDistance", 6),
            Setting.cap("entityDistanceScaling", 0.75),
            Setting.downgrade("particles", "DECREASED", "ALL"),
            Setting.cap("biomeBlendRadius", 1),
            Setting.set("entityShadows", false)));

    /** Maximum FPS: simpler graphics. */
    static final Preset POTATO = new Preset("Potato", List.of(
            Setting.cap("renderDistance", 5),
            Setting.cap("simulationDistance", 5),
            Setting.cap("entityDistanceScaling", 0.5),
            Setting.downgrade("particles", "MINIMAL", "ALL", "DECREASED"),
            Setting.cap("biomeBlendRadius", 0),
            Setting.set("entityShadows", false),
            Setting.downgrade("cloudStatus", "OFF", "FAST", "FANCY"),
            Setting.downgrade("graphicsMode", "FAST", "FANCY", "FABULOUS"),
            Setting.set("ambientOcclusion", false)));

    /** Lifts the FPS cap. Only useful if the cap or VSync was holding your FPS back. */
    static final Preset UNCAP = new Preset("Uncap", List.of(
            Setting.set("framerateLimit", 260),
            Setting.set("enableVsync", false)));

    private static final List<String> STATUS_ORDER = List.of(
            "renderDistance", "simulationDistance", "entityDistanceScaling", "particles", "biomeBlendRadius",
            "entityShadows", "cloudStatus", "graphicsMode", "ambientOcclusion", "framerateLimit", "enableVsync");

    private static final Map<String, String> LABELS = Map.ofEntries(
            Map.entry("renderDistance", "Render distance"),
            Map.entry("simulationDistance", "Simulation distance"),
            Map.entry("entityDistanceScaling", "Entity distance"),
            Map.entry("particles", "Particles"),
            Map.entry("biomeBlendRadius", "Biome blend"),
            Map.entry("entityShadows", "Entity shadows"),
            Map.entry("cloudStatus", "Clouds"),
            Map.entry("graphicsMode", "Graphics"),
            Map.entry("ambientOcclusion", "Smooth lighting"),
            Map.entry("framerateLimit", "FPS limit"),
            Map.entry("enableVsync", "VSync"));

    private Presets() {
    }

    // ------------------------------------------------------------------ public actions

    static Result apply(Preset preset) {
        List<String> changed = new ArrayList<>();
        List<String> skipped = new ArrayList<>();
        Object options = Mc.options();
        Properties backup = loadBackup();

        for (Setting setting : preset.settings()) {
            String accessor = setting.accessor();
            try {
                Object option = Reflect.invoke(options, accessor);
                Object current = Reflect.invoke(option, "get");
                Object wanted = convert(current, setting.value());
                if (wanted == null) {
                    skipped.add(label(accessor));
                    continue;
                }
                if (!shouldChange(setting, current, wanted)) {
                    continue;
                }
                if (!backup.containsKey(accessor)) {
                    backup.setProperty(accessor, asText(current));
                }
                Reflect.invoke(option, "set", wanted);
                changed.add(label(accessor) + " " + show(accessor, current) + " -> " + show(accessor, wanted));
            } catch (RuntimeException e) {
                LeanFrame.LOGGER.info("LeanFrame skipped " + accessor + ": " + e.getMessage());
                skipped.add(label(accessor));
            }
        }
        saveBackup(backup);
        saveOptions(options);
        return new Result(changed, skipped);
    }

    static Result restore() {
        List<String> changed = new ArrayList<>();
        List<String> skipped = new ArrayList<>();
        Object options = Mc.options();
        Properties backup = loadBackup();

        for (String accessor : backup.stringPropertyNames()) {
            try {
                Object option = Reflect.invoke(options, accessor);
                Object current = Reflect.invoke(option, "get");
                Object original = convert(current, backup.getProperty(accessor));
                if (original == null) {
                    skipped.add(label(accessor));
                } else if (!original.equals(current)) {
                    Reflect.invoke(option, "set", original);
                    changed.add(label(accessor) + " " + show(accessor, current) + " -> " + show(accessor, original));
                }
            } catch (RuntimeException e) {
                LeanFrame.LOGGER.info("LeanFrame could not restore " + accessor + ": " + e.getMessage());
                skipped.add(label(accessor));
            }
        }
        try {
            Files.deleteIfExists(backupFile());
        } catch (IOException e) {
            LeanFrame.LOGGER.warn("LeanFrame could not delete its backup file", e);
        }
        saveOptions(options);
        return new Result(changed, skipped);
    }

    static List<String> status() {
        List<String> parts = new ArrayList<>();
        Object options = Mc.options();
        for (String accessor : STATUS_ORDER) {
            try {
                Object current = Reflect.invoke(Reflect.invoke(options, accessor), "get");
                parts.add(label(accessor) + ": " + show(accessor, current));
            } catch (RuntimeException ignored) {
                // this setting does not exist in this Minecraft version
            }
        }
        return parts;
    }

    static boolean hasBackup() {
        return Files.exists(backupFile());
    }

    static Preset byName(String name) {
        String key = name == null ? "" : name.trim().toLowerCase(Locale.ROOT);
        if (key.equals("lowend") || key.equals("low-end") || key.equals("low")) {
            return LOWEND;
        }
        if (key.equals("potato")) {
            return POTATO;
        }
        return VANILLA;
    }

    /** Current whole-number value of an option, or -1 if it cannot be read. */
    static int readInt(String accessor) {
        try {
            Object value = Reflect.invoke(Reflect.invoke(Mc.options(), accessor), "get");
            return value instanceof Integer number ? number : -1;
        } catch (RuntimeException e) {
            return -1;
        }
    }

    // ------------------------------------------------------------------ helpers

    private static boolean shouldChange(Setting setting, Object current, Object wanted) {
        return switch (setting.kind()) {
            case SET -> !wanted.equals(current);
            case CAP -> current instanceof Number now && wanted instanceof Number max
                    && now.doubleValue() > max.doubleValue();
            case FROM -> setting.from().contains(asText(current).toUpperCase(Locale.ROOT));
        };
    }

    /**
     * Turns the wanted value into the SAME type the option already uses.
     * Returns null when the types do not fit, so a wrong type can never be pushed into the game.
     */
    private static Object convert(Object current, Object wanted) {
        String text = String.valueOf(wanted).trim();
        if (current instanceof Integer) {
            return wanted instanceof Number n ? n.intValue() : Integer.parseInt(text);
        }
        if (current instanceof Double) {
            return wanted instanceof Number n ? n.doubleValue() : Double.parseDouble(text);
        }
        if (current instanceof Boolean) {
            return wanted instanceof Boolean b ? b : Boolean.parseBoolean(text);
        }
        if (current instanceof Enum<?> e) {
            for (Object constant : e.getDeclaringClass().getEnumConstants()) {
                if (((Enum<?>) constant).name().equalsIgnoreCase(text)) {
                    return constant;
                }
            }
        }
        return null;
    }

    private static String asText(Object value) {
        return value instanceof Enum<?> e ? e.name() : String.valueOf(value);
    }

    private static String label(String accessor) {
        return LABELS.getOrDefault(accessor, accessor);
    }

    private static String show(String accessor, Object value) {
        if (value instanceof Boolean b) {
            return b ? "on" : "off";
        }
        if (value instanceof Double d && accessor.equals("entityDistanceScaling")) {
            return Math.round(d * 100) + "%";
        }
        if (value instanceof Integer i && accessor.equals("framerateLimit")) {
            return i >= 260 ? "Unlimited" : i + " FPS";
        }
        if (value instanceof Enum<?> e) {
            return e.name().toLowerCase(Locale.ROOT);
        }
        return String.valueOf(value);
    }

    private static Path backupFile() {
        return FabricLoader.getInstance().getConfigDir().resolve("leanframe-backup.properties");
    }

    private static Properties loadBackup() {
        Properties backup = new Properties();
        Path file = backupFile();
        if (Files.exists(file)) {
            try (InputStream in = Files.newInputStream(file)) {
                backup.load(in);
            } catch (IOException e) {
                LeanFrame.LOGGER.warn("LeanFrame could not read its backup file", e);
            }
        }
        return backup;
    }

    private static void saveBackup(Properties backup) {
        if (backup.isEmpty()) {
            return;
        }
        try (OutputStream out = Files.newOutputStream(backupFile())) {
            backup.store(out, "LeanFrame: your ORIGINAL settings (used by /leanframe restore)");
        } catch (IOException e) {
            LeanFrame.LOGGER.warn("LeanFrame could not write its backup file", e);
        }
    }

    private static void saveOptions(Object options) {
        try {
            Reflect.invoke(options, "save");
        } catch (RuntimeException e) {
            LeanFrame.LOGGER.info("LeanFrame could not save options.txt right now: {}", e.getMessage());
        }
    }
}
