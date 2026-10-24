from pathlib import Path
import re
p=Path(r"C:\Users\User\Desktop\Codex\Akashic-Hodgepodge-AsyncCompat\src\main\java\dev\akashic\asynciconcompat\mixins\MixinTextureMapHybridAsync.java")
t=p.read_text(encoding="utf-8")
if "TextureUploadProfiler.begin(basePath);" not in t:
    t,n=re.subn(
        r'(private void akashic\$startAtlasProfile\(IResourceManager resourceManager, CallbackInfo ci\) \{\n\s*akashic\$atlasStartNanos = System\.nanoTime\(\);)',
        r'\1\n        TextureUploadProfiler.begin(basePath);',
        t, count=1)
    if n!=1: raise RuntimeError("start insertion failed")
if "TextureUploadProfiler.finish();" not in t:
    t,n=re.subn(
        r'(private void akashic\$finishAtlasProfile\(IResourceManager resourceManager, CallbackInfo ci\) \{\n\s*final long totalNanos = System\.nanoTime\(\) - akashic\$atlasStartNanos;)',
        r'\1\n        TextureUploadProfiler.finish();',
        t, count=1)
    if n!=1: raise RuntimeError("finish insertion failed")
p.write_text(t,encoding="utf-8",newline="\n")
print("UPLOAD_PROFILER_HOOKS_INSERTED")