package com.example.speed;

import net.fabricmc.api.ModInitializer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.text.Text;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.glfw.GLFW;

import java.util.List;
import java.util.Random;

public class SpeedMod implements ModInitializer {
    private static final MinecraftClient mc = MinecraftClient.getInstance();
    private static boolean killaura = false;
    private static boolean lastR = false;
    private static Entity target = null;
    private static long lastAttackTime = 0;
    private static final Random random = new Random();

    // Настройки
    private static final float RANGE = 4.2f;
    private static final long MIN_DELAY = 750L;
    private static final long MAX_DELAY = 850L;
    private static final float ROTATION_SPEED = 0.35f;
    private static final float HEAD_CIRCLE_AMP = 12f;
    private static final float HEAD_CIRCLE_SPEED = 1.8f;
    private static final boolean USE_GCD = true;
    private static final boolean SILENT_AIM = true;
    private static final boolean ADD_NOISE = true;
    private static final boolean NO_SWING = false;

    // Silent Aim
    private static float sentYaw = 0, sentPitch = 0;
    private static boolean initSent = false;
    private static float currentYaw = 0, currentPitch = 0;

    @Override
    public void onInitialize() {
        new Thread(() -> {
            while (true) {
                try { Thread.sleep(50); } catch (InterruptedException e) { break; }
                if (mc.player == null) continue;
                long window = mc.getWindow().getHandle();
                boolean currentR = GLFW.glfwGetKey(window, GLFW.GLFW_KEY_R) == GLFW.GLFW_PRESS;
                if (currentR && !lastR) {
                    killaura = !killaura;
                    mc.player.sendMessage(Text.literal(killaura ? "§aKillaura ON" : "§cKillaura OFF"), true);
                    if (!killaura) target = null;
                    try { Thread.sleep(200); } catch (InterruptedException ignored) {}
                }
                lastR = currentR;
                if (killaura) tick();
            }
        }).start();
    }

    private static float getGCD() {
        double sens = mc.options.getMouseSensitivity().getValue();
        float gcd = (float) (sens * 0.6 + 0.2);
        gcd = gcd * gcd * gcd * 8.0f;
        return Math.max(gcd, 0.001f);
    }

    private void tick() {
        if (mc.currentScreen != null) return;

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

        if (!initSent) {
            sentYaw = mc.player.getYaw();
            sentPitch = mc.player.getPitch();
            currentYaw = sentYaw;
            currentPitch = sentPitch;
            initSent = true;
        }

        // Плавное наведение отправляемых углов
        float deltaYaw = wrap(idealYaw - currentYaw);
        float deltaPitch = idealPitch - currentPitch;
        currentYaw += deltaYaw * ROTATION_SPEED;
        currentPitch += deltaPitch * ROTATION_SPEED;
        currentYaw = wrap(currentYaw);
        currentPitch = clamp(currentPitch, -89, 89);

        // GCD
        if (USE_GCD) {
            float gcd = getGCD();
            currentYaw -= currentYaw % gcd;
            currentPitch -= currentPitch % gcd;
        }

        // Микро-шум
        if (ADD_NOISE) {
            currentYaw += (random.nextFloat() - 0.5f) * 0.3f;
            currentPitch += (random.nextFloat() - 0.5f) * 0.2f;
            currentYaw = wrap(currentYaw);
            currentPitch = clamp(currentPitch, -89, 89);
        }

        // Silent Aim – отправка пакетов на сервер
        if (SILENT_AIM && mc.getNetworkHandler() != null) {
            PlayerMoveC2SPacket.LookAndOnGround packet = new PlayerMoveC2SPacket.LookAndOnGround(currentYaw, currentPitch, mc.player.isOnGround(), false);
            mc.getNetworkHandler().sendPacket(packet);
        } else {
            mc.player.setYaw(currentYaw);
            mc.player.setPitch(currentPitch);
        }

        // Вращение головы для других
        float time = System.currentTimeMillis() / 1000f;
        float headYawOffset = (float) Math.sin(time * HEAD_CIRCLE_SPEED) * HEAD_CIRCLE_AMP;
        float bodyYaw = SILENT_AIM ? sentYaw : currentYaw;
        mc.player.headYaw = bodyYaw + headYawOffset;
        mc.player.bodyYaw = bodyYaw + headYawOffset;

        // Атака – используем реальный угол игрока для проверки, смотрит ли он на цель
        float realYaw = mc.player.getYaw();
        float realPitch = mc.player.getPitch();
        float realDeltaYaw = wrap(idealYaw - realYaw);
        float realDeltaPitch = idealPitch - realPitch;
        boolean isLookingAtTarget = Math.abs(realDeltaYaw) < 15f && Math.abs(realDeltaPitch) < 15f;

        long now = System.currentTimeMillis();
        long delay = MIN_DELAY + (long)(random.nextDouble() * (MAX_DELAY - MIN_DELAY));
        if (now - lastAttackTime >= delay && isLookingAtTarget) {
            // Атака только если игрок реально смотрит на цель
            boolean wasSprinting = mc.player.isSprinting();
            mc.interactionManager.attackEntity(mc.player, target);
            if (!NO_SWING) {
                mc.player.swingHand(net.minecraft.util.Hand.MAIN_HAND);
            }
            if (wasSprinting) mc.player.setSprinting(true);
            mc.player.setSprinting(true);
            lastAttackTime = now;
        }
    }

    private void updateTarget() {
        // Если цель существует, жива и в радиусе – оставляем
        if (target != null && target.isAlive() && mc.player.squaredDistanceTo(target) <= RANGE * RANGE) {
            return;
        }
        // Иначе ищем новую
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

    private static float wrap(float v) {
        v %= 360f;
        if (v >= 180f) v -= 360f;
        if (v < -180f) v += 360f;
        return v;
    }

    private static float clamp(float v, float min, float max) {
        return Math.max(min, Math.min(max, v));
    }
}
