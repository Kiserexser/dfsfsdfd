package com.example.speed;

import net.fabricmc.api.ModInitializer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.Perspective;
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
import java.util.concurrent.ConcurrentLinkedQueue;

public class SpeedMod implements ModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("speedmod");

    private static boolean enabled = false;
    private static boolean lastRState = false;
    private static Entity target = null;
    private static long lastAttackTime = 0;
    private static final ConcurrentLinkedQueue<Long> attackTimes = new ConcurrentLinkedQueue<>();
    private static final Random random = new Random();

    private static float lastYaw = 0, lastPitch = 0;
    private static Vector2f rotateVector = new Vector2f(0, 0);
    private static boolean rotateVectorInit = false;

    // для обходов
    private static float shakeTime = 0;
    private static float acceleration = 0;
    private static boolean isBack = false;
    private static boolean funtimeSnapMode = true;
    private static boolean lonyJirMode = true;

    private static final float RANGE = 4.2f;
    private static final long MIN_DELAY_MS = 820;
    private static final long MAX_DELAY_MS = 930;
    private static final float SHAKE_SPEED = 0.08f;
    private static final float SHAKE_INTENSITY = 0.12f;

    @Override
    public void onInitialize() {
        LOGGER.info("[SWILL] Killaura RW + Matrix/Grim bypasses (fixed). Press R.");
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
            rotateVectorInit = false;
            return;
        }

        Vec3d eyePos = client.player.getEyePos();
        Vec3d targetVec = target.getBoundingBox().getCenter().subtract(eyePos);
        double hyp = Math.hypot(targetVec.x, targetVec.z);
        float idealYaw = (float) (Math.toDegrees(Math.atan2(targetVec.z, targetVec.x)) - 90);
        float idealPitch = (float) -Math.toDegrees(Math.atan2(targetVec.y, hyp));
        idealYaw = wrapDegrees(idealYaw);
        idealPitch = clamp(idealPitch, -89, 89);

        if (!rotateVectorInit) {
            rotateVector.x = client.player.getYaw();
            rotateVector.y = client.player.getPitch();
            lastYaw = rotateVector.x;
            lastPitch = rotateVector.y;
            rotateVectorInit = true;
        }

        float newYaw = rotateVector.x;
        float newPitch = rotateVector.y;

        // ----- funtimeSnap -----
        if (funtimeSnapMode) {
            float deltaYaw = wrapDegrees(idealYaw - lastYaw);
            float deltaPitch = idealPitch - lastPitch;
            float smooth = lastYaw + deltaYaw;
            float newYawTemp = lastPitch + deltaPitch;
            float gcd = getGCD(client);
            smooth -= (smooth - lastYaw) % gcd;
            newYawTemp -= (newYawTemp - lastPitch) % gcd;
            shakeTime += SHAKE_SPEED * 0.05f;
            float intensity = SHAKE_INTENSITY;
            float shakeYaw   = (float)(Math.sin(shakeTime * 1.7)  * intensity * 0.5);
            float shakePitch = (float)(Math.sin(shakeTime * 2.3 + 1.0) * intensity * 0.25);
            newYaw = smooth + shakeYaw;
            newPitch = newYawTemp + shakePitch;
            lastYaw = smooth;
            lastPitch = newYawTemp;
        }

        // ----- lonyJir (адаптивное ускорение, симуляция планирования, третье лицо) -----
        if (lonyJirMode && target != null) {
            MinecraftClient mc = client;
            if (mc.player.isGliding()) {
                if (!isBack) {
                    acceleration += 0.005f;
                    if (acceleration >= 0.13f) isBack = true;
                } else {
                    if (acceleration >= -0.02f) acceleration -= 0.005f;
                    if (acceleration <= -0.02f) isBack = false;
                }
            } else {
                // простая проверка видимости (вместо сложного Raycast)
                if (!mc.player.canSee(target)) {
                    acceleration += 0.0015f;
                } else if (acceleration > 0.0f) {
                    acceleration -= 0.01f;
                }
            }
            float smoothFactor = Math.max(acceleration, 0.0f);
            float deltaYaw2 = wrapDegrees(idealYaw - newYaw);
            float deltaPitch2 = idealPitch - newPitch;
            float newYawL = newYaw + deltaYaw2 * Math.min(Math.max(smoothFactor, 0.0f), 1.0f);
            float newPitchL = newPitch + deltaPitch2 * Math.min(Math.max(smoothFactor / 2.0f, 0.0f), 1.0f);
            float gcd2 = getGCD(mc);
            newYawL -= (newYawL - newYaw) % gcd2;
            newPitchL -= (newPitchL - newPitch) % gcd2;

            // обработка третьего лица (камера спереди)
            float deltaYawCam = wrapDegrees(mc.gameRenderer.getCamera().getYaw() - newYawL);
            float deltaPitchCam = mc.gameRenderer.getCamera().getPitch() - newPitchL;
            if (mc.options.getPerspective() == Perspective.THIRD_PERSON_FRONT) {
                deltaYawCam = wrapDegrees(mc.gameRenderer.getCamera().getYaw() - 180.0f - newYawL);
                deltaPitchCam = -mc.gameRenderer.getCamera().getPitch() - newPitchL;
            }
            float limit = (Math.abs(deltaYawCam) > 3.0f || Math.abs(deltaPitchCam) > 3.0f) ? 0.0f : 360.0f;
            sendSilentLook(client, newYawL, newPitchL, limit, limit);
            newYaw = newYawL;
            newPitch = newPitchL;
        } else {
            sendSilentLook(client, newYaw, newPitch, 360.0f, 360.0f);
        }

        rotateVector.x = newYaw;
        rotateVector.y = newPitch;
        // коррекция движения (обход)
        client.player.bodyYaw = newYaw;
        client.player.headYaw = newYaw;
        if (client.player.age % 8 == 0) {
            client.player.headYaw += (random.nextFloat() - 0.5f) * 0.6f;
        }

        long now = System.currentTimeMillis();
        long delay = MIN_DELAY_MS + (long)(random.nextDouble() * (MAX_DELAY_MS - MIN_DELAY_MS));
        if (now - lastAttackTime >= delay) {
            boolean wasSprinting = client.player.isSprinting();
            client.interactionManager.attackEntity(client.player, target);
            if (wasSprinting) client.player.setSprinting(true);
            client.player.setSprinting(true);
            lastAttackTime = now;
            attackTimes.add(now);
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

    private static void sendSilentLook(MinecraftClient client, float yaw, float pitch, float limitYaw, float limitPitch) {
        if (client.getNetworkHandler() == null) return;
        float clampedYaw = yaw;
        float clampedPitch = pitch;
        if (limitYaw < 360.0f) {
            float delta = wrapDegrees(yaw - client.player.getYaw());
            clampedYaw = client.player.getYaw() + Math.min(Math.max(delta, -limitYaw), limitYaw);
        }
        if (limitPitch < 360.0f) {
            float delta = pitch - client.player.getPitch();
            clampedPitch = client.player.getPitch() + Math.min(Math.max(delta, -limitPitch), limitPitch);
        }
        clampedYaw = wrapDegrees(clampedYaw);
        clampedPitch = clamp(clampedPitch, -89, 89);
        PlayerMoveC2SPacket.LookAndOnGround packet = new PlayerMoveC2SPacket.LookAndOnGround(clampedYaw, clampedPitch, client.player.isOnGround(), false);
        client.getNetworkHandler().sendPacket(packet);
    }

    private static float getGCD(MinecraftClient client) {
        double sens = client.options.getMouseSensitivity().getValue();
        float gcd = (float) (sens * 0.6 + 0.2);
        gcd = gcd * gcd * gcd * 8.0f;
        return Math.max(gcd, 0.001f);
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

    static class Vector2f {
        float x, y;
        Vector2f(float x, float y) { this.x = x; this.y = y; }
    }
}
