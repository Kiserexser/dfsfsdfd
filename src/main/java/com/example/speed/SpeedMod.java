package com.example.speed;

import net.fabricmc.api.ModInitializer;
import net.minecraft.block.Block;
import net.minecraft.block.CarpetBlock;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.glfw.GLFW;

import java.util.Random;

public class SpeedMod implements ModInitializer {
    private static final MinecraftClient mc = MinecraftClient.getInstance();
    private static boolean enabled = false;
    private static boolean lastRState = false;
    private static final Random random = new Random();
    private static int tickCounter = 0;
    private static int nextActionTick = 0;

    @Override
    public void onInitialize() {
        new Thread(() -> {
            while (true) {
                try {
                    Thread.sleep(50); // 20 тиков в секунду
                    tickCounter++;
                    if (mc.player == null) continue;
                    long window = mc.getWindow().getHandle();
                    boolean currentR = GLFW.glfwGetKey(window, GLFW.GLFW_KEY_R) == GLFW.GLFW_PRESS;
                    if (currentR && !lastRState) {
                        enabled = !enabled;
                        String msg = enabled ? "§aGrim Fly ON (Carpet)" : "§cGrim Fly OFF";
                        if (mc.player != null) mc.player.sendMessage(Text.literal(msg), true);
                        tickCounter = 0;
                        nextActionTick = 0;
                        try { Thread.sleep(200); } catch (InterruptedException ignored) {}
                    }
                    lastRState = currentR;
                    if (enabled) {
                        handleGrimCarpetFly();
                    }
                } catch (InterruptedException e) {
                    break;
                }
            }
        }).start();
    }

    private void handleGrimCarpetFly() {
        if (mc.player == null || mc.world == null) return;

        // Поиск ковра в горячей панели
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

        // Рандомизированная частота действий (не каждый тик)
        if (nextActionTick == 0) {
            nextActionTick = 2 + random.nextInt(4); // 2-5 тиков
        }
        if (tickCounter < nextActionTick) return;
        tickCounter = 0;
        nextActionTick = 0;

        BlockPos playerPos = mc.player.getBlockPos();
        BlockPos blockUnder = playerPos.down();

        // Установка ковра под игроком (только если в воздухе)
        if (!mc.player.isOnGround() && mc.world.isAir(playerPos) && !mc.world.isAir(blockUnder)) {
            int prevSlot = mc.player.getInventory().selectedSlot;
            mc.player.getInventory().selectedSlot = carpetSlot;
            float prevPitch = mc.player.getPitch();
            mc.player.setPitch(90f); // смотрим вниз

            BlockHitResult hitResult = new BlockHitResult(Vec3d.ofCenter(blockUnder), Direction.UP, blockUnder, false);
            mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, hitResult);
            mc.player.swingHand(Hand.MAIN_HAND);

            mc.player.setPitch(prevPitch);
            mc.player.getInventory().selectedSlot = prevSlot;
        }

        // Прыжок с подлётом на ковре
        if (mc.player.isOnGround()) {
            Block blockUnderFeet = mc.world.getBlockState(playerPos).getBlock();
            if (blockUnderFeet instanceof CarpetBlock) {
                mc.player.jump();
                // Случайная сила прыжка (0.5 - 0.65)
                double up = 0.5 + random.nextDouble() * 0.15;
                var vel = mc.player.getVelocity();
                mc.player.setVelocity(vel.x, up, vel.z);
                // Отправка пакета только с шансом 30%, чтобы не спамить
                if (random.nextFloat() < 0.3f && mc.getNetworkHandler() != null) {
                    mc.getNetworkHandler().sendPacket(new PlayerActionC2SPacket(PlayerActionC2SPacket.Action.ABORT_DESTROY_BLOCK, playerPos.up(), Direction.UP));
                }
            }
        }
    }
}
