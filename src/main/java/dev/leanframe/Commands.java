package dev.leanframe;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.function.Consumer;

/**
 * The /leanframe command. It is registered through Fabric API, again by name at runtime, so the
 * mod still works (without commands) if Fabric API is not installed.
 */
final class Commands {
    private static final List<String> SUBCOMMANDS = List.of(
            "status", "doctor", "bench", "compare", "vanilla", "lowend", "potato", "uncap", "restore");

    private Commands() {
    }

    private interface Handler {
        Object handle(Method method, Object[] args) throws Throwable;
    }

    /** Returns true if the command was handed to Fabric API. */
    static boolean register() {
        try {
            Class<?> callbackType = Reflect.cls(
                    "net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback");
            Object event = Reflect.staticField(callbackType, "EVENT");
            Object listener = proxy(callbackType, (method, args) -> {
                if (method.getName().equals("register") && args != null && args.length > 0) {
                    try {
                        registerCommands(args[0]);
                    } catch (Throwable t) {
                        LeanFrame.LOGGER.warn("LeanFrame could not register its commands", t);
                    }
                }
                return null;
            });
            Reflect.invokeAs(Reflect.cls("net.fabricmc.fabric.api.event.Event"), event, "register", listener);
            return true;
        } catch (RuntimeException e) {
            LeanFrame.LOGGER.info("LeanFrame commands are off (" + e.getMessage() + ")");
            return false;
        }
    }

    private static void registerCommands(Object dispatcher) {
        Class<?> clientCommands = Reflect.cls("net.fabricmc.fabric.api.client.command.v2.ClientCommands");
        Object root = executes(Reflect.invokeStatic(clientCommands, "literal", "leanframe"), Commands::help);
        for (String name : SUBCOMMANDS) {
            Object child = executes(Reflect.invokeStatic(clientCommands, "literal", name), actionFor(name));
            Reflect.invoke(root, "then", child);
        }
        Reflect.invokeAs(Reflect.cls("com.mojang.brigadier.CommandDispatcher"), dispatcher, "register", root);
    }

    private static Consumer<Consumer<String>> actionFor(String name) {
        return switch (name) {
            case "status" -> Commands::status;
            case "doctor" -> Doctor::run;
            case "bench" -> Bench::start;
            case "compare" -> Bench::compare;
            case "vanilla" -> say -> applyPreset(say, Presets.VANILLA);
            case "lowend" -> say -> applyPreset(say, Presets.LOWEND);
            case "potato" -> say -> applyPreset(say, Presets.POTATO);
            case "uncap" -> say -> applyPreset(say, Presets.UNCAP);
            default -> Commands::restore;
        };
    }

    // ------------------------------------------------------------------ what the commands do

    private static void help(Consumer<String> say) {
        say.accept("/leanframe doctor   - checks RAM, mods and settings and tells you what to fix");
        say.accept("/leanframe bench    - measure your FPS for 20 seconds (saved)");
        say.accept("/leanframe compare  - compare your last two benchmarks (+/- %)");
        say.accept("/leanframe vanilla  - vanilla look, only invisible tweaks (the default)");
        say.accept("/leanframe lowend   - shorter distances, almost the same look");
        say.accept("/leanframe potato   - maximum FPS, simpler graphics");
        say.accept("/leanframe uncap    - FPS limit Unlimited, VSync off");
        say.accept("/leanframe restore  - put your original settings back");
        say.accept("/leanframe status   - show the current settings");
    }

    private static void status(Consumer<String> say) {
        int fps = Mc.fps();
        say.accept("FPS now: " + (fps >= 0 ? String.valueOf(fps) : "unknown"));
        List<String> values = Presets.status();
        say.accept(values.isEmpty() ? "Could not read the settings." : String.join(" | ", values));
        say.accept("Performance mods found: " + Mc.perfMods());
        say.accept("Original settings saved: " + (Presets.hasBackup() ? "yes (use restore)" : "no"));
    }

    private static void applyPreset(Consumer<String> say, Presets.Preset preset) {
        report(say, preset.name() + " preset", Presets.apply(preset));
    }

    private static void restore(Consumer<String> say) {
        if (!Presets.hasBackup()) {
            say.accept("Nothing to restore. Your settings were not changed by LeanFrame.");
            return;
        }
        report(say, "Restore", Presets.restore());
    }

    private static void report(Consumer<String> say, String title, Presets.Result result) {
        if (result.changed().isEmpty()) {
            say.accept(title + ": nothing to change, your settings already match.");
        } else {
            say.accept(title + ": " + String.join(", ", result.changed()));
        }
        if (!result.skipped().isEmpty()) {
            say.accept("Skipped (not found in this version): " + String.join(", ", result.skipped()));
        }
    }

    // ------------------------------------------------------------------ Brigadier glue

    private static Object executes(Object builder, Consumer<Consumer<String>> action) {
        Class<?> commandType = Reflect.cls("com.mojang.brigadier.Command");
        Object command = proxy(commandType, (method, args) -> {
            if (method.getName().equals("run")) {
                Object source = Reflect.invoke(args[0], "getSource");
                Consumer<String> say = text -> feedback(source, text);
                try {
                    action.accept(say);
                } catch (Throwable t) {
                    LeanFrame.LOGGER.warn("LeanFrame command failed", t);
                    say.accept("That did not work in this Minecraft version: " + t.getMessage());
                }
                return 1;
            }
            return null;
        });
        return Reflect.invoke(builder, "executes", command);
    }

    private static void feedback(Object source, String text) {
        Object message = Mc.literal("[LeanFrame] " + text);
        try {
            Reflect.invokeAs(Reflect.cls("net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource"),
                    source, "sendFeedback", message);
        } catch (RuntimeException e) {
            Reflect.invoke(source, "sendFeedback", message);
        }
    }

    private static Object proxy(Class<?> type, Handler handler) {
        InvocationHandler wrapper = (proxy, method, args) -> {
            if (method.getDeclaringClass() == Object.class) {
                if (method.getName().equals("hashCode")) {
                    return System.identityHashCode(proxy);
                }
                if (method.getName().equals("equals")) {
                    return proxy == args[0];
                }
                return "LeanFrame " + type.getSimpleName();
            }
            return handler.handle(method, args);
        };
        return Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, wrapper);
    }
}
