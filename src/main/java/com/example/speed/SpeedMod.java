package com.example.speed;

import net.fabricmc.api.ModInitializer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

public class SpeedMod implements ModInitializer {
    private static final MinecraftClient mc = MinecraftClient.getInstance();
    private static boolean enabled = false;
    private static boolean lastR = false;
    private static int jumpDelay = 0; // для автопрыжка

    // Настройки (подбери под сервер)
    private static final double SPEED_MULTIPLIER = 1.35;  // множитель скорости (1.0 = норма, 1.35 = +35%)
    private static final boolean AUTO_JUMP = true;       // автопрыжок (BHop)
    private static final int JUMP_DELAY_TICKS = 2;       // задержка между прыжками (в тиках)

    @Override
    public void onInitialize() {
        new Thread(() -> {
            while (true) {
                try { Thread.sleep(50); } catch (InterruptedException e) { break; }
                if (mc.player == null) continue;

                long window = mc.getWindow().getHandle();
                boolean currentR = GLFW.glfwGetKey(window, GLFW.GLFW_KEY_R) == GLFW.GLFW_PRESS;
                if (currentR && !lastR) {
                    enabled = !enabled;
                    mc.player.sendMessage(Text.literal(enabled ? "§aNormalSpeed ON" : "§cNormalSpeed OFF"), true);
                    try { Thread.sleep(200); } catch (InterruptedException ignored) {}
                }
                lastR = currentR;

                if (enabled) {
                    tick();
                }
            }
        }).start();
    }

    private void tick() {
        if (mc.player == null) return;

        // ----- Автопрыжок (BHop) -----
        if (AUTO_JUMP) {
            if (mc.player.isOnGround() && mc.options.jumpKey.isPressed() && jumpDelay <= 0) {
                mc.player.jump();
                jumpDelay = JUMP_DELAY_TICKS;
            }
            if (jumpDelay > 0) jumpDelay--;
        }

        // ----- Ускорение -----
        float forward = mc.player.input.movementForward;
        if (forward <= 0) return; // ускоряем только если идём вперёд

        // Получаем текущую скорость
        double currentSpeed = Math.hypot(mc.player.getVelocity().x, mc.player.getVelocity().z);
        double maxSpeed = 0.3 * SPEED_MULTIPLIER; // базовая скорость спринта 0.3

        if (currentSpeed < maxSpeed) {
            // Плавно добавляем скорость
            float yaw = mc.player.getYaw();
            double rad = Math.toRadians(yaw);
            double addX = -Math.sin(rad) * 0.02;
            double addZ = Math.cos(rad) * 0.02;
            mc.player.addVelocity(addX, 0, addZ);
        }

        // Автоспринт
        if (forward > 0) mc.player.setSprinting(true);
    }
}
