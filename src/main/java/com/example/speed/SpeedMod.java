package com.example.speed;

import net.fabricmc.api.ModInitializer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.awt.*;
import java.lang.reflect.Field;
import java.util.*;
import java.util.List;

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

    // ---------- GUI (внутренний класс, без public) ----------
    static class LinxesGUI extends Screen {
        private int selectedCategory = 0;
        private int scrollOffset = 0;
        private static final int WIN_W = 520;
        private static final int WIN_H = 300;
        private int winX, winY;

        private final List<String> categories = Arrays.asList("Combat", "Movement", "Visuals", "Player", "Misc");
        private final Map<String, List<ModuleEntry>> modules = new LinkedHashMap<>();

        private Font titleFont, categoryFont, moduleNameFont, descFont;

        protected LinxesGUI() {
            super(Text.literal("Linxes"));
            initModules();
            initFonts();
        }

        private void initFonts() {
            titleFont = new Font("Segoe UI", Font.BOLD, 24);
            if (titleFont.getFamily().equals("Dialog")) titleFont = new Font("Arial", Font.BOLD, 24);
            categoryFont = new Font("Segoe UI", Font.BOLD, 16);
            if (categoryFont.getFamily().equals("Dialog")) categoryFont = new Font("Arial", Font.BOLD, 16);
            moduleNameFont = new Font("Segoe UI", Font.BOLD, 15);
            descFont = new Font("Segoe UI", Font.PLAIN, 13);
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
        }

        @Override
        public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
            drawRoundedWindow(ctx, winX, winY, WIN_W, WIN_H, 14, 0xEE1E1E1E);
            Graphics2D g = getGraphics2D(ctx);
            if (g == null) return;

            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            g.setFont(titleFont);
            g.setColor(Color.WHITE);
            drawCenteredString(g, "Linxes", winX + WIN_W / 2, winY + 28);

            int catStartX = winX + 30;
            int catW = 80;
            int spacing = (WIN_W - 60 - categories.size() * catW) / (categories.size() - 1);
            if (spacing < 20) spacing = 20;
            int catY = winY + 50;
            g.setFont(categoryFont);
            for (int i = 0; i < categories.size(); i++) {
                int x = catStartX + i * (catW + spacing);
                boolean hover = mouseX >= x && mouseX <= x + catW && mouseY >= catY && mouseY <= catY + 20;
                Color color = (selectedCategory == i) ? Color.WHITE : (hover ? Color.LIGHT_GRAY : Color.GRAY);
                g.setColor(color);
                drawCenteredString(g, categories.get(i), x + catW / 2, catY + 5);
                if (selectedCategory == i) {
                    int textWidth = g.getFontMetrics().stringWidth(categories.get(i));
                    g.setColor(new Color(0x69B4FF));
                    g.fillRect(x + (catW - textWidth) / 2, catY + 20, textWidth, 2);
                }
            }

            int listX = winX + 15;
            int listY = winY + 85;
            int listW = WIN_W - 30;
            int listH = WIN_H - 105;
            Shape oldClip = g.getClip();
            g.setClip(listX, listY, listW, listH);

            List<ModuleEntry> modList = modules.get(categories.get(selectedCategory));
            if (modList != null) {
                int itemH = 46;
                int visible = listH / itemH;
                int maxScroll = Math.max(0, modList.size() - visible);
                scrollOffset = Math.max(0, Math.min(scrollOffset, maxScroll));

                for (int i = 0; i < visible && scrollOffset + i < modList.size(); i++) {
                    ModuleEntry entry = modList.get(scrollOffset + i);
                    int y = listY + i * itemH;
                    boolean hover = mouseX >= listX && mouseX <= listX + listW && mouseY >= y && mouseY <= y + itemH;
                    if (hover) {
                        g.setColor(new Color(0x33FFFFFF, true));
                        g.fillRect(listX, y, listW, itemH);
                    }
                    g.setFont(moduleNameFont);
                    g.setColor(Color.WHITE);
                    g.drawString(entry.name, listX + 10, y + 14);
                    g.setFont(descFont);
                    g.setColor(new Color(0xAAAAAA));
                    g.drawString(entry.description, listX + 10, y + 32);
                }
            }
            g.setClip(oldClip);

            g.setFont(descFont);
            g.setColor(Color.LIGHT_GRAY);
            drawCenteredString(g, "Linxes Client", winX + WIN_W / 2, winY + WIN_H - 12);
        }

        private Graphics2D getGraphics2D(DrawContext ctx) {
            try {
                Field graphicsField = DrawContext.class.getDeclaredField("graphics");
                graphicsField.setAccessible(true);
                Object graphicsObj = graphicsField.get(ctx);
                if (graphicsObj instanceof Graphics2D) return (Graphics2D) graphicsObj;
                return null;
            } catch (Exception e) {
                return null;
            }
        }

        private void drawCenteredString(Graphics2D g, String s, int x, int y) {
            FontMetrics fm = g.getFontMetrics();
            int w = fm.stringWidth(s);
            g.drawString(s, x - w / 2, y + fm.getAscent());
        }

        private void drawRoundedWindow(DrawContext ctx, int x, int y, int w, int h, int r, int color) {
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
                    if (i*i + j*j <= r*r) {
                        ctx.fill(cx + i, cy + j, cx + i + 1, cy + j + 1, color);
                    }
                }
            }
        }

        @Override
        public boolean mouseClicked(double mx, double my, int button) {
            if (button == 0) {
                int catStartX = winX + 30;
                int catW = 80;
                int spacing = (WIN_W - 60 - categories.size() * catW) / (categories.size() - 1);
                if (spacing < 20) spacing = 20;
                int catY = winY + 50;
                int catH = 20;
                for (int i = 0; i < categories.size(); i++) {
                    int x = catStartX + i * (catW + spacing);
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
        public boolean mouseScrolled(double mx, double my, double horiz, double vert) {
            int delta = (int) Math.signum(vert);
            List<ModuleEntry> modList = modules.get(categories.get(selectedCategory));
            if (modList != null) {
                int visible = (WIN_H - 105) / 46;
                int maxScroll = Math.max(0, modList.size() - visible);
                scrollOffset = Math.max(0, Math.min(scrollOffset - delta, maxScroll));
            }
            return true;
        }

        @Override
        public boolean keyPressed(int code, int scan, int mods) {
            if (code == GLFW.GLFW_KEY_ESCAPE || code == GLFW.GLFW_KEY_RIGHT_SHIFT) {
                close();
                return true;
            }
            return super.keyPressed(code, scan, mods);
        }

        @Override
        public void close() {
            client.setScreen(null);
        }

        static class ModuleEntry {
            String name, description;
            ModuleEntry(String n, String d) { name = n; description = d; }
        }
    }
}
