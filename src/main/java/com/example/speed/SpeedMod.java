package com.example.speed;

import net.fabricmc.api.ModInitializer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.text.Text;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL11;

import java.util.List;
import java.util.Random;

public class SpeedMod implements ModInitializer {
    private static final MinecraftClient mc = MinecraftClient.getInstance();
    private static boolean killaura = false;
    private static boolean lastR = false;
    private static Entity target = null;
    private static long lastAttackTime = 0;
    private static final Random random = new Random();

    // ========== НАСТРОЙКИ ==========
    private static final float RANGE = 4.2f;                 // радиус атаки (блоки)
    private static final long MIN_DELAY = 750L;              // мин. задержка (мс)
    private static final long MAX_DELAY = 850L;              // макс. задержка
    private static final float FOV_RADIUS = 60.0f;           // угол обзора (градусы)
    private static final boolean SHOW_FOV_CIRCLE = true;     // рисовать круг на экране (требует Fabric API)
    private static final boolean HEAD_SHAKE = true;          // тряска головы (обход)
    private static final float HEAD_SHAKE_AMP = 8.0f;        // амплитуда поворотов головы
    private static final float HEAD_SHAKE_SPEED = 1.5f;      // скорость

    // ========== Рендер круга (только при SHOW_FOV_CIRCLE = true и Fabric API) ==========
    static {
        if (SHOW_FOV_CIRCLE) {
            // Регистрация через Fabric API (раскомментируйте, если добавили Fabric API)
            // net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback.EVENT.register((context, tickDelta) -> renderFovCircle());
            System.out.println("[SpeedMod] FOV circle needs Fabric API. Set SHOW_FOV_CIRCLE=false or add Fabric API.");
        }
    }

    private static void renderFovCircle() {
        if (!killaura) return;
        if (mc.player == null || mc.currentScreen != null) return;

        float fovDeg = FOV_RADIUS;
        int sw = mc.getWindow().getScaledWidth();
        int sh = mc.getWindow().getScaledHeight();
        float cx = sw / 2f;
        float cy = sh / 2f;
        float gameFov = (float) mc.gameRenderer.getFov(mc.gameRenderer.getCamera(), mc.getTickDelta(), true);
        float radiusPx = (float) (Math.tan(Math.toRadians(fovDeg / 2.0)) / Math.tan(Math.toRadians(gameFov / 2.0))) * (sh / 2f);
        int segments = 64;
        int color = 0xFFFFFF;
        GL11.glEnable(GL11.GL_LINE_SMOOTH);
        net.minecraft.client.render.RenderSystem.disableTexture();
        net.minecraft.client.render.RenderSystem.enableBlend();
        Tessellator tess = Tessellator.getInstance();
        BufferBuilder buf = tess.getBuffer();
        buf.begin(GL11.GL_LINE_LOOP, VertexFormats.POSITION_COLOR);
        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = color & 0xFF;
        int a = 200;
        org.joml.Matrix4f mat = new org.joml.Matrix4f().identity();
        for (int i = 0; i < segments; i++) {
            double angle = 2.0 * Math.PI * i / segments;
            float px = cx + (float) (Math.cos(angle) * radiusPx);
            float py = cy + (float) (Math.sin(angle) * radiusPx);
            buf.vertex(px, py, 0).color(r, g, b, a).next();
        }
        tess.draw();
        net.minecraft.client.render.RenderSystem.enableTexture();
        net.minecraft.client.render.RenderSystem.disableBlend();
        GL11.glDisable(GL11.GL_LINE_SMOOTH);
    }

    // ========== Основной поток (включение/выключение по R) ==========
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

    // ========== Логика Killaura ==========
    private void tick() {
        updateTarget();
        if (target == null) return;

        // Проверка дистанции и видимости
        double distSq = mc.player.squaredDistanceTo(target);
        if (distSq > RANGE * RANGE) return;
        if (!mc.player.canSee(target)) return;

        // Проверка FOV (цель должна быть в круге)
        if (!isInFov(target, FOV_RADIUS)) return;

        long now = System.currentTimeMillis();
        long delay = MIN_DELAY + (long)(random.nextDouble() * (MAX_DELAY - MIN_DELAY));
        if (now - lastAttackTime >= delay) {
            // Отправляем пакет поворота (Silent Aim)
            sendLookAtTarget(target);
            // Атакуем
            boolean wasSprinting = mc.player.isSprinting();
            mc.interactionManager.attackEntity(mc.player, target);
            mc.player.swingHand(net.minecraft.util.Hand.MAIN_HAND);
            if (wasSprinting) mc.player.setSprinting(true);
            mc.player.setSprinting(true);
            lastAttackTime = now;
        } else {
            // Режим ожидания: тряска головы (обход)
            if (HEAD_SHAKE) {
                float time = (System.currentTimeMillis() % 2000) / 2000.0f * (float) Math.PI * 2;
                float shakeYaw = (float) Math.sin(time * HEAD_SHAKE_SPEED) * HEAD_SHAKE_AMP;
                float shakePitch = (float) Math.cos(time * HEAD_SHAKE_SPEED * 1.3f) * HEAD_SHAKE_AMP * 0.5f;
                mc.player.headYaw = mc.player.getYaw() + shakeYaw;
                mc.player.bodyYaw = mc.player.getYaw() + shakeYaw;
                // Можно добавить и pitch, но обычно не нужно
            }
        }
    }

    // Отправка пакета поворота на цель (Silent Aim)
    private void sendLookAtTarget(Entity target) {
        if (mc.getNetworkHandler() == null) return;
        Vec3d eye = mc.player.getEyePos();
        Vec3d to = target.getBoundingBox().getCenter().subtract(eye);
        double hyp = Math.hypot(to.x, to.z);
        float yaw = (float) (Math.toDegrees(Math.atan2(to.z, to.x)) - 90);
        float pitch = (float) -Math.toDegrees(Math.atan2(to.y, hyp));
        yaw = wrap(yaw);
        pitch = clamp(pitch, -89, 89);
        PlayerMoveC2SPacket.LookAndOnGround packet = new PlayerMoveC2SPacket.LookAndOnGround(yaw, pitch, mc.player.isOnGround(), false);
        mc.getNetworkHandler().sendPacket(packet);
    }

    // Проверка, находится ли цель внутри круга FOV
    private boolean isInFov(Entity entity, float fovDeg) {
        if (fovDeg >= 180.0f) return true;
        float halfFov = fovDeg / 2.0f;
        Vec3d eyes = mc.player.getEyePos();
        Box box = entity.getBoundingBox();
        double[] checkY = {box.minY, (box.minY + box.maxY) / 2.0, box.maxY};
        double cx = (box.minX + box.maxX) / 2.0;
        double cz = (box.minZ + box.maxZ) / 2.0;
        for (double y : checkY) {
            Vec3d dir = new Vec3d(cx - eyes.x, y - eyes.y, cz - eyes.z).normalize();
            float pYaw = (float) Math.toDegrees(Math.atan2(-dir.x, dir.z));
            float pPitch = (float) (-Math.toDegrees(Math.atan2(dir.y, Math.hypot(dir.x, dir.z))));
            float dYaw = Math.abs(wrap(pYaw - mc.player.getYaw()));
            float dPitch = Math.abs(pPitch - mc.player.getPitch());
            if (dYaw <= halfFov && dPitch <= halfFov) return true;
        }
        return false;
    }

    // Выбор цели
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

    // Утилиты
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
