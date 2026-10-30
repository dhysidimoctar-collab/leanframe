package dev.leanframe;

import net.fabricmc.loader.api.FabricLoader;

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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A simple FPS benchmark that reads Minecraft's own FPS counter (the same number as F3).
 * Every run is saved, so "/leanframe compare" can show the real before/after difference
 * (for example before and after adding Sodium).
 */
final class Bench {
    private static final int SECONDS = Integer.getInteger("leanframe.bench.seconds", 20);
    private static final String HEADER =
            "time,average_fps,worst_second,best_second,render_distance,simulation_distance,performance_mods";
    private static final Pattern ROW =
            Pattern.compile("^([^,]*),(\\d+),(\\d+),(\\d+),(-?\\d+),(-?\\d+),\"(.*)\"$");
    private static volatile boolean running;

    private Bench() {
    }

    // ------------------------------------------------------------------ /leanframe bench

    static void start(Consumer<String> say) {
        if (running) {
            say.accept("A benchmark is already running.");
            return;
        }
        if (Mc.fps() < 0) {
            say.accept("Cannot read the FPS counter in this version. Press F3 instead.");
            return;
        }
        running = true;
        say.accept("Benchmark started. Play normally for " + SECONDS + " seconds...");
        Thread thread = new Thread(() -> measure(say), "LeanFrame-bench");
        thread.setDaemon(true);
        thread.start();
    }

    private static void measure(Consumer<String> say) {
        List<Integer> samples = new ArrayList<>();
        try {
            long end = System.nanoTime() + SECONDS * 1_000_000_000L;
            while (System.nanoTime() < end) {
                int fps = Mc.fps();
                if (fps > 0) {
                    samples.add(fps);
                }
                Thread.sleep(50);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            running = false;
        }
        Mc.runOnMainThread(() -> report(say, samples));
    }

    private static void report(Consumer<String> say, List<Integer> samples) {
        if (samples.isEmpty()) {
            say.accept("No FPS samples were collected. Please try again.");
            return;
        }
        double average = samples.stream().mapToInt(Integer::intValue).average().orElse(0);
        int worst = Collections.min(samples);
        int best = Collections.max(samples);
        int render = Presets.readInt("renderDistance");
        int simulation = Presets.readInt("simulationDistance");
        String mods = Mc.perfMods();

        say.accept(String.format(Locale.ROOT,
                "Result (%d s): average %.0f FPS | worst second %d | best second %d", SECONDS, average, worst, best));
        say.accept("Performance mods found: " + mods);
        appendToLog(average, worst, best, render, simulation, mods);
        say.accept("Saved. After a change (for example adding Sodium), run /leanframe bench again, "
                + "then /leanframe compare.");
    }

    // ------------------------------------------------------------------ /leanframe compare

    static void compare(Consumer<String> say) {
        List<Matcher> rows = new ArrayList<>();
        try {
            Path file = logFile();
            if (Files.exists(file)) {
                for (String line : Files.readAllLines(file)) {
                    Matcher matcher = ROW.matcher(line);
                    if (matcher.matches()) {
                        rows.add(matcher);
                    }
                }
            }
        } catch (IOException e) {
            LeanFrame.LOGGER.warn("LeanFrame could not read the benchmark log", e);
        }
        if (rows.size() < 2) {
            say.accept("Run /leanframe bench at least twice first (for example before and after adding Sodium). "
                    + "Every run is saved, even after a restart.");
            return;
        }

        Matcher before = rows.get(rows.size() - 2);
        Matcher after = rows.get(rows.size() - 1);
        say.accept("Before: " + describe(before));
        say.accept("After:  " + describe(after));

        double beforeAverage = Double.parseDouble(before.group(2));
        double afterAverage = Double.parseDouble(after.group(2));
        if (beforeAverage > 0) {
            long percent = Math.round((afterAverage - beforeAverage) * 100.0 / beforeAverage);
            say.accept("Change: " + (percent >= 0 ? "+" : "") + percent + "% average FPS ("
                    + before.group(2) + " -> " + after.group(2) + ")");
        }
        if (!before.group(5).equals(after.group(5))) {
            say.accept("Note: the render distance was different (" + before.group(5) + " -> " + after.group(5)
                    + "), so part of the change comes from that.");
        }
    }

    private static String describe(Matcher row) {
        String time = row.group(1).replace('T', ' ');
        if (time.length() > 16) {
            time = time.substring(0, 16);
        }
        return time + " - average " + row.group(2) + " FPS, worst second " + row.group(3)
                + ", render " + row.group(5) + ", simulation " + row.group(6) + ", mods: " + row.group(7);
    }

    // ------------------------------------------------------------------ log file

    private static Path logFile() {
        return FabricLoader.getInstance().getConfigDir().resolve("leanframe-bench.csv");
    }

    private static void appendToLog(double average, int worst, int best, int render, int simulation, String mods) {
        Path file = logFile();
        try {
            StringBuilder text = new StringBuilder();
            if (!Files.exists(file)) {
                text.append(HEADER).append('\n');
            }
            text.append(LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS)).append(',')
                    .append(String.format(Locale.ROOT, "%.0f", average)).append(',')
                    .append(worst).append(',')
                    .append(best).append(',')
                    .append(render).append(',')
                    .append(simulation).append(",\"")
                    .append(mods).append("\"\n");
            Files.writeString(file, text, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            LeanFrame.LOGGER.warn("LeanFrame could not write the benchmark log", e);
        }
    }
}
