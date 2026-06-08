package com.example.speed;

import net.fabricmc.api.ModInitializer;
import net.minecraft.client.MinecraftClient;
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
    private static int glideTicks = 0;
    private static double lastY = 0;
    private static int lastResetTick = 0;

    // Параметры (настраиваемые)
    private static final double JUMP_FORCE = 0.42;        // сила прыжка (ванильная ~0.42)
    private static final double EXTRA_UP = 0.12;          // дополнительный вертикальный импульс при зажатии прыжка
    private static final double GLIDE_SLOW = 0.04;        // замедление падения (положительное добавление к вертикальной скорости)
    private static final int MIN_BOOST_INTERVAL = 2;      // минимальные тики между импульсами
    private static final int MAX_BOOST_INTERVAL = 5;      // максимальные тики
    private static final double HORIZONTAL_BOOST = 0.045;  // горизонтальное ускорение в воздухе
    private static final double NOISE_RANGE = 0.008;       // случайный шум к скорости

    @Override
    public void onInitialize() {
        LOGGER.info("[Matrix/Grim Flight] Jump+Glide+Noise. No Elytra. Press R.");
        Thread tickThread = new Thread(() -> {
            while (true) {
                try {
                    Thread.sleep(50);
                    if (mc.player == null || mc.world == null) continue;
                    long window = mc.getWindow().getHandle();
                    boolean currentR = GLFW.glfwGetKey(window, GLFW.GLFW_KEY_R) == GLFW.GLFW_PRESS;
                    if (currentR && !lastRState) {
                        enabled = !enabled;
                        LOGGER.info(enabled ? "Flight ON (Safe mode)" : "Flight OFF");
                        if (!enabled) {
                            jumpTicks = 0;
                            glideTicks = 0;
                        }
                        Thread.sleep(150);
                    }
                    lastRState = currentR;
                    if (enabled) tick();
                } catch (InterruptedException e) { break; }
            }
        });
        tickThread.setDaemon(true);
        tickThread.start();
    }

    private static void tick() {
        boolean jumpPressed = mc.options.jumpKey.isPressed();

        // ---- Обход отката: если сервер скорректировал позицию вниз, сбрасываем импульсы ----
        double currentY = mc.player.getY();
        if (currentY < lastY - 0.5 && mc.player.age - lastResetTick > 10) {
            // Произошёл откат (античит опустил игрока)
            jumpTicks = MAX_BOOST_INTERVAL + 1; // временно блокируем импульсы
            lastResetTick = mc.player.age;
            LOGGER.debug("Anti-cheat rollback detected, pausing boosts");
        }
        lastY = currentY;

        // ---- Glide: замедление падения при зажатом прыжке в воздухе (без элитры) ----
        if (jumpPressed && !mc.player.isOnGround()) {
            if (mc.player.getVelocity().y < -0.05) {
                // Добавляем небольшой положительный импульс, чтобы замедлить падение
                if (glideTicks >= 1) {
                    double glideBoost = GLIDE_SLOW + (random.nextDouble() * 0.02);
                    mc.player.addVelocity(0, glideBoost, 0);
                    glideTicks = 0;
                } else {
                    glideTicks++;
                }
            }
        } else {
            glideTicks = 0;
        }

        // ---- Jump Flight: дополнительные импульсы вверх при зажатом прыжке в воздухе ----
        if (jumpPressed && !mc.player.isOnGround()) {
            // Рандомизированный интервал между импульсами (обход паттернов)
            int interval = MIN_BOOST_INTERVAL + random.nextInt(MAX_BOOST_INTERVAL - MIN_BOOST_INTERVAL + 1);
            if (jumpTicks >= interval) {
                // Сила импульса с небольшим случайным разбросом
                double up = EXTRA_UP + (random.nextDouble() * 0.06) - 0.03;
                mc.player.addVelocity(0, up, 0);

                // Горизонтальный буст, если игрок двигается
                float forward = mc.player.input.movementForward;
                float strafe = mc.player.input.movementSideways;
                if (forward != 0 || strafe != 0) {
                    float yaw = mc.player.getYaw();
                    double rad = Math.toRadians(yaw);
                    double horizontal = HORIZONTAL_BOOST + (random.nextDouble() * 0.02);
                    double vx = -Math.sin(rad) * forward * horizontal;
                    double vz = Math.cos(rad) * forward * horizontal;
                    if (strafe != 0) {
                        double strafeRad = Math.toRadians(yaw + (strafe > 0 ? -90 : 90));
                        vx += -Math.sin(strafeRad) * strafe * horizontal;
                        vz += Math.cos(strafeRad) * strafe * horizontal;
                    }
                    mc.player.addVelocity(vx, 0, vz);
                }

                // Добавляем микро-шум ко всем осям (обход идеальных скоростей)
                double noiseX = (random.nextDouble() - 0.5) * NOISE_RANGE;
                double noiseZ = (random.nextDouble() - 0.5) * NOISE_RANGE;
                mc.player.addVelocity(noiseX, 0, noiseZ);

                jumpTicks = 0;
            } else {
                jumpTicks++;
            }
        } else {
            jumpTicks = 0;
        }

        // ---- Дополнительный обход: если игрок на земле и прыгает, даём небольшой бонус ----
        if (jumpPressed && mc.player.isOnGround() && mc.player.age % 3 == 0) {
            mc.player.jump();
            // Увеличиваем силу прыжка незначительно
            Vec3d vel = mc.player.getVelocity();
            mc.player.setVelocity(vel.x, JUMP_FORCE + 0.02, vel.z);
        }

        // ---- Anti-Kick: если слишком долго в воздухе, сбрасываем импульсы на секунду ----
        if (mc.player.age % 100 == 0 && !mc.player.isOnGround()) {
            jumpTicks = MAX_BOOST_INTERVAL + 2;
        }
    }
}
