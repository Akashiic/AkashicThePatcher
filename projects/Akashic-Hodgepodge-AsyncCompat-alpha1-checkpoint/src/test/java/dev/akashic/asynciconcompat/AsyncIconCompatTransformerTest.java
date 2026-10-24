package dev.akashic.asynciconcompat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import dev.akashic.asynciconcompat.core.AsyncIconCompatTransformer;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import org.junit.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

public class AsyncIconCompatTransformerTest {

    private static Path modsDir() {
        String configured = System.getenv("FORBIDDEN_MODS_DIR");
        assertNotNull("FORBIDDEN_MODS_DIR must be set", configured);
        return Paths.get(configured);
    }

    @Test
    public void patchesChiselListAddExactlyOnce() throws Exception {
        Path jar = findJar("chisel-2.17.32-GTNH");
        byte[] input = readEntry(jar, "team/chisel/ctmlib/TextureSubmap.class");
        byte[] output = new AsyncIconCompatTransformer().transform(
            "team.chisel.ctmlib.TextureSubmap",
            "team.chisel.ctmlib.TextureSubmap",
            input);

        MethodNode ctor = constructor(output);
        assertEquals(1, countCall(ctor, "dev/akashic/asynciconcompat/CompatHooks", "addChiselSubmapSynchronized"));
        assertEquals(0, countCall(ctor, "java/util/List", "add"));
    }

    @Test
    public void patchesMekanismEventBusExactlyOnce() throws Exception {
        Path jar = findJar("Mekanism-Community-Edition-1.7.10-9.10.48-AkashicPerf.3-ALL");
        byte[] input = readEntry(jar, "mekanism/client/render/block/TextureSubmap.class");
        byte[] output = new AsyncIconCompatTransformer().transform(
            "mekanism.client.render.block.TextureSubmap",
            "mekanism.client.render.block.TextureSubmap",
            input);

        MethodNode ctor = constructor(output);
        assertEquals(1, countCall(ctor, "dev/akashic/asynciconcompat/CompatHooks", "registerForgeEventHandlerSynchronized"));
        assertEquals(0, countCall(ctor, "cpw/mods/fml/common/eventhandler/EventBus", "register"));
    }

    private static Path findJar(String prefix) throws IOException {
        try (java.util.stream.Stream<Path> stream = Files.list(modsDir())) {
            return stream.filter(p -> p.getFileName().toString().startsWith(prefix))
                .findFirst().orElseThrow(() -> new IOException("Missing jar prefix " + prefix));
        }
    }

    private static byte[] readEntry(Path jar, String entryName) throws IOException {
        try (ZipFile zip = new ZipFile(jar.toFile())) {
            ZipEntry entry = zip.getEntry(entryName);
            if (entry == null) throw new IOException("Missing " + entryName + " in " + jar);
            try (java.io.InputStream in = zip.getInputStream(entry)) {
                return readAll(in);
            }
        }
    }

    private static byte[] readAll(java.io.InputStream in) throws IOException {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int read;
        while ((read = in.read(buffer)) >= 0) out.write(buffer, 0, read);
        return out.toByteArray();
    }

    private static MethodNode constructor(byte[] bytes) {
        ClassNode node = new ClassNode();
        new ClassReader(bytes).accept(node, 0);
        for (Object object : node.methods) {
            MethodNode method = (MethodNode) object;
            if ("<init>".equals(method.name) && "(Lnet/minecraft/util/IIcon;II)V".equals(method.desc)) return method;
        }
        throw new AssertionError("Expected constructor missing");
    }

    private static int countCall(MethodNode method, String owner, String name) {
        int count = 0;
        for (AbstractInsnNode insn = method.instructions.getFirst(); insn != null; insn = insn.getNext()) {
            if (!(insn instanceof MethodInsnNode)) continue;
            MethodInsnNode call = (MethodInsnNode) insn;
            if (owner.equals(call.owner) && name.equals(call.name)) count++;
        }
        return count;
    }
}