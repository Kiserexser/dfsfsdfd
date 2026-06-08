package com.example.speed;

import net.fabricmc.api.ModInitializer;
import net.minecraft.client.MinecraftClient;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Random;

public class SpeedMod implements ModInitializer {
    private static final Logger LOGGER = LoggerFactory.getLogger("speedmod");
    private static final MinecraftClient mc = MinecraftClient.getInstance();
    private static final Random RAND = new Random();

    private static boolean enabled = false;
    private static boolean lastR = false;
    private static long lastBoostTick = 0;

    // настройки обхода паутины
    private static final long MIN_DELAY_TICKS = 1;
    private static final long MAX_DELAY_TICKS = 3;
    private static final double VERTICAL_BOOST = 0.28;
    private static final double HORIZONTAL_BOOST = 0.08;

    @Override
    public void onInitialize() {
        LOGGER.info("[Web Bypass] Press R to toggle web bypass. Works only in web.");
        new Thread(() -> {
            while (true) {
                try { Thread.sleep(50); } catch (InterruptedException e) { break; }
                if (mc.player == null || mc.world == null) continue;
                long win = mc.getWindow().getHandle();
                boolean currR = GLFW.glfwGetKey(win, GLFW.GLFW_KEY_R) == GLFW.GLFW_PRESS;
                if (currR && !lastR) {
                    enabled = !enabled;
                    LOGGER.info(enabled ? "Web Bypass ON" : "Web Bypass OFF");
                    try { Thread.sleep(150); } catch (InterruptedException ignored) {}
                }
                lastR = currR;
                if (enabled) tick();
            }
        }).start();
    }

    private static void tick() {
        if (mc.player == null) return;
        // только если в паутине
        if (!mc.player.isInWeb()) return;

        // активируем спринт для естественности
        if (!mc.player.isSprinting()) {
            mc.player.setSprinting(true);
        }

        // импульсы с рандомным интервалом
        long now = mc.player.age;
        long interval = MIN_DELAY_TICKS + RAND.nextInt((int)(MAX_DELAY_TICKS - MIN_DELAY_TICKS + 1));
        if (now - lastBoostTick >= interval) {
            // небольшой случайный подъём
            double up = VERTICAL_BOOST + RAND.nextDouble() * 0.1;
            mc.player.addVelocity(0, up, 0);

            // горизонтальный импульс в направлении движения
            float yaw = mc.player.getYaw();
            float forward = mc.player.input.movementForward;
            float strafe = mc.player.input.movementSideways;
            if (forward != 0 || strafe != 0) {
                double rad = Math.toRadians(yaw);
                double horiz = HORIZONTAL_BOOST + RAND.nextDouble() * 0.04;
                double vx = -Math.sin(rad) * forward * horiz;
                double vz = Math.cos(rad) * forward * horiz;
                if (strafe != 0) {
                    double strafeRad = Math.toRadians(yaw + (strafe > 0 ? -90 : 90));
                    vx += -Math.sin(strafeRad) * strafe * horiz;
                    vz += Math.cos(strafeRad) * strafe * horiz;
                }
                mc.player.addVelocity(vx, 0, vz);
            }
            lastBoostTick = now;
        }
    }
}
