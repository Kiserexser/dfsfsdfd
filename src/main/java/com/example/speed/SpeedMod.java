package com.example.speed;

import net.fabricmc.api.ModInitializer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

public class SpeedMod implements ModInitializer {
    private static final MinecraftClient mc = MinecraftClient.getInstance();
    private static boolean enabled = false;
    private static boolean lastRState = false;
    private static int tickCounter = 0;

    @Override
    public void onInitialize() {
        new Thread(() -> {
            while (true) {
                try { Thread.sleep(50); } catch (InterruptedException e) { break; }
                if (mc.player == null) continue;
                long window = mc.getWindow().getHandle();
                boolean currentR = GLFW.glfwGetKey(window, GLFW.GLFW_KEY_R) == GLFW.GLFW_PRESS;
                if (currentR && !lastRState) {
                    enabled = !enabled;
                    String msg = enabled ? "§aWebFly ON" : "§cWebFly OFF";
                    if (mc.player != null) mc.player.sendMessage(Text.literal(msg), true);
                    try { Thread.sleep(200); } catch (InterruptedException ignored) {}
                }
                lastRState = currentR;
                if (enabled) {
                    tickCounter++;
                    handleWebFly();
                }
            }
        }).start();
    }

    private void handleWebFly() {
        if (mc.player == null) return;

        // Проверяем, находится ли игрок в паутине
        boolean inWeb = mc.player.isInWeb();

        if (inWeb) {
            // Каждый 1-2 тика даём импульс вверх (рандом, чтобы не палиться)
            if (tickCounter % (1 + (int)(Math.random() * 2)) == 0) {
                mc.player.addVelocity(0, 0.42, 0);
            }
            // Автоматически включаем спринт и прыжок для лучшего эффекта
            mc.player.setSprinting(true);
            if (mc.options.jumpKey.isPressed()) {
                mc.player.jump();
            }
        } else {
            // Если не в паутине, но игрок в воздухе – немного замедляем падение (опционально)
            if (!mc.player.isOnGround() && mc.player.getVelocity().y < -0.1) {
                mc.player.addVelocity(0, 0.04, 0);
            }
        }
    }
}
