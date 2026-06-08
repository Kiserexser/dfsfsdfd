package com.example.speed;

import net.fabricmc.api.ModInitializer;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.CarpetBlock;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.glfw.GLFW;

public class SpeedMod implements ModInitializer {
    private static final MinecraftClient mc = MinecraftClient.getInstance();
    private static boolean enabled = false;
    private static boolean lastRState = false;

    @Override
    public void onInitialize() {
        // Поток для отслеживания клавиши R
        new Thread(() -> {
            while (true) {
                try { Thread.sleep(50); } catch (InterruptedException e) { break; }
                if (mc.player == null) continue;
                long window = mc.getWindow().getHandle();
                boolean currentR = GLFW.glfwGetKey(window, GLFW.GLFW_KEY_R) == GLFW.GLFW_PRESS;
                if (currentR && !lastRState) {
                    enabled = !enabled;
                    String msg = enabled ? "§aCarpet Fly ON" : "§cCarpet Fly OFF";
                    if (mc.player != null) mc.player.sendMessage(net.minecraft.text.Text.literal(msg), false);
                    try { Thread.sleep(200); } catch (InterruptedException ignored) {}
                }
                lastRState = currentR;
                if (enabled) handleCarpetFly();
            }
        }).start();
    }

    private void handleCarpetFly() {
        if (mc.player == null || mc.world == null) return;

        // 1. Поиск ковра в горячей панели
        int carpetSlot = -1;
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (!stack.isEmpty() && stack.getItem() instanceof BlockItem) {
                Block block = ((BlockItem) stack.getItem()).getBlock();
                if (block instanceof CarpetBlock) {
                    carpetSlot = i;
                    break;
                }
            }
        }
        if (carpetSlot == -1) return;

        BlockPos playerPos = mc.player.getBlockPos();
        BlockPos blockUnder = playerPos.down();

        // 2. Установка ковра под игроком
        if (!mc.player.isOnGround() && mc.world.isAir(playerPos) && !mc.world.isAir(blockUnder)) {
            int prevSlot = mc.player.getInventory().selectedSlot;
            mc.player.getInventory().selectedSlot = carpetSlot;
            float prevPitch = mc.player.getPitch();
            mc.player.setPitch(90f);

            if (mc.interactionManager != null) {
                BlockHitResult hitResult = new BlockHitResult(Vec3d.ofCenter(blockUnder), Direction.UP, blockUnder, false);
                mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, hitResult);
            }
            mc.player.swingHand(Hand.MAIN_HAND);
            mc.player.setPitch(prevPitch);
            mc.player.getInventory().selectedSlot = prevSlot;
        }

        // 3. Прыжок с подлётом, если стоим на ковре
        if (mc.player.isOnGround()) {
            Block blockUnderFeet = mc.world.getBlockState(playerPos).getBlock();
            if (blockUnderFeet instanceof CarpetBlock) {
                mc.player.jump();
                // Небольшой дополнительный импульс вверх (можно отрегулировать)
                var vel = mc.player.getVelocity();
                mc.player.setVelocity(vel.x, 0.55, vel.z);
                // Пакет для обхода (опционально)
                if (mc.getNetworkHandler() != null) {
                    mc.getNetworkHandler().sendPacket(new PlayerActionC2SPacket(PlayerActionC2SPacket.Action.ABORT_DESTROY_BLOCK, playerPos.up(), Direction.UP));
                }
            }
        }
    }
}
