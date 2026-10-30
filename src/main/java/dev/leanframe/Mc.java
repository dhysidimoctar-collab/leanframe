package dev.leanframe;

import net.fabricmc.loader.api.FabricLoader;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;

/** Everything LeanFrame needs from Minecraft itself, all looked up by name at runtime. */
final class Mc {
    /** Well-known performance mods. Only used to label benchmark results. */
    private static final String[] PERF_MODS = {
            "sodium", "lithium", "ferritecore", "immediatelyfast", "entityculling", "modernfix", "iris"
    };

    /** Look-neutral speed mods we recommend. Every one of them has a Fabric build for Minecraft 26.3. */
    private static final String[][] SPEED_MODS = {
            {"sodium", "Sodium"}, {"lithium", "Lithium"}, {"ferritecore", "FerriteCore"},
            {"entityculling", "Entity Culling"}, {"immediatelyfast", "ImmediatelyFast"}
    };

    private Mc() {
    }

    static Object instance() {
        return Reflect.invokeStatic(Reflect.cls("net.minecraft.client.Minecraft"), "getInstance");
    }

    static Object instanceOrNull() {
        try {
            return instance();
        } catch (RuntimeException e) {
            return null;
        }
    }

    static Object options() {
        return Reflect.field(instance(), "options");
    }

    static boolean hasPlayer() {
        try {
            return Reflect.field(instance(), "player") != null;
        } catch (RuntimeException e) {
            return false;
        }
    }

    /** Runs the task on Minecraft's main thread. Errors are logged, never thrown into the game. */
    static void runOnMainThread(Runnable task) {
        Runnable safe = () -> {
            try {
                task.run();
            } catch (Throwable t) {
                LeanFrame.LOGGER.warn("LeanFrame task failed", t);
            }
        };
        Object minecraft = instance();
        if (minecraft instanceof Executor executor) {
            executor.execute(safe);
        } else {
            Reflect.invoke(minecraft, "execute", safe);
        }
    }

    /** The FPS number shown by F3, or -1 if it cannot be read. */
    static int fps() {
        try {
            if (Reflect.invoke(instance(), "getFps") instanceof Integer value) {
                return value;
            }
        } catch (RuntimeException ignored) {
            // try the field instead
        }
        try {
            if (Reflect.staticField(Reflect.cls("net.minecraft.client.Minecraft"), "fps") instanceof Integer value) {
                return value;
            }
        } catch (RuntimeException ignored) {
            // try an instance field
        }
        try {
            if (Reflect.field(instance(), "fps") instanceof Integer value) {
                return value;
            }
        } catch (RuntimeException ignored) {
            // give up
        }
        return -1;
    }

    static Object literal(String text) {
        return Reflect.invokeStatic(Reflect.cls("net.minecraft.network.chat.Component"), "literal", text);
    }

    /** Writes one line in the player's chat. Returns false if the game would not take it. */
    static boolean chat(String text) {
        try {
            Object minecraft = instance();
            Object player = Reflect.field(minecraft, "player");
            if (player == null) {
                return false;
            }
            Object message = literal("[LeanFrame] " + text);
            try {
                Reflect.invoke(player, "sendSystemMessage", message);
                return true;
            } catch (RuntimeException ignored) {
                // try the next way
            }
            try {
                Reflect.invoke(player, "displayClientMessage", message, Boolean.FALSE);
                return true;
            } catch (RuntimeException ignored) {
                // try the next way
            }
            Object chat = Reflect.invoke(Reflect.field(minecraft, "gui"), "getChat");
            Reflect.invoke(chat, "addMessage", message);
            return true;
        } catch (RuntimeException e) {
            LeanFrame.LOGGER.info("LeanFrame message not shown: {}", text);
            return false;
        }
    }

    static String perfMods() {
        List<String> found = new ArrayList<>();
        for (String id : PERF_MODS) {
            if (FabricLoader.getInstance().isModLoaded(id)) {
                found.add(id);
            }
        }
        return found.isEmpty() ? "none" : String.join(", ", found);
    }

    static List<String> installedSpeedMods() {
        return speedMods(true);
    }

    static List<String> missingSpeedMods() {
        return speedMods(false);
    }

    private static List<String> speedMods(boolean installed) {
        List<String> names = new ArrayList<>();
        for (String[] mod : SPEED_MODS) {
            if (FabricLoader.getInstance().isModLoaded(mod[0]) == installed) {
                names.add(mod[1]);
            }
        }
        return names;
    }
}
