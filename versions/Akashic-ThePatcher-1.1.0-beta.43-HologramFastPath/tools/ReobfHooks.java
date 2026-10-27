package com.akashic.compatlab.tools;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.stream.Stream;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/**
 * Offline-only: renames the Minecraft member references of classes compiled against the MCP-named stubs
 * to their SRG names (what a 1.7.10 client runs with), from an exact (kind, owner, name, descriptor) table.
 * Fails on any net/minecraft/ member reference the table does not cover (fields named akashic$* are added
 * by the bundle's own transformers and stay as they are).
 *
 *   ReobfHooks <srg-members.txt> <classes-in> <classes-out>
 */
public final class ReobfHooks {

    public static void main(String[] args) throws Exception {
        if (args.length != 3) {
            throw new IllegalArgumentException("srg-members.txt in out");
        }
        final Map<String, String> table = new HashMap<>();
        for (String line : Files.readAllLines(Paths.get(args[0]))) {
            if (line.startsWith("#") || line.trim().isEmpty()) continue;
            final String[] p = line.trim().split(" ");
            if (p.length != 5) throw new AssertionError("bad line: " + line);
            if (table.put(p[0] + " " + p[1] + " " + p[2] + " " + p[3], p[4]) != null) throw new AssertionError("dup: " + line);
        }
        final Path in = Paths.get(args[1]), out = Paths.get(args[2]);
        final List<Path> classes = new ArrayList<>();
        try (Stream<Path> walk = Files.walk(in)) {
            walk.filter(p -> p.toString().endsWith(".class")).forEach(classes::add);
        }
        Collections.sort(classes);
        final TreeSet<String> missing = new TreeSet<>(), used = new TreeSet<>();
        for (Path file : classes) {
            final ClassWriter writer = new ClassWriter(0);
            new ClassReader(Files.readAllBytes(file)).accept(new ClassVisitor(Opcodes.ASM9, writer) {
                @Override
                public MethodVisitor visitMethod(int access, String name, String desc, String sig, String[] exc) {
                    return new MethodVisitor(Opcodes.ASM9, super.visitMethod(access, name, desc, sig, exc)) {
                        @Override
                        public void visitFieldInsn(int opcode, String owner, String fname, String fdesc) {
                            super.visitFieldInsn(opcode, owner, map("F", owner, fname, fdesc), fdesc);
                        }

                        @Override
                        public void visitMethodInsn(int opcode, String owner, String mname, String mdesc, boolean itf) {
                            super.visitMethodInsn(opcode, owner, map("M", owner, mname, mdesc), mdesc, itf);
                        }
                    };
                }

                String map(String kind, String owner, String name, String desc) {
                    if (!owner.startsWith("net/minecraft/") || name.startsWith("akashic$") || name.startsWith("<")) {
                        return name;
                    }
                    final String key = kind + " " + owner + " " + name + " " + desc;
                    final String srg = table.get(key);
                    if (srg == null) {
                        missing.add(key);
                        return name;
                    }
                    used.add(key);
                    return srg;
                }
            }, 0);
            final Path target = out.resolve(in.relativize(file));
            Files.createDirectories(target.getParent());
            Files.write(target, writer.toByteArray());
        }
        if (!missing.isEmpty()) {
            throw new AssertionError("unmapped Minecraft references: " + missing);
        }
        if (used.size() != table.size()) {
            final TreeSet<String> unused = new TreeSet<>(table.keySet());
            unused.removeAll(used);
            throw new AssertionError("stale srg-members.txt entries: " + unused);
        }
        System.out.println("REOBF_HOOKS PASS classes=" + classes.size() + " references=" + used.size());
    }

    private ReobfHooks() {}
}
