package com.armormaking;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;

public class ArmorMakingMod implements ClientModInitializer {
    private static KeyMapping menuKey, markKey, stopKey;

    @Override
    public void onInitializeClient() {
        KeyMapping.Category cat = KeyMapping.Category.register(Identifier.fromNamespaceAndPath("armormaking", "main"));
        menuKey = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.armormaking.menu", InputConstants.Type.KEYSYM, InputConstants.KEY_K, cat));
        markKey = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.armormaking.mark", InputConstants.Type.KEYSYM, InputConstants.KEY_V, cat));
        stopKey = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.armormaking.stop", InputConstants.Type.KEYSYM, InputConstants.KEY_J, cat));

        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            try {
                if (mc.player == null || mc.level == null) return;
                while (menuKey.consumeClick()) mc.setScreen(new ConfigScreen());
                while (markKey.consumeClick()) mark(mc);
                while (stopKey.consumeClick()) Automation.stop("Stopped.");
                Automation.tick(mc);
            } catch (Throwable t) {
                t.printStackTrace();
                try { Automation.stop("Error, stopped safely: " + t); } catch (Throwable ignored) { }
            }
        });
    }

    private static void mark(Minecraft mc) {
        HitResult h = mc.hitResult;
        Config.Role role = Config.markRole;
        if (h instanceof EntityHitResult eh && eh.getEntity().getType() == EntityType.VILLAGER) {
            if (role == Config.Role.ANVIL) { Game.say("Current mark type is ANVIL. Change it in the menu (K) to a villager type."); return; }
            Config.addVillager(role, eh.getEntity().getUUID());
            Game.say("Villager marked as " + role.label + " (total " + Config.villagers(role).size() + ").");
        } else if (h instanceof BlockHitResult bh && mc.level.getBlockState(bh.getBlockPos()).is(BlockTags.ANVIL)) {
            Config.anvil = bh.getBlockPos().immutable();
            Game.say("Anvil marked.");
        } else {
            Game.say("Look directly at a villager (or an anvil) and press V.");
        }
    }
}
