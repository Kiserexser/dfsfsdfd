package com.example.speed;

import net.fabricmc.api.ModInitializer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
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
    private static long lastLookPacketTime = 0;
    private static final Random random = new Random();

    // Настройки
    private static final float RANGE = 4.0f;               // дистанция атаки
    private static final long MIN_DELAY_MS = 820;          // 0.82 сек
    private static final long MAX_DELAY_MS = 930;          // 0.93 сек
    private static final long MIN_LOOK_PACKET_INTERVAL = 125; // не чаще 8 пакетов в сек (125 мс)
    private static final float HITBOX_OFFSET = 0.2f;       // разброс точки удара (чтобы не бить всегда в центр)

    @Override
    public void onInitialize() {
        LOGGER.info("[RW] Lightweight KillAura (no desync). Press R to toggle.");

        Thread tickThread = new Thread(() -> {
            while (true) {
                try {
                    Thread.sleep(50); // 20 тиков в сек
                    MinecraftClient client = MinecraftClient.getInstance();
                    if (client.player == null || client.world == null) continue;

                    long window = client.getWindow().getHandle();
                    boolean currentR = GLFW.glfwGetKey(window, GLFW.GLFW_KEY_R) == GLFW.GLFW_PRESS;

                    if (currentR && !lastRState) {
                        enabled = !enabled;
                        LOGGER.info(enabled ? "KillAura ON" : "KillAura OFF");
                        target = null;
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
        updateTarget(client);
        if (target == null) return;

        // Атака с задержкой
        long now = System.currentTimeMillis();
        long delay = MIN_DELAY_MS + (long)(random.nextDouble() * (MAX_DELAY_MS - MIN_DELAY_MS));
        if (now - lastAttackTime >= delay) {
            // Отправляем пакет поворота на цель (silent look) перед атакой, но не чаще чем раз в 125 мс
            if (now - lastLookPacketTime >= MIN_LOOK_PACKET_INTERVAL) {
                sendSilentLook(client, target);
                lastLookPacketTime = now;
            }

            // Атака с небольшим смещением точки удара (обход "одной точки")
            boolean wasSprinting = client.player.isSprinting();
            attackWithOffset(client, target);
            if (wasSprinting) client.player.setSprinting(true);
            client.player.setSprinting(true); // дополнительный спринт для надёжности

            lastAttackTime = now;
        }
    }

    private static void updateTarget(MinecraftClient client) {
        // Если текущая цель жива и в радиусе – не меняем
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
            // Обязательно проверяем видимость (через стены не бьём)
            if (distSq < closest && client.player.canSee(e)) {
                closest = distSq;
                best = e;
            }
        }
        target = best;
    }

    private static void sendSilentLook(MinecraftClient client, Entity target) {
        if (client.getNetworkHandler() == null) return;

        Vec3d eyePos = client.player.getEyePos();
        Vec3d targetVec = target.getBoundingBox().getCenter().subtract(eyePos);
        double hyp = Math.hypot(targetVec.x, targetVec.z);
        float yaw = (float) (Math.toDegrees(Math.atan2(targetVec.z, targetVec.x)) - 90);
        float pitch = (float) -Math.toDegrees(Math.atan2(targetVec.y, hyp));
        yaw = wrapDegrees(yaw);
        pitch = clamp(pitch, -89, 89);

        // Отправляем только пакет поворота, без изменения позиции
        PlayerMoveC2SPacket.LookAndOnGround packet = new PlayerMoveC2SPacket.LookAndOnGround(yaw, pitch, client.player.isOnGround(), false);
        client.getNetworkHandler().sendPacket(packet);
    }

    private static void attackWithOffset(MinecraftClient client, Entity target) {
        // Сохраняем реальные углы игрока (не меняем локальную камеру)
        float realYaw = client.player.getYaw();
        float realPitch = client.player.getPitch();

        // Вычисляем углы на случайную точку внутри хитбокса (не всегда центр)
        Vec3d eyePos = client.player.getEyePos();
        Box box = target.getBoundingBox();
        double offsetX = (random.nextDouble() - 0.5) * HITBOX_OFFSET;
        double offsetY = (random.nextDouble() - 0.5) * HITBOX_OFFSET;
        double offsetZ = (random.nextDouble() - 0.5) * HITBOX_OFFSET;
        Vec3d randomPoint = new Vec3d(
                box.minX + (box.maxX - box.minX) * (0.5 + offsetX),
                box.minY + (box.maxY - box.minY) * (0.5 + offsetY),
                box.minZ + (box.maxZ - box.minZ) * (0.5 + offsetZ)
        );
        Vec3d targetVec = randomPoint.subtract(eyePos);
        double hyp = Math.hypot(targetVec.x, targetVec.z);
        float yaw = (float) (Math.toDegrees(Math.atan2(targetVec.z, targetVec.x)) - 90);
        float pitch = (float) -Math.toDegrees(Math.atan2(targetVec.y, hyp));
        yaw = wrapDegrees(yaw);
        pitch = clamp(pitch, -89, 89);

        // Отправляем пакет поворота на эту точку (другие увидят поворот, ты – нет)
        if (client.getNetworkHandler() != null) {
            PlayerMoveC2SPacket.LookAndOnGround lookPacket = new PlayerMoveC2SPacket.LookAndOnGround(yaw, pitch, client.player.isOnGround(), false);
            client.getNetworkHandler().sendPacket(lookPacket);
        }

        // Атакуем
        client.interactionManager.attackEntity(client.player, target);

        // Возвращаем локальную камеру в исходное состояние (если она изменилась – нет, мы её не трогали)
        // На всякий случай синхронизируем тело, но не дёргаем камеру игрока
        client.player.bodyYaw = realYaw;
        client.player.headYaw = realYaw;
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
