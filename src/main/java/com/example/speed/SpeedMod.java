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
    private static final Random random = new Random();

    private static final float RANGE = 4.2f;
    private static final long MIN_DELAY_MS = 820;
    private static final long MAX_DELAY_MS = 930;

    // Класс для плавного возврата углов
    private static class Rotation {
        private float currentYaw;
        private float currentPitch;
        private boolean initialized = false;

        public void init(MinecraftClient client) {
            if (!initialized) {
                currentYaw = client.player.getYaw();
                currentPitch = client.player.getPitch();
                initialized = true;
            }
        }

        // Вызывается каждый тик
        public void update(MinecraftClient client) {
            if (!initialized) init(client);

            if (target == null) {
                // Интерполяция к реальным углам игрока (скорость 10% за тик)
                float realYaw = client.player.getYaw();
                float realPitch = client.player.getPitch();
                currentYaw += (realYaw - currentYaw) * 0.1f;
                currentPitch += (realPitch - currentPitch) * 0.1f;
                currentYaw = wrapDegrees(currentYaw);
                currentPitch = clamp(currentPitch, -89, 89);
                // Не отправляем пакеты поворота, когда нет цели
                return;
            }

            // Вычисляем углы на цель
            Vec3d eyePos = client.player.getEyePos();
            Vec3d targetVec = target.getBoundingBox().getCenter().subtract(eyePos);
            double hyp = Math.hypot(targetVec.x, targetVec.z);
            float idealYaw = (float) (Math.toDegrees(Math.atan2(targetVec.z, targetVec.x)) - 90);
            float idealPitch = (float) -Math.toDegrees(Math.atan2(targetVec.y, hyp));
            idealYaw = wrapDegrees(idealYaw);
            idealPitch = clamp(idealPitch, -89, 89);

            // Плавно двигаем current углы к идеальным (20% за тик, но можно менять)
            currentYaw += (idealYaw - currentYaw) * 0.2f;
            currentPitch += (idealPitch - currentPitch) * 0.2f;
            currentYaw = wrapDegrees(currentYaw);
            currentPitch = clamp(currentPitch, -89, 89);

            // Отправляем silent look (другие видят этот поворот, ты – нет)
            sendSilentLook(client, currentYaw, currentPitch);
        }
    }

    private static final Rotation rotation = new Rotation();

    @Override
    public void onInitialize() {
        LOGGER.info("[SpeedMod] Silent Aim + smooth return. Press R.");

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
                        LOGGER.info(enabled ? "Killaura ON" : "Killaura OFF");
                        if (!enabled) target = null;
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

        // Проверка жива ли цель
        if (target != null && target instanceof LivingEntity && ((LivingEntity) target).isDead()) {
            target = null;
        }

        // Инициализируем ротацию
        rotation.init(client);

        // Вызываем update – он сам отправит silent look, если цель есть, или интерполирует к реальным углам
        rotation.update(client);

        // Атака только если цель есть и задержка прошла
        if (target != null) {
            long now = System.currentTimeMillis();
            long delay = MIN_DELAY_MS + (long)(random.nextDouble() * (MAX_DELAY_MS - MIN_DELAY_MS));
            if (now - lastAttackTime >= delay) {
                boolean wasSprinting = client.player.isSprinting();
                client.interactionManager.attackEntity(client.player, target);
                if (wasSprinting) client.player.setSprinting(true);
                client.player.setSprinting(true);
                lastAttackTime = now;
            }
        }
    }

    private static void updateTarget(MinecraftClient client) {
        // Если цель ещё жива и существует – не переключаем
        if (target != null && target.isAlive() && client.player.squaredDistanceTo(target) <= RANGE * RANGE) {
            return;
        }

        // Иначе ищем новую
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

    private static void sendSilentLook(MinecraftClient client, float yaw, float pitch) {
        if (client.getNetworkHandler() == null) return;
        PlayerMoveC2SPacket.LookAndOnGround packet = new PlayerMoveC2SPacket.LookAndOnGround(yaw, pitch, client.player.isOnGround(), false);
        client.getNetworkHandler().sendPacket(packet);
    }

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
