import java.io.InputStream;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import net.minecraft.client.renderer.culling.ICamera;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.launchwrapper.IClassTransformer;
import net.minecraft.util.AxisAlignedBB;
import net.minecraftforge.client.IItemRenderer;
import net.minecraftforge.client.MinecraftForgeClient;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.analysis.Analyzer;
import org.objectweb.asm.tree.analysis.BasicVerifier;

/**
 * beta.52 gate. The view covers x in [0, 10]; players (0.6 wide) stand at the left edge or farther out:
 *   shapes-dir patched|unpatched [other]
 *     renderEntities (1.7.10 shape: entity test, then the leash holder) and Angelica's isEntityVisible, loaded
 *     through the bundled transformer as LaunchClassLoader would. Patched: a player just outside the view holding a
 *     3D item (renderer handles EQUIPPED) is drawn, by both tests; nobody else changes (empty hand, an item whose
 *     renderer does not handle EQUIPPED, a plain item, a mob holding nothing, a player far out, a non-living entity);
 *     a leashed mob out of view is drawn when its holder holds a 3D item; the entities' own boxes are untouched.
 *     Unpatched: only the entities in view. "other": a renderEntities with one frustum test is left alone.
 *   real angelica.jar
 *     the real CeleritasWorldRenderer of that Angelica jar through the transformer: patched once, the cullBox call
 *     right before isInfiniteExtentsBox, every method passes the ASM BasicVerifier.
 */
public final class HeldItemCullGate {

    static final String RENDER_GLOBAL = "net.minecraft.client.renderer.RenderGlobal";
    static final String CELERITAS = "com.gtnewhorizons.angelica.rendering.celeritas.CeleritasWorldRenderer";
    static final String TRANSFORMER = "com.akashic.thepatcher.module.helditemcull.HeldItemCullTransformer";

    public static void main(String[] args) throws Exception {
        if ("real".equals(args[0])) {
            real(args[1]);
            return;
        }
        final Path shapes = Paths.get(args[0]);
        final String expect = args[1];
        final boolean other = args.length > 2 && "other".equals(args[2]);
        final TransformingLoader loader = new TransformingLoader(HeldItemCullGate.class.getClassLoader(), shapes,
            other ? Arrays.asList(RENDER_GLOBAL) : Arrays.asList(RENDER_GLOBAL, CELERITAS), TRANSFORMER);

        final Item rod = new Item();
        final Item rodWithoutHandRenderer = new Item();
        final Item plain = new Item();
        MinecraftForgeClient.registerItemRenderer(rod, renderer(true));
        MinecraftForgeClient.registerItemRenderer(rodWithoutHandRenderer, renderer(false));

        final List<Entity> all = new ArrayList<Entity>();
        final EntityLivingBase inView = add(all, new EntityLivingBase("in-view", 5, new ItemStack(rod)));
        final EntityLivingBase edgeRod = add(all, new EntityLivingBase("edge-rod", -1.5, new ItemStack(rod)));
        final EntityLivingBase edgeEmpty = add(all, new EntityLivingBase("edge-empty", -1.5, null));
        add(all, new EntityLivingBase("edge-entity-renderer-only", -1.5, new ItemStack(rodWithoutHandRenderer)));
        add(all, new EntityLivingBase("edge-plain-item", -1.5, new ItemStack(plain)));
        add(all, new EntityLivingBase("far-rod", -10, new ItemStack(rod)));
        add(all, new Entity("edge-not-living", -1.5));
        final EntityLiving leashed = add(all, new EntityLiving("leashed-out", -20, null));
        leashed.leashHolder = new EntityLivingBase("holder-edge-rod", -1.5, new ItemStack(rod));
        final EntityLiving leashedPlain = add(all, new EntityLiving("leashed-plain-out", -20, null));
        leashedPlain.leashHolder = edgeEmpty;
        final AxisAlignedBB edgeBox = edgeRod.boundingBox;
        final String edgeBefore = edgeBox.toString();

        final Class<?> renderGlobal = Class.forName(RENDER_GLOBAL, true, loader);
        final Object global = renderGlobal.newInstance();
        @SuppressWarnings("unchecked")
        final List<Entity> entities = (List<Entity>) renderGlobal.getField("entities").get(global);
        entities.addAll(all);
        final ICamera camera = new ICamera() {
            @Override
            public boolean isBoundingBoxInFrustum(AxisAlignedBB box) {
                return box.field_72336_d >= 0 && box.field_72340_a <= 10;
            }
        };
        renderGlobal.getMethod("renderEntities", EntityLivingBase.class, ICamera.class, float.class)
            .invoke(global, null, camera, 0F);
        final List<String> drawn = labels(renderGlobal.getField("rendered").get(global));

        final List<String> visible = new ArrayList<String>();
        if (!other) {
            final Class<?> celeritas = Class.forName(CELERITAS, true, loader);
            final Object renderer = celeritas.newInstance();
            celeritas.getField("minX").setDouble(renderer, 0);
            celeritas.getField("maxX").setDouble(renderer, 10);
            final Method isVisible = celeritas.getMethod("isEntityVisible", Entity.class);
            for (Entity e : all) {
                if ((Boolean) isVisible.invoke(renderer, e)) visible.add(e.label);
            }
        }
        System.out.println("GATE transformed=" + loader.transformed + " renderEntities=" + drawn + " isEntityVisible=" + visible);
        check(edgeRod.boundingBox == edgeBox && edgeBefore.equals(edgeBox.toString()), "an entity's own box changed");
        check(drawn.contains(inView.label), "the player in view was not drawn");

        final List<String> grown = Arrays.asList("in-view", "edge-rod", "leashed-out");
        final List<String> plainView = Arrays.asList("in-view");
        if ("patched".equals(expect)) {
            check(loader.transformed == 2, "transformed " + loader.transformed + " of 2");
            check(drawn.equals(grown), "renderEntities drew " + drawn + ", expected " + grown);
            check(visible.equals(Arrays.asList("in-view", "edge-rod")), "isEntityVisible: " + visible);
        } else {
            check(loader.transformed == 0, "transformed " + loader.transformed);
            check(drawn.equals(plainView), "renderEntities drew " + drawn + ", expected " + plainView);
            check(other || visible.equals(plainView), "isEntityVisible: " + visible);
        }
        System.out.println("HELD_ITEM_CULL_GATE PASS expect=" + expect + (other ? " shape=other" : ""));
    }

    static void real(String jar) throws Exception {
        final byte[] original;
        try (ZipFile zip = new ZipFile(jar)) {
            final ZipEntry entry = zip.getEntry(CELERITAS.replace('.', '/') + ".class");
            check(entry != null, "no CeleritasWorldRenderer in " + jar);
            try (InputStream in = zip.getInputStream(entry)) {
                original = TransformingLoader.read(in);
            }
        }
        final IClassTransformer transformer = (IClassTransformer) Class.forName(TRANSFORMER).newInstance();
        final byte[] patched = transformer.transform(CELERITAS, CELERITAS, original);
        check(!Arrays.equals(original, patched), "real CeleritasWorldRenderer not patched");
        check(Arrays.equals(patched, transformer.transform(CELERITAS, CELERITAS, patched)), "second pass changed it");
        final ClassNode node = new ClassNode();
        new ClassReader(patched).accept(node, 0);
        int hooks = 0;
        for (Object o : node.methods) {
            final MethodNode m = (MethodNode) o;
            new Analyzer(new BasicVerifier()).analyze(node.name, m);
            for (AbstractInsnNode insn = m.instructions.getFirst(); insn != null; insn = insn.getNext()) {
                if (insn instanceof MethodInsnNode && ((MethodInsnNode) insn).name.equals("cullBox")) {
                    hooks++;
                    check(m.name.equals("isEntityVisible"), "cullBox in " + m.name);
                    final AbstractInsnNode store = insn.getNext();
                    final AbstractInsnNode load = store.getNext();
                    final AbstractInsnNode call = load.getNext();
                    check(call instanceof MethodInsnNode && ((MethodInsnNode) call).name.equals("isInfiniteExtentsBox"),
                        "cullBox not right before isInfiniteExtentsBox");
                }
            }
        }
        check(hooks == 1, hooks + " cullBox calls");
        System.out.println("HELD_ITEM_CULL_GATE PASS real=" + Paths.get(jar).getFileName() + " bytes=" + original.length
            + "->" + patched.length);
    }

    static IItemRenderer renderer(final boolean equipped) {
        return new IItemRenderer() {
            @Override
            public boolean handleRenderType(ItemStack item, ItemRenderType type) {
                return type == ItemRenderType.ENTITY || equipped && type == ItemRenderType.EQUIPPED;
            }
        };
    }

    static <T extends Entity> T add(List<Entity> all, T entity) {
        all.add(entity);
        return entity;
    }

    static List<String> labels(Object list) {
        final List<String> out = new ArrayList<String>();
        for (Object o : (List<?>) list) out.add(((Entity) o).label);
        return out;
    }

    static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
