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
    private static final long ATTACK_DELAY = 460L;
    private static final float ROTATION_SPEED = 0.35f;      // скорость наведения (0.35 = 35% за тик)
    private static final float HEAD_CIRCLE_AMP = 12f;      // амплитуда вращения головы (видна другим)
    private static final float HEAD_CIRCLE_SPEED = 1.8f;   // скорость вращения головы
    private static final boolean USE_GCD = true;           // округление углов (Gross To Pixel)
    private static final boolean SILENT_AIM = true;        // локальная камера не двигается, только пакеты
    private static final boolean ADD_NOISE = true;         // случайный микро-шум в углы

    // Переменные для Silent Aim
    private static float sentYaw = 0, sentPitch = 0;
    private static boolean initSent = false;

    // Для плавного наведения (только для Silent Aim)
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

        // Плавное изменение отправляемых углов (для Silent Aim)
        float deltaYaw = wrap(idealYaw - currentYaw);
        float deltaPitch = idealPitch - currentPitch;
        currentYaw += deltaYaw * ROTATION_SPEED;
        currentPitch += deltaPitch * ROTATION_SPEED;
        currentYaw = wrap(currentYaw);
        currentPitch = clamp(currentPitch, -89, 89);

        // GCD округление
        if (USE_GCD) {
            float gcd = getGCD();
            currentYaw -= currentYaw % gcd;
            currentPitch -= currentPitch % gcd;
        }

        // Случайный микро-шум (для обхода)
        if (ADD_NOISE) {
            currentYaw += (random.nextFloat() - 0.5f) * 0.3f;
            currentPitch += (random.nextFloat() - 0.5f) * 0.2f;
            currentYaw = wrap(currentYaw);
            currentPitch = clamp(currentPitch, -89, 89);
        }

        // === Silent Aim: отправка пакетов серверу ===
        if (SILENT_AIM) {
            if (mc.getNetworkHandler() != null) {
                // Пакет поворота (другие увидят твою голову)
                PlayerMoveC2SPacket.LookAndOnGround packet = new PlayerMoveC2SPacket.LookAndOnGround(currentYaw, currentPitch, mc.player.isOnGround(), false);
                mc.getNetworkHandler().sendPacket(packet);
            }
            // Локальная камера не меняется – ты не видишь наведения
        } else {
            // Обычный режим – камера поворачивается
            mc.player.setYaw(currentYaw);
            mc.player.setPitch(currentPitch);
        }

        // Ротация головы (визуальный эффект для других) – отдельно от прицела
        float time = System.currentTimeMillis() / 1000f;
        float headYawOffset = (float) Math.sin(time * HEAD_CIRCLE_SPEED) * HEAD_CIRCLE_AMP;
        float headPitchOffset = (float) Math.cos(time * HEAD_CIRCLE_SPEED) * HEAD_CIRCLE_AMP * 0.5f;
        float bodyYaw = SILENT_AIM ? sentYaw : currentYaw;
        mc.player.headYaw = bodyYaw + headYawOffset;
        mc.player.bodyYaw = bodyYaw + headYawOffset;

        // Атака
        long now = System.currentTimeMillis();
        boolean canAttack = Math.abs(deltaYaw) < 15f && Math.abs(deltaPitch) < 15f;
        if (now - lastAttackTime >= ATTACK_DELAY && canAttack) {
            // AutoCritical
            if (mc.player.isOnGround()) {
                mc.player.jump();
            }
            boolean wasSprinting = mc.player.isSprinting();
            mc.interactionManager.attackEntity(mc.player, target);
            mc.player.swingHand(net.minecraft.util.Hand.MAIN_HAND);
            if (wasSprinting) mc.player.setSprinting(true);
            mc.player.setSprinting(true);
            lastAttackTime = now;
        }
    }

    private void updateTarget() {
        if (target != null && target.isAlive() && mc.player.squaredDistanceTo(target) <= RANGE * RANGE) {
            return;
        }
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
