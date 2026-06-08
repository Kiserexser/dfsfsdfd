package com.example.speed;

import net.fabricmc.api.ModInitializer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Random;
import java.util.concurrent.ConcurrentLinkedQueue;

public class SpeedMod implements ModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("speedmod");

    private static boolean enabled = false;
    private static boolean lastRState = false;
    private static Entity target = null;
    private static long lastAttackTime = 0;
    private static final ConcurrentLinkedQueue<Long> attackTimes = new ConcurrentLinkedQueue<>();
    private static final Random random = new Random();

    // Для плавного доворота (без дёрганья)
    private static float targetYaw = 0;
    private static float targetPitch = 0;
    private static boolean hasTargetAngles = false;
    private static long lastMouseMoveTime = 0;

    private static final float RANGE = 4.5f;          // увеличен для бега
    private static final long MIN_DELAY_MS = 820;
    private static final long MAX_DELAY_MS = 930;
    private static final float SMOOTH_FACTOR = 0.05f; // очень плавно (5% за тик)

    @Override
    public void onInitialize() {
        LOGGER.info("[SpeedMod] Smooth killaura, no aim jerk. Press R to toggle.");

        Thread tickThread = new Thread(() -> {
            while (true) {
                try {
                    Thread.sleep(50);
                    MinecraftClient client = MinecraftClient.getInstance();
                    if (client.player == null || client.world == null) continue;

                    long window = client.getWindow().getHandle();
                    boolean currentR = GLFW.glfwGetKey(window, GLFW.GLFW_KEY_R) == GLFW.GLFW_PRESS;

                    if (currentR && !lastRState) {
                        enabled = !enabled;
                        if (enabled) {
                            LOGGER.info("[Killaura] ENABLED (smooth, no jerk)");
                            hasTargetAngles = false;
                        } else {
                            LOGGER.info("[Killaura] DISABLED");
                            target = null;
                        }
                        Thread.sleep(150);
                    }
                    lastRState = currentR;

                    if (enabled) {
                        tick(client);
                    }
                } catch (InterruptedException e) {
                    break;
                }
            }
        });
        tickThread.setDaemon(true);
        tickThread.start();
    }

    private static void tick(MinecraftClient client) {
        // Обновляем цель
        updateTarget(client);
        if (target == null) {
            hasTargetAngles = false;
            return;
        }

        // Плавное наведение (без захвата мыши)
        smoothAim(client);

        // Атака с задержкой
        long now = System.currentTimeMillis();
        long delay = MIN_DELAY_MS + (long)(random.nextDouble() * (MAX_DELAY_MS - MIN_DELAY_MS));
        if (now - lastAttackTime < delay) return;

        // Удар с сохранением спринта
        boolean wasSprinting = client.player.isSprinting();
        client.interactionManager.attackEntity(client.player, target);
        if (wasSprinting) client.player.setSprinting(true);
        client.player.setSprinting(true); // форсируем для надёжности

        lastAttackTime = now;
        attackTimes.add(now);
    }

    private static void updateTarget(MinecraftClient client) {
        Entity best = null;
        double closest = RANGE * RANGE;

        Box box = client.player.getBoundingBox().expand(RANGE);
        List<Entity> entities = client.world.getOtherEntities(client.player, box,
                e -> e instanceof LivingEntity && e != client.player && !((LivingEntity) e).isDead());

        for (Entity e : entities) {
            if (e instanceof PlayerEntity && client.player.isTeammate((PlayerEntity) e)) continue;
            double dist = client.player.squaredDistanceTo(e);
            // Убираем проверку canSee для бега – атакуем даже через стены (но можно вернуть)
            if (dist < closest) {
                closest = dist;
                best = e;
            }
        }
        target = best;
    }

    private static void smoothAim(MinecraftClient client) {
        if (target == null) return;

        // Получаем идеальный угол на цель
        Vec3d eyePos = client.player.getEyePos();
        Vec3d targetVec = target.getBoundingBox().getCenter().subtract(eyePos);
        double hyp = Math.hypot(targetVec.x, targetVec.z);
        float idealYaw = (float) Math.toDegrees(Math.atan2(targetVec.z, targetVec.x)) - 90;
        float idealPitch = (float) -Math.toDegrees(Math.atan2(targetVec.y, hyp));
        idealYaw = wrapDegrees(idealYaw);
        idealPitch = clamp(idealPitch, -89, 89);

        // Если мы не инициализировали целевые углы – берём текущие как базу
        if (!hasTargetAngles) {
            targetYaw = client.player.getYaw();
            targetPitch = client.player.getPitch();
            hasTargetAngles = true;
        }

        // Обнаружение активного движения мышью (чтобы не мешать игроку)
        float yawDiff = Math.abs(wrapDegrees(idealYaw - client.player.getYaw()));
        float pitchDiff = Math.abs(idealPitch - client.player.getPitch());
        if (yawDiff > 15.0f || pitchDiff > 10.0f) {
            // Игрок сам сильно крутит – не мешаем, просто запоминаем текущую цель
            targetYaw = client.player.getYaw();
            targetPitch = client.player.getPitch();
            return;
        }

        // Плавное смещение целевых углов к идеальным (очень медленно)
        float yawDelta = wrapDegrees(idealYaw - targetYaw);
        float pitchDelta = idealPitch - targetPitch;
        targetYaw += yawDelta * SMOOTH_FACTOR;
        targetPitch += pitchDelta * SMOOTH_FACTOR;
        targetYaw = wrapDegrees(targetYaw);
        targetPitch = clamp(targetPitch, -89, 89);

        // Применяем к игроку – но так как скорость маленькая, мышь не блокируется
        client.player.setYaw(targetYaw);
        client.player.setPitch(targetPitch);
        // Синхронизируем тело и голову для обхода античитов
        client.player.bodyYaw = targetYaw;
        client.player.headYaw = targetYaw;
        if (client.player.age % 8 == 0) {
            client.player.headYaw += (random.nextFloat() - 0.5f) * 0.6f;
        }

        // Добавляем микро-шум (для натуральности)
        float noiseYaw = (random.nextFloat() - 0.5f) * 0.018f;
        float noisePitch = (random.nextFloat() - 0.5f) * 0.014f;
        client.player.setYaw(targetYaw + noiseYaw);
        client.player.setPitch(targetPitch + noisePitch);
    }

    // Вспомогательные методы
    private static float wrapDegrees(float value) {
        value %= 360.0F;
        if (value >= 180.0F) value -= 360.0F;
        if (value < -180.0F) value += 360.0F;
        return value;
    }

    private static float clamp(float value, float min, float max) {
        if (value < min) return min;
        if (value > max) return max;
        return value;
    }
}
