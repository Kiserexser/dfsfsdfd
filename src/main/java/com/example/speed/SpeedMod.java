package com.example.speed;

import net.fabricmc.api.ModInitializer;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.FireworkRocketItem;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
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
    private static boolean menuOpen = false;
    private static int mode = 0;          // 0-9 speed, 10-19 fly
    private static final String[] MODES = {
            // Speed modes (0-9)
            "WebSpeed", "BHop", "Strafe", "TimerSpeed", "AACSpeed",
            "MatrixSpeed", "LongJump", "GroundSpeed", "AirSpeed", "LegitSpeed",
            // Fly modes (10-19)
            "WebFly", "ElytraFly", "JumpFly", "PacketFly", "ScaffoldFly",
            "TowerFly", "NoFallGlide", "GhostBlockFly", "DamageFly", "HoverFly"
    };
    private static final Random random = new Random();
    private static int tickCounter = 0;
    private static int jumpDelay = 0;
    private static long lastRocketTime = 0;
    private static int ladderFlyTicks = 0;
    private static int glideTicks = 0;

    @Override
    public void onInitialize() {
        new Thread(() -> {
            while (true) {
                try { Thread.sleep(50); } catch (InterruptedException e) { break; }
                if (mc.player == null) continue;

                long window = mc.getWindow().getHandle();
                boolean rShift = GLFW.glfwGetKey(window, GLFW.GLFW_KEY_RIGHT_SHIFT) == GLFW.GLFW_PRESS;
                if (rShift && !menuOpen) {
                    menuOpen = true;
                    mc.execute(() -> mc.setScreen(new ModeMenu()));
                    try { Thread.sleep(200); } catch (InterruptedException ignored) {}
                }

                boolean currentR = GLFW.glfwGetKey(window, GLFW.GLFW_KEY_R) == GLFW.GLFW_PRESS;
                if (currentR && !menuOpen) {
                    enabled = !enabled;
                    mc.player.sendMessage(Text.literal(enabled ? "§aON [" + MODES[mode] + "]" : "§cOFF"), true);
                    try { Thread.sleep(200); } catch (InterruptedException ignored) {}
                }

                if (enabled) {
                    tickCounter++;
                    if (mode < 10) handleSpeed(mode);
                    else handleFly(mode - 10);
                } else {
                    if (mc.timer != null) mc.timer.tickLength = 50.0f;
                }
            }
        }).start();
    }

    // ==================== SPEED (0-9) ====================
    private void handleSpeed(int spdMode) {
        switch (spdMode) {
            case 0 -> webSpeed();
            case 1 -> bhopSpeed();
            case 2 -> strafeSpeed();
            case 3 -> timerSpeed();
            case 4 -> aacSpeed();
            case 5 -> matrixSpeed();
            case 6 -> longJump();
            case 7 -> groundSpeed();
            case 8 -> airSpeed();
            case 9 -> legitSpeed();
        }
    }

    private void webSpeed() {
        if (!isInWeb()) return;
        applyVelocity(0.85);
    }

    private void bhopSpeed() {
        if (mc.player.isOnGround()) {
            if (jumpDelay <= 0) { mc.player.jump(); jumpDelay = 2; }
            else jumpDelay--;
        }
        if (!mc.player.horizontalCollision) applyVelocity(0.55);
    }

    private void strafeSpeed() {
        if (mc.player.isOnGround()) applyVelocity(0.55);
    }

    private void timerSpeed() {
        if (mc.timer != null) mc.timer.tickLength = 40.0f;
        if (mc.player.isOnGround() && mc.player.input.movementForward > 0) mc.player.setSprinting(true);
    }

    private void aacSpeed() {
        if (mc.player.isOnGround() && tickCounter % 2 == 0) {
            mc.player.jump();
            applyVelocity(0.42);
        }
    }

    private void matrixSpeed() {
        if (mc.player.isOnGround()) {
            applyVelocity(0.57);
            mc.player.jump();
        }
    }

    private void longJump() {
        if (mc.player.isOnGround() && tickCounter % 5 == 0) {
            mc.player.jump();
            double yaw = Math.toRadians(mc.player.getYaw());
            double vx = -Math.sin(yaw) * 0.8;
            double vz = Math.cos(yaw) * 0.8;
            mc.player.setVelocity(vx, 0.45, vz);
        }
    }

    private void groundSpeed() { applyVelocity(0.52); }
    private void airSpeed() { if (!mc.player.isOnGround()) applyVelocity(0.35); }
    private void legitSpeed() { applyVelocity(0.38); }

    private void applyVelocity(double speed) {
        float forward = mc.player.input.movementForward;
        float strafe = mc.player.input.movementSideways;
        if (forward == 0 && strafe == 0) return;
        float yaw = mc.player.getYaw();
        double rad = Math.toRadians(yaw);
        double vx = -Math.sin(rad) * forward * speed;
        double vz = Math.cos(rad) * forward * speed;
        if (strafe != 0) {
            double strafeRad = Math.toRadians(yaw + (strafe > 0 ? -90 : 90));
            vx += -Math.sin(strafeRad) * strafe * speed;
            vz += Math.cos(strafeRad) * strafe * speed;
        }
        mc.player.setVelocity(vx, mc.player.getVelocity().y, vz);
        mc.player.setSprinting(true);
    }

    // ==================== FLY (10-19) ====================
    private void handleFly(int flyMode) {
        switch (flyMode) {
            case 0 -> webFly();
            case 1 -> elytraFly();
            case 2 -> jumpFly();
            case 3 -> packetFly();
            case 4 -> scaffoldFly();
            case 5 -> towerFly();
            case 6 -> noFallGlide();
            case 7 -> ghostBlockFly();
            case 8 -> damageFly();
            case 9 -> hoverFly();
        }
    }

    private void webFly() {
        if (!isInWeb()) return;
        if (mc.options.jumpKey.isPressed()) {
            mc.player.addVelocity(0, 0.85, 0);
            mc.player.setSprinting(true);
        }
    }

    private void elytraFly() {
        if (mc.player.isFallFlying()) {
            long now = System.currentTimeMillis();
            if (now - lastRocketTime >= 1500 && findRocket()) lastRocketTime = now;
        } else if (!mc.player.isOnGround() && mc.options.jumpKey.isPressed()) {
            mc.getNetworkHandler().sendPacket(new ClientCommandC2SPacket(mc.player, ClientCommandC2SPacket.Mode.START_FALL_FLYING));
        }
    }

    private boolean findRocket() {
        for (ItemStack stack : mc.player.getInventory().main)
            if (stack.getItem() instanceof FireworkRocketItem) {
                mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
                return true;
            }
        return false;
    }

    private void jumpFly() {
        if (mc.options.jumpKey.isPressed()) {
            if (!mc.player.isOnGround()) {
                if (tickCounter % (1 + random.nextInt(2)) == 0) mc.player.addVelocity(0, 0.42, 0);
                applyVelocity(0.045);
            } else {
                mc.player.jump();
                mc.player.addVelocity(0, 0.1, 0);
            }
        }
    }

    private void packetFly() {
        Vec3d pos = mc.player.getPos();
        mc.getNetworkHandler().sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(pos.x, pos.y + 0.42, pos.z, false));
        mc.player.setVelocity(mc.player.getVelocity().add(0, 0.1, 0));
    }

    private void scaffoldFly() {
        int slot = findBlockSlot();
        if (slot == -1) return;
        BlockPos pos = mc.player.getBlockPos().down();
        if (mc.world.isAir(pos) && mc.player.getPos().y - pos.getY() < 0.5) {
            mc.player.getInventory().selectedSlot = slot;
            mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, new BlockHitResult(Vec3d.ofCenter(pos), Direction.UP, pos, false));
            mc.player.swingHand(Hand.MAIN_HAND);
        }
        if (mc.player.isOnGround() && mc.options.jumpKey.isPressed()) mc.player.jump();
    }

    private void towerFly() {
        int slot = findBlockSlot();
        if (slot == -1) return;
        BlockPos pos = mc.player.getBlockPos().down();
        if (mc.world.isAir(pos) && mc.player.getPos().y - pos.getY() < 0.5) {
            mc.player.getInventory().selectedSlot = slot;
            mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, new BlockHitResult(Vec3d.ofCenter(pos), Direction.UP, pos, false));
            mc.player.swingHand(Hand.MAIN_HAND);
            mc.player.jump();
        }
    }

    private void noFallGlide() {
        if (mc.player.fallDistance > 3) mc.player.fallDistance = 0;
        if (mc.options.jumpKey.isPressed() && !mc.player.isOnGround() && mc.player.getVelocity().y < -0.1) {
            mc.player.addVelocity(0, 0.08, 0);
        }
    }

    private void ghostBlockFly() {
        BlockPos pos = mc.player.getBlockPos();
        if (mc.world.isAir(pos)) {
            mc.world.setBlockStateWithoutNeighborUpdates(pos, Blocks.STONE.getDefaultState());
            mc.player.setPosition(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
        }
    }

    private void damageFly() {
        if (!mc.player.isOnGround() && mc.player.fallDistance > 2) {
            mc.player.addVelocity(0, 0.5, 0);
            mc.player.fallDistance = 0;
        }
    }

    private void hoverFly() {
        if (!mc.player.isOnGround()) {
            mc.player.setVelocity(mc.player.getVelocity().multiply(1, 0, 1));
        }
        if (mc.options.jumpKey.isPressed()) mc.player.addVelocity(0, 0.1, 0);
    }

    // ==================== UTILS ====================
    private boolean isInWeb() {
        var pos = mc.player.getBlockPos();
        return mc.world.getBlockState(pos).getBlock() == Blocks.COBWEB ||
               mc.world.getBlockState(pos.down()).getBlock() == Blocks.COBWEB;
    }

    private int findBlockSlot() {
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.getInventory().getStack(i);
            if (!stack.isEmpty() && stack.getItem() instanceof net.minecraft.item.BlockItem)
                return i;
        }
        return -1;
    }

    // ==================== GUI ====================
    class ModeMenu extends Screen {
        protected ModeMenu() { super(Text.literal("Select Mode")); }
        @Override
        protected void init() {
            super.init();
            int cx = width / 2, cy = height / 2 - 120;
            for (int i = 0; i < MODES.length; i++) {
                final int idx = i;
                addDrawableChild(ButtonWidget.builder(
                        Text.literal(MODES[i] + (mode == idx ? " ✓" : "")),
                        btn -> { mode = idx; menuOpen = false; close(); }
                ).dimensions(cx - 75, cy + i * 24, 150, 20).build());
            }
            addDrawableChild(ButtonWidget.builder(Text.literal("Close"), btn -> { menuOpen = false; close(); })
                    .dimensions(cx - 75, cy + MODES.length * 24 + 10, 150, 20).build());
        }
        @Override
        public void render(DrawContext ctx, int mx, int my, float delta) {
            ctx.fill(0, 0, width, height, 0xFF222222);
            ctx.drawCenteredTextWithShadow(textRenderer, Text.literal("Speed / Fly Mode"), width / 2, height / 2 - 140, 0xFFFFFF);
            super.render(ctx, mx, my, delta);
        }
        @Override
        public boolean keyPressed(int keyCode, int scan, int mods) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE || keyCode == GLFW.GLFW_KEY_RIGHT_SHIFT) {
                menuOpen = false;
                close();
                return true;
            }
            return super.keyPressed(keyCode, scan, mods);
        }
        @Override public boolean shouldPause() { return false; }
    }
}
