package net.minecraft.inventory;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;

/** Test double: slots [0, top) are the GUI (chest/menu), the rest the player's inventory. Modes 0 and 1 only. */
public class Container {
    public List<Slot> field_75151_b = new ArrayList<Slot>();
    public int field_75152_c;
    public final int top;
    private final Set<EntityPlayer> notPresent = new HashSet<EntityPlayer>();

    public Container(int windowId, int top, int bottom) {
        this.field_75152_c = windowId;
        this.top = top;
        for (int i = 0; i < top + bottom; i++) {
            field_75151_b.add(new Slot());
        }
    }

    public Slot func_75139_a(int i) {
        return field_75151_b.get(i);
    }

    public boolean func_75129_b(EntityPlayer p) {
        return !notPresent.contains(p);
    }

    public void func_75128_a(EntityPlayer p, boolean present) {
        if (present) {
            notPresent.remove(p);
        } else {
            notPresent.add(p);
        }
    }

    public ItemStack func_75144_a(int slot, int button, int mode, EntityPlayer player) {
        if (slot < 0) {
            return null;
        }
        Slot s = field_75151_b.get(slot);
        ItemStack before = ItemStack.func_77944_b(s.stack);
        if (mode == 0) { // pick up / put down (whole stack)
            ItemStack cursor = player.cursor;
            player.cursor = s.stack;
            s.stack = cursor;
        } else if (mode == 1 && s.stack != null) { // shift: first empty slot of the other section
            int from = slot < top ? top : 0, to = slot < top ? field_75151_b.size() : top;
            for (int i = from; i < to; i++) {
                if (field_75151_b.get(i).stack == null) {
                    field_75151_b.get(i).stack = s.stack;
                    s.stack = null;
                    break;
                }
            }
        }
        return before;
    }

    /** What closing the window does with the cursor: it goes into the player's inventory. */
    public void close(EntityPlayer player) {
        if (player.cursor != null) {
            for (int i = top; i < field_75151_b.size(); i++) {
                if (field_75151_b.get(i).stack == null) {
                    field_75151_b.get(i).stack = player.cursor;
                    break;
                }
            }
            player.cursor = null;
        }
    }
}
