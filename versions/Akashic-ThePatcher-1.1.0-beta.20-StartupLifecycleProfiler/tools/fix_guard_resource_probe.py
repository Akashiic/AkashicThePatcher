from pathlib import Path
p=Path(r"C:\Users\User\Desktop\Codex\Akashic-ThePatcher-1.1.0-beta.20-StartupLifecycleProfiler\src\ForestryC2RuntimeGuard.java")
t=p.read_text(encoding="utf-8")
old='''    private static boolean hasUniversalRewrite() {
        try {
            Class.forName(
                "com.akashic.thepatcher.module.forestry.ForestryGenomeCompat",
                false,
                ForestryC2RuntimeGuard.class.getClassLoader());
            Class.forName(
                "com.akashic.thepatcher.module.forestry.ForestryGenomeC2Transformer",
                false,
                ForestryC2RuntimeGuard.class.getClassLoader());
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }
'''
new='''    private static boolean hasUniversalRewrite() {
        try {
            final ClassLoader loader =
                ForestryC2RuntimeGuard.class.getClassLoader();

            return loader.getResource(
                    "com/akashic/thepatcher/module/forestry/ForestryGenomeCompat.class")
                    != null
                && loader.getResource(
                    "com/akashic/thepatcher/module/forestry/ForestryGenomeC2Transformer.class")
                    != null;
        } catch (Throwable ignored) {
            return false;
        }
    }
'''
if old not in t:
    raise SystemExit("hasUniversalRewrite anchor not found")
p.write_text(t.replace(old,new),encoding="utf-8",newline="\n")
print("GUARD_RESOURCE_PROBE_FIXED")