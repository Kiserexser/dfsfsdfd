package com.example.speed;

import net.fabricmc.api.ModInitializer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.network.packet.c2s.play.HandSwingC2SPacket;
import net.minecraft.util.Hand;
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

    // Настройки
    private static final float RANGE = 4.0f;
    private static final long BASE_DELAY_MS = 460L;
    private static final long DELAY_VARIATION_MS = 40L;
    private static final float MAX_ANGLE_DELTA = 20.0f;   // угол, при котором атакуем
    private static final float ROTATION_SPEED = 0.9f;      // насколько быстро поворачиваем голову (0.8-1.0)

    // Состояние
    private static boolean enabled = false;
    private static boolean lastR = false;
    private static Entity target = null;
    private static long lastAttackTime = 0;
    private static int hitCount = 0;

    // Silent aim углы (отправляемые серверу)
    private static float sentYaw = 0, sentPitch = 0;
    private static boolean initSent = false;

    @Override
    public void onInitialize() {
        LOGGER.info("[Visible Killaura] Others WILL see your hits. Press R.");
        new Thread(() -> {
            while (true) {
                try { Thread.sleep(50); } catch (InterruptedException e) { break; }
                if (mc.player == null || mc.world == null) continue;
                long win = mc.getWindow().getHandle();
                boolean currR = GLFW.glfwGetKey(win, GLFW.GLFW_KEY_R) == GLFW.GLFW_PRESS;
                if (currR && !lastR) {
                    enabled = !enabled;
                    LOGGER.info(enabled ? "Killaura ON (others see)" : "Killaura OFF");
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

        // Инициализация отправляемых углов
        if (!initSent) {
            sentYaw = mc.player.getYaw();
            sentPitch = mc.player.getPitch();
            initSent = true;
        }

        // Плавная подстройка отправляемых углов к идеальным (можно сделать резкой, увеличив ROTATION_SPEED)
        float deltaYaw = wrap(idealYaw - sentYaw);
        float deltaPitch = idealPitch - sentPitch;
        float totalDelta = (float) Math.hypot(Math.abs(deltaYaw), Math.abs(deltaPitch));

        sentYaw += deltaYaw * ROTATION_SPEED;
        sentPitch += deltaPitch * ROTATION_SPEED;
        sentYaw = wrap(sentYaw);
        sentPitch = clamp(sentPitch, -89, 89);

        // Отправляем пакет поворота на сервер (чтобы другие видели, куда ты смотришь)
        if (mc.getNetworkHandler() != null) {
            PlayerMoveC2SPacket.LookAndOnGround lookPacket = new PlayerMoveC2SPacket.LookAndOnGround(sentYaw, sentPitch, mc.player.isOnGround(), false);
            mc.getNetworkHandler().sendPacket(lookPacket);
        }

        // Атака с задержкой и проверкой угла
        long now = System.currentTimeMillis();
        long delay = BASE_DELAY_MS + (long)(RAND.nextDouble() * DELAY_VARIATION_MS) - DELAY_VARIATION_MS/2;
        boolean canAttack = totalDelta < MAX_ANGLE_DELTA;
        if (now - lastAttackTime >= delay && canAttack) {
            // Отправляем атаку (сервер засчитывает урон, другие видят анимацию)
            mc.interactionManager.attackEntity(mc.player, target);
            // Отправляем взмах руки (чтобы другие видели замах)
            mc.getNetworkHandler().sendPacket(new HandSwingC2SPacket(Hand.MAIN_HAND));
            // Спринт сохраняем
            boolean wasSprinting = mc.player.isSprinting();
            if (wasSprinting) mc.player.setSprinting(true);
            mc.player.setSprinting(true);
            lastAttackTime = now;
            hitCount++;
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
