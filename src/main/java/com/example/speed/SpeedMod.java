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
        // COMBAT (5)
        modules.add(new Module("KillAura", "Автоматически атакует врага", Category.COMBAT, GLFW.GLFW_KEY_R));
        modules.add(new Module("Aimbot", "Наводит прицел", Category.COMBAT, GLFW.GLFW_KEY_UNKNOWN));
        modules.add(new Module("TriggerBot", "Стреляет при наведении", Category.COMBAT, GLFW.GLFW_KEY_UNKNOWN));
        modules.add(new Module("Reach", "Увеличивает дистанцию удара", Category.COMBAT, GLFW.GLFW_KEY_UNKNOWN));
        modules.add(new Module("Velocity", "Уменьшает отдачу", Category.COMBAT, GLFW.GLFW_KEY_UNKNOWN));
        // MOVEMENT (5)
        modules.add(new Module("Speed", "Ускоряет бег", Category.MOVEMENT, GLFW.GLFW_KEY_UNKNOWN));
        modules.add(new Module("Fly", "Позволяет летать", Category.MOVEMENT, GLFW.GLFW_KEY_UNKNOWN));
        modules.add(new Module("NoFall", "Нет урона от падения", Category.MOVEMENT, GLFW.GLFW_KEY_UNKNOWN));
        modules.add(new Module("Sprint", "Автоспринт", Category.MOVEMENT, GLFW.GLFW_KEY_UNKNOWN));
        modules.add(new Module("Step", "Забирается на блоки", Category.MOVEMENT, GLFW.GLFW_KEY_UNKNOWN));
        // RENDER (5)
        modules.add(new Module("ESP", "Подсветка игроков", Category.RENDER, GLFW.GLFW_KEY_UNKNOWN));
        modules.add(new Module("Chams", "Цветные модели", Category.RENDER, GLFW.GLFW_KEY_UNKNOWN));
        modules.add(new Module("Fullbright", "Постоянная яркость", Category.RENDER, GLFW.GLFW_KEY_UNKNOWN));
        modules.add(new Module("Nametags", "Улучшенные таблички", Category.RENDER, GLFW.GLFW_KEY_UNKNOWN));
        modules.add(new Module("Tracers", "Линии к игрокам", Category.RENDER, GLFW.GLFW_KEY_UNKNOWN));
        // WORLD (5)
        modules.add(new Module("ChestStealer", "Ворует из сундуков", Category.WORLD, GLFW.GLFW_KEY_UNKNOWN));
        modules.add(new Module("Scaffold", "Ставит блоки под ноги", Category.WORLD, GLFW.GLFW_KEY_UNKNOWN));
        modules.add(new Module("FastPlace", "Быстрая установка блоков", Category.WORLD, GLFW.GLFW_KEY_UNKNOWN));
        modules.add(new Module("AutoTool", "Автовыбор инструмента", Category.WORLD, GLFW.GLFW_KEY_UNKNOWN));
        modules.add(new Module("NoSlow", "Без замедления", Category.WORLD, GLFW.GLFW_KEY_UNKNOWN));
        // PLAYER (5)
        modules.add(new Module("AutoArmor", "Автоматическая броня", Category.PLAYER, GLFW.GLFW_KEY_UNKNOWN));
        modules.add(new Module("AutoGap", "Авто-золотые яблоки", Category.PLAYER, GLFW.GLFW_KEY_UNKNOWN));
        modules.add(new Module("AutoRespawn", "Мгновенное возрождение", Category.PLAYER, GLFW.GLFW_KEY_UNKNOWN));
        modules.add(new Module("InvCleaner", "Чистка инвентаря", Category.PLAYER, GLFW.GLFW_KEY_UNKNOWN));
        modules.add(new Module("AntiHunger", "Замедление голода", Category.PLAYER, GLFW.GLFW_KEY_UNKNOWN));
        // MISC (5)
        modules.add(new Module("MiddleClickFriend", "Друг по средней кнопке", Category.MISC, GLFW.GLFW_KEY_UNKNOWN));
        modules.add(new Module("FreeCam", "Свободная камера", Category.MISC, GLFW.GLFW_KEY_UNKNOWN));
        modules.add(new Module("InventoryMove", "Движение в инвентаре", Category.MISC, GLFW.GLFW_KEY_UNKNOWN));
        modules.add(new Module("HitSound", "Звук удара", Category.MISC, GLFW.GLFW_KEY_UNKNOWN));
        modules.add(new Module("DiscordRPC", "Дискорд присутствие", Category.MISC, GLFW.GLFW_KEY_UNKNOWN));
    }

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
        new Thread(() -> {
            while (true) {
                try { Thread.sleep(50); } catch (InterruptedException e) { break; }
                if (mc.player != null && mc.currentScreen == null) {
                    long window = mc.getWindow().getHandle();
                    boolean rShift = GLFW.glfwGetKey(window, GLFW.GLFW_KEY_RIGHT_SHIFT) == GLFW.GLFW_PRESS;
                    if (rShift && !lastRShift) {
                        mc.execute(() -> mc.setScreen(new WhiteClickGUI()));
                        try { Thread.sleep(200); } catch (InterruptedException ignored) {}
                    }
                    lastRShift = rShift;
                }
            }
        }).start();
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

    // ========== БЕЛОЕ КЛИК-МЕНЮ С НЕОНОВЫМИ АКЦЕНТАМИ ==========
    public static class WhiteClickGUI extends Screen {
        private Category current = Category.COMBAT;
        private int scrollOffset = 0;
        private String searchText = "";
        private boolean typing = false;
        private Module bindingModule = null;
        private static final int WIN_W = 480;
        private static final int WIN_H = 340;
        private int winX, winY;
        private Font titleFont, catFont, modFont, descFont;

        public WhiteClickGUI() {
            super(Text.literal("Lunxes Client"));
            try {
                titleFont = new Font("Segoe UI", Font.BOLD, 20);
                catFont = new Font("Segoe UI", Font.BOLD, 14);
                modFont = new Font("Segoe UI", Font.PLAIN, 12);
                descFont = new Font("Segoe UI", Font.PLAIN, 10);
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
            // Белый фон окна (слегка с оттенком)
            drawRoundedRect(ctx, winX, winY, WIN_W, WIN_H, 12, 0xFFF5F5F5);
            Graphics2D g = getGraphics2D(ctx);
            if (g == null) return;
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

            // Заголовок "Lunxes Client" (тёмно-серый текст)
            g.setFont(titleFont);
            g.setColor(new Color(0x333333));
            drawCenteredString(g, "Lunxes Client", winX + WIN_W/2, winY + 28);

            // Категории (слева) – фон почти белый, текст тёмно-серый, акцент неоново-голубой
            int catX = winX + 15;
            int catY = winY + 55;
            int catW = 70;
            int catH = 24;
            g.setFont(catFont);
            for (Category cat : Category.values()) {
                boolean hover = mouseX >= catX && mouseX <= catX + catW && mouseY >= catY && mouseY <= catY + catH;
                Color bgColor;
                if (current == cat) {
                    bgColor = new Color(0x88CCFF); // неоново-голубой фон
                    g.setColor(Color.WHITE);
                } else {
                    bgColor = new Color(0xEEEEEE);
                    g.setColor(new Color(0x444444));
                }
                g.setColor(bgColor);
                g.fillRoundRect(catX, catY, catW, catH, 6, 6);
                g.setColor(current == cat ? Color.WHITE : new Color(0x333333));
                drawCenteredString(g, cat.name(), catX + catW/2, catY + 7);
                catY += catH + 6;
            }

            // Поиск (светло-серый фон, текст тёмный)
            int searchX = winX + 110;
            int searchY = winY + 55;
            int searchW = WIN_W - 125;
            int searchH = 20;
            g.setColor(new Color(0xE0E0E0));
            g.fillRoundRect(searchX, searchY, searchW, searchH, 6, 6);
            g.setColor(new Color(0x666666));
            g.drawString(searchText.isEmpty() ? "Search..." : searchText + (typing ? "_" : ""), searchX + 6, searchY + 14);

            // Список модулей (справа) – белый фон, тёмный текст, неоновая подсветка при наведении
            int listX = winX + 110;
            int listY = winY + 85;
            int listW = WIN_W - 125;
            int listH = WIN_H - 105;
            g.setClip(listX, listY, listW, listH);

            List<Module> mods = modules.stream()
                    .filter(m -> current == m.category)
                    .filter(m -> searchText.isEmpty() || m.name.toLowerCase().contains(searchText.toLowerCase()))
                    .toList();
            int itemH = 38;
            int visible = listH / itemH;
            int maxScroll = Math.max(0, mods.size() - visible);
            scrollOffset = Math.max(0, Math.min(scrollOffset, maxScroll));

            for (int i = 0; i < visible && scrollOffset + i < mods.size(); i++) {
                Module m = mods.get(scrollOffset + i);
                int y = listY + i * itemH;
                boolean hover = mouseX >= listX && mouseX <= listX + listW && mouseY >= y && mouseY <= y + itemH;
                if (hover) {
                    g.setColor(new Color(0xCCE6FF, true)); // неоново-голубая полупрозрачная подсветка
                    g.fillRect(listX, y, listW, itemH);
                }
                g.setFont(modFont);
                g.setColor(new Color(0x222222));
                g.drawString(m.name, listX + 8, y + 12);
                g.setFont(descFont);
                g.setColor(new Color(0x777777));
                String desc = m.description.length() > 50 ? m.description.substring(0, 50) + "..." : m.description;
                g.drawString(desc, listX + 8, y + 26);
                // ON/OFF
                String toggle = m.enabled ? "ON" : "OFF";
                int toggleX = listX + listW - 45;
                g.setFont(modFont);
                g.setColor(m.enabled ? new Color(0x33CC33) : new Color(0xCC3333));
                g.drawString(toggle, toggleX, y + 18);
                // Bind
                String bind = getKeyName(m.keyCode);
                int bindX = toggleX - 50;
                g.setColor(new Color(0x3399FF));
                g.drawString("[" + bind + "]", bindX, y + 18);
            }
            g.setClip(null);

            if (bindingModule != null) {
                g.setFont(catFont);
                g.setColor(new Color(0xFF6600));
                drawCenteredString(g, "Press a key for " + bindingModule.name + "...", winX + WIN_W/2, winY + WIN_H - 18);
            }
            super.render(ctx, mouseX, mouseY, delta);
        }

        private String getKeyName(int key) {
            if (key == GLFW.GLFW_KEY_UNKNOWN) return "None";
            String name = GLFW.glfwGetKeyName(key, 0);
            return name != null ? name.toUpperCase() : "Key" + key;
        }

        @Override
        public boolean mouseClicked(double mx, double my, int button) {
            if (button == 0) {
                int catX = winX + 15;
                int catY = winY + 55;
                int catW = 70;
                int catH = 24;
                for (Category cat : Category.values()) {
                    if (mx >= catX && mx <= catX + catW && my >= catY && my <= catY + catH) {
                        current = cat;
                        scrollOffset = 0;
                        return true;
                    }
                    catY += catH + 6;
                }
                int searchX = winX + 110;
                int searchY = winY + 55;
                int searchW = WIN_W - 125;
                int searchH = 20;
                if (mx >= searchX && mx <= searchX + searchW && my >= searchY && my <= searchY + searchH) {
                    typing = true;
                    return true;
                } else typing = false;

                int listX = winX + 110;
                int listY = winY + 85;
                int listW = WIN_W - 125;
                int itemH = 38;
                List<Module> mods = modules.stream()
                        .filter(m -> current == m.category)
                        .filter(m -> searchText.isEmpty() || m.name.toLowerCase().contains(searchText.toLowerCase()))
                        .toList();
                for (int i = 0; i < mods.size(); i++) {
                    int y = listY + i * itemH - scrollOffset * itemH;
                    if (y < listY - itemH || y > listY + (WIN_H-105)) continue;
                    if (mx >= listX && mx <= listX + listW && my >= y && my <= y + itemH) {
                        Module m = mods.get(i);
                        int toggleX = listX + listW - 45;
                        if (mx >= toggleX && mx <= toggleX + 30) {
                            m.enabled = !m.enabled;
                            saveModules();
                            return true;
                        }
                        int bindX = toggleX - 50;
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
                else if (keyCode == GLFW.GLFW_KEY_BACKSPACE && !searchText.isEmpty())
                    searchText = searchText.substring(0, searchText.length()-1);
                else if (keyCode == GLFW.GLFW_KEY_ENTER) typing = false;
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
            if (typing && (Character.isLetterOrDigit(codePoint) || codePoint == ' ' || codePoint == '_')) {
                searchText += codePoint;
                return true;
            }
            return super.charTyped(codePoint, modifiers);
        }

        @Override
        public boolean mouseScrolled(double mx, double my, double horiz, double vert) {
            List<Module> mods = modules.stream()
                    .filter(m -> current == m.category)
                    .filter(m -> searchText.isEmpty() || m.name.toLowerCase().contains(searchText.toLowerCase()))
                    .toList();
            int visible = (WIN_H - 105) / 38;
            int max = Math.max(0, mods.size() - visible);
            scrollOffset = (int) Math.max(0, Math.min(scrollOffset - vert, max));
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
