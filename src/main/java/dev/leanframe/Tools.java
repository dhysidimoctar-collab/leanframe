package dev.leanframe;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.function.Consumer;

/** The small things LeanFrame can do: show status, lift the FPS cap, and put it back. */
final class Tools {
    /** On Minecraft's FPS slider the highest value (260) means "Unlimited". */
    static final int UNLIMITED = 260;

    /** Well-known performance mods. Only used to label benchmark results. */
    private static final String[] PERF_MODS = {
            "sodium", "lithium", "ferritecore", "immediatelyfast", "entityculling", "modernfix", "iris"
    };

    private Tools() {
    }

    // ---------------------------------------------------------------- chat output

    static void say(Consumer<Component> out, String text) {
        try {
            out.accept(Component.literal("[LeanFrame] " + text));
        } catch (RuntimeException e) {
            // For example: the player left the world while a benchmark was still running.
            LeanFrame.LOGGER.info(text);
        }
    }

    static void help(Consumer<Component> out) {
        say(out, "/leanframe bench   - measure your FPS for 20 seconds");
        say(out, "/leanframe status  - show FPS, FPS limit, VSync and performance mods");
        say(out, "/leanframe uncap   - FPS limit Unlimited + VSync off");
        say(out, "/leanframe restore - put your old settings back");
    }

    // ---------------------------------------------------------------- commands

    static void status(Consumer<Component> out) {
        int fps = currentFps();
        say(out, "FPS right now: " + (fps >= 0 ? String.valueOf(fps) : "unknown"));
        try {
            Object limit = read("framerateLimit");
            Object vsync = read("enableVsync");
            say(out, "FPS limit: " + describeLimit(limit) + " | VSync: " + (Boolean.TRUE.equals(vsync) ? "on" : "off"));
        } catch (RuntimeException e) {
            say(out, "Could not read the FPS settings: " + e.getMessage());
        }
        say(out, "Performance mods found: " + detectedPerfMods());
        say(out, "Old settings saved: " + (Files.exists(backupFile()) ? "yes (use restore)" : "no"));
    }

    static void uncap(Consumer<Component> out) {
        try {
            Object limit = read("framerateLimit");
            Object vsync = read("enableVsync");

            // Remember the ORIGINAL values once, so running "uncap" twice cannot overwrite them.
            if (!Files.exists(backupFile())) {
                Properties backup = new Properties();
                backup.setProperty("framerateLimit", String.valueOf(limit));
                backup.setProperty("enableVsync", String.valueOf(vsync));
                try (OutputStream stream = Files.newOutputStream(backupFile())) {
                    backup.store(stream, "LeanFrame: your settings before /leanframe uncap");
                }
            }

            write("framerateLimit", UNLIMITED);
            write("enableVsync", Boolean.FALSE);
            saveOptions();
            say(out, "FPS limit is now Unlimited and VSync is off. Undo it with /leanframe restore.");
        } catch (IOException | RuntimeException e) {
            fail(out, e);
        }
    }

    static void restore(Consumer<Component> out) {
        Path file = backupFile();
        if (!Files.exists(file)) {
            say(out, "Nothing to restore. Run /leanframe uncap first.");
            return;
        }
        try {
            Properties backup = new Properties();
            try (InputStream stream = Files.newInputStream(file)) {
                backup.load(stream);
            }
            write("framerateLimit", Integer.valueOf(backup.getProperty("framerateLimit", "120")));
            write("enableVsync", Boolean.valueOf(backup.getProperty("enableVsync", "true")));
            saveOptions();
            Files.deleteIfExists(file);
            say(out, "Your old FPS limit and VSync settings are back.");
        } catch (IOException | RuntimeException e) {
            fail(out, e);
        }
    }

    // ---------------------------------------------------------------- helpers

    /** The FPS number shown by F3, or -1 if it cannot be read. */
    static int currentFps() {
        Minecraft minecraft = Minecraft.getInstance();
        try {
            if (Reflect.invoke(minecraft, "getFps") instanceof Integer fps) {
                return fps;
            }
        } catch (RuntimeException ignored) {
            // fall through and try the field instead
        }
        try {
            Field field = Minecraft.class.getDeclaredField("fps");
            field.setAccessible(true);
            return field.getInt(Modifier.isStatic(field.getModifiers()) ? null : minecraft);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return -1;
        }
    }

    static String detectedPerfMods() {
        List<String> found = new ArrayList<>();
        for (String id : PERF_MODS) {
            if (FabricLoader.getInstance().isModLoaded(id)) {
                found.add(id);
            }
        }
        return found.isEmpty() ? "none" : String.join(", ", found);
    }

    private static String describeLimit(Object limit) {
        if (limit instanceof Integer value) {
            return value >= UNLIMITED ? "Unlimited" : value + " FPS";
        }
        return String.valueOf(limit);
    }

    private static Path backupFile() {
        return FabricLoader.getInstance().getConfigDir().resolve("leanframe-backup.properties");
    }

    // Options are changed through Minecraft's own OptionInstance.set(), so the game applies
    // the new value immediately (frame limiter and VSync are updated by Minecraft itself).

    private static Object option(String accessor) {
        return Reflect.invoke(Minecraft.getInstance().options, accessor);
    }

    private static Object read(String accessor) {
        return Reflect.invoke(option(accessor), "get");
    }

    private static void write(String accessor, Object value) {
        Reflect.invoke(option(accessor), "set", value);
    }

    private static void saveOptions() {
        try {
            Reflect.invoke(Minecraft.getInstance().options, "save");
        } catch (RuntimeException e) {
            LeanFrame.LOGGER.warn("Could not save options.txt: {}", e.getMessage());
        }
    }

    private static void fail(Consumer<Component> out, Exception e) {
        LeanFrame.LOGGER.warn("LeanFrame command failed", e);
        say(out, "That did not work on this Minecraft version: " + e.getMessage());
    }
}
