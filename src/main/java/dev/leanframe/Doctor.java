package dev.leanframe;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/**
 * /leanframe doctor: looks at the PC, Java memory, settings and installed speed mods, and says in plain
 * words what to fix. Everything about the system is read through reflection, so a missing Java module
 * only means "unknown", never an error.
 */
final class Doctor {
    private static final double GB = 1024.0 * 1024.0 * 1024.0;

    private Doctor() {
    }

    static void run(Consumer<String> say) {
        double heapGb = Runtime.getRuntime().maxMemory() / GB;
        double pcGb = pcRamGb();
        int threads = Runtime.getRuntime().availableProcessors();
        int render = Presets.readInt("renderDistance");
        int simulation = Presets.readInt("simulationDistance");
        int fps = Mc.fps();

        say.accept(String.format(Locale.ROOT,
                "PC: %d CPU threads, RAM %s | Minecraft may use up to %.1f GB | Java %d, GC %s",
                threads, pcGb > 0 ? String.format(Locale.ROOT, "%.0f GB", pcGb) : "unknown", heapGb,
                Runtime.version().feature(), gcName()));
        say.accept("Now: " + (fps >= 0 ? fps + " FPS" : "FPS unknown")
                + ", render distance " + number(render) + ", simulation distance " + number(simulation));
        say.accept("Speed mods installed: " + listOrNone(Mc.installedSpeedMods())
                + " | missing: " + listOrNone(Mc.missingSpeedMods()));

        for (String tip : advice(heapGb, pcGb, threads, render)) {
            say.accept("Tip: " + tip);
        }
    }

    private static List<String> advice(double heapGb, double pcGb, int threads, int render) {
        List<String> tips = new ArrayList<>();
        List<String> missing = Mc.missingSpeedMods();

        if (heapGb < 1.5) {
            tips.add(String.format(Locale.ROOT,
                    "Minecraft only has %.1f GB of memory, which causes stutter. In the launcher use -Xmx%dG "
                            + "(Installations > Edit > More options > JVM arguments).", heapGb, recommendedGb(pcGb)));
        }
        if (pcGb > 0 && heapGb > pcGb * 0.6) {
            tips.add(String.format(Locale.ROOT,
                    "You gave Minecraft more than half of your RAM (%.1f of %.0f GB), so Windows has to swap. "
                            + "Lower -Xmx to about %dG.", heapGb, pcGb, recommendedGb(pcGb)));
        }
        if (missing.contains("Sodium")) {
            tips.add("Install Sodium. It is the biggest FPS boost, and the game looks exactly the same.");
        }
        if (missing.contains("Lithium")) {
            tips.add("Install Lithium. It makes the game logic lighter, which helps weak CPUs.");
        }
        if (render > 12 && threads <= 4) {
            tips.add("Render distance " + render + " is high for this PC. 8 to 10 is a good balance: /leanframe lowend");
        }
        if (missing.contains("FerriteCore") && pcGb > 0 && pcGb <= 8) {
            tips.add("Install FerriteCore. It uses less RAM, which matters on a PC with 8 GB or less.");
        }
        if (missing.contains("Entity Culling")) {
            tips.add("Install Entity Culling. It skips mobs and chests you cannot see.");
        }
        if (tips.isEmpty()) {
            tips.add("Nothing obvious to fix. For more FPS try /leanframe lowend (shorter distances).");
        }
        return tips.size() > 5 ? tips.subList(0, 5) : tips;
    }

    /** A sensible -Xmx for the PC's RAM (leaves room for Windows and the browser). */
    private static int recommendedGb(double pcGb) {
        if (pcGb <= 0) {
            return 3;
        }
        if (pcGb <= 4.5) {
            return 2;
        }
        if (pcGb <= 8.5) {
            return 3;
        }
        return pcGb <= 16.5 ? 4 : 6;
    }

    private static String number(int value) {
        return value >= 0 ? String.valueOf(value) : "?";
    }

    private static String listOrNone(List<String> names) {
        return names.isEmpty() ? "none" : String.join(", ", names);
    }

    private static String gcName() {
        try {
            Class<?> factory = Class.forName("java.lang.management.ManagementFactory");
            Class<?> manager = Class.forName("java.lang.management.MemoryManagerMXBean");
            Object beans = factory.getMethod("getGarbageCollectorMXBeans").invoke(null);
            String all = "";
            for (Object bean : (List<?>) beans) {
                all += " " + manager.getMethod("getName").invoke(bean);
            }
            all = all.toLowerCase(Locale.ROOT);
            if (all.contains("g1")) {
                return "G1";
            }
            if (all.contains("zgc") || all.contains("z ")) {
                return "ZGC";
            }
            if (all.contains("shenandoah")) {
                return "Shenandoah";
            }
            if (all.contains("ps ") || all.contains("parallel")) {
                return "Parallel";
            }
            return all.isBlank() ? "unknown" : "Serial";
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            return "unknown";
        }
    }

    /** Total RAM of the PC in GB, or -1 if Java cannot tell. */
    private static double pcRamGb() {
        try {
            Class<?> factory = Class.forName("java.lang.management.ManagementFactory");
            Object os = factory.getMethod("getOperatingSystemMXBean").invoke(null);
            Class<?> sunOs = Class.forName("com.sun.management.OperatingSystemMXBean");
            if (!sunOs.isInstance(os)) {
                return -1;
            }
            Object bytes;
            try {
                bytes = sunOs.getMethod("getTotalMemorySize").invoke(os);
            } catch (NoSuchMethodException e) {
                bytes = sunOs.getMethod("getTotalPhysicalMemorySize").invoke(os);
            }
            return ((Number) bytes).doubleValue() / GB;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            return -1;
        }
    }
}
