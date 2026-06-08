package com.example.speed;

import net.fabricmc.api.ModInitializer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.hit.HitResult;
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
    private static long lastAttackTime = 0;
    private static final Random random = new Random();

    // Настройки
    private static final float RANGE = 4.2f;          // дальность атаки
    private static final float REQUIRED_ANGLE = 15f;  // градусов от центра экрана
    private static final long MIN_DELAY_MS = 820;
    private static final long MAX_DELAY_MS = 930;

    @Override
    public void onInitialize() {
        LOGGER.info("[SpeedMod] Simple Killaura - no aim assist. Press R to toggle.");

        Thread tickThread = new Thread(() -> {
            while (true) {
                try {
                    Thread.sleep(50); // 20 тиков в секунду
                    MinecraftClient client = MinecraftClient.getInstance();
                    if (client.player == null || client.world == null) continue;

                    long window = client.getWindow().getHandle();
                    boolean currentR = GLFW.glfwGetKey(window, GLFW.GLFW_KEY_R) == GLFW.GLFW_PRESS;

                    if (currentR && !lastRState) {
                        enabled = !enabled;
                        LOGGER.info(enabled ? "[Killaura] ENABLED" : "[Killaura] DISABLED");
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
        // 1. Найти ближайшую цель в радиусе, на которую смотрит игрок
        Entity target = findTarget(client);
        if (target == null) return;

        // 2. Проверка задержки атаки
        long now = System.currentTimeMillis();
        long delay = MIN_DELAY_MS + (long)(random.nextDouble() * (MAX_DELAY_MS - MIN_DELAY_MS));
        if (now - lastAttackTime < delay) return;

        // 3. Удар (сохраняем спринт)
        boolean wasSprinting = client.player.isSprinting();
        client.interactionManager.attackEntity(client.player, target);
        if (wasSprinting) client.player.setSprinting(true);
        client.player.setSprinting(true); // на случай, если сервер сбросил

        lastAttackTime = now;
    }

    private static Entity findTarget(MinecraftClient client) {
        // Ближайшая сущность в радиусе, на которую смотрит игрок
        Entity bestEntity = null;
        double closestDistance = RANGE * RANGE;

        Box searchBox = client.player.getBoundingBox().expand(RANGE);
        List<Entity> entities = client.world.getOtherEntities(client.player, searchBox,
                e -> e instanceof LivingEntity && e != client.player && !((LivingEntity) e).isDead());

        for (Entity e : entities) {
            if (e instanceof PlayerEntity && client.player.isTeammate((PlayerEntity) e)) continue;

            double distSq = client.player.squaredDistanceTo(e);
            if (distSq > closestDistance) continue;

            // Проверка, смотрит ли игрок на эту сущность
            if (!isLookingAt(client, e, REQUIRED_ANGLE)) continue;

            // Проверка видимости (не сквозь стены)
            if (!client.player.canSee(e)) continue;

            closestDistance = distSq;
            bestEntity = e;
        }
        return bestEntity;
    }

    private static boolean isLookingAt(MinecraftClient client, Entity entity, float maxAngleDegrees) {
        Vec3d eyePos = client.player.getEyePos();
        Vec3d lookVec = client.player.getRotationVector(); // направление взгляда
        Vec3d toEntity = entity.getBoundingBox().getCenter().subtract(eyePos).normalize();

        double dot = lookVec.dotProduct(toEntity);
        double angleRad = Math.acos(Math.min(1.0, Math.max(-1.0, dot)));
        double angleDeg = Math.toDegrees(angleRad);
        return angleDeg <= maxAngleDegrees;
    }
}
