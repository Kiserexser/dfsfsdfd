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

    private static final float RANGE = 4.0f;
    private static final long MIN_ATTACK_DELAY = 820;
    private static final long MAX_ATTACK_DELAY = 930;
    private static final long LOOK_PACKET_INTERVAL = 150; // 150 мс – плавно для других, незаметно для античита

    @Override
    public void onInitialize() {
        LOGGER.info("[RW] No camera jerk + others see aim. Press R.");
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
                        LOGGER.info(enabled ? "Killaura ON (no jerk)" : "Killaura OFF");
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
        if (target == null) return;

        long now = System.currentTimeMillis();

        // Отправляем пакеты поворота на сервер, чтобы другие видели наведение
        if (now - lastLookPacketTime >= LOOK_PACKET_INTERVAL) {
            sendLookAtTarget(client, target);
            lastLookPacketTime = now;
        }

        // Атака с задержкой
        long attackDelay = MIN_ATTACK_DELAY + (long)(random.nextDouble() * (MAX_ATTACK_DELAY - MIN_ATTACK_DELAY));
        if (now - lastAttackTime >= attackDelay) {
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

    private static void sendLookAtTarget(MinecraftClient client, Entity target) {
        if (client.getNetworkHandler() == null) return;
        Vec3d eyePos = client.player.getEyePos();
        Vec3d targetVec = target.getBoundingBox().getCenter().subtract(eyePos);
        double hyp = Math.hypot(targetVec.x, targetVec.z);
        float yaw = (float) (Math.toDegrees(Math.atan2(targetVec.z, targetVec.x)) - 90);
        float pitch = (float) -Math.toDegrees(Math.atan2(targetVec.y, hyp));
        yaw = wrapDegrees(yaw);
        pitch = clamp(pitch, -89, 89);
        PlayerMoveC2SPacket.LookAndOnGround packet = new PlayerMoveC2SPacket.LookAndOnGround(yaw, pitch, client.player.isOnGround(), false);
        client.getNetworkHandler().sendPacket(packet);
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
