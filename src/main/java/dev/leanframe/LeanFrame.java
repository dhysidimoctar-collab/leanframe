package dev.leanframe;

import net.fabricmc.api.ClientModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * LeanFrame: a vanilla-look FPS helper.
 *
 * - First start: applies the Vanilla-look preset once (it does not change how the game looks).
 * - /leanframe commands (needs Fabric API): doctor, bench, compare, vanilla, lowend, potato, uncap, restore, status.
 *
 * It does not touch rendering code, so it can sit next to Sodium and Lithium.
 * Everything in the game is reached through lookups by name (see Reflect), so a rename in a future
 * Minecraft version skips that feature instead of crashing the game.
 */
public final class LeanFrame implements ClientModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("leanframe");

    @Override
    public void onInitializeClient() {
        try {
            boolean commands = Commands.register();
            Startup.begin(commands);
            LOGGER.info("LeanFrame loaded. Commands: {}", commands ? "on" : "off (Fabric API not found)");
        } catch (Throwable t) {
            LOGGER.warn("LeanFrame could not start. Minecraft will run normally without it.", t);
        }
    }
}
