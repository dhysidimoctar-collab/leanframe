package dev.leanframe;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

/**
 * Small reflection helpers.
 *
 * Minecraft 26.x ships with its real (unobfuscated) names, so things can be looked up by name.
 * LeanFrame touches Minecraft ONLY through these helpers, never through direct references.
 * That way the mod always loads, and if Mojang renames something, that one feature is skipped
 * (and logged) instead of crashing the game.
 */
final class Reflect {
    private Reflect() {
    }

    static Class<?> cls(String name) {
        try {
            return Class.forName(name, false, Reflect.class.getClassLoader());
        } catch (ClassNotFoundException | LinkageError e) {
            throw new IllegalStateException("class " + name + " not found", e);
        }
    }

    /** Calls a public method, looked up on the object's own class. */
    static Object invoke(Object target, String name, Object... args) {
        if (target == null) {
            throw new IllegalStateException("cannot call " + name + "() on null");
        }
        return call(target.getClass(), target, name, args);
    }

    /** Calls a public method as declared on a public type (for objects whose own class is hidden). */
    static Object invokeAs(Class<?> type, Object target, String name, Object... args) {
        if (target == null) {
            throw new IllegalStateException("cannot call " + name + "() on null");
        }
        return call(type, target, name, args);
    }

    static Object invokeStatic(Class<?> type, String name, Object... args) {
        return call(type, null, name, args);
    }

    static Object field(Object target, String name) {
        if (target == null) {
            throw new IllegalStateException("cannot read " + name + " of null");
        }
        return readField(target.getClass(), target, name);
    }

    static Object staticField(Class<?> type, String name) {
        return readField(type, null, name);
    }

    // ------------------------------------------------------------------ internals

    private static Object call(Class<?> type, Object target, String name, Object[] args) {
        for (Method method : type.getMethods()) {
            if (method.getName().equals(name) && accepts(method.getParameterTypes(), args)) {
                try {
                    method.trySetAccessible();
                    return method.invoke(target, args);
                } catch (InvocationTargetException e) {
                    throw new IllegalStateException(name + "() threw " + e.getCause(), e.getCause());
                } catch (IllegalAccessException | IllegalArgumentException e) {
                    throw new IllegalStateException(name + "() failed: " + e, e);
                }
            }
        }
        throw new IllegalStateException(
                "method " + name + "(" + args.length + " args) not found on " + type.getName());
    }

    private static boolean accepts(Class<?>[] params, Object[] args) {
        if (params.length != args.length) {
            return false;
        }
        for (int i = 0; i < params.length; i++) {
            Object arg = args[i];
            if (arg == null) {
                if (params[i].isPrimitive()) {
                    return false;
                }
            } else if (!wrap(params[i]).isInstance(arg)) {
                return false;
            }
        }
        return true;
    }

    private static Class<?> wrap(Class<?> type) {
        if (!type.isPrimitive()) {
            return type;
        }
        if (type == int.class) {
            return Integer.class;
        }
        if (type == boolean.class) {
            return Boolean.class;
        }
        if (type == double.class) {
            return Double.class;
        }
        if (type == long.class) {
            return Long.class;
        }
        if (type == float.class) {
            return Float.class;
        }
        if (type == short.class) {
            return Short.class;
        }
        if (type == byte.class) {
            return Byte.class;
        }
        return Character.class;
    }

    private static Object readField(Class<?> type, Object target, String name) {
        for (Class<?> c = type; c != null; c = c.getSuperclass()) {
            try {
                Field field = c.getDeclaredField(name);
                field.setAccessible(true);
                return field.get(Modifier.isStatic(field.getModifiers()) ? null : target);
            } catch (NoSuchFieldException e) {
                // keep looking in the parent class
            } catch (IllegalAccessException | RuntimeException e) {
                throw new IllegalStateException("field " + name + " not readable: " + e, e);
            }
        }
        throw new IllegalStateException("field " + name + " not found on " + type.getName());
    }
}
