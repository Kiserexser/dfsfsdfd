package com.example.speed;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.annotations.Expose;
import net.fabricmc.api.ModInitializer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.awt.*;
import java.io.*;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class SpeedMod implements ModInitializer {
    private static final MinecraftClient mc = MinecraftClient.getInstance();
    private static boolean lastRShift = false;
    private static final Path CONFIG_PATH = Paths.get("config/killaura_toggle.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    // Состояние киллауры
    private static boolean killauraEnabled = false;

    private static void save() {
        try { Files.writeString(CONFIG_PATH, GSON.toJson(new State(killauraEnabled))); }
        catch (IOException e) { e.printStackTrace(); }
    }
    private static void load() {
        if (!Files.exists(CONFIG_PATH)) return;
        try {
            State s = GSON.fromJson(Files.readString(CONFIG_PATH), State.class);
            if (s != null) killauraEnabled = s.enabled;
        } catch (IOException e) { e.printStackTrace(); }
    }
    private static class State { @Expose boolean enabled; State(boolean e) { enabled = e; } }

    @Override
    public void onInitialize() {
        load();
        // Поток для отслеживания правого Shift
        new Thread(() -> {
            while (true) {
                try { Thread.sleep(50); } catch (InterruptedException e) { break; }
                if (mc.player != null && mc.currentScreen == null) {
                    long window = mc.getWindow().getHandle();
                    boolean rShift = GLFW.glfwGetKey(window, GLFW.GLFW_KEY_RIGHT_SHIFT) == GLFW.GLFW_PRESS;
                    if (rShift && !lastRShift) {
                        mc.execute(() -> mc.setScreen(new SimpleGUI()));
                        try { Thread.sleep(200); } catch (InterruptedException ignored) {}
                    }
                    lastRShift = rShift;
                }
            }
        }).start();
    }

    // Простое меню с одной кнопкой KillAura
    static class SimpleGUI extends Screen {
        private static final int WIN_W = 200;
        private static final int WIN_H = 100;
        private int winX, winY;
        private Font font;

        protected SimpleGUI() {
            super(Text.literal("KillAura Toggle"));
            try { font = new Font("Segoe UI", Font.PLAIN, 16); }
            catch (Exception e) { font = new Font("Dialog", Font.PLAIN, 16); }
        }

        @Override
        protected void init() {
            super.init();
            winX = (width - WIN_W) / 2;
            winY = (height - WIN_H) / 2;
        }

        @Override
        public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
            // Белый фон окна
            drawRoundedRect(ctx, winX, winY, WIN_W, WIN_H, 10, 0xFFFFFFFF);
            Graphics2D g = getGraphics2D(ctx);
            if (g == null) return;
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setFont(font);
            g.setColor(Color.BLACK);
            g.drawString("KillAura", winX + 20, winY + 35);
            // Кружок-переключатель
            int circleX = winX + WIN_W - 40;
            int circleY = winY + 25;
            int radius = 12;
            boolean hover = mouseX >= circleX - radius && mouseX <= circleX + radius && mouseY >= circleY - radius && mouseY <= circleY + radius;
            g.setColor(killauraEnabled ? new Color(0x55FF55) : new Color(0xFF5555));
            g.fillOval(circleX - radius, circleY - radius, radius*2, radius*2);
            g.setColor(Color.BLACK);
            g.drawOval(circleX - radius, circleY - radius, radius*2, radius*2);
            // Обводка при наведении
            if (hover) {
                g.setColor(new Color(0x000000, true));
                g.drawOval(circleX - radius - 1, circleY - radius - 1, radius*2+2, radius*2+2);
            }
            super.render(ctx, mouseX, mouseY, delta);
        }

        @Override
        public boolean mouseClicked(double mx, double my, int button) {
            if (button == 0) {
                int circleX = winX + WIN_W - 40;
                int circleY = winY + 25;
                int radius = 12;
                if (mx >= circleX - radius && mx <= circleX + radius && my >= circleY - radius && my <= circleY + radius) {
                    killauraEnabled = !killauraEnabled;
                    save();
                    return true;
                }
            }
            return super.mouseClicked(mx, my, button);
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
        public void close() { client.setScreen(null); }
        @Override
        public boolean shouldPause() { return false; }

        private void drawRoundedRect(DrawContext ctx, int x, int y, int w, int h, int r, int color) {
            ctx.fill(x + r, y, x + w - r, y + h, color);
            ctx.fill(x, y + r, x + w, y + h - r, color);
            for (int i = -r; i <= r; i++) for (int j = -r; j <= r; j++) if (i*i + j*j <= r*r) ctx.fill(x + r + i, y + r + j, x + r + i + 1, y + r + j + 1, color);
            for (int i = -r; i <= r; i++) for (int j = -r; j <= r; j++) if (i*i + j*j <= r*r) ctx.fill(x + w - r + i, y + r + j, x + w - r + i + 1, y + r + j + 1, color);
            for (int i = -r; i <= r; i++) for (int j = -r; j <= r; j++) if (i*i + j*j <= r*r) ctx.fill(x + r + i, y + h - r + j, x + r + i + 1, y + h - r + j + 1, color);
            for (int i = -r; i <= r; i++) for (int j = -r; j <= r; j++) if (i*i + j*j <= r*r) ctx.fill(x + w - r + i, y + h - r + j, x + w - r + i + 1, y + h - r + j + 1, color);
        }
        private Graphics2D getGraphics2D(DrawContext ctx) {
            try { Field f = DrawContext.class.getDeclaredField("graphics"); f.setAccessible(true); return (Graphics2D) f.get(ctx); }
            catch (Exception e) { return null; }
        }
    }
}
