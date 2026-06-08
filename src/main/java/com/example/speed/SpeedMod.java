package com.example.speed;

import net.fabricmc.api.ModInitializer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
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
                    mc.player.sendMessage(Text.literal(enabled ? "§aGrimSpeed ON" : "§cGrimSpeed OFF"), true);
                    try { Thread.sleep(200); } catch (InterruptedException ignored) {}
                }
                lastR = currentR;
                if (enabled) tick();
            }
        }).start();
    }

    private void tick() {
        if (mc.player == null || mc.getNetworkHandler() == null) return;

        // Принудительный прыжок (каждый тик)
        mc.player.jump();

        // Отправка пакетов: обычный пакет движения (StatusOnly не существует)
        if (mc.player.age % 2 == 0) {
            // В 1.21.4 пакет движения с флагом onGround
            mc.getNetworkHandler().sendPacket(new PlayerMoveC2SPacket(mc.player.isOnGround()));
            // Пакет активации элитры
            mc.getNetworkHandler().sendPacket(new ClientCommandC2SPacket(mc.player, ClientCommandC2SPacket.Mode.START_FALL_FLYING));
        }

        // Расчёт скорости (как в оригинале)
        float grim = 0.03f;
        grim *= mc.player.isOnGround() ? 2.8500699f : 1.0200699f;

        float dir = getDirection();
        if (dir != -1.0f) {
            double yaw = Math.toRadians(dir + 90f);
            double mx = grim * Math.cos(yaw);
            double mz = grim * Math.sin(yaw);
            mc.player.addVelocity(mx, 0.0, mz);
        }

        // Вертикальная коррекция в воздухе
        if (!mc.player.isOnGround()) {
            mc.player.addVelocity(0.0, -0.050699, 0.0);
        }
    }

    private float getDirection() {
        float yaw = mc.player.getYaw();
        float forward = mc.player.input.movementForward;
        float strafe = mc.player.input.movementSideways;
        if (forward == 0 && strafe == 0) return -1.0f;
        float angle = yaw + (strafe > 0 ? -90 : 90) * (strafe != 0 ? 1 : 0);
        if (forward < 0) angle += 180;
        return angle;
    }
}
