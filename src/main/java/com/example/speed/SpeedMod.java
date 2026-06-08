package com.example.speed;

import net.fabricmc.api.ModInitializer;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

public class SpeedMod implements ModInitializer {
    private static final MinecraftClient mc = MinecraftClient.getInstance();
    private static boolean enabled = false;
    private static boolean lastRState = false;
    private static final double SPEED_BOOST = 0.80;

    @Override
    public void onInitialize() {
        new Thread(() -> {
            while (true) {
                try { Thread.sleep(50); } catch (InterruptedException e) { break; }
                if (mc.player == null) continue;
                long window = mc.getWindow().getHandle();
                boolean currentR = GLFW.glfwGetKey(window, GLFW.GLFW_KEY_R) == GLFW.GLFW_PRESS;
                if (currentR && !lastRState) {
                    enabled = !enabled;
                    String msg = enabled ? "§aWebSpeed ON (0.80)" : "§cWebSpeed OFF";
                    if (mc.player != null) mc.player.sendMessage(Text.literal(msg), true);
                    try { Thread.sleep(200); } catch (InterruptedException ignored) {}
                }
                lastRState = currentR;
                if (enabled) {
                    handleSpeed();
                }
            }
        }).start();
    }

    private static boolean isInWeb() {
        if (mc.player == null || mc.world == null) return false;
        var blockPos = mc.player.getBlockPos();
        return mc.world.getBlockState(blockPos).getBlock() == Blocks.COBWEB ||
               mc.world.getBlockState(blockPos.down()).getBlock() == Blocks.COBWEB;
    }

    private void handleSpeed() {
        if (mc.player == null) return;

        if (isInWeb()) {
            float forward = mc.player.input.movementForward;
            float strafe = mc.player.input.movementSideways;
            if (forward != 0 || strafe != 0) {
                float yaw = mc.player.getYaw();
                double rad = Math.toRadians(yaw);
                double vx = -Math.sin(rad) * forward * SPEED_BOOST;
                double vz = Math.cos(rad) * forward * SPEED_BOOST;
                if (strafe != 0) {
                    double strafeRad = Math.toRadians(yaw + (strafe > 0 ? -90 : 90));
                    vx += -Math.sin(strafeRad) * strafe * SPEED_BOOST;
                    vz += Math.cos(strafeRad) * strafe * SPEED_BOOST;
                }
                mc.player.addVelocity(vx, 0, vz);
            }
            mc.player.setSprinting(true);
        }
    }
}
