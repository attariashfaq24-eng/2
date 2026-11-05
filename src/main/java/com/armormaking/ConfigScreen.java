package com.armormaking;

import com.armormaking.Config.Gear;
import com.armormaking.Config.Role;
import com.armormaking.Enchs.Ench;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Button-only menu: pick items, pick enchant levels, mark villagers, start. */
public class ConfigScreen extends Screen {
    private Gear sel = Gear.HELMET;

    public ConfigScreen() {
        super(Component.literal("Armor Making Mod"));
    }

    private Button add(String text, int x, int y, int w, Button.OnPress press) {
        Button b = Button.builder(Component.literal(text), press).bounds(x, y, w, 20).build();
        addRenderableWidget(b);
        return b;
    }

    private void label(String text, int x, int y, int w) {
        add(text, x, y, w, b -> { }).active = false;
    }

    @Override
    protected void init() {
        // left: gear list
        int x = 8, y = 8;
        label("ITEMS", x, y, 112);
        y += 22;
        for (Gear g : Gear.values()) {
            String t = (g == sel ? "> " : "") + g.label + (Config.enabled.contains(g) ? "  [ON]" : "");
            add(t, x, y, 112, b -> { sel = g; rebuildWidgets(); });
            y += 22;
        }

        // middle: enchants of the selected gear
        int mx = 128;
        y = 8;
        label(sel.label + " - enchants", mx, y, 160);
        y += 22;
        add("Make this item: " + (Config.enabled.contains(sel) ? "ON" : "OFF"), mx, y, 160,
                b -> { Config.toggle(sel); rebuildWidgets(); });
        y += 22;
        for (Ench e : sel.options) {
            int lv = Config.level(sel, e);
            String t = e.name() + ": " + (lv == 0 ? "off" : String.valueOf(lv)) + (e.max() > 1 ? "  (max " + e.max() + ")" : "");
            add(t, mx, y, 160, b -> { Config.cycle(sel, e); rebuildWidgets(); });
            y += 22;
        }

        // right: villager marks + start/stop
        int rx = 296;
        y = 8;
        label("MARKS", rx, y, 130);
        y += 22;
        Role r = Config.markRole;
        int n = r == Role.ANVIL ? (Config.anvil == null ? 0 : 1) : Config.villagers(r).size();
        add("Mark as: " + r.name() + " (" + n + ")", rx, y, 130, b -> { Config.markRole = Config.markRole.next(); rebuildWidgets(); });
        y += 22;
        label("Look at it, press V", rx, y, 130);
        y += 22;
        add("Clear all marks", rx, y, 130, b -> { Config.clearMarks(); rebuildWidgets(); });
        y += 34;
        add("START", rx, y, 130, b -> { onClose(); Automation.start(minecraft); });
        y += 22;
        add("STOP", rx, y, 130, b -> Automation.stop("Stopped."));
    }
}
