package com.example.speed; // или ваш пакет

import net.fabricmc.api.ModInitializer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class ConsoleScreen extends Screen {
    private static final int MIN_WINDOW_WIDTH = 300;
    private static final int MIN_WINDOW_HEIGHT = 150;
    private static final int PADDING = 10;
    private static final int LINE_HEIGHT = 12; // для стандартного шрифта
    private static final int RESIZE_AREA_SIZE = 20;
    private static final long UPDATE_INTERVAL = 1000;

    private int scrollOffset = 0;
    private final List<String> logs = new ArrayList<>();
    private float windowX, windowY, windowWidth = 400, windowHeight = 300;
    private boolean dragging = false;
    private float dragStartX, dragStartY;
    private boolean resizing = false;
    private float resizeStartX, resizeStartY, initialWidth, initialHeight;
    private long lastUpdateTime = 0;

    public ConsoleScreen() {
        super(Text.literal("Console"));
        centerWindow();
        loadLogsFromFile();
    }

    private void centerWindow() {
        MinecraftClient client = MinecraftClient.getInstance();
        windowX = (client.getWindow().getScaledWidth() - windowWidth) / 2;
        windowY = (client.getWindow().getScaledHeight() - windowHeight) / 2;
    }

    private void loadLogsFromFile() {
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
            logs.add("Error reading log file: " + e.getMessage());
        }
    }

    private void updateLogs() {
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastUpdateTime >= UPDATE_INTERVAL) {
            File logFile = new File(MinecraftClient.getInstance().runDirectory, "logs/latest.log");
            if (logFile.exists()) {
                try (BufferedReader reader = new BufferedReader(new FileReader(logFile))) {
                    List<String> newLogs = new ArrayList<>();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        newLogs.add(line);
                    }
                    int existingSize = logs.size();
                    if (newLogs.size() > existingSize) {
                        logs.addAll(newLogs.subList(existingSize, newLogs.size()));
                    }
                } catch (IOException e) {
                    logs.add("Error updating log file: " + e.getMessage());
                }
            }
            lastUpdateTime = currentTime;
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        // Ограничиваем окно в пределах экрана
        MinecraftClient client = MinecraftClient.getInstance();
        int screenWidth = client.getWindow().getScaledWidth();
        int screenHeight = client.getWindow().getScaledHeight();
        windowWidth = Math.max(MIN_WINDOW_WIDTH, windowWidth);
        windowHeight = Math.max(MIN_WINDOW_HEIGHT, windowHeight);
        windowX = Math.max(0, Math.min(windowX, screenWidth - windowWidth));
        windowY = Math.max(0, Math.min(windowY, screenHeight - windowHeight));

        // Рисуем фон окна (скруглённый прямоугольник)
        drawRoundedRect(context, (int) windowX, (int) windowY, (int) windowWidth, (int) windowHeight, 8, 0xEE1E1E1E);
        // Полоска заголовка
        context.fill((int) windowX, (int) windowY + 15, (int) (windowX + windowWidth), (int) windowY + 17, 0xFF050505);

        // Заголовок и кнопка закрытия
        context.drawText(client.textRenderer, "Console Debug", (int) windowX + 20, (int) windowY + 5, 0xFFFFFF, false);
        context.drawText(client.textRenderer, "x", (int) (windowX + windowWidth - 12), (int) windowY + 4, 0xFFFFFF, false);

        // Обновляем логи
        updateLogs();

        // Обрезка области для списка
        int listStartX = (int) windowX + PADDING;
        int listStartY = (int) windowY + PADDING + 15; // отступ от заголовка
        int listWidth = (int) windowWidth - 2 * PADDING;
        int listHeight = (int) windowHeight - 2 * PADDING - 15;

        context.enableScissor(listStartX, listStartY, listStartX + listWidth, listStartY + listHeight);

        int maxLines = listHeight / LINE_HEIGHT;
        int startIndex = Math.max(0, logs.size() - maxLines + scrollOffset);
        for (int i = startIndex; i < logs.size() && i < startIndex + maxLines; i++) {
            String log = logs.get(i);
            int y = listStartY + (i - startIndex) * LINE_HEIGHT;
            context.drawText(client.textRenderer, log, listStartX, y, 0xAAAAAA, false);
        }
        context.disableScissor();

        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            // Проверка на зону изменения размера
            if (mouseX >= windowX + windowWidth - RESIZE_AREA_SIZE && mouseX <= windowX + windowWidth &&
                mouseY >= windowY + windowHeight - RESIZE_AREA_SIZE && mouseY <= windowY + windowHeight) {
                resizing = true;
                resizeStartX = (float) mouseX;
                resizeStartY = (float) mouseY;
                initialWidth = windowWidth;
                initialHeight = windowHeight;
                return true;
            }
            // Проверка на заголовок (перетаскивание)
            if (mouseY >= windowY && mouseY <= windowY + 15 && mouseX >= windowX && mouseX <= windowX + windowWidth) {
                dragging = true;
                dragStartX = (float) (mouseX - windowX);
                dragStartY = (float) (mouseY - windowY);
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            dragging = false;
            resizing = false;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (dragging && button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            windowX = (float) (mouseX - dragStartX);
            windowY = (float) (mouseY - dragStartY);
            return true;
        }
        if (resizing && button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            float newWidth = Math.max(MIN_WINDOW_WIDTH, initialWidth + ((float) mouseX - resizeStartX));
            float newHeight = Math.max(MIN_WINDOW_HEIGHT, initialHeight + ((float) mouseY - resizeStartY));
            windowWidth = newWidth;
            windowHeight = newHeight;
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (mouseX >= windowX && mouseX <= windowX + windowWidth && mouseY >= windowY && mouseY <= windowY + windowHeight) {
            int maxLines = (int) ((windowHeight - 2 * PADDING - 15) / LINE_HEIGHT);
            int maxScrollOffset = Math.max(0, logs.size() - maxLines);
            scrollOffset -= (int) verticalAmount;
            scrollOffset = Math.max(0, Math.min(scrollOffset, maxScrollOffset));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public void resize(MinecraftClient client, int width, int height) {
        super.resize(client, width, height);
        centerWindow();
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            close();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void drawRoundedRect(DrawContext context, int x, int y, int w, int h, int r, int color) {
        context.fill(x + r, y, x + w - r, y + h, color);
        context.fill(x, y + r, x + w, y + h - r, color);
        drawCircle(context, x + r, y + r, r, color);
        drawCircle(context, x + w - r, y + r, r, color);
        drawCircle(context, x + r, y + h - r, r, color);
        drawCircle(context, x + w - r, y + h - r, r, color);
    }

    private void drawCircle(DrawContext context, int cx, int cy, int r, int color) {
        for (int i = -r; i <= r; i++) {
            for (int j = -r; j <= r; j++) {
                if (i * i + j * j <= r * r) {
                    context.fill(cx + i, cy + j, cx + i + 1, cy + j + 1, color);
                }
            }
        }
    }
}
