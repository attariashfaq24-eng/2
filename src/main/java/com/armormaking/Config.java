package com.armormaking;

import static com.armormaking.Enchs.*;

import com.armormaking.Enchs.Ench;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

/** In-memory settings chosen in the menu. */
public final class Config {
    private Config() {}

    public enum Role {
        STRING("String seller (Fisherman)"),
        LIBRARIAN("Librarian (books)"),
        ARMORER("Armorer"),
        TOOLSMITH("Toolsmith"),
        WEAPONSMITH("Weaponsmith"),
        CLERIC("Cleric (XP bottles)"),
        ANVIL("Anvil block");

        public final String label;
        Role(String label) { this.label = label; }
        public Role next() { Role[] v = values(); return v[(ordinal() + 1) % v.length]; }
    }

    public enum Gear {
        HELMET("Helmet", Items.DIAMOND_HELMET, Role.ARMORER, PROT, UNB, MEND, RESP, AQUA),
        CHEST("Chestplate", Items.DIAMOND_CHESTPLATE, Role.ARMORER, PROT, UNB, MEND),
        LEGS("Leggings", Items.DIAMOND_LEGGINGS, Role.ARMORER, PROT, UNB, MEND, SWIFT),
        BOOTS("Boots", Items.DIAMOND_BOOTS, Role.ARMORER, PROT, UNB, MEND, FEATHER, DEPTH, SOUL),
        SWORD("Sword", Items.DIAMOND_SWORD, Role.WEAPONSMITH, SHARP, UNB, MEND, LOOT, FIRE, SWEEP),
        AXE("Axe", Items.DIAMOND_AXE, Role.WEAPONSMITH, SHARP, EFF, UNB, MEND, SILK, FORT),
        PICK("Pickaxe", Items.DIAMOND_PICKAXE, Role.TOOLSMITH, EFF, UNB, MEND, SILK, FORT);

        public final String label;
        public final Item item;
        public final Role role;
        public final List<Ench> options;

        Gear(String label, Item item, Role role, Ench... options) {
            this.label = label;
            this.item = item;
            this.role = role;
            this.options = List.of(options);
        }
    }

    public static final Set<Gear> enabled = EnumSet.noneOf(Gear.class);
    public static final Map<Gear, Map<Ench, Integer>> wanted = new EnumMap<>(Gear.class);
    public static final Map<Role, List<UUID>> villagers = new EnumMap<>(Role.class);
    public static BlockPos anvil = null;
    public static Role markRole = Role.STRING;

    public static int level(Gear g, Ench e) {
        return wanted.getOrDefault(g, Map.of()).getOrDefault(e, 0);
    }

    public static Map<Ench, Integer> picks(Gear g) {
        return wanted.getOrDefault(g, Map.of());
    }

    public static void cycle(Gear g, Ench e) {
        int c = level(g, e);
        int n = c >= e.max() ? 0 : c + 1;
        Map<Ench, Integer> m = wanted.computeIfAbsent(g, k -> new LinkedHashMap<>());
        if (n == 0) {
            m.remove(e);
        } else {
            m.put(e, n);
            // Silk Touch and Fortune cannot go together
            if (e.equals(SILK)) m.remove(FORT);
            if (e.equals(FORT)) m.remove(SILK);
        }
    }

    public static void toggle(Gear g) {
        if (!enabled.remove(g)) enabled.add(g);
    }

    public static List<UUID> villagers(Role r) {
        return villagers.computeIfAbsent(r, k -> new ArrayList<>());
    }

    public static void addVillager(Role r, UUID id) {
        for (List<UUID> l : villagers.values()) l.remove(id);
        villagers(r).add(id);
    }

    public static void clearMarks() {
        villagers.clear();
        anvil = null;
    }
}
