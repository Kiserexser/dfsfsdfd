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
    private static Entity target = null;
    private static long lastAttackTime = 0;
    private static final ConcurrentLinkedQueue<Long> attackTimes = new ConcurrentLinkedQueue<>();
    private static final Random random = new Random();

    private static float currentYaw = 0;
    private static float currentPitch = 0;
    private static boolean anglesInitialized = false;

    private static final float RANGE = 4.2f;
    private static final float MIN_CPS = 6.5f;
    private static final float MAX_CPS = 9.5f;

    @Override
    public void onInitialize() {
        LOGGER.info("[SpeedMod] Killaura (RW mode) loaded. Press R to toggle.");

        Thread tickThread = new Thread(() -> {
            while (true) {
                try {
                    Thread.sleep(50);
                    MinecraftClient client = MinecraftClient.getInstance();
                    if (client.player == null || client.world == null) continue;

                    long window = client.getWindow().getHandle();
                    boolean rPressed = GLFW.glfwGetKey(window, GLFW.GLFW_KEY_R) == GLFW.GLFW_PRESS;

                    if (rPressed && !enabled) {
                        enabled = true;
                        LOGGER.info("[Killaura] ENABLED");
                        Thread.sleep(200);
                    } else if (!rPressed && enabled) {
                        enabled = false;
                        target = null;
                        LOGGER.info("[Killaura] DISABLED");
                        Thread.sleep(200);
                    }

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
        attackTimes.removeIf(t -> now - t > 1000);
        float targetCps = MIN_CPS + random.nextFloat() * (MAX_CPS - MIN_CPS);
        long delay = (long) (1000.0 / targetCps);
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

        float targetYaw = (float) Math.toDegrees(Math.atan2(targetVec.z, targetVec.x)) - 90;
        float targetPitch = (float) -Math.toDegrees(Math.atan2(targetVec.y, hyp));

        targetYaw = wrapDegrees(targetYaw);
        targetPitch = clamp(targetPitch, -89, 89);

        if (!anglesInitialized) {
            currentYaw = client.player.getYaw();
            currentPitch = client.player.getPitch();
            anglesInitialized = true;
        }

        float yawDelta = wrapDegrees(targetYaw - currentYaw);
        float pitchDelta = wrapDegrees(targetPitch - currentPitch);

        float baseYawSpeed = 52.0f;
        float basePitchSpeed = 44.0f;

        float newYaw, newPitch;
        boolean attack = true;

        if (attack && target != null) {
            float snapFactor = 0.88f + (Math.min(Math.abs(yawDelta) / 90.0f, 1.0f) * 0.12f);
            newYaw = currentYaw + yawDelta * snapFactor;
            newPitch = currentPitch + pitchDelta * snapFactor;
        } else {
            float yawSpeed = Math.min(Math.abs(yawDelta), baseYawSpeed);
            float pitchSpeed = Math.min(Math.abs(pitchDelta), basePitchSpeed);
            newYaw = currentYaw + (yawDelta > 0 ? yawSpeed : -yawSpeed);
            newPitch = currentPitch + (pitchDelta > 0 ? pitchSpeed : -pitchSpeed);
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

        float gcd = getGCD(client);
        float gcdVariation = 0.985f + (random.nextFloat() * 0.03f);
        newYaw -= (newYaw - currentYaw) % (gcd * gcdVariation);
        newPitch -= (newPitch - currentPitch) % (gcd * gcdVariation * 0.95f);

        float maxChange = 38.0f;
        newYaw = currentYaw + clamp(newYaw - currentYaw, -maxChange, maxChange);
        newPitch = currentPitch + clamp(newPitch - currentPitch, -maxChange * 0.85f, maxChange * 0.85f);

        newPitch = clamp(newPitch, -89, 89);

        float smoothFactor = 0.78f + random.nextFloat() * 0.22f;
        newYaw = currentYaw + (newYaw - currentYaw) * smoothFactor;
        newPitch = currentPitch + (newPitch - currentPitch) * smoothFactor;

        currentYaw = newYaw;
        currentPitch = newPitch;

        client.player.setYaw(currentYaw);
        client.player.setPitch(currentPitch);
        client.player.bodyYaw = currentYaw;
        client.player.headYaw = currentYaw;
        if (age % 8 == 0) {
            client.player.headYaw += (random.nextFloat() - 0.5f) * 0.6f;
        }

        client.interactionManager.attackEntity(client.player, target);

        if (time % 2000 < 100) {
            float randomizer = 0.9f + random.nextFloat() * 0.2f;
            currentYaw *= randomizer;
            currentPitch = clamp(currentPitch * randomizer, -89, 89);
        }
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

    private static float getGCD(MinecraftClient client) {
        float sens = client.options.getMouseSensitivity().getValue();
        float gcd = sens * 0.6f + 0.2f;
        gcd = gcd * gcd * gcd * 8.0f;
        return gcd;
    }
}
