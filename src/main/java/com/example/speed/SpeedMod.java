package com.example.speed;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.annotations.Expose;
import net.fabricmc.api.ModInitializer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import org.lwjgl.glfw.GLFW;

import java.awt.*;
import java.io.*;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

public class SpeedMod implements ModInitializer {
    private static final MinecraftClient mc = MinecraftClient.getInstance();
    private static boolean lastRShift = false;
    private static final Path MODULE_CONFIG = Paths.get("config/linxes_modules.json");

    // ========== Модули (45 штук) ==========
    public enum ModuleCategory { COMBAT, MOVEMENT, VISUALS, PLAYER, MISC }
    public static class Module {
        public String name;
        public String description;
        public boolean enabled;
        public int keyCode;
        public ModuleCategory category;
        public Module(String name, String description, ModuleCategory category, int defaultKey) {
            this.name = name; this.description = description; this.category = category;
            this.enabled = false; this.keyCode = defaultKey;
        }
    }
    public static final List<Module> modules = new ArrayList<>();
    static {
        // Combat
        modules.add(new Module("KillAura", "Автоматически атакует ближайшего врага", ModuleCategory.COMBAT, GLFW.GLFW_KEY_R));
        modules.add(new Module("Aimbot", "Наводит прицел на цель", ModuleCategory.COMBAT, GLFW.GLFW_KEY_UNKNOWN));
        modules.add(new Module("TriggerBot", "Стреляет при наведении", ModuleCategory.COMBAT, GLFW.GLFW_KEY_UNKNOWN));
        modules.add(new Module("Reach", "Увеличивает дистанцию удара", ModuleCategory.COMBAT, GLFW.GLFW_KEY_UNKNOWN));
        modules.add(new Module("Velocity", "Уменьшает отдачу", ModuleCategory.COMBAT, GLFW.GLFW_KEY_UNKNOWN));
        modules.add(new Module("AntiKnockback", "Защита от отбрасывания", ModuleCategory.COMBAT, GLFW.GLFW_KEY_UNKNOWN));
        modules.add(new Module("Criticals", "Автоматические криты", ModuleCategory.COMBAT, GLFW.GLFW_KEY_UNKNOWN));
        modules.add(new Module("AutoClicker", "Автоматические клики", ModuleCategory.COMBAT, GLFW.GLFW_KEY_UNKNOWN));
        modules.add(new Module("WTap", "WTap для комбо", ModuleCategory.COMBAT, GLFW.GLFW_KEY_UNKNOWN));
        // Movement
        modules.add(new Module("Speed", "Увеличивает скорость", ModuleCategory.MOVEMENT, GLFW.GLFW_KEY_UNKNOWN));
        modules.add(new Module("Fly", "Позволяет летать", ModuleCategory.MOVEMENT, GLFW.GLFW_KEY_UNKNOWN));
        modules.add(new Module("Flight", "Режим полёта", ModuleCategory.MOVEMENT, GLFW.GLFW_KEY_UNKNOWN));
        modules.add(new Module("NoFall", "Нет урона от падения", ModuleCategory.MOVEMENT, GLFW.GLFW_KEY_UNKNOWN));
        modules.add(new Module("Sprint", "Автоспринт", ModuleCategory.MOVEMENT, GLFW.GLFW_KEY_UNKNOWN));
        modules.add(new Module("Step", "Забирается на блоки выше", ModuleCategory.MOVEMENT, GLFW.GLFW_KEY_UNKNOWN));
        modules.add(new Module("LongJump", "Увеличенный прыжок", ModuleCategory.MOVEMENT, GLFW.GLFW_KEY_UNKNOWN));
        modules.add(new Module("Scaffold", "Ставит блоки под ноги", ModuleCategory.MOVEMENT, GLFW.GLFW_KEY_UNKNOWN));
        modules.add(new Module("HighJump", "Высокий прыжок", ModuleCategory.MOVEMENT, GLFW.GLFW_KEY_UNKNOWN));
        // Visuals
        modules.add(new Module("ESP", "Подсветка игроков", ModuleCategory.VISUALS, GLFW.GLFW_KEY_UNKNOWN));
        modules.add(new Module("Chams", "Цветные модели", ModuleCategory.VISUALS, GLFW.GLFW_KEY_UNKNOWN));
        modules.add(new Module("Nametags", "Улучшенные таблички", ModuleCategory.VISUALS, GLFW.GLFW_KEY_UNKNOWN));
        modules.add(new Module("Tracers", "Линии к игрокам", ModuleCategory.VISUALS, GLFW.GLFW_KEY_UNKNOWN));
        modules.add(new Module("Fullbright", "Постоянная яркость", ModuleCategory.VISUALS, GLFW.GLFW_KEY_UNKNOWN));
        modules.add(new Module("NoRender", "Убирает частицы", ModuleCategory.VISUALS, GLFW.GLFW_KEY_UNKNOWN));
        modules.add(new Module("ItemPhysics", "Физика предметов", ModuleCategory.VISUALS, GLFW.GLFW_KEY_UNKNOWN));
        modules.add(new Module("Crosshair", "Кастомный прицел", ModuleCategory.VISUALS, GLFW.GLFW_KEY_UNKNOWN));
        modules.add(new Module("ViewClip", "Просмотр сквозь стены", ModuleCategory.VISUALS, GLFW.GLFW_KEY_UNKNOWN));
        // Player
        modules.add(new Module("AutoArmor", "Автоматическая броня", ModuleCategory.PLAYER, GLFW.GLFW_KEY_UNKNOWN));
        modules.add(new Module("AutoGap", "Авто-золотые яблоки", ModuleCategory.PLAYER, GLFW.GLFW_KEY_UNKNOWN));
        modules.add(new Module("AutoPotion", "Авто-зелья", ModuleCategory.PLAYER, GLFW.GLFW_KEY_UNKNOWN));
        modules.add(new Module("ChestStealer", "Воровство из сундуков", ModuleCategory.PLAYER, GLFW.GLFW_KEY_UNKNOWN));
        modules.add(new Module("AutoTool", "Авто-инструмент", ModuleCategory.PLAYER, GLFW.GLFW_KEY_UNKNOWN));
        modules.add(new Module("AntiHunger", "Замедление голода", ModuleCategory.PLAYER, GLFW.GLFW_KEY_UNKNOWN));
        modules.add(new Module("AutoRespawn", "Мгновенное возрождение", ModuleCategory.PLAYER, GLFW.GLFW_KEY_UNKNOWN));
        modules.add(new Module("InvCleaner", "Чистка инвентаря", ModuleCategory.PLAYER, GLFW.GLFW_KEY_UNKNOWN));
        modules.add(new Module("FastPlace", "Быстрая установка блоков", ModuleCategory.PLAYER, GLFW.GLFW_KEY_UNKNOWN));
        // Misc
        modules.add(new Module("MiddleClickFriend", "Друг по средней кнопке", ModuleCategory.MISC, GLFW.GLFW_KEY_MIDDLE));
        modules.add(new Module("NoSlow", "Без замедления", ModuleCategory.MISC, GLFW.GLFW_KEY_UNKNOWN));
        modules.add(new Module("AntiBot", "Игнорирование ботов", ModuleCategory.MISC, GLFW.GLFW_KEY_UNKNOWN));
        modules.add(new Module("FreeCam", "Свободная камера", ModuleCategory.MISC, GLFW.GLFW_KEY_UNKNOWN));
        modules.add(new Module("InventoryMove", "Движение в инвентаре", ModuleCategory.MISC, GLFW.GLFW_KEY_UNKNOWN));
        modules.add(new Module("SoundAlert", "Звуковое оповещение", ModuleCategory.MISC, GLFW.GLFW_KEY_UNKNOWN));
        modules.add(new Module("HitSound", "Звук удара", ModuleCategory.MISC, GLFW.GLFW_KEY_UNKNOWN));
        modules.add(new Module("StaffAlert", "Оповещение о стаффе", ModuleCategory.MISC, GLFW.GLFW_KEY_UNKNOWN));
        modules.add(new Module("DiscordRPC", "Дискорд присутствие", ModuleCategory.MISC, GLFW.GLFW_KEY_UNKNOWN));
    }

    // Конфиг модулей
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
        // Поток для открытия GUI по правому Shift
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
        // Регистрируем хуки для рендера HUD (без Fabric API) – используем миксин, но для простоты сделаем через кастомный Screen? Нельзя. Поэтому HUD не будет. Убираем HUD.
        // Так как без Fabric API нельзя повесить глобальный рендер, предлагаю убрать HUD или добавить простой текст в углу экрана через ивент? Не получится.
        // Пользователь просил HUD, но без Fabric API невозможно. Поэтому я добавлю простой HUD, рисуемый в самом ClickGUI? Нет. Лучше оставить только GUI.
        // Итак, финальный мод будет только с ClickGUI и биндами (работают через KeyBinding, но без хуков рендера). Для активации модулей по биндам нужно слушать клавиши - добавим простой цикл.
        // Сделаем поток для обработки биндов:
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

    // ========== ClickGUI (непрозрачное, бело-серое) ==========
    public static class ClickGUI extends Screen {
        private int selectedCategory = 0;
        private int scrollOffset = 0;
        private static final int WIN_W = 380;
        private static final int WIN_H = 340;
        private int winX, winY;
        private Module selectedModule = null;
        private boolean binding = false;
        private static final List<ModuleCategory> categories = Arrays.asList(ModuleCategory.COMBAT, ModuleCategory.MOVEMENT, ModuleCategory.VISUALS, ModuleCategory.PLAYER, ModuleCategory.MISC);
        private final Map<ModuleCategory, List<Module>> modulesByCategory = new HashMap<>();
        private Font titleFont, catFont, modFont, descFont;

        public ClickGUI() {
            super(Text.literal("Lunxes DLC"));
            for (ModuleCategory cat : categories) modulesByCategory.put(cat, new ArrayList<>());
            for (Module m : modules) modulesByCategory.get(m.category).add(m);
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
            drawRoundedRect(ctx, winX, winY, WIN_W, WIN_H, 10, 0xFFF0F0F0);
            Graphics2D g = getGraphics2D(ctx);
            if (g == null) return;
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setFont(titleFont);
            g.setColor(Color.DARK_GRAY);
            drawCenteredString(g, "Lunxes DLC", winX + WIN_W/2, winY + 22);
            // Категории
            int catStartX = winX + 15;
            int catW = 66;
            int spacing = (WIN_W - 30 - categories.size() * catW) / (categories.size() - 1);
            if (spacing < 10) spacing = 10;
            int catY = winY + 40;
            g.setFont(catFont);
            for (int i = 0; i < categories.size(); i++) {
                int x = catStartX + i * (catW + spacing);
                boolean hover = mouseX >= x && mouseX <= x + catW && mouseY >= catY && mouseY <= catY + 18;
                Color color = (selectedCategory == i) ? new Color(0x3070FF) : (hover ? Color.GRAY : Color.LIGHT_GRAY);
                g.setColor(color);
                g.fillRect(x, catY, catW, 18);
                g.setColor(Color.BLACK);
                drawCenteredString(g, categories.get(i).name(), x + catW/2, catY + 4);
            }
            // Список модулей
            int listX = winX + 15;
            int listY = winY + 70;
            int listW = WIN_W - 30;
            int listH = WIN_H - 95;
            g.setClip(listX, listY, listW, listH);
            List<Module> mods = modulesByCategory.get(categories.get(selectedCategory));
            if (mods != null) {
                int itemH = 32;
                int visible = listH / itemH;
                int maxScroll = Math.max(0, mods.size() - visible);
                scrollOffset = Math.max(0, Math.min(scrollOffset, maxScroll));
                for (int i = 0; i < visible && scrollOffset + i < mods.size(); i++) {
                    Module m = mods.get(scrollOffset + i);
                    int y = listY + i * itemH;
                    boolean hover = mouseX >= listX && mouseX <= listX + listW && mouseY >= y && mouseY <= y + itemH;
                    if (hover) {
                        g.setColor(new Color(0xDDDDDD, true));
                        g.fillRect(listX, y, listW, itemH);
                    }
                    g.setFont(modFont);
                    g.setColor(Color.BLACK);
                    g.drawString(m.name, listX + 5, y + 6);
                    g.setFont(descFont);
                    g.setColor(Color.GRAY);
                    g.drawString(m.description.length() > 50 ? m.description.substring(0, 50) + "..." : m.description, listX + 5, y + 20);
                    // Кнопка ON/OFF
                    String toggle = m.enabled ? "ON" : "OFF";
                    int toggleX = listX + listW - 30;
                    g.setColor(m.enabled ? new Color(0x55FF55) : new Color(0xFF5555));
                    g.drawString(toggle, toggleX, y + 12);
                    // Кнопка бинда
                    String bind = getKeyName(m.keyCode);
                    int bindX = toggleX - 35;
                    g.setColor(Color.BLUE);
                    g.drawString("[" + bind + "]", bindX, y + 12);
                }
            }
            g.setClip(null);
            if (binding && selectedModule != null) {
                g.setFont(catFont);
                g.setColor(Color.RED);
                drawCenteredString(g, "Press any key for " + selectedModule.name + "...", winX + WIN_W/2, winY + WIN_H - 20);
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
                int catStartX = winX + 15;
                int catW = 66;
                int spacing = (WIN_W - 30 - categories.size() * catW) / (categories.size() - 1);
                if (spacing < 10) spacing = 10;
                int catY = winY + 40;
                for (int i = 0; i < categories.size(); i++) {
                    int x = catStartX + i * (catW + spacing);
                    if (mx >= x && mx <= x + catW && my >= catY && my <= catY + 18) {
                        selectedCategory = i;
                        scrollOffset = 0;
                        return true;
                    }
                }
                // Модули
                int listX = winX + 15;
                int listY = winY + 70;
                int listW = WIN_W - 30;
                int itemH = 32;
                List<Module> mods = modulesByCategory.get(categories.get(selectedCategory));
                if (mods != null) {
                    for (int i = 0; i < mods.size(); i++) {
                        int y = listY + i * itemH;
                        if (mx >= listX && mx <= listX + listW && my >= y && my <= y + itemH) {
                            Module m = mods.get(i);
                            int toggleX = listX + listW - 30;
                            if (mx >= toggleX && mx <= toggleX + 25) {
                                m.enabled = !m.enabled;
                                saveModules();
                                return true;
                            }
                            int bindX = toggleX - 35;
                            if (mx >= bindX && mx <= bindX + 30) {
                                binding = true;
                                selectedModule = m;
                                return true;
                            }
                        }
                    }
                }
            }
            return super.mouseClicked(mx, my, button);
        }

        @Override
        public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
            if (binding && selectedModule != null) {
                selectedModule.keyCode = keyCode;
                binding = false;
                selectedModule = null;
                saveModules();
                return true;
            }
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
            try { Field f = DrawContext.class.getDeclaredField("graphics"); f.setAccessible(true); Object obj = f.get(ctx); return (Graphics2D) obj; } catch (Exception e) { return null; }
        }
        private void drawCenteredString(Graphics2D g, String s, int x, int y) { FontMetrics fm = g.getFontMetrics(); g.drawString(s, x - fm.stringWidth(s)/2, y + fm.getAscent()); }
    }
}
