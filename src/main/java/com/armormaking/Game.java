package com.armormaking;

import java.util.function.Predicate;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerboundSelectTradePacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Every call that talks to Minecraft internals lives here.
 * If a Minecraft update renames something, this is the file to fix.
 */
final class Game {
    private Game() {}

    static Minecraft mc() { return Minecraft.getInstance(); }

    static void say(String s) {
        if (mc().player != null) mc().player.sendSystemMessage(Component.literal("[ArmorMaking] " + s));
    }

    /** Counts items in the 36 normal inventory slots. */
    static int count(Predicate<ItemStack> p) {
        var inv = mc().player.getInventory();
        int n = 0;
        for (int i = 0; i < 36; i++) {
            ItemStack s = inv.getItem(i);
            if (!s.isEmpty() && p.test(s)) n += s.getCount();
        }
        return n;
    }

    static Entity find(UUID id) {
        if (mc().level == null) return null;
        for (Entity e : mc().level.entitiesForRendering()) {
            if (e.getUUID().equals(id)) return e;
        }
        return null;
    }

    static void lookAt(Vec3 t) {
        var p = mc().player;
        Vec3 e = p.getEyePosition();
        double dx = t.x - e.x, dy = t.y - e.y, dz = t.z - e.z;
        double h = Math.sqrt(dx * dx + dz * dz);
        p.setYRot((float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0));
        p.setXRot((float) -Math.toDegrees(Math.atan2(dy, h)));
    }

    static void interactEntity(Entity e) {
        mc().gameMode.interact(mc().player, e, new EntityHitResult(e), InteractionHand.MAIN_HAND);
    }

    static void useBlock(BlockPos p, Direction face) {
        Vec3 hit = Vec3.atCenterOf(p).add(face.getStepX() * 0.5, face.getStepY() * 0.5, face.getStepZ() * 0.5);
        mc().gameMode.useItemOn(mc().player, InteractionHand.MAIN_HAND, new BlockHitResult(hit, face, p, false));
    }

    static void useItem() {
        mc().gameMode.useItem(mc().player, InteractionHand.MAIN_HAND);
    }

    static void startBreak(BlockPos p) {
        mc().gameMode.startDestroyBlock(p, Direction.UP);
        mc().player.swing(InteractionHand.MAIN_HAND);
    }

    static void continueBreak(BlockPos p) {
        mc().gameMode.continueDestroyBlock(p, Direction.UP);
        mc().player.swing(InteractionHand.MAIN_HAND);
    }

    static void click(int slot, int button, ContainerInput type) {
        AbstractContainerMenu m = mc().player.containerMenu;
        mc().gameMode.handleContainerInput(m.containerId, slot, button, type, mc().player);
    }

    static void selectTrade(MerchantMenu m, int index) {
        m.setSelectionHint(index);
        m.tryMoveItems(index);
        mc().getConnection().send(new ServerboundSelectTradePacket(index));
    }

    /** First slot (index >= from) of the open menu holding a matching stack, or -1. */
    static int findSlot(AbstractContainerMenu m, int from, Predicate<ItemStack> p) {
        for (int i = from; i < m.slots.size(); i++) {
            ItemStack s = m.getSlot(i).getItem();
            if (!s.isEmpty() && p.test(s)) return i;
        }
        return -1;
    }

    /** Where to put the stack held on the cursor: a stack to merge with, else an empty slot (inventory part only). */
    static int findPlaceSlot(AbstractContainerMenu m, ItemStack cursor) {
        for (int i = 3; i < m.slots.size(); i++) {
            ItemStack s = m.getSlot(i).getItem();
            if (!s.isEmpty() && ItemStack.isSameItemSameComponents(s, cursor)
                    && s.getCount() + cursor.getCount() <= s.getMaxStackSize()) return i;
        }
        for (int i = 3; i < m.slots.size(); i++) {
            if (!m.getSlot(i).hasItem()) return i;
        }
        return -1;
    }

    /** Puts a matching item into the selected hotbar slot (swap if needed). Wait a few ticks after calling. */
    static boolean holdItem(Predicate<ItemStack> p) {
        var inv = mc().player.getInventory();
        for (int i = 0; i < 36; i++) {
            ItemStack s = inv.getItem(i);
            if (s.isEmpty() || !p.test(s)) continue;
            if (i < 9) inv.setSelectedSlot(i);
            else click(i, inv.getSelectedSlot(), ContainerInput.SWAP);
            return true;
        }
        return false;
    }

    /** True if the stack is an enchanted book holding exactly this one enchantment at exactly this level. */
    static boolean isBook(ItemStack s, ResourceKey<Enchantment> key, int lvl) {
        if (!s.is(Items.ENCHANTED_BOOK)) return false;
        ItemEnchantments ie = s.get(DataComponents.STORED_ENCHANTMENTS);
        if (ie == null || ie.size() != 1) return false;
        for (Holder<Enchantment> h : ie.keySet()) {
            if (h.is(key) && ie.getLevel(h) == lvl) return true;
        }
        return false;
    }
}
