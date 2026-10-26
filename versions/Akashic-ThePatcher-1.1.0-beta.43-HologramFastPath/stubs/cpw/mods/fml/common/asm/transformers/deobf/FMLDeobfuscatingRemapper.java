package cpw.mods.fml.common.asm.transformers.deobf;

/** Shape only (Forge 10.13.4.1614): notch -> SRG names; the real one extends ASM's Remapper. */
public class FMLDeobfuscatingRemapper {
    public static final FMLDeobfuscatingRemapper INSTANCE = new FMLDeobfuscatingRemapper();

    public String mapMethodName(String owner, String name, String desc) { return name; }

    public String mapMethodDesc(String desc) { return desc; }
}
