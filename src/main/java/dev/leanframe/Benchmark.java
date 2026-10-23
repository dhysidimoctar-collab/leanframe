package dev.leanframe;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.network.chat.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/**
 * A simple 20-second FPS benchmark. It reads Minecraft's own FPS counter (the same number as F3),
 * so you can compare "before" and "after" when you add or remove performance mods.
 */
final class Benchmark {
    private static final int SECONDS = 20;
    private static final int TICKS_PER_SECOND = 20;

    private static final List<Integer> SAMPLES = new ArrayList<>();
    private static boolean running;
    private static int ticksLeft;
    private static Consumer<Component> out;

    private Benchmark() {
    }

    static void start(Consumer<Component> reply) {
        if (running) {
            Tools.say(reply, "A benchmark is already running.");
            return;
        }
        if (Tools.currentFps() < 0) {
            Tools.say(reply, "Cannot read the FPS counter on this version. Use F3 instead.");
            return;
        }
        SAMPLES.clear();
        ticksLeft = SECONDS * TICKS_PER_SECOND;
        out = reply;
        running = true;
        Tools.say(reply, "Benchmark started. Play normally for " + SECONDS + " seconds...");
    }

    /** Called 20 times per second by the client tick event. */
    static void onClientTick() {
        if (!running) {
            return;
        }
        int fps = Tools.currentFps();
        if (fps > 0) {
            SAMPLES.add(fps);
        }
        if (--ticksLeft <= 0) {
            finish();
        }
    }

    private static void finish() {
        running = false;
        Consumer<Component> reply = out;
        out = null;

        if (SAMPLES.isEmpty()) {
            Tools.say(reply, "No FPS samples were collected. Please try again.");
            return;
        }
        double average = SAMPLES.stream().mapToInt(Integer::intValue).average().orElse(0);
        int worst = Collections.min(SAMPLES);
        int best = Collections.max(SAMPLES);
        String mods = Tools.detectedPerfMods();

        Tools.say(reply, String.format(Locale.ROOT,
                "Result (%d s): average %.0f FPS | worst second %d | best second %d", SECONDS, average, worst, best));
        Tools.say(reply, "Performance mods found: " + mods);
        appendToLog(average, worst, best, mods);
        Tools.say(reply, "Saved to config/leanframe-benchmarks.csv. Run it again after changing mods to compare.");
    }

    private static void appendToLog(double average, int worst, int best, String mods) {
        Path file = FabricLoader.getInstance().getConfigDir().resolve("leanframe-benchmarks.csv");
        try {
            StringBuilder text = new StringBuilder();
            if (!Files.exists(file)) {
                text.append("time,average_fps,worst_second,best_second,performance_mods\n");
            }
            text.append(LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS)).append(',')
                    .append(String.format(Locale.ROOT, "%.0f", average)).append(',')
                    .append(worst).append(',')
                    .append(best).append(",\"")
                    .append(mods).append("\"\n");
            Files.writeString(file, text, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            LeanFrame.LOGGER.warn("Could not write the benchmark log", e);
        }
    }
}
