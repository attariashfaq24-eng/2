package com.armormaking;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;

public final class Enchs {
    private Enchs() {}

    public record Ench(String name, ResourceKey<Enchantment> key, int max) {}

    public static final Ench PROT = new Ench("Protection", Enchantments.PROTECTION, 4);
    public static final Ench UNB = new Ench("Unbreaking", Enchantments.UNBREAKING, 3);
    public static final Ench MEND = new Ench("Mending", Enchantments.MENDING, 1);
    public static final Ench RESP = new Ench("Respiration", Enchantments.RESPIRATION, 3);
    public static final Ench AQUA = new Ench("Aqua Affinity", Enchantments.AQUA_AFFINITY, 1);
    public static final Ench SWIFT = new Ench("Swift Sneak", Enchantments.SWIFT_SNEAK, 3);
    public static final Ench FEATHER = new Ench("Feather Falling", Enchantments.FEATHER_FALLING, 4);
    public static final Ench DEPTH = new Ench("Depth Strider", Enchantments.DEPTH_STRIDER, 3);
    public static final Ench SOUL = new Ench("Soul Speed", Enchantments.SOUL_SPEED, 3);
    public static final Ench SHARP = new Ench("Sharpness", Enchantments.SHARPNESS, 5);
    public static final Ench LOOT = new Ench("Looting", Enchantments.LOOTING, 3);
    public static final Ench FIRE = new Ench("Fire Aspect", Enchantments.FIRE_ASPECT, 2);
    public static final Ench SWEEP = new Ench("Sweeping Edge", Enchantments.SWEEPING_EDGE, 3);
    public static final Ench EFF = new Ench("Efficiency", Enchantments.EFFICIENCY, 5);
    public static final Ench SILK = new Ench("Silk Touch", Enchantments.SILK_TOUCH, 1);
    public static final Ench FORT = new Ench("Fortune", Enchantments.FORTUNE, 3);
}
