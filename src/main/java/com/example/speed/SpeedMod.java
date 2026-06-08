package com.example.speed;

import net.fabricmc.api.ModInitializer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.text.Text;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.glfw.GLFW;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

public class SpeedMod implements ModInitializer {
    private static final MinecraftClient mc = MinecraftClient.getInstance();
    private static boolean lastR = false;
    private static boolean killauraEnabled = false;
    private static int selectedMode = 0; // 0-24
    private static final String[] MODES = {
        "FunTime", "SlothAi", "SlothAC", "UniAC", "Legit",
        "Noise", "Inertia", "RandomWalk", "GCDOnly", "SilentSmooth",
        "Spin", "Jitter", "RandomTeleport", "LagSwitch", "PerfectTrace",
        "Wave", "Spiral", "Dance", "Static", "Shake",
        "Circle", "Cross", "Dot", "Star", "Heart"
    };
    private static final Path CONFIG_PATH = Paths.get("config/speedmod_killaura.json");

    // Переменные Killaura
    private static Entity target = null;
    private static long lastAttackTime = 0;
    private static int hitCount = 0;
    private static Random random = new Random();
    private static final float RANGE = 4.2f;
    private static final long DELAY_MS = 460L;
    private static float sentYaw = 0, sentPitch = 0; // для silent aim

    // Для круга-аима
    private static float aimCircleRadius = 30f; // градусы от центра экрана
    private static boolean drawCircle = true;

    static {
        loadConfig();
    }

    private static void loadConfig() {
        if (Files.exists(CONFIG_PATH)) {
            try (BufferedReader reader = Files.newBufferedReader(CONFIG_PATH)) {
                killauraEnabled = Boolean.parseBoolean(reader.readLine());
                selectedMode = Integer.parseInt(reader.readLine());
            } catch (IOException ignored) {}
        }
    }

    private static void saveConfig() {
        try (BufferedWriter writer = Files.newBufferedWriter(CONFIG_PATH)) {
            writer.write(String.valueOf(killauraEnabled));
            writer.newLine();
            writer.write(String.valueOf(selectedMode));
        } catch (IOException ignored) {}
    }

    public static void toggleKillaura() {
        killauraEnabled = !killauraEnabled;
        saveConfig();
    }

    public static void setMode(int mode) {
        selectedMode = mode;
        saveConfig();
    }

    @Override
    public void onInitialize() {
        // GUI по правому Shift
        new Thread(() -> {
            while (true) {
                try { Thread.sleep(50); } catch (InterruptedException e) { break; }
                if (mc.player != null && mc.currentScreen == null) {
                    long window = mc.getWindow().getHandle();
                    boolean rShift = GLFW.glfwGetKey(window, GLFW.GLFW_KEY_RIGHT_SHIFT) == GLFW.GLFW_PRESS;
                    if (rShift) {
                        mc.execute(() -> mc.setScreen(new ModeMenu()));
                        try { Thread.sleep(200); } catch (InterruptedException ignored) {}
                    }
                }
            }
        }).start();
        // Killaura по R
        new Thread(() -> {
            while (true) {
                try { Thread.sleep(50); } catch (InterruptedException e) { break; }
                if (mc.player == null || mc.world == null) continue;
                long window = mc.getWindow().getHandle();
                boolean currentR = GLFW.glfwGetKey(window, GLFW.GLFW_KEY_R) == GLFW.GLFW_PRESS;
                if (currentR && !lastR) {
                    toggleKillaura();
                    try { Thread.sleep(200); } catch (InterruptedException ignored) {}
                }
                lastR = currentR;
                if (killauraEnabled) {
                    tickKillaura();
                }
            }
        }).start();
        // Рендер круга (через простой поток? нельзя, нужен хук. Используем событие? Но без Fabric API сложно. Сделаем через кастомный оверлей? Можно, но проще не рисовать круг, либо добавить миксин. Не будем усложнять.
        // Для рисования круга без Fabric API можно использовать OverlayRenderer? Нет. Я опущу рисование круга, но логика "цель в круге" будет работать.
    }

    private static void tickKillaura() {
        updateTarget();
        if (target == null) return;

        // Проверка, находится ли цель в круге (угол от центра экрана)
        float angleToTarget = getAngleToTarget(target);
        if (angleToTarget > aimCircleRadius) return;

        // Идеальные углы на цель
        Vec3d eyePos = mc.player.getEyePos();
        Vec3d targetVec = target.getBoundingBox().getCenter().subtract(eyePos);
        double hyp = Math.hypot(targetVec.x, targetVec.z);
        float idealYaw = (float) (Math.toDegrees(Math.atan2(targetVec.z, targetVec.x)) - 90);
        float idealPitch = (float) -Math.toDegrees(Math.atan2(targetVec.y, hyp));
        idealYaw = wrapDegrees(idealYaw);
        idealPitch = clamp(idealPitch, -89, 89);

        // Применяем ротацию (silent aim)
        float[] rotated = applyRotation(idealYaw, idealPitch);
        sendSilentLook(rotated[0], rotated[1]);

        // Атака с задержкой
        long now = System.currentTimeMillis();
        if (now - lastAttackTime >= DELAY_MS) {
            boolean wasSprinting = mc.player.isSprinting();
            mc.interactionManager.attackEntity(mc.player, target);
            mc.player.swingHand(net.minecraft.util.Hand.MAIN_HAND);
            if (wasSprinting) mc.player.setSprinting(true);
            mc.player.setSprinting(true);
            lastAttackTime = now;
            hitCount++;
        }
    }

    private static void sendSilentLook(float yaw, float pitch) {
        if (mc.getNetworkHandler() == null) return;
        PlayerMoveC2SPacket.LookAndOnGround packet = new PlayerMoveC2SPacket.LookAndOnGround(yaw, pitch, mc.player.isOnGround(), false);
        mc.getNetworkHandler().sendPacket(packet);
        sentYaw = yaw;
        sentPitch = pitch;
    }

    private static float getAngleToTarget(Entity target) {
        Vec3d eyePos = mc.player.getEyePos();
        Vec3d lookVec = mc.player.getRotationVector();
        Vec3d toTarget = target.getBoundingBox().getCenter().subtract(eyePos).normalize();
        double dot = lookVec.dotProduct(toTarget);
        double angleRad = Math.acos(Math.min(1.0, Math.max(-1.0, dot)));
        return (float) Math.toDegrees(angleRad);
    }

    private static float[] applyRotation(float yaw, float pitch) {
        float outYaw = yaw, outPitch = pitch;
        long now = System.currentTimeMillis();
        Random r = random;
        switch (selectedMode) {
            case 0: // FunTime
                float t = mc.player.age * 1.0f;
                outYaw += (float) Math.sin(t) * 17.0f;
                outPitch += (float) Math.cos(t) * 8.5f;
                break;
            case 1: // SlothAi
                boolean canAttack = (now - lastAttackTime) < DELAY_MS;
                if (!canAttack) {
                    outYaw += r.nextFloat() * 40 * (float) Math.sin(now / 60.0);
                    outPitch += r.nextFloat() * 150 * (float) Math.cos(now / 40.0);
                }
                float speed = canAttack ? 1.0f : 0.3f;
                float dy = wrapDegrees(outYaw - sentYaw);
                float dp = outPitch - sentPitch;
                outYaw = sentYaw + dy * speed;
                outPitch = sentPitch + dp * speed;
                break;
            case 2: // SlothAC
                boolean attacking = (now - lastAttackTime) < DELAY_MS;
                if (!attacking) {
                    outYaw += (float) (9999 * Math.sin(now / 0.1));
                    outPitch += (float) (9999 * Math.cos(now / 0.1));
                }
                float spd = attacking ? 115555555555555f : 1.1f;
                float dy2 = wrapDegrees(outYaw - sentYaw);
                float dp2 = outPitch - sentPitch;
                float lineYaw = Math.abs(dy2 / (float) Math.hypot(dy2, dp2)) * 180;
                float linePitch = Math.abs(dp2) * 180;
                dy2 = MathHelper.clamp(dy2, -lineYaw, lineYaw);
                dp2 = MathHelper.clamp(dp2, -linePitch, linePitch);
                outYaw = sentYaw + dy2 * Math.min(spd, 1.0f);
                outPitch = sentPitch + dp2 * Math.min(spd, 1.0f);
                break;
            case 3: // UniAC
                float timeSec = (now % 10000) / 1000.0f;
                float ya = (float) Math.sin(timeSec * 2 * Math.PI * 4) * 25f;
                float pi = (float) Math.sin(timeSec * 2 * Math.PI * 4) * 15f;
                outYaw += ya;
                outPitch += pi;
                outPitch = clamp(outPitch, -89, 90);
                float gcd = getGCD();
                outYaw -= outYaw % gcd;
                outPitch -= outPitch % gcd;
                break;
            case 4: // Legit
                float speedLegit = 0.2f;
                float dyl = wrapDegrees(outYaw - sentYaw);
                float dpl = outPitch - sentPitch;
                outYaw = sentYaw + dyl * speedLegit;
                outPitch = sentPitch + dpl * speedLegit;
                break;
            case 5: // Noise
                outYaw += (r.nextFloat() - 0.5f) * 6;
                outPitch += (r.nextFloat() - 0.5f) * 3;
                break;
            case 6: // Inertia
                float inertia = 0.7f;
                outYaw = sentYaw + (outYaw - sentYaw) * inertia;
                outPitch = sentPitch + (outPitch - sentPitch) * inertia;
                break;
            case 7: // RandomWalk
                outYaw += (r.nextFloat() - 0.5f) * 15;
                outPitch += (r.nextFloat() - 0.5f) * 8;
                break;
            case 8: // GCDOnly
                float g = getGCD();
                outYaw -= outYaw % g;
                outPitch -= outPitch % g;
                break;
            case 9: // SilentSmooth
                float ss = 0.15f;
                float dys = wrapDegrees(outYaw - sentYaw);
                float dps = outPitch - sentPitch;
                outYaw = sentYaw + dys * ss;
                outPitch = sentPitch + dps * ss;
                break;
            case 10: // Spin
                outYaw += 360f * (now % 1000) / 1000f;
                break;
            case 11: // Jitter
                outYaw += r.nextGaussian() * 20;
                outPitch += r.nextGaussian() * 10;
                break;
            case 12: // RandomTeleport
                if (r.nextInt(20) == 0) {
                    outYaw += r.nextFloat() * 360;
                    outPitch += r.nextFloat() * 180 - 90;
                }
                break;
            case 13: // LagSwitch
                if (r.nextInt(10) == 0) {
                    try { Thread.sleep(100); } catch (InterruptedException ignored) {}
                }
                break;
            case 14: // PerfectTrace
                // без изменений – точное наведение
                break;
            case 15: // Wave
                outYaw += (float) Math.sin(now / 100.0) * 30;
                outPitch += (float) Math.cos(now / 80.0) * 20;
                break;
            case 16: // Spiral
                float angle = (now % 2000) / 2000f * 360f;
                outYaw += (float) Math.sin(Math.toRadians(angle)) * 15;
                outPitch += (float) Math.cos(Math.toRadians(angle)) * 10;
                break;
            case 17: // Dance
                outYaw += (float) Math.sin(now / 150.0) * 25;
                outPitch += (float) Math.cos(now / 120.0) * 12;
                break;
            case 18: // Static
                // не меняем
                break;
            case 19: // Shake
                outYaw += r.nextFloat() * 5 - 2.5f;
                outPitch += r.nextFloat() * 3 - 1.5f;
                break;
            case 20: // Circle
                float rad = (float) Math.toRadians(now % 360);
                outYaw += (float) Math.cos(rad) * 12;
                outPitch += (float) Math.sin(rad) * 6;
                break;
            case 21: // Cross
                float cross = (now % 1000) / 1000f * 360;
                outYaw += (float) Math.sin(cross) * 10;
                outPitch += (float) Math.sin(cross * 2) * 10;
                break;
            case 22: // Dot
                outYaw += (r.nextFloat() - 0.5f) * 2;
                outPitch += (r.nextFloat() - 0.5f) * 2;
                break;
            case 23: // Star
                float star = (now % 2000) / 2000f * 360;
                outYaw += (float) Math.sin(star * 3) * 8;
                outPitch += (float) Math.cos(star * 2) * 8;
                break;
            case 24: // Heart
                float h = (now % 3000) / 3000f * (float)(2*Math.PI);
                outYaw += (float) (16 * Math.pow(Math.sin(h), 3));
                outPitch += (float) (13 * Math.cos(h) - 5 * Math.cos(2*h) - 2*Math.cos(3*h) - Math.cos(4*h));
                break;
        }
        outYaw = wrapDegrees(outYaw);
        outPitch = clamp(outPitch, -89, 89);
        return new float[]{outYaw, outPitch};
    }

    private static void updateTarget() {
        if (target != null && target.isAlive() && mc.player.squaredDistanceTo(target) <= RANGE * RANGE) {
            return;
        }
        Entity best = null;
        double closest = RANGE * RANGE;
        Box box = mc.player.getBoundingBox().expand(RANGE);
        List<Entity> entities = mc.world.getOtherEntities(mc.player, box,
                e -> e instanceof LivingEntity && e != mc.player && ((LivingEntity) e).isAlive());
        for (Entity e : entities) {
            if (e instanceof PlayerEntity && mc.player.isTeammate((PlayerEntity) e)) continue;
            double dist = mc.player.squaredDistanceTo(e);
            if (dist < closest && mc.player.canSee(e)) {
                closest = dist;
                best = e;
            }
        }
        target = best;
    }

    private static float getGCD() {
        double sens = mc.options.getMouseSensitivity().getValue();
        float gcd = (float) (sens * 0.6 + 0.2);
        gcd = gcd * gcd * gcd * 8.0f;
        return Math.max(gcd, 0.001f);
    }

    private static float wrapDegrees(float value) {
        value %= 360.0f;
        if (value >= 180.0f) value -= 360.0f;
        if (value < -180.0f) value += 360.0f;
        return value;
    }

    private static float clamp(float value, float min, float max) {
        if (value < min) return min;
        if (value > max) return max;
        return value;
    }

    // ========== GUI выбора режима ==========
    static class ModeMenu extends Screen {
        private int scrollOffset = 0;
        private static final int BUTTONS_PER_PAGE = 10;

        protected ModeMenu() {
            super(Text.literal("Select Killaura Mode"));
        }

        @Override
        protected void init() {
            super.init();
            updateButtons();
        }

        private void updateButtons() {
            clearChildren();
            int centerX = width / 2;
            int startY = height / 2 - 120;
            for (int i = scrollOffset; i < Math.min(scrollOffset + BUTTONS_PER_PAGE, MODES.length); i++) {
                final int mode = i;
                ButtonWidget btn = ButtonWidget.builder(
                        Text.literal(MODES[i] + (selectedMode == mode ? " ✓" : "")),
                        button -> {
                            setMode(mode);
                            close();
                        }
                ).dimensions(centerX - 100, startY + (i - scrollOffset) * 22, 200, 20).build();
                addDrawableChild(btn);
            }
            if (scrollOffset > 0) {
                addDrawableChild(ButtonWidget.builder(Text.literal("↑"), b -> { scrollOffset -= BUTTONS_PER_PAGE; updateButtons(); })
                        .dimensions(centerX + 110, startY, 20, 20).build());
            }
            if (scrollOffset + BUTTONS_PER_PAGE < MODES.length) {
                addDrawableChild(ButtonWidget.builder(Text.literal("↓"), b -> { scrollOffset += BUTTONS_PER_PAGE; updateButtons(); })
                        .dimensions(centerX + 110, startY + BUTTONS_PER_PAGE * 22 - 20, 20, 20).build());
            }
            addDrawableChild(ButtonWidget.builder(Text.literal("Close"), button -> close())
                    .dimensions(centerX - 100, startY + BUTTONS_PER_PAGE * 22 + 10, 200, 20).build());
        }

        @Override
        public void render(DrawContext context, int mouseX, int mouseY, float delta) {
            context.fill(0, 0, width, height, 0xCC000000);
            context.drawCenteredTextWithShadow(textRenderer, Text.literal("Killaura Modes (25)"), width / 2, height / 2 - 140, 0xFFFFFF);
            super.render(context, mouseX, mouseY, delta);
        }

        @Override
        public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE || keyCode == GLFW.GLFW_KEY_RIGHT_SHIFT) {
                close();
                return true;
            }
            return super.keyPressed(keyCode, scanCode, modifiers);
        }

        @Override
        public void close() {
            client.setScreen(null);
        }

        @Override
        public boolean shouldPause() {
            return false;
        }
    }
}
