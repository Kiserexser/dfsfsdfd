package com.example.speed.killaura;

import com.example.speed.SpeedMod;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.client.MinecraftClient;

public class KillAura {
    public static final KillAura INSTANCE = new KillAura();
    
    private Entity target;
    private Object config;
    private boolean enabled;
    private long lastAttackTime;

    public void init() {
        this.enabled = true;
        this.config = new Object();
        SpeedMod.LOGGER.info("KillAura initialized");
    }

    public void updateTarget() {
        if (!enabled) {
            target = null;
            return;
        }
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null || client.player == null) {
            target = null;
            return;
        }
        double nearestDistance = 4.5;
        Entity nearestEntity = null;
        for (Entity entity : client.world.getEntities()) {
            if (entity == client.player) continue;
            if (!(entity instanceof LivingEntity)) continue;
            if (entity instanceof PlayerEntity && ((PlayerEntity) entity).isCreative()) continue;
            double dist = client.player.distanceTo(entity);
            if (dist < nearestDistance) {
                nearestDistance = dist;
                nearestEntity = entity;
            }
        }
        target = nearestEntity;
    }

    public Entity getTarget() {
        return target;
    }

    public void setTarget(Entity target) {
        this.target = target;
    }

    public Object getConfig() {
        return config;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean canAttack(int ticks) {
        return System.currentTimeMillis() - lastAttackTime >= ticks * 50;
    }

    public void attack() {
        if (target != null && target instanceof LivingEntity) {
            MinecraftClient.getInstance().interactionManager.attackEntity(MinecraftClient.getInstance().player, (LivingEntity) target);
            lastAttackTime = System.currentTimeMillis();
        }
    }
}
