package com.example.speed;

import net.fabricmc.api.ModInitializer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.*;

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
                        mc.execute(() -> mc.setScreen(new ModernGUI()));
                        try { Thread.sleep(200); } catch (InterruptedException ignored) {}
                    }
                    lastRShift = rShift;
                }
            }
        }).start();
    }

    static class ModernGUI extends Screen {
        private int selectedCategory = 0;
        private int scrollOffset = 0;
        private final List<String> categories = Arrays.asList(
                "KeyBinds", "ClickPearl", "Combat", "Movement", "Player", "Render", "Misc", "Theme"
        );
        private final Map<String, List<String>> modules = new LinkedHashMap<>();
        private final Map<String, Boolean> toggles = new HashMap<>();
        private int mouseScroll = 0;

        protected ModernGUI() {
            super(Text.literal("SpeedMod"));
            initModules();
        }

        private void initModules() {
            modules.put("KeyBinds", Arrays.asList("No Unmatched", "StaffList"));
            modules.put("ClickPearl", Arrays.asList("AntiPush", "Velocity", "NoSlow"));
            modules.put("Combat", Arrays.asList("AutoArmor", "AutoExplosion", "AutoGap", "AutoPotion", "AutoSwap", "AutoTotem", "HitBox", "KillAura", "NoEntityTrace", "TriggerBot", "Velocity"));
            modules.put("Movement", Arrays.asList("AutoSprint", "ElytraFly", "Fly", "NoClip", "NoSlow", "Phase", "Speed", "Spider", "Strafe", "TargetStrafe", "Timer", "NoEntityTrace", "NoRotate"));
            modules.put("Player", Arrays.asList("AntiPush", "AutoLeave", "AutoTool", "ChestStealer", "ClickFriend", "ClickPearl", "FreeCamel", "InventoryMove", "ItemsCooldown", "NoInteract", "NoJumpDelay", "NoRotate"));
            modules.put("Render", Arrays.asList("ItemPhysic", "AutoCarpet", "JumpCircle", "AutoTool", "NoRender", "Particles", "ClickFriend", "Pointers", "Predictions", "Snow", "StorageESP", "SwingAnimation", "TargetESP", "Tracers", "Trails", "ViewModel", "World"));
            modules.put("Misc", Arrays.asList("AutoAccept", "AutoBuyUI", "AutoFish", "AutoRespawn", "AutoTransfer", "ClientSounds", "ElytraHelper", "GriefHelper", "HitSound", "ItemScroller", "ItemSwapFix", "LeaveTracker", "NameProtect", "NoEventDelay", "CalfProtect"));
            modules.put("Theme", Arrays.asList("Slight Ocean View", "Mojito", "Rose Water", "Anamnesis", "Ultra Violet", "Quepal", "Intergalactic"));

            for (List<String> list : modules.values()) {
                for (String mod : list) {
                    toggles.put(mod, false);
                }
            }
        }

        @Override
        public void render(DrawContext context, int mouseX, int mouseY, float delta) {
            context.fill(0, 0, width, height, 0xCC000000);
            int catWidth = 140;
            int catHeight = 28;
            int startX = 20;
            int startY = 40;
            context.fill(startX - 5, startY - 5, startX + catWidth + 5, height - 20, 0xAA151515);
            for (int i = 0; i < categories.size(); i++) {
                int y = startY + i * catHeight;
                boolean hover = mouseX >= startX && mouseX <= startX + catWidth && mouseY >= y && mouseY <= y + catHeight;
                int bgColor = (i == selectedCategory) ? 0xFF3A6EA5 : (hover ? 0xFF2C2C2C : 0xFF1E1E1E);
                context.fill(startX, y, startX + catWidth, y + catHeight, bgColor);
                if (i == selectedCategory) {
                    context.fill(startX, y, startX + 4, y + catHeight, 0xFF69B4FF);
                }
                String catText = categories.get(i);
                int textWidth = textRenderer.getWidth(catText);
                int textX = startX + (catWidth - textWidth) / 2;
                int textY = y + (catHeight - 8) / 2;
                context.drawText(textRenderer, catText, textX, textY, 0xFFFFFF, false);
            }

            int rightX = startX + catWidth + 15;
            int rightWidth = width - rightX - 20;
            int moduleHeight = 26;
            context.fill(rightX - 5, startY - 5, rightX + rightWidth + 5, height - 20, 0xAA151515);
            List<String> modList = modules.get(categories.get(selectedCategory));
            if (modList != null) {
                int visibleCount = (height - startY - 20) / moduleHeight;
                int maxScroll = Math.max(0, modList.size() - visibleCount);
                scrollOffset = Math.max(0, Math.min(scrollOffset, maxScroll));
                if (mouseScroll != 0) {
                    scrollOffset -= mouseScroll;
                    mouseScroll = 0;
                    scrollOffset = Math.max(0, Math.min(scrollOffset, maxScroll));
                }
                for (int i = 0; i < visibleCount && scrollOffset + i < modList.size(); i++) {
                    String modName = modList.get(scrollOffset + i);
                    int y = startY + i * moduleHeight;
                    boolean hover = mouseX >= rightX && mouseX <= rightX + rightWidth && mouseY >= y && mouseY <= y + moduleHeight;
                    boolean state = toggles.getOrDefault(modName, false);
                    int bgColor = hover ? 0xFF2D2D2D : 0xFF1A1A1A;
                    context.fill(rightX, y, rightX + rightWidth, y + moduleHeight, bgColor);
                    context.drawText(textRenderer, modName, rightX + 8, y + (moduleHeight - 8) / 2, 0xE0E0E0, false);
                    String toggleText = state ? "ON" : "OFF";
                    int toggleColor = state ? 0xFF55FF55 : 0xFFFF5555;
                    int toggleX = rightX + rightWidth - textRenderer.getWidth(toggleText) - 8;
                    context.drawText(textRenderer, toggleText, toggleX, y + (moduleHeight - 8) / 2, toggleColor, false);
                }
            }

            String title = "SpeedMod";
            int titleWidth = textRenderer.getWidth(title);
            context.drawText(textRenderer, title, (width - titleWidth) / 2, 12, 0xFFFFFF, true);
            super.render(context, mouseX, mouseY, delta);
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
                int catWidth = 140;
                int startX = 20;
                int startY = 40;
                int catHeight = 28;
                for (int i = 0; i < categories.size(); i++) {
                    int y = startY + i * catHeight;
                    if (mouseX >= startX && mouseX <= startX + catWidth && mouseY >= y && mouseY <= y + catHeight) {
                        selectedCategory = i;
                        scrollOffset = 0;
                        return true;
                    }
                }
                int rightX = startX + catWidth + 15;
                int rightWidth = width - rightX - 20;
                int moduleHeight = 26;
                List<String> modList = modules.get(categories.get(selectedCategory));
                if (modList != null) {
                    int visibleCount = (height - startY - 20) / moduleHeight;
                    for (int i = 0; i < visibleCount && scrollOffset + i < modList.size(); i++) {
                        int y = startY + i * moduleHeight;
                        if (mouseX >= rightX && mouseX <= rightX + rightWidth && mouseY >= y && mouseY <= y + moduleHeight) {
                            String modName = modList.get(scrollOffset + i);
                            toggles.put(modName, !toggles.getOrDefault(modName, false));
                            return true;
                        }
                    }
                }
            }
            return super.mouseClicked(mouseX, mouseY, button);
        }

        @Override
        public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
            mouseScroll = (int) Math.signum(verticalAmount);
            return true;
        }

        @Override
        public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE || keyCode == GLFW.GLFW_KEY_RIGHT_SHIFT) {
                this.close();
                return true;
            }
            return super.keyPressed(keyCode, scanCode, modifiers);
        }

        @Override
        public void close() {
            this.client.setScreen(null);
        }
    }
}
