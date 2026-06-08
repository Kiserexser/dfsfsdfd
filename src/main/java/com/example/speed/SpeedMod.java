package com.example.speed;

import net.fabricmc.api.ModInitializer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Random;

public class SpeedMod implements ModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("speedmod");
    private static final MinecraftClient mc = MinecraftClient.getInstance();
    private static final Random random = new Random();

    private static boolean enabled = false;
    private static boolean lastRState = false;
    private static int jumpTicks = 0;
    private static double verticalVelocity = 0;

    // Настройки (можно менять)
    private static final double VERTICAL_BOOST = 0.28;  // сила подъёма
    private static final double HORIZONTAL_BOOST = 0.08; // горизонтальное ускорение
    private static final int MIN_INTERVAL = 2;          // мин. тиков между импульсами
    private static final int MAX_INTERVAL = 5;          // макс. тиков
    private static int currentInterval = MIN_INTERVAL;

    @Override
    public void onInitialize() {
        LOGGER.info("[RW Flight] Jump Flight + Horizontal Boost. Press R to toggle.");
        Thread tickThread = new Thread(() -> {
            while (true) {
                try {
                    Thread.sleep(50); // 20 tps
                    if (mc.player == null || mc.world == null) continue;

                    long window = mc.getWindow().getHandle();
                    boolean currentR = GLFW.glfwGetKey(window, GLFW.GLFW_KEY_R) == GLFW.GLFW_PRESS;
                    if (currentR && !lastRState) {
                        enabled = !enabled;
                        LOGGER.info(enabled ? "Flight ON (Jump Flight)" : "Flight OFF");
                        if (!enabled) {
                            verticalVelocity = 0;
                            jumpTicks = 0;
                        }
                        Thread.sleep(150);
                    }
                    lastRState = currentR;

                    if (enabled) {
                        tick();
                    }
                } catch (InterruptedException e) {
                    break;
                }
            }
        });
        tickThread.setDaemon(true);
        tickThread.start();
    }

    private static void tick() {
        // Проверяем, зажат ли прыжок
        KeyBinding jumpKey = mc.options.jumpKey;
        boolean isJumping = jumpKey.isPressed();

        if (isJumping) {
            if (!mc.player.isOnGround()) {
                // В воздухе: даём импульс вверх каждые несколько тиков (рандом)
                if (jumpTicks >= currentInterval) {
                    // Небольшое случайное отклонение, чтобы не быть идеальным
                    double boost = VERTICAL_BOOST + (random.nextDouble() * 0.05);
                    mc.player.addVelocity(0, boost, 0);
                    verticalVelocity = mc.player.getVelocity().y;

                    // Горизонтальный буст (если игрок двигается)
                    float forward = mc.player.input.movementForward;
                    float strafe = mc.player.input.movementSideways;
                    if (forward != 0 || strafe != 0) {
                        float yaw = mc.player.getYaw();
                        double rad = Math.toRadians(yaw);
                        double horizontal = HORIZONTAL_BOOST + (random.nextDouble() * 0.03);
                        double vx = -Math.sin(rad) * forward * horizontal;
                        double vz = Math.cos(rad) * forward * horizontal;
                        // добавляем strafe
                        if (strafe != 0) {
                            double strafeRad = Math.toRadians(yaw + (strafe > 0 ? -90 : 90));
                            vx += -Math.sin(strafeRad) * strafe * horizontal;
                            vz += Math.cos(strafeRad) * strafe * horizontal;
                        }
                        mc.player.addVelocity(vx, 0, vz);
                    }

                    // Сбрасываем счётчик и выбираем новый интервал
                    jumpTicks = 0;
                    currentInterval = MIN_INTERVAL + random.nextInt(MAX_INTERVAL - MIN_INTERVAL + 1);
                } else {
                    jumpTicks++;
                }
            } else {
                // На земле: если зажат прыжок, просто прыгаем, но даём небольшой бонус
                if (jumpTicks == 0) {
                    mc.player.jump();
                    // Небольшое дополнительное ускорение вверх
                    mc.player.addVelocity(0, 0.1, 0);
                }
                jumpTicks++;
                if (jumpTicks > 5) jumpTicks = 0;
            }
        } else {
            // Если не прыгаем, сбрасываем счётчики
            jumpTicks = 0;
            verticalVelocity = 0;
        }

        // Дополнительно: если игрок в воздухе и держит W, даём горизонтальный дрифт
        if (!mc.player.isOnGround() && (mc.player.input.movementForward != 0 || mc.player.input.movementSideways != 0)) {
            // Небольшое ускорение каждые 2 тика
            if (mc.player.age % 2 == 0) {
                float yaw = mc.player.getYaw();
                float forward = mc.player.input.movementForward;
                float strafe = mc.player.input.movementSideways;
                double rad = Math.toRadians(yaw);
                double speed = 0.04;
                double vx = -Math.sin(rad) * forward * speed;
                double vz = Math.cos(rad) * forward * speed;
                if (strafe != 0) {
                    double strafeRad = Math.toRadians(yaw + (strafe > 0 ? -90 : 90));
                    vx += -Math.sin(strafeRad) * strafe * speed;
                    vz += Math.cos(strafeRad) * strafe * speed;
                }
                mc.player.addVelocity(vx, 0, vz);
            }
        }
    }
}
