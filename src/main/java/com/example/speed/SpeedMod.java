package com.example.speed;

import net.fabricmc.api.ModInitializer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ShieldItem;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Random;

public class SpeedMod implements ModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("speedmod");
    private static final MinecraftClient mc = MinecraftClient.getInstance();

    private static boolean enabled = false;
    private static boolean lastRState = false;
    private static LivingEntity target = null;
    private static long lastAttackTime = 0;
    private static int ticks = 0;
    private static final Random random = new Random();

    // Параметры
    private static float distance = 3.0f;
    private static float snapTicks = 1.0f;
    private static boolean wallsBypass = true;
    private static boolean onlyCrits = true;
    private static boolean shieldBreaker = true;
    private static boolean unpressShield = true;

    // Ротация
    private static Vector2f selfRotation = new Vector2f(0, 0);
    private static Vector2f targetRotation = new Vector2f(0, 0);
    private static Vector2f fakeRotation = new Vector2f(0, 0);
    private static Vector2f fakeTargetRotation = new Vector2f(0, 0);

    @Override
    public void onInitialize() {
        LOGGER.info("[ReallyWorld Aura] Loaded. Press R to toggle.");
        Thread tickThread = new Thread(() -> {
            while (true) {
                try {
                    Thread.sleep(50);
                    if (mc.player == null || mc.world == null) continue;

                    long window = mc.getWindow().getHandle();
                    boolean currentR = GLFW.glfwGetKey(window, GLFW.GLFW_KEY_R) == GLFW.GLFW_PRESS;
                    if (currentR && !lastRState) {
                        enabled = !enabled;
                        LOGGER.info(enabled ? "Aura ON (ReallyWorld mode)" : "Aura OFF");
                        if (!enabled) target = null;
                        Thread.sleep(150);
                    }
                    lastRState = currentR;

                    if (enabled) tick();
                } catch (InterruptedException e) {
                    break;
                }
            }
        });
        tickThread.setDaemon(true);
        tickThread.start();
    }

    private static void tick() {
        updateTarget();
        if (target == null) {
            selfRotation = new Vector2f(mc.player.getYaw(), mc.player.getPitch());
            fakeRotation = new Vector2f(mc.player.getYaw(), mc.player.getPitch());
            return;
        }

        updateRotation();
        attackTarget();
        fakeRotation();

        // Режим ReallyWorld – оригинальная логика
        boolean hasBlock = isBlockBetween(mc.player.getEyePos(), target.getBoundingBox().getCenter(), distance);
        boolean isGliding = mc.player.isGliding(); // исправлено: isFallFlying -> isGliding

        if (hasBlock && !isGliding && !wallsBypass) {
            if (ticks > 0) {
                fastRotation();
                ticks--;
            } else {
                selfRotation = new Vector2f(mc.player.getYaw(), mc.player.getPitch());
            }
        } else {
            fastRotation();
        }

        applySyncRotation();
    }

    private static void updateTarget() {
        if (target != null && target.isAlive() && mc.player.squaredDistanceTo(target) <= distance * distance) {
            return;
        }
        double closest = distance * distance;
        LivingEntity best = null;
        Box box = mc.player.getBoundingBox().expand(distance);
        List<Entity> entities = mc.world.getOtherEntities(mc.player, box,
                e -> e instanceof LivingEntity && e != mc.player && ((LivingEntity) e).isAlive());
        for (Entity e : entities) {
            if (e instanceof PlayerEntity && mc.player.isTeammate((PlayerEntity) e)) continue;
            double distSq = mc.player.squaredDistanceTo(e);
            if (distSq < closest && mc.player.canSee(e)) {
                closest = distSq;
                best = (LivingEntity) e;
            }
        }
        target = best;
    }

    private static void updateRotation() {
        targetRotation = rotationAngles(target);
        fakeTargetRotation = rotationAngles(target);
    }

    private static Vector2f rotationAngles(Entity target) {
        Vec3d eyePos = mc.player.getEyePos();
        Vec3d targetVec = target.getBoundingBox().getCenter().subtract(eyePos);
        double hyp = Math.hypot(targetVec.x, targetVec.z);
        float yaw = (float) (Math.toDegrees(Math.atan2(targetVec.z, targetVec.x)) - 90);
        float pitch = (float) -Math.toDegrees(Math.atan2(targetVec.y, hyp));
        return new Vector2f(wrapDegrees(yaw), clamp(pitch, -89, 89));
    }

    private static void fastRotation() {
        selfRotation = new Vector2f(targetRotation.x, targetRotation.y);
    }

    private static void fakeRotation() {
        fakeRotation = new Vector2f(fakeTargetRotation.x, fakeTargetRotation.y);
    }

    private static void attackTarget() {
        if (mc.player.getAttackCooldownProgress(0.5f) < 1.0f) return;
        if (target == null) return;
        if (mc.player.squaredDistanceTo(target) > distance * distance) return;

        if (onlyCrits && mc.player.isOnGround()) return; // только в прыжке

        long now = System.currentTimeMillis();
        if (now - lastAttackTime >= 460L) {
            if (wallsBypass && isBlockBetween(mc.player.getEyePos(), target.getBoundingBox().getCenter(), distance)) {
                ticks = (int) snapTicks;
            } else {
                ticks = 0;
            }

            if (unpressShield && mc.player.getOffHandStack().getItem() instanceof ShieldItem) {
                mc.getNetworkHandler().sendPacket(new PlayerActionC2SPacket(PlayerActionC2SPacket.Action.RELEASE_USE_ITEM, BlockPos.ORIGIN, Direction.DOWN));
            }

            mc.interactionManager.attackEntity(mc.player, target);
            mc.player.swingHand(Hand.MAIN_HAND);

            // Shield Breaker упрощённо (можно расширить)
            if (shieldBreaker && target instanceof PlayerEntity && ((PlayerEntity) target).isBlocking()) {
                // дополнительная атака не реализована, но структура осталась
            }

            lastAttackTime = now;
        }
    }

    private static void applySyncRotation() {
        if (selfRotation != null) {
            mc.player.setYaw(selfRotation.x);
            mc.player.setPitch(selfRotation.y);
            mc.player.headYaw = selfRotation.x;
            mc.player.bodyYaw = selfRotation.x;
        }
    }

    private static boolean isBlockBetween(Vec3d from, Vec3d to, double range) {
        Vec3d direction = to.subtract(from).normalize();
        Vec3d end = from.add(direction.multiply(range));
        BlockHitResult hit = mc.world.raycast(new RaycastContext(from, end, RaycastContext.ShapeType.OUTLINE, RaycastContext.FluidHandling.NONE, mc.player));
        return hit.getType() == HitResult.Type.BLOCK;
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
