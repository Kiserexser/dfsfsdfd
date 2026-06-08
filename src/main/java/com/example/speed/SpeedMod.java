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

    private static float lastYaw, lastPitch;
    private static Vector2f rotateVector = new Vector2f(0, 0);

    private static final float RANGE = 4.2f;
    // Новая задержка: от 820 до 930 миллисекунд
    private static final long MIN_DELAY_MS = 820;
    private static final long MAX_DELAY_MS = 930;
    private static final boolean CORRECTION_MOTION = true;

    @Override
    public void onInitialize() {
        LOGGER.info("[SpeedMod] RW mode. Delay 0.82-0.93 sec. Press R ONCE to toggle.");

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
                            LOGGER.info("[Killaura] ENABLED (delay 0.82-0.93s)");
                            rotateVector = new Vector2f(client.player.getYaw(), client.player.getPitch());
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
        updateTarget(client);
        if (target == null) return;

        long now = System.currentTimeMillis();
        // Очищаем старые времена (не обязательно, но оставим)
        attackTimes.removeIf(t -> now - t > 2000);

        // Выбираем случайную задержку от MIN_DELAY_MS до MAX_DELAY_MS
        long delay = MIN_DELAY_MS + (long)(random.nextDouble() * (MAX_DELAY_MS - MIN_DELAY_MS));
        if (now - lastAttackTime < delay) return;

        rotateAndAttack(client);
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
            if (dist < closest && client.player.canSee(e)) {
                closest = dist;
                best = e;
            }
        }
        target = best;
    }

    private static void rotateAndAttack(MinecraftClient client) {
        if (target == null) return;

        Vec3d eyePos = client.player.getEyePos();
        Vec3d targetVec = target.getBoundingBox().getCenter().subtract(eyePos);
        double hyp = Math.hypot(targetVec.x, targetVec.z);

        float targetYaw = (float) MathHelper.wrapDegrees(Math.toDegrees(Math.atan2(targetVec.z, targetVec.x)) - 90);
        float targetPitch = (float) (-Math.toDegrees(Math.atan2(targetVec.y, hyp)));
        targetPitch = MathHelper.clamp(targetPitch, -89.0F, 89.0F);

        float yawDelta1 = MathHelper.wrapDegrees(targetYaw - rotateVector.x);
        float pitchDelta1 = MathHelper.wrapDegrees(targetPitch - rotateVector.y);

        float baseYawSpeed = 52.0f;
        float basePitchSpeed = 44.0f;

        float newYaw, newPitch;
        boolean attack = true;

        if (attack && target != null) {
            float snapFactor = 0.88f + (Math.min(Math.abs(yawDelta1) / 90.0f, 1.0f) * 0.12f);
            newYaw = rotateVector.x + yawDelta1 * snapFactor;
            newPitch = rotateVector.y + pitchDelta1 * snapFactor;
        } else {
            float yawSpeed = Math.min(Math.abs(yawDelta1), baseYawSpeed);
            float pitchSpeed = Math.min(Math.abs(pitchDelta1), basePitchSpeed);
            newYaw = rotateVector.x + (yawDelta1 > 0 ? yawSpeed : -yawSpeed);
            newPitch = rotateVector.y + (pitchDelta1 > 0 ? pitchSpeed : -pitchSpeed);
        }

        long time = System.currentTimeMillis();
        float timeFactor = time * 0.001f;
        int age = client.player.age;

        if (age % (3 + (int)(Math.sin(timeFactor) * 2)) == 0) {
            float shakeIntensity = 0.12f + (float) Math.sin(timeFactor * 2.5f) * 0.06f;
            newYaw += (random.nextFloat() - 0.5f) * shakeIntensity;
            newPitch += (random.nextFloat() - 0.5f) * shakeIntensity * 0.8f;
        }

        newYaw += (random.nextFloat() - 0.5f) * 0.018f;
        newPitch += (random.nextFloat() - 0.5f) * 0.014f;

        float gcd = SensUtils.getGCDValue(client);
        float gcdVariation = 0.985f + (random.nextFloat() * 0.03f);
        newYaw -= (newYaw - rotateVector.x) % (gcd * gcdVariation);
        newPitch -= (newPitch - rotateVector.y) % (gcd * gcdVariation * 0.95f);

        float maxChange = attack ? 38.0f : 28.0f;
        newYaw = rotateVector.x + MathHelper.clamp(newYaw - rotateVector.x, -maxChange, maxChange);
        newPitch = rotateVector.y + MathHelper.clamp(newPitch - rotateVector.y, -maxChange * 0.85f, maxChange * 0.85f);

        newPitch = MathHelper.clamp(newPitch, -89.0F, 89.0F);

        float smoothFactor = 0.78f + (random.nextFloat() * 0.22f);
        newYaw = rotateVector.x + (newYaw - rotateVector.x) * smoothFactor;
        newPitch = rotateVector.y + (newPitch - rotateVector.y) * smoothFactor;

        rotateVector = new Vector2f(newYaw, newPitch);

        if (CORRECTION_MOTION) {
            client.player.bodyYaw = newYaw;
            client.player.headYaw = newYaw;
            client.player.renderYawOffset = newYaw;
            if (age % 8 == 0) {
                client.player.headYaw += (random.nextFloat() - 0.5f) * 0.6f;
            }
        }

        lastYaw = newYaw;
        lastPitch = newPitch;

        if (time % 2000 < 100) {
            float randomizer = 0.9f + (random.nextFloat() * 0.2f);
            rotateVector = new Vector2f(
                rotateVector.x * randomizer,
                MathHelper.clamp(rotateVector.y * randomizer, -89.0F, 89.0F)
            );
        }

        client.player.setYaw(rotateVector.x);
        client.player.setPitch(rotateVector.y);

        // No Sprint Reset
        boolean wasSprinting = client.player.isSprinting();
        client.interactionManager.attackEntity(client.player, target);
        if (wasSprinting) client.player.setSprinting(true);
        client.player.setSprinting(true);
    }

    // ====== Вспомогательные классы ======
    static class Vector2f {
        float x, y;
        Vector2f(float x, float y) { this.x = x; this.y = y; }
    }

    static class MathHelper {
        static float wrapDegrees(float value) {
            value %= 360.0F;
            if (value >= 180.0F) value -= 360.0F;
            if (value < -180.0F) value += 360.0F;
            return value;
        }
        static float clamp(float value, float min, float max) {
            if (value < min) return min;
            if (value > max) return max;
            return value;
        }
    }

    static class SensUtils {
        static float getGCDValue(MinecraftClient client) {
            double sens = client.options.getMouseSensitivity().getValue();
            float gcd = (float) (sens * 0.6 + 0.2);
            gcd = gcd * gcd * gcd * 8.0f;
            return gcd;
        }
    }
}
