package cpw.mods.fml.common.asm.transformers.deobf;

/**
 * Compile-time shape only (Forge 10.13.4.1614): notch -> MCP class / SRG member names. The real one extends ASM's
 * Remapper (map, mapDesc and mapMethodDesc come from there). Never on a gate's run-time class path: there the
 * transformer falls back to the names as they are.
 */
public class FMLDeobfuscatingRemapper {
    public static final FMLDeobfuscatingRemapper INSTANCE = new FMLDeobfuscatingRemapper();

    public String map(String typeName) { return typeName; }

    public String mapMethodName(String owner, String name, String desc) { return name; }

    public String mapFieldName(String owner, String name, String desc) { return name; }

    public String mapDesc(String desc) { return desc; }

    public String mapMethodDesc(String desc) { return desc; }
}
