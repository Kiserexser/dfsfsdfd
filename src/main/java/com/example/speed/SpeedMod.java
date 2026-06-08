package com.example.speed;

import net.fabricmc.api.ModInitializer;
import net.minecraft.client.MinecraftClient;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SpeedMod implements ModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("speedmod");
    private static final MinecraftClient mc = MinecraftClient.getInstance();
    private static boolean lastRShift = false;

    @Override
    public void onInitialize() {
        LOGGER.info("Speed Mod with Console Screen loaded. Press RIGHT SHIFT to open console.");
        new Thread(() -> {
            while (true) {
                try { Thread.sleep(50); } catch (InterruptedException e) { break; }
                if (mc.player != null && mc.currentScreen == null) {
                    long window = mc.getWindow().getHandle();
                    boolean rShift = GLFW.glfwGetKey(window, GLFW.GLFW_KEY_RIGHT_SHIFT) == GLFW.GLFW_PRESS;
                    if (rShift && !lastRShift) {
                        mc.execute(() -> mc.setScreen(new ConsoleScreen()));
                        try { Thread.sleep(200); } catch (InterruptedException ignored) {}
                    }
                    lastRShift = rShift;
                }
            }
        }).start();
    }
}
