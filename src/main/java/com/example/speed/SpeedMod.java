package com.example.speed;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.annotations.Expose;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffectUtil;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.MathHelper;
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

    @Override
    public void onInitialize() {
        ClickGUI.loadConfig();
        HudRenderer.loadConfig();
        HudRenderer.register();
        // Клавиша для открытия GUI (правый Shift)
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
    }

    // ========== Модули (функции читов) ==========
    public enum ModuleCategory { COMBAT, MOVEMENT, VISUALS, PLAYER, MISC }
    public static class Module {
        public String name;
        public String description;
        public boolean enabled;
        public int keyCode; // клавиша для бинда (GLFW)
        public ModuleCategory category;
        public Module(String name, String description, ModuleCategory category, int defaultKey) {
            this.name = name; this.description = description; this.category = category;
            this.enabled = false; this.keyCode = defaultKey;
        }
    }

    // Список всех 45 модулей (по 9 на категорию)
    public static final List<Module> modules = new ArrayList<>();
    static {
        // COMBAT (9)
        modules.add(new Module("KillAura", "Автоматически атакует ближайшего врага", ModuleCategory.COMBAT, GLFW.GLFW_KEY_R));
        modules.add(new Module("Aimbot", "Наводит прицел на цель", ModuleCategory.COMBAT, GLFW.GLFW_KEY_UNKNOWN));
        modules.add(new Module("TriggerBot", "Стреляет при наведении на цель", ModuleCategory.COMBAT, GLFW.GLFW_KEY_UNKNOWN));
        modules.add(new Module("Reach", "Увеличивает дистанцию удара", ModuleCategory.COMBAT, GLFW.GLFW_KEY_UNKNOWN));
        modules.add(new Module("Velocity", "Уменьшает отдачу при ударах", ModuleCategory.COMBAT, GLFW.GLFW_KEY_UNKNOWN));
        modules.add(new Module("AntiKnockback", "Защита от отбрасывания", ModuleCategory.COMBAT, GLFW.GLFW_KEY_UNKNOWN));
        modules.add(new Module("Criticals", "Автоматические критические удары", ModuleCategory.COMBAT, GLFW.GLFW_KEY_UNKNOWN));
        modules.add(new Module("AutoClicker", "Автоматические клики", ModuleCategory.COMBAT, GLFW.GLFW_KEY_UNKNOWN));
        modules.add(new Module("WTap", "WTap для комбо", ModuleCategory.COMBAT, GLFW.GLFW_KEY_UNKNOWN));
        // MOVEMENT (9)
        modules.add(new Module("Speed", "Увеличивает скорость бега", ModuleCategory.MOVEMENT, GLFW.GLFW_KEY_UNKNOWN));
        modules.add(new Module("Fly", "Позволяет летать", ModuleCategory.MOVEMENT, GLFW.GLFW_KEY_UNKNOWN));
        modules.add(new Module("Flight", "Режим полёта", ModuleCategory.MOVEMENT, GLFW.GLFW_KEY_UNKNOWN));
        modules.add(new Module("NoFall", "Отключает урон от падения", ModuleCategory.MOVEMENT, GLFW.GLFW_KEY_UNKNOWN));
        modules.add(new Module("Sprint", "Автоспринт", ModuleCategory.MOVEMENT, GLFW.GLFW_KEY_UNKNOWN));
        modules.add(new Module("Step", "Позволяет забираться на блоки выше", ModuleCategory.MOVEMENT, GLFW.GLFW_KEY_UNKNOWN));
        modules.add(new Module("LongJump", "Увеличенный прыжок", ModuleCategory.MOVEMENT, GLFW.GLFW_KEY_UNKNOWN));
        modules.add(new Module("Scaffold", "Ставит блоки под ноги", ModuleCategory.MOVEMENT, GLFW.GLFW_KEY_UNKNOWN));
        modules.add(new Module("HighJump", "Высокий прыжок", ModuleCategory.MOVEMENT, GLFW.GLFW_KEY_UNKNOWN));
        // VISUALS (9)
        modules.add(new Module("ESP", "Подсвечивает игроков сквозь стены", ModuleCategory.VISUALS, GLFW.GLFW_KEY_UNKNOWN));
        modules.add(new Module("Chams", "Меняет цвет игроков", ModuleCategory.VISUALS, GLFW.GLFW_KEY_UNKNOWN));
        modules.add(new Module("Nametags", "Улучшенные таблички", ModuleCategory.VISUALS, GLFW.GLFW_KEY_UNKNOWN));
        modules.add(new Module("Tracers", "Линии к игрокам", ModuleCategory.VISUALS, GLFW.GLFW_KEY_UNKNOWN));
        modules.add(new Module("Fullbright", "Постоянная яркость", ModuleCategory.VISUALS, GLFW.GLFW_KEY_UNKNOWN));
        modules.add(new Module("NoRender", "Убирает некоторые частицы", ModuleCategory.VISUALS, GLFW.GLFW_KEY_UNKNOWN));
        modules.add(new Module("ItemPhysics", "Физика предметов", ModuleCategory.VISUALS, GLFW.GLFW_KEY_UNKNOWN));
        modules.add(new Module("Crosshair", "Кастомный прицел", ModuleCategory.VISUALS, GLFW.GLFW_KEY_UNKNOWN));
        modules.add(new Module("ViewClip", "Позволяет видеть сквозь стены в 3-м лице", ModuleCategory.VISUALS, GLFW.GLFW_KEY_UNKNOWN));
        // PLAYER (9)
        modules.add(new Module("AutoArmor", "Автоматически надевает броню", ModuleCategory.PLAYER, GLFW.GLFW_KEY_UNKNOWN));
        modules.add(new Module("AutoGap", "Автоматически ест золотые яблоки", ModuleCategory.PLAYER, GLFW.GLFW_KEY_UNKNOWN));
        modules.add(new Module("AutoPotion", "Автоматические зелья", ModuleCategory.PLAYER, GLFW.GLFW_KEY_UNKNOWN));
        modules.add(new Module("ChestStealer", "Ворует из сундуков", ModuleCategory.PLAYER, GLFW.GLFW_KEY_UNKNOWN));
        modules.add(new Module("AutoTool", "Автоматический выбор инструмента", ModuleCategory.PLAYER, GLFW.GLFW_KEY_UNKNOWN));
        modules.add(new Module("AntiHunger", "Замедляет голод", ModuleCategory.PLAYER, GLFW.GLFW_KEY_UNKNOWN));
        modules.add(new Module("AutoRespawn", "Мгновенное возрождение", ModuleCategory.PLAYER, GLFW.GLFW_KEY_UNKNOWN));
        modules.add(new Module("InvCleaner", "Чистка инвентаря", ModuleCategory.PLAYER, GLFW.GLFW_KEY_UNKNOWN));
        modules.add(new Module("FastPlace", "Быстрая установка блоков", ModuleCategory.PLAYER, GLFW.GLFW_KEY_UNKNOWN));
        // MISC (9)
        modules.add(new Module("MiddleClickFriend", "Добавляет друга по средней кнопке", ModuleCategory.MISC, GLFW.GLFW_KEY_MIDDLE));
        modules.add(new Module("NoSlow", "Отключает замедление", ModuleCategory.MISC, GLFW.GLFW_KEY_UNKNOWN));
        modules.add(new Module("AntiBot", "Игнорирует ботов", ModuleCategory.MISC, GLFW.GLFW_KEY_UNKNOWN));
        modules.add(new Module("FreeCam", "Свободная камера", ModuleCategory.MISC, GLFW.GLFW_KEY_UNKNOWN));
        modules.add(new Module("InventoryMove", "Движение в инвентаре", ModuleCategory.MISC, GLFW.GLFW_KEY_UNKNOWN));
        modules.add(new Module("SoundAlert", "Звуковое оповещение", ModuleCategory.MISC, GLFW.GLFW_KEY_UNKNOWN));
        modules.add(new Module("HitSound", "Звук удара", ModuleCategory.MISC, GLFW.GLFW_KEY_UNKNOWN));
        modules.add(new Module("StaffAlert", "Оповещение о входе стаффа", ModuleCategory.MISC, GLFW.GLFW_KEY_UNKNOWN));
        modules.add(new Module("DiscordRPC", "Дискорд присутствие", ModuleCategory.MISC, GLFW.GLFW_KEY_UNKNOWN));
    }

    // ========== Конфигурация модулей (сохранение биндов и состояний) ==========
    static class ModuleConfig {
        @Expose Map<String, Boolean> enabled = new HashMap<>();
        @Expose Map<String, Integer> keyBind = new HashMap<>();
    }
    private static final Path CONFIG_PATH = Paths.get("config/linxes_modules.json");
    private static final Gson GSON = new GsonBuilder().excludeFieldsWithoutExposeAnnotation().setPrettyPrinting().create();
    public static void saveModules() {
        ModuleConfig cfg = new ModuleConfig();
        for (Module m : modules) {
            cfg.enabled.put(m.name, m.enabled);
            cfg.keyBind.put(m.name, m.keyCode);
        }
        try { Files.writeString(CONFIG_PATH, GSON.toJson(cfg)); } catch (IOException e) { e.printStackTrace(); }
    }
    public static void loadModules() {
        if (!Files.exists(CONFIG_PATH)) return;
        try {
            ModuleConfig cfg = GSON.fromJson(Files.readString(CONFIG_PATH), ModuleConfig.class);
            if (cfg.enabled != null) for (Module m : modules) m.enabled = cfg.enabled.getOrDefault(m.name, false);
            if (cfg.keyBind != null) for (Module m : modules) m.keyCode = cfg.keyBind.getOrDefault(m.name, GLFW.GLFW_KEY_UNKNOWN);
        } catch (IOException e) { e.printStackTrace(); }
    }

    // ========== HUD (перетаскиваемые элементы) ==========
    public static class HudElement {
        public String id;
        public int x, y;
        public boolean visible;
        public HudElement(String id, int defaultX, int defaultY, boolean visible) { this.id = id; this.x = defaultX; this.y = defaultY; this.visible = visible; }
    }
    public static List<HudElement> hudElements = new ArrayList<>();
    public static void initHudElements() {
        hudElements.add(new HudElement("health_target", 10, 60, true));
        hudElements.add(new HudElement("ping", 10, 80, true));
        hudElements.add(new HudElement("coords", 10, 100, true));
        hudElements.add(new HudElement("effects", 10, 120, true));
        hudElements.add(new HudElement("binds", 10, 200, true));
    }
    static { initHudElements(); }
    private static final Path HUD_PATH = Paths.get("config/linxes_hud.json");
    public static void saveHudPositions() {
        try { Files.writeString(HUD_PATH, GSON.toJson(hudElements)); } catch (IOException e) { e.printStackTrace(); }
    }
    public static void loadHudConfig() {
        if (!Files.exists(HUD_PATH)) return;
        try {
            HudElement[] arr = GSON.fromJson(Files.readString(HUD_PATH), HudElement[].class);
            if (arr != null) hudElements = new ArrayList<>(Arrays.asList(arr));
        } catch (IOException e) { e.printStackTrace(); }
    }

    // ========== HudRenderer (с использованием Fabric API) ==========
    public static class HudRenderer {
        private static boolean dragging = false;
        private static HudElement draggingElement = null;
        private static int dragX, dragY;
        public static void register() {
            HudRenderCallback.EVENT.register(HudRenderer::render);
            ClientTickEvents.END_CLIENT_TICK.register(client -> {
                if (client.currentScreen != null) return;
                long handle = client.getWindow().getHandle();
                boolean altPressed = (GLFW.glfwGetKey(handle, GLFW.GLFW_KEY_LEFT_ALT) == GLFW.GLFW_PRESS);
                if (!altPressed && dragging) {
                    dragging = false;
                    draggingElement = null;
                    saveHudPositions();
                }
            });
        }
        private static void render(DrawContext ctx, float tickDelta) {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc.player == null || mc.currentScreen instanceof ClickGUI) return;
            TextRenderer tr = mc.textRenderer;
            // Получаем данные
            LivingEntity target = null;
            if (mc.crosshairTarget != null && mc.crosshairTarget.getType() == HitResult.Type.ENTITY) {
                if (((EntityHitResult) mc.crosshairTarget).getEntity() instanceof LivingEntity)
                    target = (LivingEntity) ((EntityHitResult) mc.crosshairTarget).getEntity();
            }
            int ping = 0;
            if (mc.getNetworkHandler() != null && mc.player != null) {
                var entry = mc.getNetworkHandler().getPlayerListEntry(mc.player.getUuid());
                if (entry != null) ping = entry.getLatency();
            }
            String coords = String.format("XYZ: %.1f, %.1f, %.1f", mc.player.getX(), mc.player.getY(), mc.player.getZ());
            // Эффекты
            StringBuilder effectsStr = new StringBuilder();
            for (StatusEffectInstance effect : mc.player.getStatusEffects()) {
                String name = Formatting.strip(effect.getEffectType().getName().getString());
                int duration = effect.getDuration() / 20;
                effectsStr.append(name).append(": ").append(duration).append("s\n");
            }
            // Бинды активных модулей
            StringBuilder bindsStr = new StringBuilder();
            for (Module m : modules) {
                if (m.enabled && m.keyCode != GLFW.GLFW_KEY_UNKNOWN) {
                    String keyName = GLFW.glfwGetKeyName(m.keyCode, 0);
                    if (keyName == null) keyName = "Key" + m.keyCode;
                    bindsStr.append(m.name).append(" [").append(keyName).append("]\n");
                }
            }
            // Обработка перетаскивания
            long handle = mc.getWindow().getHandle();
            boolean altPressed = GLFW.glfwGetKey(handle, GLFW.GLFW_KEY_LEFT_ALT) == GLFW.GLFW_PRESS;
            double mouseX = mc.mouse.getX() / mc.getWindow().getScaleFactor();
            double mouseY = mc.mouse.getY() / mc.getWindow().getScaleFactor();
            if (altPressed && !dragging) {
                for (HudElement el : hudElements) {
                    if (!el.visible) continue;
                    int width = 0;
                    switch (el.id) {
                        case "health_target": if (target != null) width = tr.getWidth("❤ " + (int) target.getHealth()); break;
                        case "ping": width = tr.getWidth("Ping: " + ping + "ms"); break;
                        case "coords": width = tr.getWidth(coords); break;
                        case "effects": width = tr.getWidth(effectsStr.length() > 0 ? effectsStr.substring(0, effectsStr.indexOf("\n")) : "Effects"); break;
                        case "binds": width = tr.getWidth(bindsStr.length() > 0 ? bindsStr.substring(0, bindsStr.indexOf("\n")) : "Binds"); break;
                    }
                    int height = 9;
                    if (mouseX >= el.x && mouseX <= el.x + width && mouseY >= el.y && mouseY <= el.y + height) {
                        dragging = true;
                        draggingElement = el;
                        dragX = (int) (mouseX - el.x);
                        dragY = (int) (mouseY - el.y);
                        break;
                    }
                }
            }
            if (dragging && draggingElement != null) {
                draggingElement.x = (int) (mouseX - dragX);
                draggingElement.y = (int) (mouseY - dragY);
            }
            // Рисование элементов
            Graphics2D g = getGraphics2D(ctx);
            if (g != null) {
                g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                g.setFont(new Font("Segoe UI", Font.PLAIN, 12));
                for (HudElement el : hudElements) {
                    if (!el.visible) continue;
                    String text = "";
                    switch (el.id) {
                        case "health_target": if (target != null) text = "❤ " + (int) target.getHealth(); break;
                        case "ping": text = "Ping: " + ping + "ms"; break;
                        case "coords": text = coords; break;
                        case "effects": text = effectsStr.toString().trim(); break;
                        case "binds": text = bindsStr.toString().trim(); break;
                    }
                    if (text.isEmpty()) continue;
                    String[] lines = text.split("\n");
                    for (int i = 0; i < lines.length; i++) {
                        g.setColor(Color.WHITE);
                        g.drawString(lines[i], el.x, el.y + i * 11);
                    }
                }
            }
        }
        private static Graphics2D getGraphics2D(DrawContext ctx) {
            try {
                Field f = DrawContext.class.getDeclaredField("graphics");
                f.setAccessible(true);
                Object obj = f.get(ctx);
                if (obj instanceof Graphics2D) return (Graphics2D) obj;
            } catch (Exception e) {}
            return null;
        }
    }

    // ========== ClickGUI (непрозрачный, бело-серый, с биндами) ==========
    public static class ClickGUI extends Screen {
        private int selectedCategory = 0;
        private int scrollOffset = 0;
        private static final int WIN_W = 380;
        private static final int WIN_H = 340;
        private int winX, winY;
        private Module selectedModule = null;
        private boolean binding = false;
        private static final List<ModuleCategory> categories = Arrays.asList(ModuleCategory.COMBAT, ModuleCategory.MOVEMENT, ModuleCategory.VISUALS, ModuleCategory.PLAYER, ModuleCategory.MISC);
        private Map<ModuleCategory, List<Module>> modulesByCategory = new HashMap<>();
        private Font titleFont, catFont, modFont, descFont;

        public ClickGUI() {
            super(Text.literal("Lunxes DLC"));
            for (ModuleCategory cat : categories) modulesByCategory.put(cat, new ArrayList<>());
            for (Module m : modules) modulesByCategory.get(m.category).add(m);
            loadModules();
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
            drawRoundedRect(ctx, winX, winY, WIN_W, WIN_H, 10, 0xFFF0F0F0); // белый фон
            Graphics2D g = getGraphics2D(ctx);
            if (g == null) return;
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            // Заголовок
            g.setFont(titleFont);
            g.setColor(Color.DARK_GRAY);
            drawCenteredString(g, "Lunxes DLC", winX + WIN_W/2, winY + 22);
            // Категории (горизонтальные кнопки)
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
            // Список модулей в выбранной категории
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
                    // Кнопка включения/выключения
                    String toggle = m.enabled ? "ON" : "OFF";
                    int toggleX = listX + listW - 30;
                    g.setColor(m.enabled ? new Color(0x55FF55) : new Color(0xFF5555));
                    g.drawString(toggle, toggleX, y + 12);
                    // Кнопка бинда
                    String bind = getKeyName(m.keyCode);
                    int bindX = toggleX - 35;
                    g.setColor(Color.BLUE);
                    g.drawString("[" + bind + "]", bindX, y + 12);
                    // Обработка кликов (переключение и бинд) – в mouseClicked
                }
            }
            g.setClip(null);
            if (binding && selectedModule != null) {
                drawCenteredString(g, "Press any key for " + selectedModule.name + "...", winX + WIN_W/2, winY + WIN_H - 20);
            }
        }

        private String getKeyName(int key) {
            if (key == GLFW.GLFW_KEY_UNKNOWN) return "None";
            String name = GLFW.glfwGetKeyName(key, 0);
            if (name == null) return "Key" + key;
            return name.toUpperCase();
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
                            // Область переключения ON/OFF (правая часть)
                            int toggleX = listX + listW - 30;
                            if (mx >= toggleX && mx <= toggleX + 25) {
                                m.enabled = !m.enabled;
                                saveModules();
                                return true;
                            }
                            // Область бинда
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
