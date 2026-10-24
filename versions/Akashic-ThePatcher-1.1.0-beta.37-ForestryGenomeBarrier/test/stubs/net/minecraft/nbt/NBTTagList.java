package net.minecraft.nbt;
import java.util.*;
public class NBTTagList extends NBTBase {
    public final List<NBTTagCompound> list = new ArrayList<NBTTagCompound>();
    public int func_74745_c() { return list.size(); }
    public NBTTagCompound func_150305_b(int i) { return i >= 0 && i < list.size() ? list.get(i) : new NBTTagCompound(); }
    public void func_74742_a(NBTBase b) { list.add((NBTTagCompound) b); }
    public String toString() { return list.toString(); }
}
