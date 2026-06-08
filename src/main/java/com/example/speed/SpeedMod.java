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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SpeedMod implements ModInitializer {
    private static final Logger LOGGER = LoggerFactory.getLogger("speedmod");
    private static final MinecraftClient mc = MinecraftClient.getInstance();
    private static boolean enabled = false;
    private static boolean lastR = false;

    @Override
    public void onInitialize() {
        LOGGER.info("Carpet Fly mod loaded. Press R to toggle.");
        new Thread(() -> {
            while (true) {
                try { Thread.sleep(50); } catch (InterruptedException e) { break; }
                if (mc.player == null || mc.world == null) continue;
                long window = mc.getWindow().getHandle();
                boolean currentR = GLFW.glfwGetKey(window, GLFW.GLFW_KEY_R) == GLFW.GLFW_PRESS;
                if (currentR && !lastR) {
                    enabled = !enabled;
                    LOGGER.info(enabled ? "Carpet Fly ON" : "Carpet Fly OFF");
                    try { Thread.sleep(200); } catch (InterruptedException ignored) {}
                }
                lastR = currentR;
                if (enabled) {
                    tick();
                }
            }
        }).start();
    }

    private static void tick() {
        // 1. Поиск ковра в инвентаре (первые 9 слотов)
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

        // Условие: игрок не на земле, блок под ногами не воздух, блок на уровне ног — воздух
        if (!mc.player.isOnGround() && !mc.world.isAir(blockUnder) && mc.world.isAir(playerPos)) {
            int prevSlot = mc.player.getInventory().selectedSlot;
            mc.player.getInventory().selectedSlot = carpetSlot;

            float prevPitch = mc.player.getPitch();
            mc.player.setPitch(90f); // смотрим вниз

            // Симулируем правый клик по блоку под игроком (ставим ковёр)
            BlockHitResult hit = new BlockHitResult(
                    new Vec3d(blockUnder.getX() + 0.5, blockUnder.getY() + 0.5, blockUnder.getZ() + 0.5),
                    Direction.UP, blockUnder, false
            );
            mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, hit);

            mc.player.swingHand(Hand.MAIN_HAND);

            // Возвращаем обратно
            mc.player.setPitch(prevPitch);
            mc.player.getInventory().selectedSlot = prevSlot;
        }

        // Прыжок, если стоим на ковре
        if (mc.player.isOnGround()) {
            Block blockUnderFeet = mc.world.getBlockState(mc.player.getBlockPos()).getBlock();
            if (blockUnderFeet instanceof CarpetBlock) {
                // Отправляем пакет ABORT_DESTROY_BLOCK (для обхода)
                mc.getNetworkHandler().sendPacket(new PlayerActionC2SPacket(
                        PlayerActionC2SPacket.Action.ABORT_DESTROY_BLOCK,
                        mc.player.getBlockPos().up(),
                        Direction.UP
                ));
                mc.player.jump();
                // Увеличиваем вертикальную скорость
                double motionY = 0.55;
                mc.player.setVelocity(mc.player.getVelocity().x, motionY, mc.player.getVelocity().z);
            }
        }
    }
}
