package com.example.speed;

import net.fabricmc.api.ModInitializer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SpeedMod implements ModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("speedmod");
    private static final MinecraftClient mc = MinecraftClient.getInstance();

    private static boolean enabled = false;
    private static boolean lastRState = false;
    private static int ticks = 0;
    private static int groundTicks = 0;

    private static final float TIMER_FAST = 1.7f;
    private static final float TIMER_SLOW = 0.3f;
    private static final float VERTICAL_BOOST = 0.03f;
    private static final float GROUND_BOOST = 0.085f;
    private static final float AIR_BOOST = 0.03f;

    // Храним оригинальную длину тика (50 мс)
    private static long originalTickLength = 50L;

    @Override
    public void onInitialize() {
        LOGGER.info("[Speed] Full bypass module loaded. Press R to toggle.");

        // Получаем оригинальную длину тика
        if (mc.timer != null) {
            originalTickLength = mc.timer.tickLength;
        }

        Thread tickThread = new Thread(() -> {
            while (true) {
                try {
                    Thread.sleep(50);
                    if (mc.player == null || mc.world == null) continue;

                    long window = mc.getWindow().getHandle();
                    boolean currentR = GLFW.glfwGetKey(window, GLFW.GLFW_KEY_R) == GLFW.GLFW_PRESS;
                    if (currentR && !lastRState) {
                        enabled = !enabled;
                        LOGGER.info(enabled ? "Speed ON (Timer+Jump+Vel)" : "Speed OFF");
                        if (!enabled) {
                            resetTimer();
                            ticks = 0;
                            groundTicks = 0;
                        }
                        Thread.sleep(150);
                    }
                    lastRState = currentR;

                    if (enabled) {
                        tick();
                    } else {
                        resetTimer();
                    }
                } catch (InterruptedException e) {
                    break;
                }
            }
        });
        tickThread.setDaemon(true);
        tickThread.start();
    }

    private static void setTimer(float factor) {
        if (mc.timer != null) {
            // factor > 1 ускоряет, < 1 замедляет
            mc.timer.tickLength = (long) (originalTickLength / factor);
        }
    }

    private static void resetTimer() {
        if (mc.timer != null) {
            mc.timer.tickLength = originalTickLength;
        }
    }

    private static void tick() {
        // 1. Timer bypass (ускорение)
        setTimer(TIMER_FAST);

        // 2. Вертикальный и горизонтальный буст (каждые 2 тика)
        if (ticks > 3) {
            double bst = AIR_BOOST;
            if (ticks % 2 == 0) {
                mc.player.addVelocity(0, VERTICAL_BOOST, 0);
                bst = mc.player.isOnGround() ? GROUND_BOOST : AIR_BOOST;
            }
            double yaw = Math.toRadians(getDirection());
            double xt = -Math.sin(yaw);
            double zt = Math.cos(yaw);
            if (getDirection() == -1.0f) {
                xt = 0.0;
                zt = 0.0;
            }
            mc.player.addVelocity(xt * bst, 0, zt * bst);
        }

        ticks++;

        // 3. Jump on ground (обход антиспам прыжков)
        if (mc.player.isOnGround() && !mc.player.isGliding()) {
            groundTicks++;
        } else {
            groundTicks = 0;
        }
        if (groundTicks >= 1) {
            mc.player.jump();
        }

        // 4. Elytra desync + timer slow (каждые 2 тика)
        if (ticks % 2 == 0) {
            setTimer(TIMER_SLOW);
            if (mc.getNetworkHandler() != null) {
                mc.getNetworkHandler().sendPacket(new ClientCommandC2SPacket(mc.player, ClientCommandC2SPacket.Mode.START_FALL_FLYING));
            }
        }
    }

    // Получаем направление движения (0..360) или -1 если не движется
    private static float getDirection() {
        float yaw = mc.player.getYaw();
        float forward = mc.player.input.movementForward;
        float strafe = mc.player.input.movementSideways;
        if (forward == 0 && strafe == 0) return -1.0f;
        float angle = yaw + (strafe > 0 ? -90 : 90) * (strafe != 0 ? 1 : 0);
        if (forward < 0) angle += 180;
        return angle;
    }
}
