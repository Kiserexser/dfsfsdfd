package com.example.speed;

import net.fabricmc.api.ModInitializer;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
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

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class SpeedMod implements ModInitializer {
    private static final Logger LOGGER = LoggerFactory.getLogger("speedmod");
    private static final MinecraftClient mc = MinecraftClient.getInstance();
    private static boolean lastR = false;
    private static boolean flyEnabled = false;
    private static final Path CONFIG_PATH = Paths.get("config/speedmod_fly.json");

    static {
        loadConfig();
    }

    private static void loadConfig() {
        if (Files.exists(CONFIG_PATH)) {
            try (BufferedReader reader = Files.newBufferedReader(CONFIG_PATH)) {
                flyEnabled = Boolean.parseBoolean(reader.readLine());
            } catch (IOException ignored) {}
        }
    }

    private static void saveConfig() {
        try (BufferedWriter writer = Files.newBufferedWriter(CONFIG_PATH)) {
            writer.write(String.valueOf(flyEnabled));
        } catch (IOException ignored) {}
    }

    public static void toggleFly() {
        flyEnabled = !flyEnabled;
        saveConfig();
        LOGGER.info("Fly mode: " + (flyEnabled ? "ON" : "OFF"));
    }

    @Override
    public void onInitialize() {
        LOGGER.info("SpeedMod Fly (Carpet) initialized. Press R to toggle.");

        new Thread(() -> {
            while (true) {
                try { Thread.sleep(50); } catch (InterruptedException e) { break; }
                if (mc.player == null || mc.world == null) continue;

                long window = mc.getWindow().getHandle();
                boolean currentR = GLFW.glfwGetKey(window, GLFW.GLFW_KEY_R) == GLFW.GLFW_PRESS;
                if (currentR && !lastR) {
                    toggleFly();
                    try { Thread.sleep(200); } catch (InterruptedException ignored) {}
                }
                lastR = currentR;

                if (flyEnabled) {
                    handleGriefCarpet();
                }
            }
        }).start();
    }

    private static void handleGriefCarpet() {
        ClientPlayerEntity player = mc.player;
        if (player == null || mc.world == null) return;

        // 1. Поиск слота с ковром (в первых 9 слотах)
        int carpetSlot = -1;
        PlayerInventory inv = player.getInventory();
        for (int i = 0; i < 9; i++) {
            ItemStack stack = inv.getStack(i);
            if (!stack.isEmpty() && stack.getItem() instanceof BlockItem) {
                Block block = ((BlockItem) stack.getItem()).getBlock();
                if (block == Blocks.WHITE_CARPET || block == Blocks.ORANGE_CARPET || block == Blocks.MAGENTA_CARPET ||
                    block == Blocks.LIGHT_BLUE_CARPET || block == Blocks.YELLOW_CARPET || block == Blocks.LIME_CARPET ||
                    block == Blocks.PINK_CARPET || block == Blocks.GRAY_CARPET || block == Blocks.LIGHT_GRAY_CARPET ||
                    block == Blocks.CYAN_CARPET || block == Blocks.PURPLE_CARPET || block == Blocks.BLUE_CARPET ||
                    block == Blocks.BROWN_CARPET || block == Blocks.GREEN_CARPET || block == Blocks.RED_CARPET ||
                    block == Blocks.BLACK_CARPET) {
                    carpetSlot = i;
                    break;
                }
            }
        }
        if (carpetSlot == -1) return;

        BlockPos playerPos = player.getBlockPos();
        BlockPos blockUnder = playerPos.down();

        // 2. Установка ковра (когда игрок в воздухе, под ним твёрдый блок, а в ногах воздух)
        if (!player.isOnGround() && mc.world.isAir(playerPos) && !mc.world.isAir(blockUnder)) {
            int prevSlot = inv.selectedSlot;
            inv.selectedSlot = carpetSlot;

            float prevPitch = player.getPitch();
            player.setPitch(90f); // смотрим вниз

            BlockHitResult hitResult = new BlockHitResult(
                    new Vec3d(blockUnder.getX() + 0.5, blockUnder.getY(), blockUnder.getZ() + 0.5),
                    Direction.UP, blockUnder, false);
            mc.interactionManager.interactBlock(player, Hand.MAIN_HAND, hitResult);
            player.swingHand(Hand.MAIN_HAND);

            player.setPitch(prevPitch);
            inv.selectedSlot = prevSlot;
        }

        // 3. Прыжок на ковре
        if (player.isOnGround()) {
            if (mc.world.getBlockState(playerPos).getBlock() instanceof net.minecraft.block.CarpetBlock) {
                player.jump();
                Vec3d vel = player.getVelocity();
                player.setVelocity(vel.x, 0.55, vel.z); // высота подлёта
                // Отправляем пакет ABORT_DESTROY_BLOCK (опционально)
                if (mc.getNetworkHandler() != null) {
                    mc.getNetworkHandler().sendPacket(new PlayerActionC2SPacket(PlayerActionC2SPacket.Action.ABORT_DESTROY_BLOCK, playerPos.up(), Direction.UP));
                }
            }
        }
    }
}
