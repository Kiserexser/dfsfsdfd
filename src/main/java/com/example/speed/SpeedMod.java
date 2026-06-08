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

    private static final float VERTICAL_BOOST = 0.03f;
    private static final float GROUND_BOOST = 0.085f;
    private static final float AIR_BOOST = 0.03f;

    @Override
    public void onInitialize() {
        LOGGER.info("[Speed] Bypass module (no timer). Press R to toggle.");
        Thread tickThread = new Thread(() -> {
            while (true) {
                try {
                    Thread.sleep(50);
                    if (mc.player == null || mc.world == null) continue;
                    long window = mc.getWindow().getHandle();
                    boolean currentR = GLFW.glfwGetKey(window, GLFW.GLFW_KEY_R) == GLFW.GLFW_PRESS;
                    if (currentR && !lastRState) {
                        enabled = !enabled;
                        LOGGER.info(enabled ? "Speed ON" : "Speed OFF");
                        if (!enabled) {
                            ticks = 0;
                            groundTicks = 0;
                        }
                        Thread.sleep(150);
                    }
                    lastRState = currentR;
                    if (enabled) tick();
                } catch (InterruptedException e) { break; }
            }
        });
        tickThread.setDaemon(true);
        tickThread.start();
    }

    private static void tick() {
        // Jump spam (обход прыжков)
        if (mc.player.isOnGround() && !mc.player.isGliding()) {
            groundTicks++;
        } else {
            groundTicks = 0;
        }
        if (groundTicks >= 1) {
            mc.player.jump();
        }

        // Вертикальный и горизонтальный буст (каждые 2 тика)
        if (ticks > 3) {
            double bst = AIR_BOOST;
            if (ticks % 2 == 0) {
                mc.player.addVelocity(0, VERTICAL_BOOST, 0);
                bst = mc.player.isOnGround() ? GROUND_BOOST : AIR_BOOST;
            }
            float dir = getDirection();
            if (dir != -1.0f) {
                double yaw = Math.toRadians(dir);
                double xt = -Math.sin(yaw);
                double zt = Math.cos(yaw);
                mc.player.addVelocity(xt * bst, 0, zt * bst);
            }
        }

        ticks++;

        // Elytra desync (каждые 2 тика)
        if (ticks % 2 == 0 && mc.getNetworkHandler() != null) {
            mc.getNetworkHandler().sendPacket(new ClientCommandC2SPacket(mc.player, ClientCommandC2SPacket.Mode.START_FALL_FLYING));
        }
    }

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
