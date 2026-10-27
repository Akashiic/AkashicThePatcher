package net.minecraft.inventory;

import java.util.List;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;

public abstract class Container {
    public List<Slot> field_75151_b; // inventorySlots
    public int field_75152_c;        // windowId

    public Slot func_75139_a(int index) { // getSlot
        throw new UnsupportedOperationException("stub");
    }

    public ItemStack func_75144_a(int slot, int button, int mode, EntityPlayer player) { // slotClick
        throw new UnsupportedOperationException("stub");
    }

    public boolean func_75129_b(EntityPlayer player) { // isPlayerNotUsingContainer (true = clicks accepted)
        throw new UnsupportedOperationException("stub");
    }

    public void func_75128_a(EntityPlayer player, boolean present) { // setPlayerIsPresent
        throw new UnsupportedOperationException("stub");
    }
}
