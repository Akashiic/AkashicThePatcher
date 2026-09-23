package com.akashic.compatlab.tools;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.util.Arrays;

import com.akashic.thepatcher.module.cinematic.CinematicAngelicaCompatTransformer;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

public final class ApplyCinematicAngelicaCompat {
    private static final String RAW =
        "br.com.redeakashic.cinematic.render.RawScope";
    private static final String VSYNC =
        "com.gtnewhorizons.angelica.glsm.backend.Lwjgl2GLRenderBackend";

    private ApplyCinematicAngelicaCompat() {}

    public static void main(String[] args) throws Exception {
        if (args.length != 4) {
            throw new IllegalArgumentException("target mode input.class output.class");
        }
        final String target=args[0];
        final String mode=args[1];
        final Path input=Paths.get(args[2]);        final Path output=Paths.get(args[3]);
        byte[] supplied=Files.readAllBytes(input);

        System.clearProperty("akashic.cinematicAngelicaStateFence.disable");
        System.clearProperty("akashic.angelicaVSyncGuard.disable");

        if ("disabled".equals(mode)) {
            if ("raw".equals(target)) {
                System.setProperty("akashic.cinematicAngelicaStateFence.disable","true");
            } else {
                System.setProperty("akashic.angelicaVSyncGuard.disable","true");
            }
        } else if ("shapeMismatch".equals(mode)) {
            supplied=breakShape(target,supplied);
        } else if (!"exact".equals(mode) && !"fingerprintMismatch".equals(mode)) {
            throw new IllegalArgumentException("unknown mode "+mode);
        }

        final String className="raw".equals(target)?RAW:VSYNC;
        final CinematicAngelicaCompatTransformer transformer=
            new CinematicAngelicaCompatTransformer();
        final byte[] result=transformer.transform(className,className,supplied);
        Files.createDirectories(output.getParent());
        Files.write(output,result);

        final boolean changed=!Arrays.equals(supplied,result);
        if ("exact".equals(mode)) {
            if (!changed) {
                throw new IllegalStateException(target+" exact transform did not change bytes");
            }
            if ("raw".equals(target)
                && CinematicAngelicaCompatTransformer.rawScopePatchCount()!=1) {
                throw new IllegalStateException("raw patch count mismatch");            }
            if ("vsync".equals(target)
                && CinematicAngelicaCompatTransformer.vsyncPatchCount()!=1) {
                throw new IllegalStateException("vsync patch count mismatch");
            }
        } else if (changed) {
            throw new IllegalStateException(target+" "+mode+" unexpectedly changed bytes");
        }

        System.out.println(
            "BETA10_TARGET "+target.toUpperCase()+"_"+mode.toUpperCase()+"_PASS"
            +" input="+sha256(supplied)
            +" output="+sha256(result)
            +" rawCount="+CinematicAngelicaCompatTransformer.rawScopePatchCount()
            +" vsyncCount="+CinematicAngelicaCompatTransformer.vsyncPatchCount());
    }

    private static byte[] breakShape(String target,byte[] input) {
        final ClassNode node=new ClassNode();
        new ClassReader(input).accept(node,0);
        boolean changed=false;
        for (MethodNode method:node.methods) {
            if ("raw".equals(target) && "close".equals(method.name)) {
                method.name="cloze";
                changed=true;
                break;
            }
            if ("vsync".equals(target) && "applyVSyncMode".equals(method.name)) {
                for (AbstractInsnNode insn=method.instructions.getFirst();
                     insn!=null;insn=insn.getNext()) {
                    if (insn instanceof MethodInsnNode) {
                        final MethodInsnNode call=(MethodInsnNode)insn;                        if ("org/lwjgl/opengl/Display".equals(call.owner)
                            && "setVSyncEnabled".equals(call.name)) {
                            call.name="setVSyncEnabledBroken";
                            changed=true;
                            break;
                        }
                    }
                }
            }
            if (changed) {
                break;
            }
        }
        if (!changed) {
            throw new IllegalStateException("shape mutation target not found");
        }
        final ClassWriter writer=new ClassWriter(0);
        node.accept(writer);
        return writer.toByteArray();
    }

    private static String sha256(byte[] bytes)throws Exception {
        final byte[] hash=MessageDigest.getInstance("SHA-256").digest(bytes);
        final StringBuilder out=new StringBuilder(hash.length*2);
        for(byte value:hash) {
            out.append(String.format("%02x",value&0xff));
        }
        return out.toString();
    }
}
