package com.example.speed;

import net.fabricmc.api.ModInitializer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.*;
import java.util.stream.Collectors;

public class SpeedMod implements ModInitializer {
    private static final MinecraftClient mc = MinecraftClient.getInstance();
    private static boolean lastRShift = false;

    @Override
    public void onInitialize() {
        new Thread(() -> {
            while (true) {
                try { Thread.sleep(50); } catch (InterruptedException e) { break; }
                if (mc.player != null && mc.currentScreen == null) {
                    long window = mc.getWindow().getHandle();
                    boolean rShift = GLFW.glfwGetKey(window, GLFW.GLFW_KEY_RIGHT_SHIFT) == GLFW.GLFW_PRESS;
                    if (rShift && !lastRShift) {
                        mc.execute(() -> mc.setScreen(new NeokeepGUI()));
                        try { Thread.sleep(200); } catch (InterruptedException ignored) {}
                    }
                    lastRShift = rShift;
                }
            }
        }).start();
    }

    static class NeokeepGUI extends Screen {
        private int selectedCategory = 0;
        private int scrollOffset = 0;
        private TextFieldWidget searchBox;
        private String filter = "";

        private final List<String> categories = Arrays.asList("Combat", "Movement", "Visuals", "Player", "Misc");
        private final Map<String, List<ModuleEntry>> modules = new LinkedHashMap<>();

        public NeokeepGUI() {
            super(Text.literal("Neokeep"));
            initModules();
        }

        private void initModules() {
            modules.put("Combat", Arrays.asList(
                new ModuleEntry("MiddleClickFriend", "Добавляет игрока в френд лист при нажатии на кнопку мыши")
            ));
            modules.put("Movement", Arrays.asList(
                new ModuleEntry("NameProtect", "Позволяет скрывать информацию о себе и других игроках")
            ));
            modules.put("Visuals", Arrays.asList(
                new ModuleEntry("ChatHistory", "Не удаляет историю чата при перезаходе на сервер")
            ));
            modules.put("Player", Arrays.asList(
                new ModuleEntry("KitMessage", "Пишет \"Мне\" если кто-то написал \"Кому жить\"")
            ));
            modules.put("Misc", Arrays.asList(
                new ModuleEntry("FakePlayer", "Не удаляет историю чата при перезаходе на сервер")
            ));
        }

        @Override
        protected void init() {
            super.init();
            searchBox = new TextFieldWidget(textRenderer, width / 2 - 100, 20, 200, 18, Text.literal("Search..."));
            searchBox.setMaxLength(50);
            searchBox.setDrawsBackground(true);
            searchBox.setPlaceholder(Text.literal("Search..."));
            addSelectableChild(searchBox);
        }

        @Override
        public void render(DrawContext context, int mouseX, int mouseY, float delta) {
            // Полупрозрачный тёмный фон (имитация блюра)
            context.fill(0, 0, width, height, 0xCC000000);

            // Левый блок (категории)
            int leftWidth = 130;
            int startX = 20;
            int startY = 55;
            // Рамка левого блока
            context.fill(startX - 3, startY - 3, startX + leftWidth + 3, height - 15, 0xAA151515);
            context.fill(startX, startY, startX + leftWidth, height - 18, 0xEE1A1A1A);

            for (int i = 0; i < categories.size(); i++) {
                int y = startY + i * 28;
                boolean hover = mouseX >= startX && mouseX <= startX + leftWidth && mouseY >= y && mouseY <= y + 24;
                int bgColor;
                if (i == selectedCategory) bgColor = 0xFF3A6EA5;
                else if (hover) bgColor = 0xFF2C2C2C;
                else bgColor = 0x00FFFFFF; // прозрачный
                if (i == selectedCategory || hover) {
                    context.fill(startX, y, startX + leftWidth, y + 24, bgColor);
                }
                if (i == selectedCategory) {
                    context.fill(startX, y, startX + 4, y + 24, 0xFF69B4FF);
                }
                context.drawText(textRenderer, categories.get(i), startX + 10, y + 6, 0xFFFFFF, false);
            }

            // Правый блок (модули)
            int rightX = startX + leftWidth + 15;
            int rightWidth = width - rightX - 20;
            context.fill(rightX - 3, startY - 3, rightX + rightWidth + 3, height - 15, 0xAA151515);
            context.fill(rightX, startY, rightX + rightWidth, height - 18, 0xEE1A1A1A);

            // Фильтрация по поиску
            List<ModuleEntry> modList = modules.get(categories.get(selectedCategory));
            if (modList != null) {
                List<ModuleEntry> filtered = modList.stream()
                        .filter(m -> m.name.toLowerCase().contains(filter.toLowerCase()))
                        .collect(Collectors.toList());

                int moduleHeight = 46;
                int visibleCount = (height - startY - 25) / moduleHeight;
                int maxScroll = Math.max(0, filtered.size() - visibleCount);
                scrollOffset = Math.max(0, Math.min(scrollOffset, maxScroll));

                for (int i = 0; i < visibleCount && scrollOffset + i < filtered.size(); i++) {
                    ModuleEntry entry = filtered.get(scrollOffset + i);
                    int y = startY + i * moduleHeight;
                    boolean hover = mouseX >= rightX && mouseX <= rightX + rightWidth && mouseY >= y && mouseY <= y + moduleHeight;
                    if (hover) {
                        context.fill(rightX, y, rightX + rightWidth, y + moduleHeight, 0x552C2C2C);
                    }
                    // Название модуля (белый)
                    context.drawText(textRenderer, entry.name, rightX + 10, y + 8, 0xFFFFFF, false);
                    // Описание (светло-серый)
                    context.drawText(textRenderer, entry.description, rightX + 10, y + 26, 0xAAAAAA, false);
                }
            }

            // Заголовок "Neokeep"
            context.drawCenteredTextWithShadow(textRenderer, Text.literal("Neokeep"), width / 2, 8, 0xFFFFFF);
            // Поиск
            searchBox.render(context, mouseX, mouseY, delta);
            super.render(context, mouseX, mouseY, delta);
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            if (button == 0) {
                // Клик по категориям
                int leftWidth = 130;
                int startX = 20;
                int startY = 55;
                for (int i = 0; i < categories.size(); i++) {
                    int y = startY + i * 28;
                    if (mouseX >= startX && mouseX <= startX + leftWidth && mouseY >= y && mouseY <= y + 24) {
                        selectedCategory = i;
                        scrollOffset = 0;
                        return true;
                    }
                }
            }
            return super.mouseClicked(mouseX, mouseY, button);
        }

        @Override
        public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE || keyCode == GLFW.GLFW_KEY_RIGHT_SHIFT) {
                close();
                return true;
            }
            if (searchBox.isFocused()) {
                boolean handled = searchBox.keyPressed(keyCode, scanCode, modifiers);
                filter = searchBox.getText();
                return handled;
            }
            return super.keyPressed(keyCode, scanCode, modifiers);
        }

        @Override
        public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
            int delta = (int) Math.signum(verticalAmount);
            List<ModuleEntry> modList = modules.get(categories.get(selectedCategory));
            if (modList != null) {
                int visibleCount = (height - 80) / 46;
                int maxScroll = Math.max(0, modList.size() - visibleCount);
                scrollOffset = Math.max(0, Math.min(scrollOffset - delta, maxScroll));
            }
            return true;
        }

        @Override
        public void close() {
            client.setScreen(null);
        }

        private static class ModuleEntry {
            String name;
            String description;
            ModuleEntry(String name, String desc) {
                this.name = name;
                this.description = desc;
            }
        }
    }
}
