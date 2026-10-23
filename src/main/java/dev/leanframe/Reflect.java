package dev.leanframe;

import java.lang.reflect.Method;

/**
 * Tiny reflection helper.
 *
 * Minecraft 26.x ships with its real (unobfuscated) names, so looking things up by name works.
 * LeanFrame uses this for the few Minecraft internals it touches (FPS limit, VSync, FPS counter),
 * so the mod still BUILDS and LOADS even if Mojang renames one of them. In that case the matching
 * command prints "not found" instead of crashing the game.
 */
final class Reflect {
    private Reflect() {
    }

    /** Calls a public method by name and argument count. Throws IllegalStateException on any problem. */
    static Object invoke(Object target, String name, Object... args) {
        if (target == null) {
            throw new IllegalStateException("cannot call " + name + "() on null");
        }
        for (Method method : target.getClass().getMethods()) {
            if (method.getName().equals(name) && method.getParameterCount() == args.length) {
                try {
                    return method.invoke(target, args);
                } catch (ReflectiveOperationException | IllegalArgumentException e) {
                    throw new IllegalStateException(name + "() failed: " + e, e);
                }
            }
        }
        throw new IllegalStateException("method " + name + "() not found on " + target.getClass().getName());
    }
}
