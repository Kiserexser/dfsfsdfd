package com.example.speed;

import net.fabricmc.api.ModInitializer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.Perspective;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.network.packet.c2s.play.HandSwingC2SPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Random;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.stream.Collectors;
import java.util.concurrent.atomic.AtomicLong;

public class SpeedMod implements ModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("speedmod");
    
    // ---------- RW State ----------
    private static boolean enabled = false;
    private static boolean lastRState = false;
    private static Entity target = null;
    private static long lastAttackTime = 0;
    private static final ConcurrentLinkedQueue<Long> attackTimes = new ConcurrentLinkedQueue<>();
    private static final Random random = new Random();
    
    // ---------- Rotation & Timing ----------
    private static float lastYaw = 0, lastPitch = 0;
    private static Vector2f rotateVector = new Vector2f(0, 0);
    private static boolean rotateVectorInit = false;
    private static float shakeTime = 0;
    private static float acceleration = 0;
    private static boolean isBack = false;
    private static final AtomicLong lastTeleportTime = new AtomicLong(0);
    
    // ---------- RW Config ----------
    private static final float RANGE = 4.15f;          // RW Reach проверки ±
    private static final long MIN_DELAY_MS = 820;      // 0.82 сек
    private static final long MAX_DELAY_MS = 930;      // 0.93 сек
    private static final float SHAKE_SPEED = 0.08f;
    private static final float SHAKE_INTENSITY = 0.12f;
    private static final int MAX_PACKETS_PER_SECOND = 12;
    
    // RW обходы (bypass)
    private static final boolean BYPASS_MATRIX_ROT = true;
    private static final boolean BYPASS_GRIM_TIMING = true;
    private static final boolean BYPASS_RW_DECOY = true;
    private static final boolean BYPASS_RW_FAKE_LAG = true;
    private static final boolean BYPASS_MATRIX_VELOCITY = true;
    private static final boolean BYPASS_GRIM_PACKET_SPAM = true;
    private static final boolean BYPASS_RW_AIM_ASSIST = true;
    private static final boolean BYPASS_RW_NAME_FILTER = true;
    
    @Override
    public void onInitialize() {
        LOGGER.info("[RW] Anti-Cheat Bypass Module Active");
        Thread tickThread = new Thread(() -> {
            while (true) {
                try {
                    // 1. Timer Desync (49-53ms вместо 50)
                    Thread.sleep(49 + random.nextInt(5));
                    MinecraftClient client = MinecraftClient.getInstance();
                    if (client.player == null || client.world == null) continue;
                    long window = client.getWindow().getHandle();
                    boolean currentR = GLFW.glfwGetKey(window, GLFW.GLFW_KEY_R) == GLFW.GLFW_PRESS;
                    
                    if (currentR && !lastRState) {
                        enabled = !enabled;
                        LOGGER.info(enabled ? "[RW] KillAura ON" : "[RW] KillAura OFF");
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
        
        // 2. Decoy Filter (RW honeypot)
        if (BYPASS_RW_DECOY && isRWDecoy(target)) return;
        if (BYPASS_RW_NAME_FILTER && isRWBot(target)) return;
        
        // 3. Calculate ideal angles
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
        
        // 4. funtimeSnap (Matrix/Grim Rotation Bypass)
        if (BYPASS_MATRIX_ROT) {
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
        
        // 5. lonyJir (Adaptive Acceleration for RW)
        if (BYPASS_GRIM_TIMING && target != null) {
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
            
            // Camera perspective handling
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
        
        // 6. Apply final rotation
        rotateVector.x = newYaw;
        rotateVector.y = newPitch;
        client.player.bodyYaw = newYaw;
        client.player.headYaw = newYaw;
        if (client.player.age % 8 == 0) {
            client.player.headYaw += (random.nextFloat() - 0.5f) * 0.6f;
        }
        
        // 7. Attack with RW Timing
        long now = System.currentTimeMillis();
        long delay = MIN_DELAY_MS + (long)(random.nextDouble() * (MAX_DELAY_MS - MIN_DELAY_MS));
        if (BYPASS_RW_FAKE_LAG && random.nextFloat() < 0.15f) {
            // Fake lag spike
            Thread.sleep(random.nextInt(45));
        }
        if (now - lastAttackTime < delay) return;
        
        // 8. Pre-attack onGround bypass (Matrix)
        if (BYPASS_MATRIX_VELOCITY) {
            sendOnGroundPacket(client);
        }
        
        // 9. Attack
        boolean wasSprinting = client.player.isSprinting();
        client.interactionManager.attackEntity(client.player, target);
        if (wasSprinting) client.player.setSprinting(true);
        client.player.setSprinting(true);
        
        // 10. Post-attack swing randomization (Grim)
        if (BYPASS_GRIM_PACKET_SPAM) {
            long swingDelay = 30 + random.nextInt(50);
            client.getNetworkHandler().sendPacket(new HandSwingC2SPacket(Hand.MAIN_HAND));
        }
        
        lastAttackTime = now;
        attackTimes.add(now);
    }
    
    // ---------- Target Selection ----------
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
    
    // ---------- RW Specific Filters ----------
    private static boolean isRWDecoy(Entity e) {
        String name = e.getCustomName() != null ? e.getCustomName().getString().toLowerCase() : "";
        if (name.contains("decoy") || name.contains("antibot") || name.contains("fake")) return true;
        if (e.getClass().getName().toLowerCase().contains("decoy")) return true;
        if (e.getUuid().toString().contains("00000000")) return true;
        return false;
    }
    
    private static boolean isRWBot(Entity e) {
        if (e.getCustomName() == null) return false;
        String name = e.getCustomName().getString();
        if (name.contains("§k") || name.contains("Bot") || name.contains("Npc")) return true;
        return false;
    }
    
    // ---------- Packet Helpers ----------
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
    
    private static void sendOnGroundPacket(MinecraftClient client) {
        if (client.getNetworkHandler() == null) return;
        PlayerMoveC2SPacket.LookAndOnGround packet = new PlayerMoveC2SPacket.LookAndOnGround(client.player.getYaw(), client.player.getPitch(), true, false);
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
