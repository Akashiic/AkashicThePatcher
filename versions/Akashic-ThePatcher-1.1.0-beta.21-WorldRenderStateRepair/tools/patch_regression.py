from pathlib import Path
src=Path(r"C:\Users\User\Desktop\Codex\Akashic-ThePatcher-1.1.0-beta.20-StartupLifecycleProfiler\tools\beta20-inherited-regression.py")
dst=Path(r"C:\Users\User\Desktop\Codex\Akashic-ThePatcher-1.1.0-beta.21-WorldRenderStateRepair\tools\beta21-inherited-regression.py")
t=src.read_text(encoding="utf-8")
t=t.replace("Akashic-ThePatcher-1.1.0-beta.20-StartupLifecycleProfiler","Akashic-ThePatcher-1.1.0-beta.21-WorldRenderStateRepair")
t=t.replace("!Akashic-ThePatcher-1.1.0-beta.20-StartupLifecycleProfiler.jar","!Akashic-ThePatcher-1.1.0-beta.21-WorldRenderStateRepair.jar")
t=t.replace("00a29e58ebb428e915d3579ba3f6b9bceb6464d29d4f8c08ef7fe20285e3a48a","79dcb3f15653dc764c7075a06eafd0fb494261c8db35d3af3b5a2d830e1720f7")
t=t.replace("BETA20_","BETA21_")
t=t.replace("beta20-regression","beta21-regression")
t=t.replace("beta.20 StartupLifecycleProfiler candidate","beta.21 WorldRenderStateRepair candidate")
start=t.index('    if build.get("baseBeta19Sha256")')
end=t.index("\n\n    report = {",start)
replacement='''    if build.get("baseBeta20Sha256") != "00a29e58ebb428e915d3579ba3f6b9bceb6464d29d4f8c08ef7fe20285e3a48a":
        raise AssertionError("Build report beta.20 baseline mismatch")
    if build.get("changedClasses") != [
        "com/akashic/thepatcher/AkashicThePatcherMod.class",
    ]:
        raise AssertionError("Unexpected changed class set")
    expected_added = sorted([
        "dev/akashic/asynciconcompat/graphics/WorldRenderStateProbe.class",
        "dev/akashic/asynciconcompat/mixins/MixinCeleritasWorldStateProbe.class",
    ])
    if build.get("addedClasses") != expected_added:
        raise AssertionError("Unexpected added class set")
    expected_removed = sorted([
        "dev/akashic/asynciconcompat/mixins/MixinLoadControllerStartupProfiler.class",
        "dev/akashic/asynciconcompat/startup/StartupLifecycleProfiler$1.class",
        "dev/akashic/asynciconcompat/startup/StartupLifecycleProfiler$2.class",
        "dev/akashic/asynciconcompat/startup/StartupLifecycleProfiler$DispatchSample.class",
        "dev/akashic/asynciconcompat/startup/StartupLifecycleProfiler$ModTiming.class",
        "dev/akashic/asynciconcompat/startup/StartupLifecycleProfiler$Phase.class",
        "dev/akashic/asynciconcompat/startup/StartupLifecycleProfiler.class",
    ])
    if build.get("removedClasses") != expected_removed:
        raise AssertionError("Unexpected removed class set")
'''
dst.write_text(t[:start]+replacement+t[end:],encoding="utf-8",newline="\n")
print("BETA21_REGRESSION_SCRIPT_PATCHED")