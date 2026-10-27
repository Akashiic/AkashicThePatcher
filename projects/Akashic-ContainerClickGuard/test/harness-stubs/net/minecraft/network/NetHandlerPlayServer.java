package net.minecraft.network;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.network.play.client.C0EPacketClickWindow;

/** Test double of Crucible's processClickWindow: dead check, window/presence check, InventoryClickEvent, mismatch. */
public class NetHandlerPlayServer implements INetHandler {
    public interface Plugin {
        /** InventoryClickEvent: return true to cancel (DENY). */
        boolean cancel(C0EPacketClickWindow click);
    }

    public EntityPlayerMP field_147369_b;
    public Plugin plugin = click -> false;
    public int events;

    public void func_147351_a(C0EPacketClickWindow p) {
        EntityPlayerMP player = field_147369_b;
        if (player.dead) {
            return;
        }
        if (player.field_71070_bA.field_75152_c != p.func_149548_c() || !player.field_71070_bA.func_75129_b(player)) {
            return;
        }
        events++;
        if (plugin.cancel(p)) {
            player.func_71120_a(player.field_71070_bA);
            return;
        }
        ItemStack result = player.field_71070_bA.func_75144_a(p.func_149544_d(), p.func_149543_e(), p.func_149542_h(), player);
        if (!ItemStack.same(p.func_149546_g(), result)) {
            player.field_71070_bA.func_75128_a(player, false);
            player.func_71120_a(player.field_71070_bA);
        }
    }
}
