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

import java.util.List;
import java.util.Random;

public class SpeedMod implements ModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("speedmod");

    private static boolean enabled = false;
    private static boolean lastRState = false;
    private static Entity target = null;
    private static long lastAttackTime = 0;
    private static final Random random = new Random();

    // Настройки
    private static final float RANGE = 4.2f;
    private static final long MIN_DELAY_MS = 820;
    private static final long MAX_DELAY_MS = 930;
    private static final float ROTATION_SPEED = 8.0f; // градусов в тик (плавно, но достаточно для попадания)

    @Override
    public void onInitialize() {
        LOGGER.info("[SpeedMod] Auto-aim killaura, smooth rotation. Press R to toggle.");

        Thread tickThread = new Thread(() -> {
            while (true) {
                try {
                    Thread.sleep(50); // 20 тиков в секунду
                    MinecraftClient client = MinecraftClient.getInstance();
                    if (client.player == null || client.world == null) continue;

                    long window = client.getWindow().getHandle();
                    boolean currentR = GLFW.glfwGetKey(window, GLFW.GLFW_KEY_R) == GLFW.GLFW_PRESS;

                    if (currentR && !lastRState) {
                        enabled = !enabled;
                        LOGGER.info(enabled ? "[Killaura] ENABLED" : "[Killaura] DISABLED");
                        if (!enabled) target = null;
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
        // Выбор цели
        updateTarget(client);
        if (target == null) return;

        // Плавный поворот к цели (без резких движений)
        rotateToTarget(client);

        // Атака с задержкой
        long now = System.currentTimeMillis();
        long delay = MIN_DELAY_MS + (long)(random.nextDouble() * (MAX_DELAY_MS - MIN_DELAY_MS));
        if (now - lastAttackTime < delay) return;

        // Удар (сохраняем спринт)
        boolean wasSprinting = client.player.isSprinting();
        client.interactionManager.attackEntity(client.player, target);
        if (wasSprinting) client.player.setSprinting(true);
        client.player.setSprinting(true);

        lastAttackTime = now;
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
            if (dist < closest) {
                closest = dist;
                best = e;
            }
        }
        target = best;
    }

    private static void rotateToTarget(MinecraftClient client) {
        if (target == null) return;

        // Вычисляем требуемые углы для взгляда на центр хитбокса цели
        Vec3d eyePos = client.player.getEyePos();
        Vec3d targetPos = target.getBoundingBox().getCenter();
        Vec3d diff = targetPos.subtract(eyePos);
        double distanceXZ = Math.hypot(diff.x, diff.z);

        float targetYaw = (float) Math.toDegrees(Math.atan2(diff.z, diff.x)) - 90;
        float targetPitch = (float) -Math.toDegrees(Math.atan2(diff.y, distanceXZ));

        // Нормализуем углы
        targetYaw = MathHelper.wrapDegrees(targetYaw);
        targetPitch = MathHelper.clamp(targetPitch, -89, 89);

        // Текущие углы игрока
        float currentYaw = client.player.getYaw();
        float currentPitch = client.player.getPitch();

        // Плавный поворот: ограничиваем изменение скорости вращения
        float deltaYaw = MathHelper.wrapDegrees(targetYaw - currentYaw);
        float deltaPitch = targetPitch - currentPitch;

        float stepYaw = MathHelper.clamp(deltaYaw, -ROTATION_SPEED, ROTATION_SPEED);
        float stepPitch = MathHelper.clamp(deltaPitch, -ROTATION_SPEED, ROTATION_SPEED);

        float newYaw = currentYaw + stepYaw;
        float newPitch = currentPitch + stepPitch;
        newPitch = MathHelper.clamp(newPitch, -89, 89);

        // Применяем поворот
        client.player.setYaw(newYaw);
        client.player.setPitch(newPitch);
        // Для отображения другим игрокам синхронизируем углы тела и головы
        client.player.bodyYaw = newYaw;
        client.player.headYaw = newYaw;
        // Небольшой случайный шум для натуральности (опционально)
        if (client.player.age % 10 == 0) {
            client.player.headYaw += (random.nextFloat() - 0.5f) * 0.5f;
        }
    }
}
