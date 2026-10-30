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
import java.util.Properties;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

/**
 * "Ready to play": on the very first start a preset is applied once (by default the Vanilla-look one,
 * which does NOT change how the game looks), then a short message in chat explains what happened.
 * After that LeanFrame stays quiet. All of this runs on a background thread and hands the real work
 * to Minecraft's main thread.
 */
final class Startup {
    private static final String CONFIG_COMMENT = """
            LeanFrame settings
            firstRunPreset     = vanilla | lowend | potato | off   (applied once, the first time the game starts)
                                 vanilla = does NOT change how the game looks (default)
            firstRunDone       = set to true by LeanFrame after that (no need to edit)
            restoreOnNextStart = set to true to put your original settings back at the next start""";

    private Startup() {
    }

    static void begin(boolean commandsAvailable) {
        Thread thread = new Thread(() -> {
            try {
                run(commandsAvailable);
            } catch (Throwable t) {
                LeanFrame.LOGGER.warn("LeanFrame start-up task failed", t);
            }
        }, "LeanFrame-startup");
        thread.setDaemon(true);
        thread.start();
    }

    private static void run(boolean commandsAvailable) throws Exception {
        Properties config = loadConfig();
        boolean legacyNotice = migrate(config);
        boolean restore = flag(config, "restoreOnNextStart", false);
        String presetName = config.getProperty("firstRunPreset", "vanilla").trim().toLowerCase(Locale.ROOT);
        boolean firstRun = !flag(config, "firstRunDone", false) && !presetName.equals("off");
        if (!restore && !firstRun && !legacyNotice) {
            return; // nothing to do on this start
        }

        if (!waitFor(() -> Mc.instanceOrNull() != null, 120)) {
            LeanFrame.LOGGER.warn("LeanFrame gave up waiting for Minecraft to start");
            return;
        }

        CompletableFuture<List<String>> result = new CompletableFuture<>();
        Mc.runOnMainThread(() -> {
            try {
                List<String> lines = new ArrayList<>();
                if (restore) {
                    lines.addAll(restoreNow());
                } else if (firstRun) {
                    lines.addAll(applyFirstRun(presetName, commandsAvailable));
                }
                if (legacyNotice && !restore) {
                    lines.add(legacyLine(commandsAvailable));
                }
                result.complete(lines);
            } catch (Throwable t) {
                result.completeExceptionally(t);
            }
        });
        List<String> notice = result.get(120, TimeUnit.SECONDS);

        if (restore) {
            config.setProperty("restoreOnNextStart", "false");
            config.setProperty("firstRunPreset", "off");
        }
        config.setProperty("firstRunDone", "true");
        saveConfig(config);

        // Show the message once the player is inside a world.
        if (waitFor(Mc::hasPlayer, 60 * 30)) {
            Thread.sleep(1500);
            Mc.runOnMainThread(() -> notice.forEach(Mc::chat));
        }
    }

    // ------------------------------------------------------------------ what is shown

    private static List<String> applyFirstRun(String presetName, boolean commandsAvailable) {
        Presets.Preset preset = Presets.byName(presetName);
        Presets.Result result = Presets.apply(preset);
        List<String> lines = new ArrayList<>();

        if (result.changed().isEmpty()) {
            lines.add("Your settings already match the " + preset.name() + " preset, so nothing was changed.");
        } else {
            lines.add(preset.name() + " preset applied (first start only): " + String.join(", ", result.changed()));
        }
        if (!result.skipped().isEmpty()) {
            lines.add("Skipped (not found in this version): " + String.join(", ", result.skipped()));
        }
        if (preset == Presets.VANILLA) {
            lines.add("Your graphics were NOT changed: the game looks exactly like vanilla.");
        }

        List<String> missing = Mc.missingSpeedMods();
        if (!missing.isEmpty()) {
            lines.add("Biggest FPS boost with the same look: add " + String.join(", ", missing)
                    + " (free on modrinth.com)." + (commandsAvailable ? " Type /leanframe doctor for tips." : ""));
        }
        lines.add(commandsAvailable
                ? "Undo: /leanframe restore. Measure: /leanframe bench, then /leanframe compare."
                : "Undo: set restoreOnNextStart=true in config/leanframe.properties. "
                        + "Install Fabric API to get the /leanframe commands.");
        return lines;
    }

    private static List<String> restoreNow() {
        if (!Presets.hasBackup()) {
            return List.of("Nothing to restore. Your settings were not changed by LeanFrame.");
        }
        Presets.Result result = Presets.restore();
        return List.of(result.changed().isEmpty()
                ? "Your original settings were already in place."
                : "Your original settings are back: " + String.join(", ", result.changed()));
    }

    private static String legacyLine(boolean commandsAvailable) {
        return "LeanFrame 0.2 changed some settings for you (render and simulation distance, entity distance, "
                + "particles, biome blend, entity shadows). "
                + (commandsAvailable
                ? "Type /leanframe restore to get your original look back, or keep them for more FPS."
                : "To get your original look back, set restoreOnNextStart=true in config/leanframe.properties "
                        + "and start the game again.");
    }

    // ------------------------------------------------------------------ config file

    /**
     * Upgrades a config file written by LeanFrame 0.2.
     * Returns true if the old Low-end preset had been applied back then (so a one-time hint is shown).
     */
    private static boolean migrate(Properties config) throws IOException {
        if (config.getProperty("configVersion") != null) {
            return false;
        }
        boolean fromOldVersion = config.getProperty("firstRunDone") != null;
        boolean wasAuto = flag(config, "autoApplyLowEnd", true);
        boolean wasApplied = fromOldVersion && flag(config, "firstRunDone", false) && wasAuto && Presets.hasBackup();

        config.remove("autoApplyLowEnd");
        config.setProperty("configVersion", "2");
        config.setProperty("firstRunPreset", fromOldVersion && !wasAuto ? "off" : "vanilla");
        config.putIfAbsent("firstRunDone", "false");
        config.putIfAbsent("restoreOnNextStart", "false");
        saveConfig(config);
        return wasApplied;
    }

    private static Properties loadConfig() throws IOException {
        Properties config = new Properties();
        Path file = configFile();
        if (Files.exists(file)) {
            try (InputStream in = Files.newInputStream(file)) {
                config.load(in);
            }
        } else {
            config.setProperty("configVersion", "2");
            config.setProperty("firstRunPreset", "vanilla");
            config.setProperty("firstRunDone", "false");
            config.setProperty("restoreOnNextStart", "false");
            saveConfig(config);
        }
        return config;
    }

    private static void saveConfig(Properties config) throws IOException {
        try (OutputStream out = Files.newOutputStream(configFile())) {
            config.store(out, CONFIG_COMMENT);
        }
    }

    private static Path configFile() {
        return FabricLoader.getInstance().getConfigDir().resolve("leanframe.properties");
    }

    // ------------------------------------------------------------------ helpers

    private static boolean waitFor(BooleanSupplier condition, int seconds) throws InterruptedException {
        for (int i = 0; i < seconds * 2; i++) {
            if (condition.getAsBoolean()) {
                return true;
            }
            Thread.sleep(500);
        }
        return false;
    }

    private static boolean flag(Properties config, String key, boolean fallback) {
        return Boolean.parseBoolean(config.getProperty(key, String.valueOf(fallback)).trim());
    }
}
