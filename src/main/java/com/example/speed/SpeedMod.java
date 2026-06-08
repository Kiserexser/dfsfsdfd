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
        private static final int WIDTH = 560;
        private static final int HEIGHT = 420;
        private int guiLeft, guiTop;

        private final List<String> categories = Arrays.asList("Combat", "Movement", "Visuals", "Player", "Misc");
        private final Map<String, List<ModuleEntry>> modules = new LinkedHashMap<>();

        protected NeokeepGUI() {
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
            guiLeft = (width - WIDTH) / 2;
            guiTop = (height - HEIGHT) / 2;

            searchBox = new TextFieldWidget(textRenderer, guiLeft + 10, guiTop + 35, 200, 18, Text.literal("Search..."));
            searchBox.setMaxLength(50);
            searchBox.setDrawsBackground(true);
            searchBox.setPlaceholder(Text.literal("Search..."));
            addSelectableChild(searchBox);
        }

        @Override
        public void render(DrawContext context, int mouseX, int mouseY, float delta) {
            // размытый фон (полупрозрачный тёмный слой на весь экран)
            context.fill(0, 0, width, height, 0xAA000000);

            // рисуем основное окно с закруглёнными углами (рисуем прямоугольник и скругляем углы)
            drawRoundedRect(context, guiLeft, guiTop, WIDTH, HEIGHT, 8, 0xEE1E1E1E);

            // заголовок
            context.drawCenteredTextWithShadow(textRenderer, Text.literal("Neokeep"), guiLeft + WIDTH / 2, guiTop + 8, 0xFFFFFF);

            // категории (горизонтальные кнопки)
            int catStartX = guiLeft + 10;
            int catY = guiTop + 30;
            int catSpacing = 75;
            for (int i = 0; i < categories.size(); i++) {
                int catX = catStartX + i * catSpacing;
                boolean hover = mouseX >= catX && mouseX <= catX + 65 && mouseY >= catY && mouseY <= catY + 20;
                int textColor = (selectedCategory == i) ? 0xFFFFFF : (hover ? 0xCCCCCC : 0x888888);
                // подчёркивание для выбранной категории
                if (selectedCategory == i) {
                    context.fill(catX, catY + 18, catX + textRenderer.getWidth(categories.get(i)) + 2, catY + 19, 0xFF69B4FF);
                }
                context.drawText(textRenderer, categories.get(i), catX, catY, textColor, false);
            }

            // поле поиска
            searchBox.render(context, mouseX, mouseY, delta);

            // список модулей
            int listX = guiLeft + 10;
            int listY = guiTop + 65;
            int listWidth = WIDTH - 20;
            int listHeight = HEIGHT - 75;
            // обрезаем по области окна (clipping)
            context.enableScissor(listX, listY, listX + listWidth, listY + listHeight);
            List<ModuleEntry> modList = modules.get(categories.get(selectedCategory));
            if (modList != null) {
                List<ModuleEntry> filtered = modList.stream()
                        .filter(m -> m.name.toLowerCase().contains(filter.toLowerCase()))
                        .collect(Collectors.toList());
                int moduleHeight = 42;
                int visibleCount = listHeight / moduleHeight;
                int maxScroll = Math.max(0, filtered.size() - visibleCount);
                scrollOffset = Math.max(0, Math.min(scrollOffset, maxScroll));
                for (int i = 0; i < visibleCount && scrollOffset + i < filtered.size(); i++) {
                    ModuleEntry entry = filtered.get(scrollOffset + i);
                    int y = listY + i * moduleHeight;
                    boolean hover = mouseX >= listX && mouseX <= listX + listWidth && mouseY >= y && mouseY <= y + moduleHeight;
                    if (hover) {
                        context.fill(listX, y, listX + listWidth, y + moduleHeight, 0x33FFFFFF);
                    }
                    context.drawText(textRenderer, entry.name, listX + 5, y + 6, 0xFFFFFF, false);
                    context.drawText(textRenderer, entry.description, listX + 5, y + 24, 0xAAAAAA, false);
                }
            }
            context.disableScissor();

            super.render(context, mouseX, mouseY, delta);
        }

        // метод для рисования прямоугольника с закруглёнными углами
        private void drawRoundedRect(DrawContext context, int x, int y, int w, int h, int radius, int color) {
            // рисуем основной прямоугольник
            context.fill(x + radius, y, x + w - radius, y + h, color);
            context.fill(x, y + radius, x + w, y + h - radius, color);
            // углы (круги) – для простоты можно не рисовать идеально, но добавим для красоты
            drawCircleCorner(context, x + radius, y + radius, radius, color, 1);
            drawCircleCorner(context, x + w - radius, y + radius, radius, color, 2);
            drawCircleCorner(context, x + radius, y + h - radius, radius, color, 3);
            drawCircleCorner(context, x + w - radius, y + h - radius, radius, color, 4);
        }

        private void drawCircleCorner(DrawContext context, int cx, int cy, int r, int color, int corner) {
            // грубое приближение: рисуем маленькие квадраты, но для простоты опустим, либо используем текстуру
            // можно просто не заморачиваться – скругления будут видны, но не идеальны
            // альтернатива: использовать стандартный метод fill с окружностями через пиксели
            for (int i = -r; i <= r; i++) {
                for (int j = -r; j <= r; j++) {
                    if (i*i + j*j <= r*r) {
                        int x = cx + i;
                        int y = cy + j;
                        if (x >= 0 && x < width && y >= 0 && y < height) {
                            context.fill(x, y, x+1, y+1, color);
                        }
                    }
                }
            }
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
                // клик по категориям
                int catStartX = guiLeft + 10;
                int catY = guiTop + 30;
                int catSpacing = 75;
                for (int i = 0; i < categories.size(); i++) {
                    int catX = catStartX + i * catSpacing;
                    if (mouseX >= catX && mouseX <= catX + 65 && mouseY >= catY && mouseY <= catY + 20) {
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
                scrollOffset = 0;
                return handled;
            }
            return super.keyPressed(keyCode, scanCode, modifiers);
        }

        @Override
        public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
            int delta = (int) Math.signum(verticalAmount);
            List<ModuleEntry> modList = modules.get(categories.get(selectedCategory));
            if (modList != null) {
                List<ModuleEntry> filtered = modList.stream()
                        .filter(m -> m.name.toLowerCase().contains(filter.toLowerCase()))
                        .collect(Collectors.toList());
                int visibleCount = (HEIGHT - 75) / 42;
                int maxScroll = Math.max(0, filtered.size() - visibleCount);
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
            ModuleEntry(String name, String description) {
                this.name = name;
                this.description = description;
            }
        }
    }
}
