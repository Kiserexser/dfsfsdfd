package com.example.speed;

import net.fabricmc.api.ModInitializer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.FireworkRocketItem;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;
import net.minecraft.util.Hand;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SpeedMod implements ModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("speedmod");
    private static final MinecraftClient mc = MinecraftClient.getInstance();

    private static boolean enabled = false;
    private static boolean lastRState = false;
    private static long lastRocketTime = 0;
    private static long lastGlideBoost = 0;

    @Override
    public void onInitialize() {
        LOGGER.info("[RW Flight] Elytra Rocket + Glide. Press R to toggle.");
        Thread tickThread = new Thread(() -> {
            while (true) {
                try {
                    Thread.sleep(50);
                    if (mc.player == null || mc.world == null) continue;

                    long window = mc.getWindow().getHandle();
                    boolean currentR = GLFW.glfwGetKey(window, GLFW.GLFW_KEY_R) == GLFW.GLFW_PRESS;
                    if (currentR && !lastRState) {
                        enabled = !enabled;
                        LOGGER.info(enabled ? "Flight ON (Rocket+Glide)" : "Flight OFF");
                        Thread.sleep(150);
                    }
                    lastRState = currentR;

                    if (enabled) {
                        tick();
                    }
                } catch (InterruptedException e) { break; }
            }
        });
        tickThread.setDaemon(true);
        tickThread.start();
    }

    private static void tick() {
        // 1. Elytra Rocket Flight (авто-ракета, когда летим)
        if (mc.player.isFallFlying()) {
            long now = System.currentTimeMillis();
            // Раз в 1.5 секунды запускаем ракету (если есть в инвентаре)
            if (now - lastRocketTime >= 1500) {
                // Ищем ракету в главной руке или горячей панели
                ItemStack mainHand = mc.player.getMainHandStack();
                boolean hasRocket = mainHand.getItem() instanceof FireworkRocketItem;
                if (!hasRocket) {
                    // Проверяем инвентарь (упрощённо: ищем в первых 9 слотах)
                    PlayerInventory inv = mc.player.getInventory();
                    for (int i = 0; i < 9; i++) {
                        ItemStack stack = inv.getStack(i);
                        if (stack.getItem() instanceof FireworkRocketItem) {
                            // Переключаемся на этот слот
                            inv.selectedSlot = i;
                            hasRocket = true;
                            break;
                        }
                    }
                }
                if (hasRocket) {
                    // Активируем ракету (правый клик)
                    mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
                    lastRocketTime = now;
                } else {
                    // Если ракет нет, хотя бы даём небольшой импульс вперёд (безопасный)
                    double yaw = Math.toRadians(mc.player.getYaw());
                    double vx = -Math.sin(yaw) * 0.1;
                    double vz = Math.cos(yaw) * 0.1;
                    mc.player.addVelocity(vx, 0, vz);
                    lastRocketTime = now - 1000; // не спамим
                }
            }
        } else {
            // Если не летим, но игрок в воздухе и зажат прыжок — пытаемся активировать элитру
            if (!mc.player.isOnGround() && mc.options.jumpKey.isPressed()) {
                mc.getNetworkHandler().sendPacket(new ClientCommandC2SPacket(mc.player, ClientCommandC2SPacket.Mode.START_FALL_FLYING));
            }
        }

        // 2. Glide (замедление падения при зажатом прыжке, "коврик")
        if (mc.options.jumpKey.isPressed() && !mc.player.isOnGround() && !mc.player.isFallFlying()) {
            // Уменьшаем вертикальную скорость, если она отрицательная (падение)
            if (mc.player.getVelocity().y < -0.1) {
                long now = System.currentTimeMillis();
                if (now - lastGlideBoost >= 100) {
                    mc.player.addVelocity(0, 0.08, 0); // лёгкий подъём против гравитации
                    lastGlideBoost = now;
                }
            }
        }
    }
}
