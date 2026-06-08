package com.example.speed;

import net.fabricmc.api.ModInitializer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

public class SpeedMod implements ModInitializer {
    private static final MinecraftClient mc = MinecraftClient.getInstance();
    private static boolean enabled = false;
    private static boolean lastR = false;

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
                    mc.player.sendMessage(Text.literal(enabled ? "§aLegitSpeed ON" : "§cLegitSpeed OFF"), true);
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

        // 1. Автоспринт (если зажат W)
        if (mc.player.input.movementForward > 0) {
            mc.player.setSprinting(true);
        }

        // 2. No Sprint Reset (сохраняем спринт после атаки)
        // Просто каждый тик форсируем спринт, если нужно
        if (mc.player.input.movementForward > 0 && mc.player.isSprinting()) {
            // ничего не делаем, спринт уже включён
        }

        // 3. Лёгкий Strafe (улучшение поворотов) – очень мягко
        if (mc.player.isSprinting() && mc.player.isOnGround()) {
            float yaw = mc.player.getYaw();
            float forward = mc.player.input.movementForward;
            float strafe = mc.player.input.movementSideways;
            if (forward > 0 && strafe == 0) {
                // Идеальный поворот: если игрок поворачивает, не замедляться
                // В ваниле при повороте скорость снижается, мы это компенсируем
                double currentSpeed = Math.hypot(mc.player.getVelocity().x, mc.player.getVelocity().z);
                double idealSpeed = 0.32; // ванильный спринт
                if (currentSpeed < idealSpeed - 0.02) {
                    // Добавляем микро-импульс только если скорость упала ниже нормы
                    double rad = Math.toRadians(yaw);
                    mc.player.addVelocity(-Math.sin(rad) * 0.005, 0, Math.cos(rad) * 0.005);
                }
            }
        }
    }
}
