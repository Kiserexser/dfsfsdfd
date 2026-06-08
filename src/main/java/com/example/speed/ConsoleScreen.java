package com.example.speed;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class ConsoleScreen extends Screen {
    private static final int MIN_W = 300;
    private static final int MIN_H = 150;
    private static final int PADDING = 10;
    private static final int LINE_H = 12;
    private static final int RESIZE_AREA = 15;
    private static final long UPDATE_MS = 1000;

    private int scrollOffset = 0;
    private final List<String> logs = new ArrayList<>();
    private float winX, winY, winW = 500, winH = 300;
    private boolean dragging = false, resizing = false;
    private float dragStartX, dragStartY, resizeStartX, resizeStartY, initialW, initialH;
    private long lastUpdate = 0;

    public ConsoleScreen() {
        super(Text.literal("Console"));
        centerWindow();
        loadLogs();
    }

    private void centerWindow() {
        MinecraftClient mc = MinecraftClient.getInstance();
        winX = (mc.getWindow().getScaledWidth() - winW) / 2;
        winY = (mc.getWindow().getScaledHeight() - winH) / 2;
    }

    private void loadLogs() {
        File logFile = new File(MinecraftClient.getInstance().runDirectory, "logs/latest.log");
        if (!logFile.exists()) {
            logs.add("Log file not found: " + logFile.getAbsolutePath());
            return;
        }
        try (BufferedReader reader = new BufferedReader(new FileReader(logFile))) {
            String line;
            while ((line = reader.readLine()) != null) {
                logs.add(line);
            }
        } catch (IOException e) {
            logs.add("Error reading log: " + e.getMessage());
        }
    }

    private void updateLogs() {
        long now = System.currentTimeMillis();
        if (now - lastUpdate < UPDATE_MS) return;
        lastUpdate = now;
        File logFile = new File(MinecraftClient.getInstance().runDirectory, "logs/latest.log");
        if (!logFile.exists()) return;
        try (BufferedReader reader = new BufferedReader(new FileReader(logFile))) {
            List<String> newLines = new ArrayList<>();
            String line;
            while ((line = reader.readLine()) != null) newLines.add(line);
            if (newLines.size() > logs.size()) {
                logs.addAll(newLines.subList(logs.size(), newLines.size()));
            }
        } catch (IOException ignored) {}
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        updateLogs();

        // Окно
        drawRoundedRect(ctx, (int) winX, (int) winY, (int) winW, (int) winH, 6, 0xEE1E1E1E);
        // Заголовок
        ctx.fill((int) winX, (int) winY, (int) (winX + winW), (int) (winY + 18), 0xDD111111);
        ctx.drawText(textRenderer, "Console Debug", (int) (winX + 30), (int) (winY + 5), 0xFFFFFF, false);
        ctx.drawText(textRenderer, "x", (int) (winX + winW - 15), (int) (winY + 5), 0xFFFFFF, false);

        // Область логов
        int listX = (int) (winX + PADDING);
        int listY = (int) (winY + 20);
        int listW = (int) (winW - 2 * PADDING);
        int listH = (int) (winH - 25 - PADDING);
        ctx.enableScissor(listX, listY, listX + listW, listY + listH);

        int maxLines = listH / LINE_H;
        int total = logs.size();
        int startIdx = Math.max(0, total - maxLines + scrollOffset);
        for (int i = startIdx; i < logs.size() && i < startIdx + maxLines; i++) {
            String line = logs.get(i);
            ctx.drawText(textRenderer, line, listX, listY + (i - startIdx) * LINE_H, 0xAAAAAA, false);
        }
        ctx.disableScissor();
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button == 0) {
            // Заголовок
            if (mx >= winX && mx <= winX + winW && my >= winY && my <= winY + 18) {
                dragging = true;
                dragStartX = (float) (mx - winX);
                dragStartY = (float) (my - winY);
                return true;
            }
            // Угол изменения размера
            if (mx >= winX + winW - RESIZE_AREA && my >= winY + winH - RESIZE_AREA) {
                resizing = true;
                resizeStartX = (float) mx;
                resizeStartY = (float) my;
                initialW = winW;
                initialH = winH;
                return true;
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (dragging && button == 0) {
            winX = (float) (mx - dragStartX);
            winY = (float) (my - dragStartY);
            return true;
        }
        if (resizing && button == 0) {
            float newW = Math.max(MIN_W, initialW + (float) mx - resizeStartX);
            float newH = Math.max(MIN_H, initialH + (float) my - resizeStartY);
            winW = newW;
            winH = newH;
            return true;
        }
        return super.mouseDragged(mx, my, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int button) {
        dragging = false;
        resizing = false;
        return super.mouseReleased(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double horiz, double vert) {
        if (mx >= winX && mx <= winX + winW && my >= winY && my <= winY + winH) {
            int maxLines = (int) ((winH - 40) / LINE_H);
            int maxOffset = Math.max(0, logs.size() - maxLines);
            scrollOffset = Math.max(-maxOffset, Math.min(0, scrollOffset - (int) vert));
            return true;
        }
        return super.mouseScrolled(mx, my, horiz, vert);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 256) { // ESC
            close();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void close() {
        MinecraftClient.getInstance().setScreen(null);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    // Рисование скруглённого прямоугольника
    private void drawRoundedRect(DrawContext ctx, int x, int y, int w, int h, int r, int color) {
        ctx.fill(x + r, y, x + w - r, y + h, color);
        ctx.fill(x, y + r, x + w, y + h - r, color);
        drawCircle(ctx, x + r, y + r, r, color);
        drawCircle(ctx, x + w - r, y + r, r, color);
        drawCircle(ctx, x + r, y + h - r, r, color);
        drawCircle(ctx, x + w - r, y + h - r, r, color);
    }

    private void drawCircle(DrawContext ctx, int cx, int cy, int r, int color) {
        for (int i = -r; i <= r; i++) {
            for (int j = -r; j <= r; j++) {
                if (i * i + j * j <= r * r) {
                    ctx.fill(cx + i, cy + j, cx + i + 1, cy + j + 1, color);
                }
            }
        }
    }
}
