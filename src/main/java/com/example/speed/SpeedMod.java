package com.example.speed;

import net.fabricmc.api.ModInitializer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.SecureRandom;
import java.util.List;
import java.util.Random;

public class SpeedMod implements ModInitializer {
    private static final Logger LOGGER = LoggerFactory.getLogger("speedmod");
    private static final MinecraftClient mc = MinecraftClient.getInstance();
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Random RAND = new Random();

    // ========== НАСТРОЙКИ ==========
    private static final float RANGE = 4.0f;
    private static final long BASE_DELAY_MS = 460L;
    private static final long DELAY_VARIATION_MS = 40L;   // разброс задержки
    private static final float MAX_ANGLE_DELTA = 15.0f;   // для атаки

    // ========== СОСТОЯНИЕ ==========
    private static boolean enabled = false;
    private static boolean lastR = false;
    private static Entity target = null;
    private static long lastAttackTime = 0;
    private static int hitCount = 0;
    private static long attackTimerStart = 0;
    private static int lastBoostHitCount = -1;

    // Данные для ротации (как в FTAngle)
    private static float lastYaw = 0, lastPitch = 0;
    private static boolean initAngles = false;

    @Override
    public void onInitialize() {
        LOGGER.info("[FTAngle Killaura] Press R to toggle.");
        new Thread(() -> {
            while (true) {
                try { Thread.sleep(50); } catch (InterruptedException e) { break; }
                if (mc.player == null || mc.world == null) continue;
                long win = mc.getWindow().getHandle();
                boolean currR = GLFW.glfwGetKey(win, GLFW.GLFW_KEY_R) == GLFW.GLFW_PRESS;
                if (currR && !lastR) {
                    enabled = !enabled;
                    LOGGER.info(enabled ? "Killaura ON" : "Killaura OFF");
                    if (!enabled) target = null;
                    Thread.sleep(150);
                }
                lastR = currR;
                if (enabled) tick();
            }
        }).start();
    }

    private static void tick() {
        updateTarget();
        if (target == null) {
            initAngles = false;
            return;
        }

        if (!initAngles) {
            lastYaw = mc.player.getYaw();
            lastPitch = mc.player.getPitch();
            initAngles = true;
        }

        // Идеальные углы на цель
        Vec3d eye = mc.player.getEyePos();
        Vec3d to = target.getBoundingBox().getCenter().subtract(eye);
        double hyp = Math.hypot(to.x, to.z);
        float idealYaw = wrap((float) (Math.toDegrees(Math.atan2(to.z, to.x)) - 90));
        float idealPitch = clamp((float) -Math.toDegrees(Math.atan2(to.y, hyp)), -89, 89);

        // Текущие углы
        float curYaw = lastYaw;
        float curPitch = lastPitch;

        float yawDelta = wrap(idealYaw - curYaw);
        float pitchDelta = idealPitch - curPitch;
        float totalDelta = (float) Math.hypot(Math.abs(yawDelta), Math.abs(pitchDelta));

        // Атака с задержкой и проверкой угла
        long now = System.currentTimeMillis();
        long attackDelay = BASE_DELAY_MS + (long)(RAND.nextDouble() * DELAY_VARIATION_MS) - DELAY_VARIATION_MS/2;
        boolean canAttack = totalDelta < MAX_ANGLE_DELTA;
        if (now - lastAttackTime >= attackDelay && canAttack) {
            boolean wasSprint = mc.player.isSprinting();
            mc.interactionManager.attackEntity(mc.player, target);
            mc.player.swingHand(net.minecraft.util.Hand.MAIN_HAND);
            if (wasSprint) mc.player.setSprinting(true);
            mc.player.setSprinting(true);
            lastAttackTime = now;
            hitCount++;
            attackTimerStart = now;
        }

        // Вычисляем новые углы через логику FTAngle
        Turns newTurns = limitAngleChange(curYaw, curPitch, idealYaw, idealPitch, yawDelta, pitchDelta, totalDelta);
        lastYaw = newTurns.yaw;
        lastPitch = newTurns.pitch;

        // Применяем ротацию (видимую для всех)
        mc.player.setYaw(lastYaw);
        mc.player.setPitch(lastPitch);
        mc.player.headYaw = lastYaw;
        mc.player.bodyYaw = lastYaw;
    }

    // Полная логика FTAngle (земля/воздух, шумы, буст-джиттер)
    private static Turns limitAngleChange(float curYaw, float curPitch, float targetYaw, float targetPitch,
                                          float yawDelta, float pitchDelta, float totalDelta) {
        boolean onGround = mc.player.isOnGround();
        if (totalDelta < 1e-4f) {
            return new Turns(targetYaw, targetPitch);
        }
        if (onGround) {
            return buildGroundRotation(curYaw, curPitch, targetYaw, targetPitch,
                    yawDelta, pitchDelta, totalDelta);
        } else {
            return buildAirRotation(curYaw, curPitch, targetYaw, targetPitch,
                    yawDelta, pitchDelta, totalDelta);
        }
    }

    private static Turns buildGroundRotation(float curYaw, float curPitch, float targetYaw, float targetPitch,
                                             float yawDelta, float pitchDelta, float totalDelta) {
        // Параметры как в оригинальном FTAngle (без зависимостей от Aura)
        boolean instantTrack = target != null && (System.currentTimeMillis() - lastAttackTime) < 500;
        float followStrength = instantTrack ? 1.0f : (RANDOM.nextBoolean() ? 1.2f : 2.0f);
        float yawLimit = Math.abs(yawDelta / totalDelta) * 180.0f;
        float pitchLimit = Math.abs(pitchDelta / totalDelta) * 180.0f;
        float clampedYaw = MathHelper.clamp(yawDelta, -yawLimit, yawLimit);
        float clampedPitch = MathHelper.clamp(pitchDelta, -pitchLimit, pitchLimit);
        float interp = MathHelper.clamp(randomBetween(followStrength, followStrength + 0.2f), 0.0f, 1.0f);
        float newYaw = MathHelper.lerp(interp, curYaw, curYaw + clampedYaw);
        float newPitch = MathHelper.lerp(interp, curPitch, curPitch + clampedPitch);

        // Паттерн шума (ground)
        long elapsed = System.currentTimeMillis() - attackTimerStart;
        boolean attackFinished = elapsed > 1000;
        int pattern = hitCount % 3;
        float patternTime = elapsed / 40.0f + (hitCount % 6);
        Turns offset = switch (pattern) {
            case 0 -> new Turns((float) Math.cos(patternTime), (float) Math.sin(patternTime));
            case 1 -> new Turns((float) Math.sin(patternTime), (float) Math.cos(patternTime));
            case 2 -> new Turns((float) Math.sin(patternTime), (float) -Math.cos(patternTime));
            default -> new Turns((float) -Math.cos(patternTime), (float) Math.sin(patternTime));
        };

        boolean applyBoost = shouldApplyBoostJitter();
        if (applyBoost) lastBoostHitCount = hitCount;

        float yawNoise, pitchNoise;
        if (applyBoost) {
            float noiseScale = randomBetween(323232.0f, 298.0f) * Math.abs((float) Math.cos(System.currentTimeMillis() / 2000.0));
            yawNoise = !attackFinished ? randomBetween(25.0f, 26.0f) * offset.yaw : 0.0f;
            pitchNoise = !attackFinished ? -randomBetween(232.0f, 232323.0f) * Math.abs(offset.pitch) * noiseScale : 0.0f;
        } else {
            yawNoise = !attackFinished ? randomBetween(9.0f, 15.0f) * offset.yaw : 0.0f;
            pitchNoise = !attackFinished ? randomBetween(2.0f, 7.0f) * offset.pitch : 0.0f;
        }

        // Вторичное ограничение (noiseScale для yaw/pitch)
        float noiseScale2 = Math.abs(yawDelta / totalDelta) * 180.0f;
        float pitchScale2 = Math.abs(pitchDelta / totalDelta) * 180.0f;
        float clampedYaw2 = MathHelper.clamp(yawDelta, -noiseScale2, noiseScale2);
        float clampedPitch2 = MathHelper.clamp(pitchDelta, -pitchScale2, pitchScale2);
        float interp2 = MathHelper.clamp(randomBetween(-1.0f, -0.8f), 0.0f, 1.0f); // как baseInterpolation = -1.0f? Но по коду берем из attackTimer.finished(1000)? Упростим: если attackFinished то 1.0, иначе -1.0
        float baseInterp = attackFinished ? 1.0f : -1.0f;
        float interpFinal = MathHelper.clamp(randomBetween(baseInterp, baseInterp + 0.2f), 0.0f, 1.0f);
        float finalYaw = MathHelper.lerp(interpFinal, curYaw, curYaw + clampedYaw2) + yawNoise;
        float finalPitch = MathHelper.lerp(interpFinal, curPitch, curPitch + clampedPitch2) + pitchNoise;
        finalPitch = applyBoost ? MathHelper.clamp(finalPitch, -89.0f, 90.0f) : MathHelper.clamp(finalPitch, -89.0f, 89.0f);
        return new Turns(finalYaw, finalPitch);
    }

    private static Turns buildAirRotation(float curYaw, float curPitch, float targetYaw, float targetPitch,
                                          float yawDelta, float pitchDelta, float totalDelta) {
        boolean instantTrack = target != null && (System.currentTimeMillis() - lastAttackTime) < 500;
        float followStrength = instantTrack ? 1.0f : (RANDOM.nextBoolean() ? 1.2f : 2.0f);
        float yawLimit = Math.abs(yawDelta / totalDelta) * 180.0f;
        float pitchLimit = Math.abs(pitchDelta / totalDelta) * 180.0f;
        float clampedYaw = MathHelper.clamp(yawDelta, -yawLimit, yawLimit);
        float clampedPitch = MathHelper.clamp(pitchDelta, -pitchLimit, pitchLimit);
        float interp = MathHelper.clamp(randomBetween(followStrength, followStrength + 0.2f), 0.0f, 1.0f);
        float newYaw = MathHelper.lerp(interp, curYaw, curYaw + clampedYaw);
        float newPitch = MathHelper.lerp(interp, curPitch, curPitch + clampedPitch);

        long elapsed = System.currentTimeMillis() - attackTimerStart;
        boolean attackFinished = elapsed > 1000;
        int pattern = hitCount % 3;
        float patternTime = elapsed / 40.0f + (hitCount % 6);
        Turns offset = switch (pattern) {
            case 0 -> new Turns((float) Math.cos(patternTime), (float) Math.sin(patternTime));
            case 1 -> new Turns((float) Math.sin(patternTime), (float) Math.cos(patternTime));
            case 2 -> new Turns((float) Math.sin(patternTime), (float) -Math.cos(patternTime));
            default -> new Turns((float) -Math.cos(patternTime), (float) Math.sin(patternTime));
        };

        boolean applyBoost = shouldApplyBoostJitter();
        if (applyBoost) lastBoostHitCount = hitCount;

        float yawNoise, pitchNoise;
        if (applyBoost) {
            float noiseScale = randomBetween(25.0f, 32.0f) * Math.abs((float) Math.cos(System.currentTimeMillis() / 2000.0));
            yawNoise = !attackFinished ? randomBetween(6.0f, 10.0f) * offset.yaw : 0.0f;
            pitchNoise = !attackFinished ? -randomBetween(25.0f, 35.0f) * Math.abs(offset.pitch) * noiseScale : 0.0f;
        } else {
            yawNoise = !attackFinished ? randomBetween(20.0f, 26.0f) * offset.yaw : 0.0f;
            pitchNoise = !attackFinished ? randomBetween(2.0f, 5.0f) * offset.pitch : 0.0f;
        }

        float noiseScale2 = Math.abs(yawDelta / totalDelta) * 180.0f;
        float pitchScale2 = Math.abs(pitchDelta / totalDelta) * 180.0f;
        float clampedYaw2 = MathHelper.clamp(yawDelta, -noiseScale2, noiseScale2);
        float clampedPitch2 = MathHelper.clamp(pitchDelta, -pitchScale2, pitchScale2);
        float baseInterp = attackFinished ? randomBetween(0.21f, 0.31f) : randomBetween(0.15f, 0.11f);
        float interpFinal = MathHelper.clamp(randomBetween(baseInterp, baseInterp + 0.15f), 0.0f, 1.0f);
        float finalYaw = MathHelper.lerp(interpFinal, curYaw, curYaw + clampedYaw2) + yawNoise;
        float finalPitch = MathHelper.lerp(interpFinal, curPitch, curPitch + clampedPitch2) + pitchNoise;
        finalPitch = applyBoost ? MathHelper.clamp(finalPitch, -89.0f, 90.0f) : MathHelper.clamp(finalPitch, -89.0f, 89.0f);
        return new Turns(finalYaw, finalPitch);
    }

    private static boolean shouldApplyBoostJitter() {
        return hitCount > 0 && (hitCount % 20) * RAND.nextFloat() == 0.0f && hitCount != lastBoostHitCount;
    }

    private static float randomBetween(float min, float max) {
        return MathHelper.lerp(RANDOM.nextFloat(), min, max);
    }

    private static void updateTarget() {
        if (target != null && target.isAlive() && mc.player.squaredDistanceTo(target) <= RANGE * RANGE) return;
        Entity best = null;
        double closest = RANGE * RANGE;
        Box box = mc.player.getBoundingBox().expand(RANGE);
        List<Entity> entities = mc.world.getOtherEntities(mc.player, box,
                e -> e instanceof LivingEntity && e != mc.player && ((LivingEntity) e).isAlive());
        for (Entity e : entities) {
            if (e instanceof PlayerEntity && mc.player.isTeammate((PlayerEntity) e)) continue;
            double dist = mc.player.squaredDistanceTo(e);
            if (dist < closest && mc.player.canSee(e)) {
                closest = dist;
                best = e;
            }
        }
        target = best;
    }

    private static float wrap(float v) { v %= 360f; if (v >= 180f) v -= 360f; if (v < -180f) v += 360f; return v; }
    private static float clamp(float v, float min, float max) { return Math.max(min, Math.min(max, v)); }

    static class Turns { float yaw, pitch; Turns(float y, float p) { yaw = y; pitch = p; } }
}
