package net.minecraft.nbt;
import java.util.*;
/** Minimal SRG-named NBT compound for driving Forestry outside Minecraft. */
public class NBTTagCompound extends NBTBase {
    public final Map<String, Object> map = new HashMap<String, Object>();
    public NBTTagList func_150295_c(String key, int type) { Object o = map.get(key); return o instanceof NBTTagList ? (NBTTagList) o : new NBTTagList(); }
    public byte func_74771_c(String key) { Object o = map.get(key); return o instanceof Byte ? (Byte) o : 0; }
    public void func_74774_a(String key, byte value) { map.put(key, value); }
    public String func_74779_i(String key) { Object o = map.get(key); return o instanceof String ? (String) o : ""; }
    public void func_74778_a(String key, String value) { map.put(key, value); }
    public void func_74782_a(String key, NBTBase value) { map.put(key, value); }
    public boolean func_74764_b(String key) { return map.containsKey(key); }
    public String toString() { return map.toString(); }
}
