package com.example.speed;

import net.fabricmc.api.ModInitializer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
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

    private static final float RANGE = 4.0f;
    private static final long BASE_DELAY_MS = 460L;
    private static final long DELAY_VARIATION_MS = 40L;
    private static final float MAX_ANGLE_DELTA = 25.0f;   // увеличено для более быстрой атаки

    private static boolean enabled = false;
    private static boolean lastR = false;
    private static Entity target = null;
    private static long lastAttackTime = 0;
    private static int hitCount = 0;
    private static long attackTimerStart = 0;
    private static int lastBoostHitCount = -1;

    // для silent aim
    private static float lastSentYaw = 0, lastSentPitch = 0;
    private static boolean initSent = false;

    @Override
    public void onInitialize() {
        LOGGER.info("[Fast Silent Killaura] Press R to toggle.");
        new Thread(() -> {
            while (true) {
                try { Thread.sleep(50); } catch (InterruptedException e) { break; }
                if (mc.player == null || mc.world == null) continue;
                long win = mc.getWindow().getHandle();
                boolean currR = GLFW.glfwGetKey(win, GLFW.GLFW_KEY_R) == GLFW.GLFW_PRESS;
                if (currR && !lastR) {
                    enabled = !enabled;
                    LOGGER.info(enabled ? "Killaura ON (silent, fast)" : "Killaura OFF");
                    if (!enabled) target = null;
                    try { Thread.sleep(150); } catch (InterruptedException ignored) {}
                }
                lastR = currR;
                if (enabled) tick();
            }
        }).start();
    }

    private static void tick() {
        updateTarget();
        if (target == null) {
            initSent = false;
            return;
        }

        // Идеальные углы на цель
        Vec3d eye = mc.player.getEyePos();
        Vec3d to = target.getBoundingBox().getCenter().subtract(eye);
        double hyp = Math.hypot(to.x, to.z);
        float idealYaw = wrap((float) (Math.toDegrees(Math.atan2(to.z, to.x)) - 90));
        float idealPitch = clamp((float) -Math.toDegrees(Math.atan2(to.y, hyp)), -89, 89);

        // ---- Silent Aim: отправляем пакет поворота на сервер (другие видят) ----
        if (!initSent) {
            lastSentYaw = mc.player.getYaw();
            lastSentPitch = mc.player.getPitch();
            initSent = true;
        }

        // Быстрая подстройка отправляемых углов к идеальным (почти мгновенно)
        float deltaYaw = wrap(idealYaw - lastSentYaw);
        float deltaPitch = idealPitch - lastSentPitch;
        float totalDelta = (float) Math.hypot(Math.abs(deltaYaw), Math.abs(deltaPitch));

        // Ускоренная наводка: если дельта больше 0.1, двигаем сразу на 80% разницы
        float speed = 0.8f;
        float newYaw = lastSentYaw + deltaYaw * speed;
        float newPitch = lastSentPitch + deltaPitch * speed;
        newYaw = wrap(newYaw);
        newPitch = clamp(newPitch, -89, 89);

        // Добавляем лёгкий шум (чтобы не палиться)
        if (hitCount > 0) {
            newYaw += (RANDOM.nextFloat() - 0.5f) * 0.5f;
            newPitch += (RANDOM.nextFloat() - 0.5f) * 0.3f;
        }

        // Отправляем пакет поворота на сервер (без изменения локальной камеры)
        if (mc.getNetworkHandler() != null) {
            PlayerMoveC2SPacket.LookAndOnGround packet = new PlayerMoveC2SPacket.LookAndOnGround(newYaw, newPitch, mc.player.isOnGround(), false);
            mc.getNetworkHandler().sendPacket(packet);
        }
        lastSentYaw = newYaw;
        lastSentPitch = newPitch;

        // ---- Атака (с задержкой и проверкой угла) ----
        long now = System.currentTimeMillis();
        long delay = BASE_DELAY_MS + (long)(RAND.nextDouble() * DELAY_VARIATION_MS) - DELAY_VARIATION_MS/2;
        boolean canAttack = totalDelta < MAX_ANGLE_DELTA;
        if (now - lastAttackTime >= delay && canAttack) {
            boolean wasSprint = mc.player.isSprinting();
            mc.interactionManager.attackEntity(mc.player, target);
            mc.player.swingHand(net.minecraft.util.Hand.MAIN_HAND);
            if (wasSprint) mc.player.setSprinting(true);
            mc.player.setSprinting(true);
            lastAttackTime = now;
            hitCount++;
            attackTimerStart = now;
        }
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
}
