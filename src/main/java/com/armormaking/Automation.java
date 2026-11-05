package com.armormaking;

import com.armormaking.Config.Gear;
import com.armormaking.Config.Role;
import com.armormaking.Enchs.Ench;
import java.util.*;
import java.util.function.Predicate;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/** The step-by-step state machine. One small action per tick; any error stops it safely. */
public final class Automation {
    private Automation() {}

    static final ArrayDeque<Step> queue = new ArrayDeque<>();
    static boolean running = false;
    static int cooldown = 0;

    static final class Abort extends RuntimeException {
        Abort(String m) { super(m); }
    }

    abstract static class Step {
        /** @return true when finished */
        abstract boolean tick(Minecraft mc);
    }

    record Need(ResourceKey<Enchantment> key, String name, int lvl) {}

    static void pause(int ticks) { cooldown = ticks; }

    public static boolean isRunning() { return running; }

    // ------------------------------------------------------------ control

    public static void stop(String why) {
        queue.clear();
        boolean was = running;
        running = false;
        cooldown = 0;
        Minecraft mc = Minecraft.getInstance();
        try {
            if (mc.player != null && mc.player.containerMenu != mc.player.inventoryMenu) mc.player.closeContainer();
        } catch (Throwable ignored) { }
        if (was || why != null) Game.say(why == null ? "Stopped." : why);
    }

    public static void tick(Minecraft mc) {
        if (!running) return;
        if (mc.player == null || mc.level == null) { stop("Left the world, stopped."); return; }
        if (cooldown > 0) { cooldown--; return; }
        Step s = queue.peekFirst();
        if (s == null) { running = false; Game.say("All done!"); return; }
        try {
            if (s.tick(mc)) queue.removeFirstOccurrence(s);
        } catch (Abort a) {
            stop(a.getMessage());
        } catch (Throwable t) {
            t.printStackTrace();
            stop("Internal error, stopped safely: " + t);
        }
    }

    // ------------------------------------------------------------ plan

    public static void start(Minecraft mc) {
        if (mc.player == null) return;
        queue.clear();
        cooldown = 0;

        List<Gear> gears = new ArrayList<>();
        for (Gear g : Gear.values()) if (Config.enabled.contains(g)) gears.add(g);
        if (gears.isEmpty()) { Game.say("Open the menu (K) and switch at least one item ON."); return; }

        Map<Need, Integer> needs = new LinkedHashMap<>();
        Map<Gear, List<Need>> perGear = new EnumMap<>(Gear.class);
        for (Gear g : gears) {
            List<Need> l = new ArrayList<>();
            for (Map.Entry<Ench, Integer> e : Config.picks(g).entrySet()) {
                Need n = new Need(e.getKey().key(), e.getKey().name(), e.getValue());
                l.add(n);
                needs.merge(n, 1, Integer::sum);
            }
            perGear.put(g, l);
        }

        // check that the needed villagers / anvil were marked
        List<String> missing = new ArrayList<>();
        for (Gear g : gears) if (Config.villagers(g.role).isEmpty()) missing.add(g.role.label);
        if (!needs.isEmpty()) {
            if (Config.villagers(Role.LIBRARIAN).isEmpty()) missing.add(Role.LIBRARIAN.label);
            if (Config.anvil == null) missing.add(Role.ANVIL.label);
        }
        if (!missing.isEmpty()) {
            Game.say("Not marked yet: " + String.join(", ", new LinkedHashSet<>(missing)) + ". Use the menu (K) then look at it and press V.");
            return;
        }
        if (Config.villagers(Role.STRING).isEmpty()) Game.say("Warning: no String seller marked, emeralds cannot be refilled.");
        if (Config.villagers(Role.CLERIC).isEmpty()) Game.say("Warning: no Cleric marked, XP cannot be refilled.");

        int total = 0;
        for (int c : needs.values()) total += c;
        if (total > 0) {
            int haveBooks = Game.count(s -> s.is(Items.BOOK));
            int shelves = Math.max(0, (int) Math.ceil((total - haveBooks) / 3.0));
            if (shelves > 0) {
                queue.add(new Ensure(9 * shelves));
                queue.add(new Trade(Role.LIBRARIAN, o -> o.getResult().is(Items.BOOKSHELF),
                        s -> s.is(Items.BOOKSHELF), shelves, true, "bookshelves"));
                for (int i = 0; i < shelves; i++) queue.add(new PlaceBreak());
            }
            for (Map.Entry<Need, Integer> e : needs.entrySet()) {
                Need n = e.getKey();
                queue.add(new Ensure(64));
                queue.add(new Trade(Role.LIBRARIAN, o -> Game.isBook(o.getResult(), n.key(), n.lvl()),
                        s -> Game.isBook(s, n.key(), n.lvl()), e.getValue(), true, n.name() + " " + n.lvl()));
            }
        }
        for (Gear g : gears) {
            queue.add(new Ensure(64));
            queue.add(new Trade(g.role, o -> o.getResult().is(g.item), s -> s.is(g.item), 1, true, g.label));
        }
        for (Gear g : gears) {
            List<Need> l = perGear.get(g);
            if (!l.isEmpty()) queue.add(new AnvilStep(g, l));
        }
        running = true;
        Game.say("Started. Stand still near the marked villagers and the anvil. Press J to stop.");
    }

    // ------------------------------------------------------------ steps

    /** Makes sure we hold at least n emeralds by selling string to the fisherman. */
    static final class Ensure extends Step {
        final int n;
        int last = -1;
        Ensure(int n) { this.n = n; }

        boolean tick(Minecraft mc) {
            int have = Game.count(s -> s.is(Items.EMERALD));
            if (have >= n) return true;
            if (last >= 0 && have <= last) throw new Abort("Not enough emeralds (have " + have + ", need " + n + "). Put more string in the inventory.");
            if (Game.count(s -> s.is(Items.STRING)) == 0) throw new Abort("Not enough emeralds (have " + have + ", need " + n + ") and no string left to sell.");
            last = have;
            queue.addFirst(new Trade(Role.STRING, o -> o.getCostA().is(Items.STRING),
                    s -> s.is(Items.EMERALD), Integer.MAX_VALUE, false, "emeralds"));
            return false;
        }
    }

    /** Makes sure the player reaches a given XP level by buying and throwing XP bottles. */
    static final class EnsureLevels extends Step {
        final int need;
        int rounds = 0;
        EnsureLevels(int need) { this.need = need; }

        boolean tick(Minecraft mc) {
            if (mc.player.getAbilities().instabuild || mc.player.experienceLevel >= need) return true;
            if (++rounds > 6) throw new Abort("Could not reach XP level " + need + ".");
            queue.addFirst(new Throw());
            queue.addFirst(new Trade(Role.CLERIC, o -> o.getResult().is(Items.EXPERIENCE_BOTTLE),
                    s -> s.is(Items.EXPERIENCE_BOTTLE), 32, false, "XP bottles"));
            queue.addFirst(new Ensure(64));
            return false;
        }
    }

    /** Throws every XP bottle in the inventory at our own feet. */
    static final class Throw extends Step {
        int timer = 0;
        boolean tick(Minecraft mc) {
            LocalPlayer p = mc.player;
            if (Game.count(s -> s.is(Items.EXPERIENCE_BOTTLE)) == 0) return true;
            if (++timer > 600) throw new Abort("XP bottle throwing took too long.");
            if (p.containerMenu != p.inventoryMenu) { p.closeContainer(); return false; }
            p.setXRot(90f);
            if (timer % 8 == 1) Game.holdItem(s -> s.is(Items.EXPERIENCE_BOTTLE));
            if (timer % 8 == 5) Game.useItem();
            return false;
        }
    }

    /** Buys items from the villagers registered under one role. */
    static final class Trade extends Step {
        final Role role;
        final Predicate<MerchantOffer> offer;
        final Predicate<ItemStack> target;
        final int want;
        final boolean strict;
        final String what;
        int vi = 0, phase = 0, timer = 0, start = -1, retries = 0;

        Trade(Role role, Predicate<MerchantOffer> offer, Predicate<ItemStack> target, int want, boolean strict, String what) {
            this.role = role; this.offer = offer; this.target = target;
            this.want = want; this.strict = strict; this.what = what;
        }

        boolean tick(Minecraft mc) {
            LocalPlayer p = mc.player;
            if (start < 0) start = Game.count(target);
            List<UUID> vs = Config.villagers(role);
            if (vs.isEmpty()) throw new Abort("No villager marked as: " + role.label);
            int got = Game.count(target) - start;

            switch (phase) {
                case 0 -> {
                    if (got >= want || vi >= vs.size()) return finish(mc, got);
                    Entity e = Game.find(vs.get(vi));
                    if (e == null || e.distanceTo(p) > 4.5f) {
                        Game.say(role.label + " #" + (vi + 1) + " is not in reach, skipping.");
                        vi++;
                        return false;
                    }
                    Game.lookAt(e.getEyePosition());
                    Game.interactEntity(e);
                    phase = 1; timer = 0; pause(3);
                }
                case 1 -> {
                    if (p.containerMenu instanceof MerchantMenu m && !m.getOffers().isEmpty()) {
                        phase = 2; pause(2);
                    } else if (++timer > 40) {
                        Game.say("A villager did not open its trade screen, skipping.");
                        vi++; phase = 0;
                    }
                }
                case 2 -> {
                    if (!(p.containerMenu instanceof MerchantMenu m)) { vi++; phase = 0; return false; }
                    if (got >= want) { p.closeContainer(); phase = 0; return false; }
                    int idx = -1;
                    MerchantOffers os = m.getOffers();
                    for (int i = 0; i < os.size(); i++) {
                        MerchantOffer o = os.get(i);
                        if (!o.isOutOfStock() && offer.test(o)) { idx = i; break; }
                    }
                    if (idx < 0) { p.closeContainer(); vi++; phase = 0; return false; }
                    Game.selectTrade(m, idx);
                    phase = 3; timer = 0; pause(3);
                }
                case 3 -> {
                    if (!(p.containerMenu instanceof MerchantMenu m)) { vi++; phase = 0; return false; }
                    if (m.getSlot(2).hasItem()) {
                        Game.click(2, 0, ContainerInput.PICKUP);
                        phase = 4; timer = 0; pause(2);
                    } else if (++timer > 4) {
                        p.closeContainer(); vi++; phase = 0; // cannot afford / sold out
                    }
                }
                case 4 -> {
                    if (!(p.containerMenu instanceof MerchantMenu m)) { vi++; phase = 0; return false; }
                    ItemStack c = m.getCarried();
                    if (c.isEmpty()) { phase = 2; pause(2); return false; }
                    int s = Game.findPlaceSlot(m, c);
                    if (s < 0) throw new Abort("Inventory is full.");
                    if (++timer > 10) throw new Abort("Could not move the trade result into the inventory.");
                    Game.click(s, 0, ContainerInput.PICKUP);
                    pause(2);
                }
                default -> phase = 0;
            }
            return false;
        }

        private boolean finish(Minecraft mc, int got) {
            LocalPlayer p = mc.player;
            if (p.containerMenu != p.inventoryMenu) p.closeContainer();
            if (strict && got < want) {
                boolean canRefill = Game.count(s -> s.is(Items.EMERALD)) < 64
                        && Game.count(s -> s.is(Items.STRING)) > 0;
                if (canRefill && retries < 8) {
                    retries++; vi = 0; phase = 0;
                    queue.addFirst(new Ensure(64));
                    return false;
                }
                throw new Abort("Could not get " + what + " (got " + got + " of " + want
                        + "). The villager may not sell it, be sold out, or you lack emeralds/books.");
            }
            return true;
        }
    }

    /** Places one bookshelf on the floor next to the player and breaks it to get books. */
    static final class PlaceBreak extends Step {
        int phase = 0, timer = 0;
        BlockPos base, spot;

        boolean tick(Minecraft mc) {
            LocalPlayer p = mc.player;
            switch (phase) {
                case 0 -> {
                    if (Game.count(s -> s.is(Items.BOOKSHELF)) == 0) return true;
                    if (p.containerMenu != p.inventoryMenu) p.closeContainer();
                    spot = null;
                    for (Direction d : Direction.Plane.HORIZONTAL) {
                        BlockPos b = p.blockPosition().relative(d).below();
                        BlockPos a = b.above();
                        if (mc.level.getBlockState(a).isAir()
                                && mc.level.getBlockState(b).isFaceSturdy(mc.level, b, Direction.UP)) {
                            base = b; spot = a; break;
                        }
                    }
                    if (spot == null) throw new Abort("Need a free floor block next to you to place bookshelves.");
                    Game.holdItem(s -> s.is(Items.BOOKSHELF));
                    Game.lookAt(Vec3.atCenterOf(base).add(0, 0.5, 0));
                    phase = 1; pause(6);
                }
                case 1 -> {
                    Game.useBlock(base, Direction.UP);
                    phase = 2; timer = 0; pause(4);
                }
                case 2 -> {
                    if (!mc.level.getBlockState(spot).is(Blocks.BOOKSHELF)) {
                        if (++timer > 10) throw new Abort("Could not place the bookshelf.");
                        return false;
                    }
                    Game.lookAt(Vec3.atCenterOf(spot));
                    Game.startBreak(spot);
                    phase = 3; timer = 0;
                }
                case 3 -> {
                    if (mc.level.getBlockState(spot).isAir()) { phase = 4; pause(20); return false; }
                    if (++timer > 600) throw new Abort("Breaking the bookshelf took too long.");
                    Game.lookAt(Vec3.atCenterOf(spot));
                    Game.continueBreak(spot);
                }
                default -> { return true; }
            }
            return false;
        }
    }

    /** Applies the wanted books to one gear piece on the marked anvil. */
    static final class AnvilStep extends Step {
        final Gear g;
        final List<Need> books;
        int idx = 0, phase = 0, timer = 0;

        AnvilStep(Gear g, List<Need> books) { this.g = g; this.books = books; }

        boolean tick(Minecraft mc) {
            LocalPlayer p = mc.player;
            if (idx >= books.size()) {
                if (p.containerMenu != p.inventoryMenu) p.closeContainer();
                return true;
            }
            Need n = books.get(idx);
            switch (phase) {
                case 0 -> {
                    BlockPos a = Config.anvil;
                    if (a == null) throw new Abort("Anvil is not marked.");
                    if (!(p.containerMenu instanceof AnvilMenu)) {
                        if (a.distToCenterSqr(p.getEyePosition()) > 20.0) throw new Abort("Stand within reach of the anvil.");
                        Game.lookAt(Vec3.atCenterOf(a));
                        Game.useBlock(a, Direction.UP);
                    }
                    phase = 1; timer = 0; pause(3);
                }
                case 1 -> {
                    if (p.containerMenu instanceof AnvilMenu) { phase = 2; pause(2); }
                    else if (++timer > 30) throw new Abort("The anvil did not open.");
                }
                case 2 -> {
                    if (!(p.containerMenu instanceof AnvilMenu m)) { phase = 0; return false; }
                    int sg = Game.findSlot(m, 3, s -> s.is(g.item));
                    if (sg < 0) throw new Abort("No " + g.label + " in the inventory.");
                    int sb = Game.findSlot(m, 3, s -> Game.isBook(s, n.key(), n.lvl()));
                    if (sb < 0) throw new Abort("Missing book: " + n.name() + " " + n.lvl());
                    Game.click(sg, 0, ContainerInput.PICKUP);
                    Game.click(0, 0, ContainerInput.PICKUP);
                    Game.click(sb, 0, ContainerInput.PICKUP);
                    Game.click(1, 0, ContainerInput.PICKUP);
                    phase = 3; timer = 0; pause(4);
                }
                case 3 -> {
                    if (!(p.containerMenu instanceof AnvilMenu m)) { phase = 0; return false; }
                    if (m.getSlot(2).hasItem()) {
                        int cost = m.getCost();
                        if (!p.getAbilities().instabuild && p.experienceLevel < cost) {
                            p.closeContainer();
                            phase = 0;
                            queue.addFirst(new EnsureLevels(cost));
                            return false;
                        }
                        Game.click(2, 0, ContainerInput.QUICK_MOVE);
                        phase = 4; pause(4);
                    } else if (++timer > 15) {
                        throw new Abort("Anvil gave no result for " + g.label + " + " + n.name() + " " + n.lvl()
                                + " (too expensive or incompatible).");
                    }
                }
                case 4 -> { idx++; phase = 2; pause(2); }
                default -> phase = 0;
            }
            return false;
        }
    }
}
