package net.minecraft.entity.player;

import net.minecraft.inventory.Container;
import net.minecraft.network.NetHandlerPlayServer;

public class EntityPlayerMP extends EntityPlayer {
    public NetHandlerPlayServer field_71135_a;
    public boolean dead;
    public int resyncs;

    public void func_71120_a(Container c) {
        resyncs++;
    }

    public void func_143004_u() {
    }
}
