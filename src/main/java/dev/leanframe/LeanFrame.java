package dev.leanframe;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * LeanFrame: a small FPS toolkit. It does not touch rendering, so Minecraft looks exactly the same.
 *
 * Commands (type them in chat, inside a world):
 *   /leanframe bench    measure FPS for 20 seconds and log it
 *   /leanframe status   show FPS, FPS limit, VSync and detected performance mods
 *   /leanframe uncap    FPS limit Unlimited + VSync off (old values are saved)
 *   /leanframe restore  put the old values back
 */
public final class LeanFrame implements ClientModInitializer {
    public static final String MOD_ID = "leanframe";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitializeClient() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> Benchmark.onClientTick());

        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
                dispatcher.register(ClientCommands.literal("leanframe")
                        .executes(context -> {
                            Tools.help(message -> context.getSource().sendFeedback(message));
                            return 1;
                        })
                        .then(ClientCommands.literal("status").executes(context -> {
                            Tools.status(message -> context.getSource().sendFeedback(message));
                            return 1;
                        }))
                        .then(ClientCommands.literal("uncap").executes(context -> {
                            Tools.uncap(message -> context.getSource().sendFeedback(message));
                            return 1;
                        }))
                        .then(ClientCommands.literal("restore").executes(context -> {
                            Tools.restore(message -> context.getSource().sendFeedback(message));
                            return 1;
                        }))
                        .then(ClientCommands.literal("bench").executes(context -> {
                            Benchmark.start(message -> context.getSource().sendFeedback(message));
                            return 1;
                        }))));

        LOGGER.info("LeanFrame loaded. Type /leanframe in a world to see the commands.");
    }
}
