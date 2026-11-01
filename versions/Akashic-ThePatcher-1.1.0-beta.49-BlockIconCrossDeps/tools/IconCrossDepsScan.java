import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import org.objectweb.asm.tree.analysis.*;
import java.io.*; import java.util.*; import java.util.zip.*;

/**
 * beta.49: finds the blocks for CompatHooks.CROSS_DEPENDENT_BLOCKS.  java -cp asm-all-5.0.3.jar:. IconCrossDepsScan <mods dir>
 * registerBlockIcons (func_149651_a) and every method of the SAME class it reaches
 * (transitively), flagging (a) icon getters on a receiver other than this, (b) GETFIELD of an IIcon / IIcon[]
 * field on a receiver other than this, (c) GETSTATIC of an IIcon / IIcon[] field owned by another class.
 */
public class IconCrossDepsScan {
  static final Set<String> GETTERS = new HashSet<>(Arrays.asList(
    "func_149691_a(II)Lnet/minecraft/util/IIcon;", "func_149733_h(I)Lnet/minecraft/util/IIcon;",
    "func_149673_e(Lnet/minecraft/world/IBlockAccess;IIII)Lnet/minecraft/util/IIcon;",
    "func_77617_a(I)Lnet/minecraft/util/IIcon;", "func_77650_f(Lnet/minecraft/item/ItemStack;)Lnet/minecraft/util/IIcon;",
    "func_77954_c()Lnet/minecraft/util/IIcon;", "func_77618_c(II)Lnet/minecraft/util/IIcon;"));
  static final String REG = "(Lnet/minecraft/client/renderer/texture/IIconRegister;)V";
  static boolean iconType(String d) { return d.equals("Lnet/minecraft/util/IIcon;") || d.equals("[Lnet/minecraft/util/IIcon;"); }
  public static void main(String[] a) throws Exception {
    Map<String, Set<String>> out = new TreeMap<>();
    for (File f : new File(a[0]).listFiles()) {
      if (!f.getName().endsWith(".jar")) continue;
      try (ZipFile z = new ZipFile(f)) {
        for (Enumeration<? extends ZipEntry> en = z.entries(); en.hasMoreElements();) {
          ZipEntry e = en.nextElement();
          if (!e.getName().endsWith(".class")) continue;
          ClassNode cn = new ClassNode();
          try { new ClassReader(z.getInputStream(e)).accept(cn, ClassReader.SKIP_FRAMES); } catch (Exception ex) { continue; }
          Map<String, MethodNode> byKey = new HashMap<>();
          for (Object o : cn.methods) { MethodNode m = (MethodNode) o; byKey.put(m.name + m.desc, m); }
          MethodNode root = byKey.get("func_149651_a" + REG);
          if (root == null) continue;
          Deque<MethodNode> work = new ArrayDeque<>(); Set<String> seen = new HashSet<>();
          work.add(root); seen.add(root.name + root.desc);
          while (!work.isEmpty()) {
            MethodNode m = work.poll();
            Frame[] frames;
            try { frames = new Analyzer(new SourceInterpreter()).analyze(cn.name, m); } catch (Exception ex) { continue; }
            boolean inst = (m.access & Opcodes.ACC_STATIC) == 0;
            AbstractInsnNode[] insns = m.instructions.toArray();
            for (int i = 0; i < insns.length; i++) {
              AbstractInsnNode in = insns[i]; Frame fr = frames[i];
              if (fr == null) continue;
              String hit = null;
              if (in instanceof MethodInsnNode) {
                MethodInsnNode mi = (MethodInsnNode) in;
                if (mi.owner.equals(cn.name) && byKey.containsKey(mi.name + mi.desc) && seen.add(mi.name + mi.desc)) work.add(byKey.get(mi.name + mi.desc));
                if (GETTERS.contains(mi.name + mi.desc) && in.getOpcode() != Opcodes.INVOKESTATIC) {
                  int nargs = Type.getArgumentTypes(mi.desc).length;
                  if (!isSelf((SourceValue) fr.getStack(fr.getStackSize() - 1 - nargs), inst)) hit = "getter " + mi.name + " on " + describe((SourceValue) fr.getStack(fr.getStackSize() - 1 - nargs));
                }
              } else if (in instanceof FieldInsnNode && iconType(((FieldInsnNode) in).desc)) {
                FieldInsnNode fi = (FieldInsnNode) in;
                if (in.getOpcode() == Opcodes.GETFIELD && !isSelf((SourceValue) fr.getStack(fr.getStackSize() - 1), inst))
                  hit = "field " + fi.owner + "." + fi.name + " on " + describe((SourceValue) fr.getStack(fr.getStackSize() - 1));
                else if (in.getOpcode() == Opcodes.GETSTATIC && !fi.owner.equals(cn.name))
                  hit = "static field " + fi.owner + "." + fi.name;
              }
              if (hit != null) out.computeIfAbsent(cn.name.replace('/', '.') + "  [" + f.getName() + "]", k -> new TreeSet<>()).add((m == root ? "" : "via " + m.name + ": ") + hit);
            }
          }
        }
      }
    }
    for (Map.Entry<String, Set<String>> en : out.entrySet()) {
      System.out.println(en.getKey());
      for (String s : en.getValue()) System.out.println("    " + s);
    }
    System.out.println("TOTAL_CLASSES " + out.size());
  }
  static boolean isSelf(SourceValue v, boolean inst) {
    if (v.insns.isEmpty()) return false;
    for (Object o : v.insns) { AbstractInsnNode s = (AbstractInsnNode) o;
      if (!(inst && s instanceof VarInsnNode && ((VarInsnNode) s).var == 0)) return false; }
    return true;
  }
  static String describe(SourceValue v) {
    StringBuilder b = new StringBuilder();
    for (Object o : v.insns) { AbstractInsnNode s = (AbstractInsnNode) o;
      if (s instanceof FieldInsnNode) b.append(((FieldInsnNode) s).owner).append('.').append(((FieldInsnNode) s).name);
      else if (s instanceof MethodInsnNode) b.append("ret ").append(((MethodInsnNode) s).owner).append('.').append(((MethodInsnNode) s).name);
      else if (s instanceof VarInsnNode) b.append("local").append(((VarInsnNode) s).var);
      else b.append("op").append(s.getOpcode());
      b.append(' '); }
    return b.toString().trim();
  }
}
