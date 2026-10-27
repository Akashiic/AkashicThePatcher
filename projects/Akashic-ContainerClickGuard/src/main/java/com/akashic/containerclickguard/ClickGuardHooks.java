package com.akashic.containerclickguard;

import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import cpw.mods.fml.relauncher.Side;
import java.lang.reflect.Field;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.Container;
import net.minecraft.item.ItemStack;
import net.minecraft.network.play.client.C0EPacketClickWindow;

/**
 * Called from the patched mod packet handlers (see {@link ClickGuardTransformer}). Runs on the server thread: 1.7.10
 * processes custom payloads (and so FML channel handlers) inside the network tick, like every other packet.
 *
 * Member names are the SRG names the server uses at run time (compiled against SRG-named stubs).
 */
public final class ClickGuardHooks {
    static final String INFINITY_CHEST = "wanion.avaritiaddons.block.chest.infinity.ContainerInfinityChest";
    private static final int LOG_LIMIT = 50;

    /** C0EPacketClickWindow fields, in the order windowId, slot, button, actionNumber, clickedItem, mode. */
    private static final String[] PACKET_FIELDS = {
        "field_149554_a", "field_149552_b", "field_149553_c", "field_149550_d", "field_149551_e", "field_149549_f"};

    private static Field[] packetFields;
    private static boolean packetFieldsBroken;
    /** Action numbers of the clicks we build; negative so they never meet the client's own (0, 1, 2, ...). */
    private static short nextActionNumber = -1;
    private static int logged;

    private ClickGuardHooks() {
    }

    /**
     * Replaces {@code player.openContainer.slotClick(slot, button, mode, player)} in InventoryTweaks'
     * ClickMessageHandler (InvTweaks already checked the window id). The click is handed to
     * NetHandlerPlayServer.processClickWindow as a C0EPacketClickWindow, so it takes the same path as a vanilla
     * click: dead-player check, InventoryClickEvent / InventoryDragEvent (a plugin can cancel it), resync on
     * cancel, Backhand's and Et Futurum's processClickWindow mixins. InvTweaks ignores the return value.
     */
    public static ItemStack invTweaksSlotClick(Container container, int slot, int button, int mode, EntityPlayer player) {
        if (!(player instanceof EntityPlayerMP) || ((EntityPlayerMP) player).field_71135_a == null) {
            return null;
        }
        EntityPlayerMP mp = (EntityPlayerMP) player;
        int size = container.field_75151_b.size();
        if (slot != -999 && (slot < -1 || slot >= size)) {
            report(mp, "InventoryTweaks click on slot " + slot + " of a " + size + "-slot container; dropped");
            mp.func_71120_a(container);
            return null;
        }
        // What a vanilla client sends as clickedItem: the stack before the click (slotClick returns that for modes
        // 0 and 1). A mismatch is harmless - processClickWindow then resends the whole container.
        ItemStack expected = slot >= 0 ? ItemStack.func_77944_b(container.func_75139_a(slot).func_75211_c()) : null;
        C0EPacketClickWindow packet = clickPacket(container.field_75152_c, slot, button, mode, expected, nextActionNumber());
        if (packet == null) {
            // Fail closed: without the packet the click cannot be checked, so it is dropped and the client resynced.
            mp.func_71120_a(container);
            return null;
        }
        boolean wasPresent = container.func_75129_b(mp);
        mp.field_71135_a.func_147351_a(packet);
        // On a clickedItem mismatch vanilla stops accepting clicks in this window until the client confirms the
        // resync. InvTweaks sends a whole sort in one go and never relied on that, so keep accepting them, as before.
        if (wasPresent && mp.field_71070_bA == container && !container.func_75129_b(mp)) {
            container.func_75128_a(mp, true);
        }
        return null;
    }

    /**
     * Head of Avaritiaddons' InfinityChestClick handler: true drops the message. Its GUI only sends it while an
     * Infinity Chest is open; with any other container (a plugin menu included) it would run slotClick with no
     * InventoryClickEvent.
     */
    public static boolean blockInfinityChestClick(MessageContext ctx) {
        if (ctx.side != Side.SERVER) {
            return false;
        }
        EntityPlayerMP player = ctx.getServerHandler().field_147369_b;
        Container open = player.field_71070_bA;
        if (open != null && isInfinityChest(open.getClass())) {
            return false;
        }
        report(player, "InfinityChestClick while " + (open == null ? "no container" : open.getClass().getName())
                + " is open; dropped");
        return true;
    }

    /**
     * Head of Avaritiaddons' InfinityChestSlotSync and InfinityChestSyncAllSlots handlers: true drops the message.
     * The server sends these to the client; received on the server they would put any client-chosen item into the
     * open Infinity Chest.
     */
    public static boolean blockServerBoundSync(MessageContext ctx) {
        if (ctx.side != Side.SERVER) {
            return false;
        }
        report(ctx.getServerHandler().field_147369_b, "server-to-client Infinity Chest sync message sent by the client; dropped");
        return true;
    }

    static boolean isInfinityChest(Class<?> c) {
        for (; c != null; c = c.getSuperclass()) {
            if (INFINITY_CHEST.equals(c.getName())) {
                return true;
            }
        }
        return false;
    }

    static C0EPacketClickWindow clickPacket(int windowId, int slot, int button, int mode, ItemStack clickedItem, short action) {
        Field[] f = packetFields();
        if (f == null) {
            return null;
        }
        try {
            // The server has only the no-argument constructor (the other one is client-only).
            C0EPacketClickWindow packet = new C0EPacketClickWindow();
            f[0].setInt(packet, windowId);
            f[1].setInt(packet, slot);
            f[2].setInt(packet, button);
            f[3].setShort(packet, action);
            f[4].set(packet, clickedItem);
            f[5].setInt(packet, mode);
            return packet;
        } catch (ReflectiveOperationException | RuntimeException e) {
            packetFieldsBroken = true;
            Log.error("cannot fill C0EPacketClickWindow; InventoryTweaks clicks are dropped from now on", e);
            return null;
        }
    }

    private static Field[] packetFields() {
        if (packetFields == null && !packetFieldsBroken) {
            try {
                Field[] f = new Field[PACKET_FIELDS.length];
                for (int i = 0; i < f.length; i++) {
                    f[i] = C0EPacketClickWindow.class.getDeclaredField(PACKET_FIELDS[i]);
                    f[i].setAccessible(true);
                }
                packetFields = f;
            } catch (ReflectiveOperationException | RuntimeException e) {
                packetFieldsBroken = true;
                Log.error("C0EPacketClickWindow fields not found; InventoryTweaks clicks are dropped", e);
            }
        }
        return packetFieldsBroken ? null : packetFields;
    }

    private static short nextActionNumber() {
        short n = nextActionNumber;
        nextActionNumber = n == Short.MIN_VALUE ? -1 : (short) (n - 1);
        return n;
    }

    private static void report(EntityPlayer player, String what) {
        if (logged < LOG_LIMIT) {
            logged++;
            Log.warn(player.func_70005_c_() + ": " + what + (logged == LOG_LIMIT ? " (further reports suppressed)" : ""));
        }
    }
}
