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

public class SpeedMod implements ModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("speedmod");

    private static boolean enabled = false;
    private static boolean lastRState = false;
    private static Entity target = null;
    private static long lastAttackTime = 0;
    private static final Random random = new Random();

    // Плавная ротация
    private static float currentYaw = 0;
    private static float currentPitch = 0;
    private static boolean initialized = false;
    private static final float ROTATION_SPEED = 0.12f; // 12% от разницы за тик – плавно, без рывков

    private static final float RANGE = 4.2f;
    private static final long MIN_ATTACK_DELAY = 820;
    private static final long MAX_ATTACK_DELAY = 930;
    private static final float REQUIRED_ANGLE = 12.0f; // градусов до цели, чтобы начать атаку

    @Override
    public void onInitialize() {
        LOGGER.info("[RW] Smooth Rotation Killaura (camera turns gently). Press R.");
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
                        LOGGER.info(enabled ? "Killaura ON (smooth rotation)" : "Killaura OFF");
                        if (!enabled) target = null;
                        Thread.sleep(150);
                    }
                    lastRState = currentR;
                    if (enabled) tick(client);
                } catch (InterruptedException e) { break; }
            }
        });
        tickThread.setDaemon(true);
        tickThread.start();
    }

    private static void tick(MinecraftClient client) {
        updateTarget(client);
        if (target == null) {
            initialized = false;
            return;
        }

        // Инициализация текущих углов
        if (!initialized) {
            currentYaw = client.player.getYaw();
            currentPitch = client.player.getPitch();
            initialized = true;
        }

        // Вычисляем идеальный угол на цель
        Vec3d eyePos = client.player.getEyePos();
        Vec3d targetVec = target.getBoundingBox().getCenter().subtract(eyePos);
        double hyp = Math.hypot(targetVec.x, targetVec.z);
        float idealYaw = (float) (Math.toDegrees(Math.atan2(targetVec.z, targetVec.x)) - 90);
        float idealPitch = (float) -Math.toDegrees(Math.atan2(targetVec.y, hyp));
        idealYaw = wrapDegrees(idealYaw);
        idealPitch = clamp(idealPitch, -89, 89);

        // Плавное изменение углов (ротация)
        float deltaYaw = wrapDegrees(idealYaw - currentYaw);
        float deltaPitch = idealPitch - currentPitch;
        currentYaw += deltaYaw * ROTATION_SPEED;
        currentPitch += deltaPitch * ROTATION_SPEED;
        currentYaw = wrapDegrees(currentYaw);
        currentPitch = clamp(currentPitch, -89, 89);

        // Применяем поворот к камере (плавно, без рывков)
        client.player.setYaw(currentYaw);
        client.player.setPitch(currentPitch);
        client.player.bodyYaw = currentYaw;
        client.player.headYaw = currentYaw;

        // Проверяем, смотрим ли мы достаточно близко к цели
        float angleToTarget = getAngleToTarget(client, target);
        if (angleToTarget > REQUIRED_ANGLE) return; // не атакуем, если смотрим мимо

        // Атака с задержкой
        long now = System.currentTimeMillis();
        long delay = MIN_ATTACK_DELAY + (long)(random.nextDouble() * (MAX_ATTACK_DELAY - MIN_ATTACK_DELAY));
        if (now - lastAttackTime >= delay) {
            boolean wasSprinting = client.player.isSprinting();
            client.interactionManager.attackEntity(client.player, target);
            if (wasSprinting) client.player.setSprinting(true);
            client.player.setSprinting(true);
            lastAttackTime = now;
        }
    }

    private static void updateTarget(MinecraftClient client) {
        if (target != null && target.isAlive() && client.player.squaredDistanceTo(target) <= RANGE * RANGE) {
            return;
        }
        Entity best = null;
        double closest = RANGE * RANGE;
        Box box = client.player.getBoundingBox().expand(RANGE);
        List<Entity> entities = client.world.getOtherEntities(client.player, box,
                e -> e instanceof LivingEntity && e != client.player && !((LivingEntity) e).isDead());
        for (Entity e : entities) {
            if (e instanceof PlayerEntity && client.player.isTeammate((PlayerEntity) e)) continue;
            double distSq = client.player.squaredDistanceTo(e);
            if (distSq < closest && client.player.canSee(e)) {
                closest = distSq;
                best = e;
            }
        }
        target = best;
    }

    private static float getAngleToTarget(MinecraftClient client, Entity target) {
        Vec3d eyePos = client.player.getEyePos();
        Vec3d lookVec = client.player.getRotationVector();
        Vec3d toTarget = target.getBoundingBox().getCenter().subtract(eyePos).normalize();
        double dot = lookVec.dotProduct(toTarget);
        double angleRad = Math.acos(Math.min(1.0, Math.max(-1.0, dot)));
        return (float) Math.toDegrees(angleRad);
    }

    private static float wrapDegrees(float value) {
        value %= 360.0f;
        if (value >= 180.0f) value -= 360.0f;
        if (value < -180.0f) value += 360.0f;
        return value;
    }

    private static float clamp(float value, float min, float max) {
        if (value < min) return min;
        if (value > max) return max;
        return value;
    }
}
