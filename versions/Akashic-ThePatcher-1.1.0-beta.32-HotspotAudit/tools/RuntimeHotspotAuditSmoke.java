package com.akashic.compatlab.tools;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.util.Arrays;
import java.util.jar.JarFile;
import java.util.zip.ZipEntry;

import com.akashic.thepatcher.module.hotspotaudit.HotspotAuditTransformer;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

public final class RuntimeHotspotAuditSmoke {

    private static final String HOOKS =
        "com/akashic/thepatcher/module/startupaudit/FmlPhaseAuditHooks";

    private RuntimeHotspotAuditSmoke() {}

    public static void main(String[] args)
        throws Exception {

        if (args.length != 1) {
            throw new IllegalArgumentException(
                "usage: <mods-directory>");
        }

        File mods = new File(args[0]);

        Spec[] specs =
            new Spec[] {
                spec(
                    "Decocraft-2.4.2_1.7.10.jar",
                    "com.mia.props.Props"),

                spec(
                    "geckolib-unofficial-1.7.10-1.0.4.jar",
                    "software.bernie.geckolib3.GeckoLib"),

                spec(
                    "ChromatiCraft 1.7.10 V33a.jar",
                    "Reika.ChromatiCraft.Auxiliary.APIImpl.CCAPICore"),

                spec(
                    "ChromatiCraft 1.7.10 V33a.jar",
                    "Reika.ChromatiCraft.ChromaRecipes"),

                spec(
                    "ChromatiCraft 1.7.10 V33a.jar",
                    "Reika.ChromatiCraft.World.Dimension.ChromaDimensionManager"),

                spec(
                    "ChromatiCraft 1.7.10 V33a.jar",
                    "Reika.ChromatiCraft.Registry.ChromaStructures"),

                spec(
                    "ChromatiCraft 1.7.10 V33a.jar",
                    "Reika.ChromatiCraft.Auxiliary.ChromaDescriptions"),

                spec(
                    "ChromatiCraft 1.7.10 V33a.jar",
                    "Reika.ChromatiCraft.ChromatiCraft"),

                spec(
                    "CustomNPC-Plus-1.10.2.jar",
                    "noppes.npcs.config.LoadConfiguration"),

                spec(
                    "CustomNPC-Plus-1.10.2.jar",
                    "noppes.npcs.enchants.EnchantInterface"),

                spec(
                    "CustomNPC-Plus-1.10.2.jar",
                    "noppes.npcs.CustomItems"),

                spec(
                    "CustomNPC-Plus-1.10.2.jar",
                    "noppes.npcs.CommonProxy"),

                spec(
                    "CustomNPC-Plus-1.10.2.jar",
                    "noppes.npcs.client.ClientProxy"),

                spec(
                    "Eln-1.24.6.jar",
                    "mods.eln.config.ConfigHandler"),

                spec(
                    "Eln-1.24.6.jar",
                    "mods.eln.mqtt.MqttManager"),

                spec(
                    "mobsinfo-0.5.20-GTNH.jar",
                    "com.kuba6000.mobsinfo.ClientProxy"),

                spec(
                    "mobsinfo-0.5.20-GTNH.jar",
                    "com.kuba6000.mobsinfo.loader.MobRecipeLoader"),

                spec(
                    "mobsinfo-0.5.20-GTNH.jar",
                    "com.kuba6000.mobsinfo.loader.VillagerTradesLoader")
            };

        HotspotAuditTransformer transformer =
            new HotspotAuditTransformer();

        int changedClasses = 0;
        int enterHooks = 0;
        int exitHooks = 0;

        for (Spec spec : specs) {
            File jar =
                new File(
                    mods,
                    spec.jar);

            if (!jar.isFile()) {
                throw new IllegalStateException(
                    "missing jar "
                        + jar);
            }

            byte[] original =
                readClass(
                    jar,
                    spec.className);

            byte[] patched =
                transformer.transform(
                    spec.className,
                    spec.className,
                    original);

            if (patched == null
                || Arrays.equals(
                    original,
                    patched)) {

                throw new IllegalStateException(
                    "class was not transformed "
                        + spec.className);
            }

            ClassNode node =
                new ClassNode();

            new ClassReader(patched)
                .accept(node, 0);

            int classEnters =
                countHooks(
                    node,
                    "spanEnter");

            int classExits =
                countHooks(
                    node,
                    "spanExit");

            if (classEnters < 1
                || classExits < classEnters) {

                throw new IllegalStateException(
                    "hook mismatch "
                        + spec.className
                        + " enter="
                        + classEnters
                        + " exit="
                        + classExits);
            }

            changedClasses++;
            enterHooks += classEnters;
            exitHooks += classExits;

            System.out.println(
                "HOTSPOT_SMOKE class="
                    + spec.className
                    + " spans="
                    + classEnters
                    + " original="
                    + original.length
                    + " patched="
                    + patched.length);
        }

        if (changedClasses != 18) {
            throw new IllegalStateException(
                "expected 18 transformed classes, got "
                    + changedClasses);
        }

        if (enterHooks != 20
            || exitHooks < 20) {

            throw new IllegalStateException(
                "expected 20 span entries and at least 20 exits, enter="
                    + enterHooks
                    + " exit="
                    + exitHooks);
        }

        System.out.println(
            "BETA32_RUNTIME_HOTSPOT_SMOKE PASS classes="
                + changedClasses
                + " spans="
                + enterHooks);
    }

    private static int countHooks(
        ClassNode node,
        String name) {

        int count = 0;

        for (Object object : node.methods) {
            MethodNode method =
                (MethodNode) object;

            for (AbstractInsnNode insn =
                     method.instructions.getFirst();
                 insn != null;
                 insn = insn.getNext()) {

                if (!(insn instanceof MethodInsnNode)) {
                    continue;
                }

                MethodInsnNode call =
                    (MethodInsnNode) insn;

                if (HOOKS.equals(call.owner)
                    && name.equals(call.name)
                    && "(Ljava/lang/String;)V".equals(
                        call.desc)) {

                    count++;
                }
            }
        }

        return count;
    }

    private static byte[] readClass(
        File jar,
        String className)
        throws Exception {

        String entryName =
            className.replace('.', '/')
                + ".class";

        JarFile file =
            new JarFile(jar);

        try {
            ZipEntry entry =
                file.getEntry(entryName);

            if (entry == null) {
                throw new IllegalStateException(
                    "missing class "
                        + className
                        + " in "
                        + jar.getName());
            }

            InputStream input =
                file.getInputStream(entry);

            try {
                ByteArrayOutputStream output =
                    new ByteArrayOutputStream();

                byte[] buffer =
                    new byte[8192];

                int read;

                while ((read =
                        input.read(buffer)) >= 0) {

                    output.write(
                        buffer,
                        0,
                        read);
                }

                return output.toByteArray();
            } finally {
                input.close();
            }
        } finally {
            file.close();
        }
    }

    private static Spec spec(
        String jar,
        String className) {

        return new Spec(
            jar,
            className);
    }

    private static final class Spec {
        final String jar;
        final String className;

        Spec(
            String jar,
            String className) {

            this.jar = jar;
            this.className = className;
        }
    }
}
