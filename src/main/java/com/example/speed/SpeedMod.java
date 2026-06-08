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
                    String msg = enabled ? "§aWebFly ON (FAST)" : "§cWebFly OFF";
                    if (mc.player != null) mc.player.sendMessage(Text.literal(msg), true);
                    try { Thread.sleep(200); } catch (InterruptedException ignored) {}
                }
                lastRState = currentR;
                if (enabled) {
                    handleWebFly();
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

    private void handleWebFly() {
        if (mc.player == null) return;

        if (isInWeb()) {
            // Ускоренный подъём – каждый тик импульс 0.85 (в 2 раза быстрее)
            mc.player.addVelocity(0, 0.85, 0);
            mc.player.setSprinting(true);
            if (mc.options.jumpKey.isPressed()) {
                mc.player.jump();
            }
        } else {
            if (!mc.player.isOnGround() && mc.player.getVelocity().y < -0.1) {
                mc.player.addVelocity(0, 0.04, 0);
            }
        }
    }
}
