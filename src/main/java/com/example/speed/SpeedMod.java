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
import java.util.*;
import java.util.List;

public class SpeedMod implements ModInitializer {
    private static final MinecraftClient mc = MinecraftClient.getInstance();
    private static boolean lastRShift = false;
    private static final Path MODULE_CONFIG = Paths.get("config/lunxes_modules.json");

    // ========== Модули (около 20) ==========
    public enum Category { COMBAT, MOVEMENT, RENDER, WORLD, PLAYER, MISC }
    public static class Module {
        public String name;
        public String description;
        public boolean enabled;
        public int keyCode;
        public Category category;
        public Module(String name, String description, Category category, int defaultKey) {
            this.name = name; this.description = description; this.category = category;
            this.enabled = false; this.keyCode = defaultKey;
        }
    }
    public static final List<Module> modules = new ArrayList<>();
    static {
        // COMBAT
        modules.add(new Module("KillAura", "Автоматическая атака противников", Category.COMBAT, GLFW.GLFW_KEY_R));
        modules.add(new Module("Aimbot", "Наведение на цель", Category.COMBAT, GLFW.GLFW_KEY_UNKNOWN));
        modules.add(new Module("TriggerBot", "Автострельба", Category.COMBAT, GLFW.GLFW_KEY_UNKNOWN));
        modules.add(new Module("Reach", "Увеличенная дистанция удара", Category.COMBAT, GLFW.GLFW_KEY_UNKNOWN));
        // MOVEMENT
        modules.add(new Module("Speed", "Ускорение передвижения", Category.MOVEMENT, GLFW.GLFW_KEY_UNKNOWN));
        modules.add(new Module("Fly", "Режим полёта", Category.MOVEMENT, GLFW.GLFW_KEY_UNKNOWN));
        modules.add(new Module("NoFall", "Без урона от падения", Category.MOVEMENT, GLFW.GLFW_KEY_UNKNOWN));
        modules.add(new Module("Sprint", "Автоматический спринт", Category.MOVEMENT, GLFW.GLFW_KEY_UNKNOWN));
        // RENDER
        modules.add(new Module("Fullbright", "Постоянная яркость", Category.RENDER, GLFW.GLFW_KEY_UNKNOWN));
        modules.add(new Module("ESP", "Подсветка игроков", Category.RENDER, GLFW.GLFW_KEY_UNKNOWN));
        modules.add(new Module("Chams", "Цветные модели", Category.RENDER, GLFW.GLFW_KEY_UNKNOWN));
        modules.add(new Module("Nametags", "Улучшенные таблички", Category.RENDER, GLFW.GLFW_KEY_UNKNOWN));
        // WORLD
        modules.add(new Module("Scaffold", "Автоматическая стройка", Category.WORLD, GLFW.GLFW_KEY_UNKNOWN));
        modules.add(new Module("ChestStealer", "Воровство из сундуков", Category.WORLD, GLFW.GLFW_KEY_UNKNOWN));
        modules.add(new Module("FastPlace", "Быстрая установка блоков", Category.WORLD, GLFW.GLFW_KEY_UNKNOWN));
        // PLAYER
        modules.add(new Module("AutoArmor", "Автоматическая броня", Category.PLAYER, GLFW.GLFW_KEY_UNKNOWN));
        modules.add(new Module("AutoGap", "Авто-золотые яблоки", Category.PLAYER, GLFW.GLFW_KEY_UNKNOWN));
        modules.add(new Module("ChestStealer", "Авто-пополнение", Category.PLAYER, GLFW.GLFW_KEY_UNKNOWN));
        // MISC
        modules.add(new Module("NoSlow", "Без замедления", Category.MISC, GLFW.GLFW_KEY_UNKNOWN));
        modules.add(new Module("AntiBot", "Игнорирование ботов", Category.MISC, GLFW.GLFW_KEY_UNKNOWN));
        modules.add(new Module("FreeCam", "Свободная камера", Category.MISC, GLFW.GLFW_KEY_UNKNOWN));
    }

    // Конфиг
    private static class ModuleConfig { @Expose Map<String, Boolean> enabled = new HashMap<>(); @Expose Map<String, Integer> keyBind = new HashMap<>(); }
    private static final Gson GSON = new GsonBuilder().excludeFieldsWithoutExposeAnnotation().setPrettyPrinting().create();
    private static void saveModules() {
        ModuleConfig cfg = new ModuleConfig();
        for (Module m : modules) { cfg.enabled.put(m.name, m.enabled); cfg.keyBind.put(m.name, m.keyCode); }
        try { Files.writeString(MODULE_CONFIG, GSON.toJson(cfg)); } catch (IOException e) { e.printStackTrace(); }
    }
    private static void loadModules() {
        if (!Files.exists(MODULE_CONFIG)) return;
        try {
            ModuleConfig cfg = GSON.fromJson(Files.readString(MODULE_CONFIG), ModuleConfig.class);
            if (cfg.enabled != null) for (Module m : modules) m.enabled = cfg.enabled.getOrDefault(m.name, false);
            if (cfg.keyBind != null) for (Module m : modules) m.keyCode = cfg.keyBind.getOrDefault(m.name, GLFW.GLFW_KEY_UNKNOWN);
        } catch (IOException e) { e.printStackTrace(); }
    }

    @Override
    public void onInitialize() {
        loadModules();
        // Открытие GUI по правому Shift
        new Thread(() -> {
            while (true) {
                try { Thread.sleep(50); } catch (InterruptedException e) { break; }
                if (mc.player != null && mc.currentScreen == null) {
                    long window = mc.getWindow().getHandle();
                    boolean rShift = GLFW.glfwGetKey(window, GLFW.GLFW_KEY_RIGHT_SHIFT) == GLFW.GLFW_PRESS;
                    if (rShift && !lastRShift) {
                        mc.execute(() -> mc.setScreen(new ClickGUI()));
                        try { Thread.sleep(200); } catch (InterruptedException ignored) {}
                    }
                    lastRShift = rShift;
                }
            }
        }).start();
        // Обработка биндов
        new Thread(() -> {
            while (true) {
                try { Thread.sleep(50); } catch (InterruptedException e) { break; }
                if (mc.player == null || mc.currentScreen != null) continue;
                for (Module m : modules) {
                    if (m.keyCode != GLFW.GLFW_KEY_UNKNOWN && GLFW.glfwGetKey(mc.getWindow().getHandle(), m.keyCode) == GLFW.GLFW_PRESS) {
                        m.enabled = !m.enabled;
                        saveModules();
                        try { Thread.sleep(200); } catch (InterruptedException ignored) {}
                    }
                }
            }
        }).start();
    }

    // ========== Белое меню с неон-акцентами ==========
    public static class ClickGUI extends Screen {
        private Category current = Category.COMBAT;
        private int scrollOffset = 0;
        private String searchText = "";
        private boolean typing = false;
        private Module bindingModule = null;
        private static final int WIN_W = 520;
        private static final int WIN_H = 380;
        private int winX, winY;
        private Font titleFont, catFont, modFont, descFont;

        public ClickGUI() {
            super(Text.literal("Lunxes Client"));
            try {
                titleFont = new Font("Segoe UI", Font.BOLD, 22);
                catFont = new Font("Segoe UI", Font.PLAIN, 14);
                modFont = new Font("Segoe UI", Font.PLAIN, 13);
                descFont = new Font("Segoe UI", Font.PLAIN, 11);
            } catch (Exception e) { titleFont = modFont = descFont = catFont = new Font("Dialog", Font.PLAIN, 12); }
        }

        @Override
        protected void init() {
            super.init();
            winX = (width - WIN_W) / 2;
            winY = (height - WIN_H) / 2;
        }

        @Override
        public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
            // Белый фон окна (немного светлее белого)
            drawRoundedRect(ctx, winX, winY, WIN_W, WIN_H, 12, 0xFFF5F5F5);
            Graphics2D g = getGraphics2D(ctx);
            if (g == null) return;
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

            // Заголовок
            g.setFont(titleFont);
            g.setColor(new Color(0x222222));
            drawCenteredString(g, "Lunxes Client", winX + WIN_W/2, winY + 28);

            // Категории (подчеркнутые, без фона)
            int catStartX = winX + 20;
            int catW = 70;
            int catH = 20;
            int spacing = (WIN_W - 40 - 6 * catW) / 5;
            if (spacing < 15) spacing = 15;
            int catY = winY + 55;
            g.setFont(catFont);
            Category[] cats = Category.values();
            for (int i = 0; i < cats.length; i++) {
                int x = catStartX + i * (catW + spacing);
                boolean hover = mouseX >= x && mouseX <= x + catW && mouseY >= catY && mouseY <= catY + catH;
                g.setColor(current == cats[i] ? new Color(0x33AAFF) : (hover ? new Color(0x77AAFF) : new Color(0x666666)));
                drawCenteredString(g, cats[i].name(), x + catW/2, catY + 4);
                if (current == cats[i]) {
                    g.setColor(new Color(0x33AAFF));
                    g.fillRect(x + catW/2 - 15, catY + catH, 30, 2);
                }
            }

            // Поиск
            int searchX = winX + WIN_W - 130;
            int searchY = winY + 55;
            int searchW = 110;
            int searchH = 20;
            g.setColor(new Color(0xE0E0E0));
            g.fillRoundRect(searchX, searchY, searchW, searchH, 8, 8);
            g.setColor(new Color(0x666666));
            g.drawString(searchText.isEmpty() ? "Search..." : searchText + (typing ? "_" : ""), searchX + 8, searchY + 14);

            // Список модулей (две колонки)
            int listX = winX + 20;
            int listY = winY + 90;
            int listW = WIN_W - 40;
            int listH = WIN_H - 115;
            g.setClip(listX, listY, listW, listH);
            List<Module> mods = modules.stream()
                    .filter(m -> m.category == current)
                    .filter(m -> searchText.isEmpty() || m.name.toLowerCase().contains(searchText.toLowerCase()))
                    .toList();
            int itemH = 36;
            int columns = 2;
            int colW = (listW - 15) / columns;
            int visibleRows = listH / itemH;
            int maxRows = (int) Math.ceil(mods.size() / (double) columns);
            int maxScroll = Math.max(0, maxRows - visibleRows);
            scrollOffset = Math.max(0, Math.min(scrollOffset, maxScroll));
            for (int idx = 0; idx < mods.size(); idx++) {
                Module m = mods.get(idx);
                int col = idx % columns;
                int row = idx / columns;
                if (row < scrollOffset || row >= scrollOffset + visibleRows) continue;
                int x = listX + col * (colW + 5);
                int y = listY + (row - scrollOffset) * itemH;
                boolean hover = mouseX >= x && mouseX <= x + colW && mouseY >= y && mouseY <= y + itemH;
                if (hover) {
                    g.setColor(new Color(0xCCCCCC, true));
                    g.fillRect(x, y, colW, itemH);
                }
                g.setFont(modFont);
                g.setColor(new Color(0x222222));
                g.drawString(m.name, x + 8, y + 12);
                g.setFont(descFont);
                g.setColor(new Color(0x888888));
                String desc = m.description.length() > 35 ? m.description.substring(0, 32) + "..." : m.description;
                g.drawString(desc, x + 8, y + 26);
                // ON/OFF с неон-эффектом
                String toggle = m.enabled ? "ON" : "OFF";
                int toggleX = x + colW - 38;
                g.setFont(modFont);
                if (m.enabled) {
                    g.setColor(new Color(0x33FF99)); // неоново-зелёный
                    g.drawString(toggle, toggleX, y + 16);
                } else {
                    g.setColor(new Color(0xFF6666));
                    g.drawString(toggle, toggleX, y + 16);
                }
                // Bind
                String bind = getKeyName(m.keyCode);
                int bindX = toggleX - 45;
                g.setColor(new Color(0x33AAFF));
                g.drawString("[" + bind + "]", bindX, y + 16);
            }
            g.setClip(null);
            if (bindingModule != null) {
                g.setFont(catFont);
                g.setColor(new Color(0xFFAA33));
                drawCenteredString(g, "Press any key for " + bindingModule.name + "...", winX + WIN_W/2, winY + WIN_H - 18);
            }
        }

        private String getKeyName(int key) {
            if (key == GLFW.GLFW_KEY_UNKNOWN) return "None";
            String name = GLFW.glfwGetKeyName(key, 0);
            return name != null ? name.toUpperCase() : "Key" + key;
        }

        @Override
        public boolean mouseClicked(double mx, double my, int button) {
            if (button == 0) {
                // Категории
                int catStartX = winX + 20;
                int catW = 70;
                int spacing = (WIN_W - 40 - 6 * catW) / 5;
                if (spacing < 15) spacing = 15;
                int catY = winY + 55;
                int catH = 20;
                Category[] cats = Category.values();
                for (int i = 0; i < cats.length; i++) {
                    int x = catStartX + i * (catW + spacing);
                    if (mx >= x && mx <= x + catW && my >= catY && my <= catY + catH) {
                        current = cats[i];
                        scrollOffset = 0;
                        return true;
                    }
                }
                // Поиск
                int searchX = winX + WIN_W - 130;
                int searchY = winY + 55;
                int searchW = 110;
                int searchH = 20;
                if (mx >= searchX && mx <= searchX + searchW && my >= searchY && my <= searchY + searchH) {
                    typing = true;
                    return true;
                } else typing = false;
                // Модули
                List<Module> mods = modules.stream()
                        .filter(m -> m.category == current)
                        .filter(m -> searchText.isEmpty() || m.name.toLowerCase().contains(searchText.toLowerCase()))
                        .toList();
                int columns = 2;
                int colW = (WIN_W - 55) / columns;
                int itemH = 36;
                int listX = winX + 20;
                int listY = winY + 90;
                for (int idx = 0; idx < mods.size(); idx++) {
                    Module m = mods.get(idx);
                    int col = idx % columns;
                    int row = idx / columns;
                    if (row < scrollOffset || row >= scrollOffset + (WIN_H - 115)/itemH) continue;
                    int x = listX + col * (colW + 5);
                    int y = listY + (row - scrollOffset) * itemH;
                    if (mx >= x && mx <= x + colW && my >= y && my <= y + itemH) {
                        int toggleX = x + colW - 38;
                        if (mx >= toggleX && mx <= toggleX + 30) {
                            m.enabled = !m.enabled;
                            saveModules();
                            return true;
                        }
                        int bindX = toggleX - 45;
                        if (mx >= bindX && mx <= bindX + 40) {
                            bindingModule = m;
                            return true;
                        }
                    }
                }
            }
            return super.mouseClicked(mx, my, button);
        }

        @Override
        public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
            if (bindingModule != null) {
                if (keyCode == GLFW.GLFW_KEY_DELETE) bindingModule.keyCode = GLFW.GLFW_KEY_UNKNOWN;
                else bindingModule.keyCode = keyCode;
                bindingModule = null;
                saveModules();
                return true;
            }
            if (typing) {
                if (keyCode == GLFW.GLFW_KEY_ESCAPE) typing = false;
                else if (keyCode == GLFW.GLFW_KEY_BACKSPACE) {
                    if (!searchText.isEmpty()) searchText = searchText.substring(0, searchText.length()-1);
                } else if (keyCode == GLFW.GLFW_KEY_ENTER) typing = false;
                else if (keyCode == GLFW.GLFW_KEY_V && (modifiers & GLFW.GLFW_MOD_CONTROL) != 0) {
                    String clip = GLFW.glfwGetClipboardString(mc.getWindow().getHandle());
                    if (clip != null) searchText += clip;
                }
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_ESCAPE || keyCode == GLFW.GLFW_KEY_RIGHT_SHIFT) {
                close();
                return true;
            }
            return super.keyPressed(keyCode, scanCode, modifiers);
        }

        @Override
        public boolean charTyped(char codePoint, int modifiers) {
            if (typing && (Character.isLetterOrDigit(codePoint) || codePoint == ' ' || codePoint == '_' || codePoint == '-')) {
                searchText += codePoint;
                return true;
            }
            return super.charTyped(codePoint, modifiers);
        }

        @Override
        public boolean mouseScrolled(double mx, double my, double horiz, double vert) {
            List<Module> mods = modules.stream()
                    .filter(m -> m.category == current)
                    .filter(m -> searchText.isEmpty() || m.name.toLowerCase().contains(searchText.toLowerCase()))
                    .toList();
            int columns = 2;
            int rows = (int) Math.ceil(mods.size() / (double) columns);
            int visibleRows = (WIN_H - 115) / 36;
            int maxScroll = Math.max(0, rows - visibleRows);
            scrollOffset = (int) Math.max(0, Math.min(scrollOffset - vert, maxScroll));
            return true;
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
            try { Field f = DrawContext.class.getDeclaredField("graphics"); f.setAccessible(true); return (Graphics2D) f.get(ctx); } catch (Exception e) { return null; }
        }

        private void drawCenteredString(Graphics2D g, String s, int x, int y) {
            FontMetrics fm = g.getFontMetrics();
            g.drawString(s, x - fm.stringWidth(s)/2, y + fm.getAscent());
        }
    }
}
