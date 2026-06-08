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
                        mc.execute(() -> mc.setScreen(new LinxesGUI()));
                        try { Thread.sleep(200); } catch (InterruptedException ignored) {}
                    }
                    lastRShift = rShift;
                }
            }
        }).start();
    }

    static class LinxesGUI extends Screen {
        private int selectedCategory = 0;
        private int scrollOffset = 0;
        private TextFieldWidget searchBox;
        private String filter = "";
        private static final int WIN_W = 360;
        private static final int WIN_H = 260;
        private int winX, winY;

        private final List<String> categories = Arrays.asList("Combat", "Movement", "Visuals", "Player", "Misc");
        private final Map<String, List<ModuleEntry>> modules = new LinkedHashMap<>();

        protected LinxesGUI() {
            super(Text.literal("Linxes"));
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
            winX = (width - WIN_W) / 2;
            winY = (height - WIN_H) / 2;
            searchBox = new TextFieldWidget(textRenderer, winX + 10, winY + 35, 150, 16, Text.literal("Search..."));
            searchBox.setMaxLength(50);
            searchBox.setDrawsBackground(true);
            searchBox.setPlaceholder(Text.literal("Search..."));
            addSelectableChild(searchBox);
        }

        @Override
        public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
            // Блюр убран – фон не затемняется, окно рисуется прямо на игровом экране

            // окно с закруглёнными углами
            fillRounded(ctx, winX, winY, WIN_W, WIN_H, 12, 0xEE1E1E1E);

            // заголовок "Linxes"
            ctx.drawCenteredTextWithShadow(textRenderer, Text.literal("Linxes"), winX + WIN_W / 2, winY + 8, 0xFFFFFF);

            // категории
            int catX = winX + 10;
            int catY = winY + 32;
            int catW = 56;
            int catH = 16;
            for (int i = 0; i < categories.size(); i++) {
                int x = catX + i * (catW + 4);
                boolean hover = mouseX >= x && mouseX <= x + catW && mouseY >= catY && mouseY <= catY + catH;
                int color = (selectedCategory == i) ? 0xFFFFFF : (hover ? 0xCCCCCC : 0x888888);
                ctx.drawText(textRenderer, categories.get(i), x, catY, color, false);
                if (selectedCategory == i) {
                    int w = textRenderer.getWidth(categories.get(i));
                    ctx.fill(x, catY + catH - 1, x + w, catY + catH, 0xFF69B4FF);
                }
            }

            // поиск
            searchBox.render(ctx, mouseX, mouseY, delta);

            // список модулей
            int listX = winX + 10;
            int listY = winY + 65;
            int listW = WIN_W - 20;
            int listH = WIN_H - 85;
            ctx.enableScissor(listX, listY, listX + listW, listY + listH);

            List<ModuleEntry> base = modules.get(categories.get(selectedCategory));
            if (base != null) {
                List<ModuleEntry> filtered = base.stream()
                        .filter(e -> e.name.toLowerCase().contains(filter.toLowerCase()))
                        .collect(Collectors.toList());
                int itemH = 36;
                int visible = listH / itemH;
                int maxOff = Math.max(0, filtered.size() - visible);
                scrollOffset = Math.max(0, Math.min(scrollOffset, maxOff));

                for (int i = 0; i < visible && scrollOffset + i < filtered.size(); i++) {
                    ModuleEntry entry = filtered.get(scrollOffset + i);
                    int y = listY + i * itemH;
                    boolean hover = mouseX >= listX && mouseX <= listX + listW && mouseY >= y && mouseY <= y + itemH;
                    if (hover) {
                        ctx.fill(listX, y, listX + listW, y + itemH, 0x33FFFFFF);
                    }
                    ctx.drawText(textRenderer, entry.name, listX + 6, y + 6, 0xFFFFFF, false);
                    ctx.drawText(textRenderer, entry.description, listX + 6, y + 22, 0xAAAAAA, false);
                }
            }
            ctx.disableScissor();

            // нижняя строка (можно изменить на что-то своё или оставить)
            ctx.drawCenteredTextWithShadow(textRenderer, Text.literal("Linxes Client"), winX + WIN_W / 2, winY + WIN_H - 12, 0xAAAAAA);

            super.render(ctx, mouseX, mouseY, delta);
        }

        private void fillRounded(DrawContext ctx, int x, int y, int w, int h, int r, int color) {
            ctx.fill(x + r, y, x + w - r, y + h, color);
            ctx.fill(x, y + r, x + w, y + h - r, color);
            fillCircle(ctx, x + r, y + r, r, color);
            fillCircle(ctx, x + w - r, y + r, r, color);
            fillCircle(ctx, x + r, y + h - r, r, color);
            fillCircle(ctx, x + w - r, y + h - r, r, color);
        }

        private void fillCircle(DrawContext ctx, int cx, int cy, int r, int color) {
            for (int i = -r; i <= r; i++) {
                for (int j = -r; j <= r; j++) {
                    if (i*i + j*j <= r*r) {
                        ctx.fill(cx + i, cy + j, cx + i + 1, cy + j + 1, color);
                    }
                }
            }
        }

        @Override
        public boolean mouseClicked(double mx, double my, int button) {
            if (button == 0) {
                int catX = winX + 10;
                int catY = winY + 32;
                int catW = 56;
                int catH = 16;
                for (int i = 0; i < categories.size(); i++) {
                    int x = catX + i * (catW + 4);
                    if (mx >= x && mx <= x + catW && my >= catY && my <= catY + catH) {
                        selectedCategory = i;
                        scrollOffset = 0;
                        return true;
                    }
                }
            }
            return super.mouseClicked(mx, my, button);
        }

        @Override
        public boolean keyPressed(int code, int scan, int mods) {
            if (code == GLFW.GLFW_KEY_ESCAPE || code == GLFW.GLFW_KEY_RIGHT_SHIFT) {
                close();
                return true;
            }
            if (searchBox.isFocused()) {
                boolean handled = searchBox.keyPressed(code, scan, mods);
                filter = searchBox.getText();
                scrollOffset = 0;
                return handled;
            }
            return super.keyPressed(code, scan, mods);
        }

        @Override
        public boolean mouseScrolled(double mx, double my, double horiz, double vert) {
            int delta = (int) Math.signum(vert);
            List<ModuleEntry> base = modules.get(categories.get(selectedCategory));
            if (base != null) {
                List<ModuleEntry> filtered = base.stream()
                        .filter(e -> e.name.toLowerCase().contains(filter.toLowerCase()))
                        .collect(Collectors.toList());
                int visible = (WIN_H - 85) / 36;
                int maxOff = Math.max(0, filtered.size() - visible);
                scrollOffset = Math.max(0, Math.min(scrollOffset - delta, maxOff));
            }
            return true;
        }

        @Override
        public void close() {
            client.setScreen(null);
        }

        private static class ModuleEntry {
            String name, description;
            ModuleEntry(String n, String d) { name = n; description = d; }
        }
    }
}
