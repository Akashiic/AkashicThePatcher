from pathlib import Path
src=Path(r"C:\Users\User\Desktop\Codex\Akashic-ThePatcher-1.1.0-beta.21-WorldRenderStateRepair\tools\beta21-inherited-regression.py")
dst=Path(r"C:\Users\User\Desktop\Codex\Akashic-ThePatcher-1.1.0-beta.22-WorldRenderStateProbeFix\tools\beta22-inherited-regression.py")
t=src.read_text(encoding="utf-8")
t=t.replace("Akashic-ThePatcher-1.1.0-beta.21-WorldRenderStateRepair","Akashic-ThePatcher-1.1.0-beta.22-WorldRenderStateProbeFix")
t=t.replace("!Akashic-ThePatcher-1.1.0-beta.21-WorldRenderStateRepair.jar","!Akashic-ThePatcher-1.1.0-beta.22-WorldRenderStateProbeFix.jar")
t=t.replace("79dcb3f15653dc764c7075a06eafd0fb494261c8db35d3af3b5a2d830e1720f7","1843eec1e4c0a98b67fcbf74d98f55cef8a51ac33eadff95c07b3010d72f1fff")
t=t.replace("BETA21_","BETA22_")
t=t.replace("beta21-regression","beta22-regression")
t=t.replace("beta.21 WorldRenderStateRepair candidate","beta.22 WorldRenderStateProbeFix candidate")
start=t.index('    if build.get("baseBeta20Sha256")')
end=t.index("\n\n    report = {",start)
replacement='''    if build.get("baseBeta21Sha256") != "79dcb3f15653dc764c7075a06eafd0fb494261c8db35d3af3b5a2d830e1720f7":
        raise AssertionError("Build report beta.21 baseline mismatch")
    if build.get("changedClasses") != sorted([
        "com/akashic/thepatcher/AkashicThePatcherMod.class",
        "dev/akashic/asynciconcompat/graphics/WorldRenderStateProbe.class",
    ]):
        raise AssertionError("Unexpected changed class set")
'''
dst.write_text(t[:start]+replacement+t[end:],encoding="utf-8",newline="\n")
print("BETA22_REGRESSION_SCRIPT_PATCHED")