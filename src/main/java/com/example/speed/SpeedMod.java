package com.example.speed;

import net.fabricmc.api.ModInitializer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

public class SpeedMod implements ModInitializer {
    private static final MinecraftClient mc = MinecraftClient.getInstance();
    private static boolean lastRShift = false;
    private static final Path CONFIG_PATH = Paths.get("config/speedmod.json");
    private static Map<String, Boolean> modules = new HashMap<>();
    private static boolean killauraEnabled = false;

    static {
        loadConfig();
    }

    private static void loadConfig() {
        if (Files.exists(CONFIG_PATH)) {
            try (BufferedReader reader = Files.newBufferedReader(CONFIG_PATH)) {
                killauraEnabled = Boolean.parseBoolean(reader.readLine());
            } catch (IOException ignored) {}
        }
    }

    private static void saveConfig() {
        try (BufferedWriter writer = Files.newBufferedWriter(CONFIG_PATH)) {
            writer.write(String.valueOf(killauraEnabled));
        } catch (IOException ignored) {}
    }

    public static void toggleKillaura() {
        killauraEnabled = !killauraEnabled;
        saveConfig();
        if (killauraEnabled) {
            // Здесь будет реальная логика Killaura
            System.out.println("Killaura enabled");
        } else {
            System.out.println("Killaura disabled");
        }
    }

    @Override
    public void onInitialize() {
        new Thread(() -> {
            while (true) {
                try { Thread.sleep(50); } catch (InterruptedException e) { break; }
                if (mc.player != null && mc.currentScreen == null) {
                    long window = mc.getWindow().getHandle();
                    boolean rShift = GLFW.glfwGetKey(window, GLFW.GLFW_KEY_RIGHT_SHIFT) == GLFW.GLFW_PRESS;
                    if (rShift && !lastRShift) {
                        mc.execute(() -> mc.setScreen(new SimpleMenu()));
                        try { Thread.sleep(200); } catch (InterruptedException ignored) {}
                    }
                    lastRShift = rShift;
                }
            }
        }).start();
    }

    static class SimpleMenu extends Screen {
        private ButtonWidget killauraBtn;

        protected SimpleMenu() {
            super(Text.literal("SpeedMod Menu"));
        }

        @Override
        protected void init() {
            super.init();
            int centerX = width / 2;
            int y = height / 2 - 50;

            killauraBtn = ButtonWidget.builder(
                    Text.literal("Killaura: " + (killauraEnabled ? "ON" : "OFF")),
                    button -> {
                        toggleKillaura();
                        button.setMessage(Text.literal("Killaura: " + (killauraEnabled ? "ON" : "OFF")));
                    }
            ).dimensions(centerX - 75, y, 150, 20).build();
            addDrawableChild(killauraBtn);

            // Кнопка закрытия
            addDrawableChild(ButtonWidget.builder(Text.literal("Close"), button -> close())
                    .dimensions(centerX - 75, y + 30, 150, 20).build());
        }

        @Override
        public void render(DrawContext context, int mouseX, int mouseY, float delta) {
            // Белый фон
            context.fill(0, 0, width, height, 0xFFFFFFFF);
            // Тёмный текст заголовка
            context.drawCenteredTextWithShadow(textRenderer, Text.literal("SpeedMod Menu"), width / 2, height / 2 - 80, 0x000000);
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
