from pathlib import Path
src=Path(r"C:\Users\User\Desktop\Codex\Akashic-ThePatcher-1.1.0-beta.22-WorldRenderStateProbeFix\tools\beta22-inherited-regression.py")
dst=Path(r"C:\Users\User\Desktop\Codex\Akashic-ThePatcher-1.1.0-beta.23-VertexCompatIsolation\tools\beta23-inherited-regression.py")
t=src.read_text(encoding="utf-8")
t=t.replace("Akashic-ThePatcher-1.1.0-beta.22-WorldRenderStateProbeFix","Akashic-ThePatcher-1.1.0-beta.23-VertexCompatIsolation")
t=t.replace("!Akashic-ThePatcher-1.1.0-beta.22-WorldRenderStateProbeFix.jar","!Akashic-ThePatcher-1.1.0-beta.23-VertexCompatIsolation.jar")
t=t.replace("1843eec1e4c0a98b67fcbf74d98f55cef8a51ac33eadff95c07b3010d72f1fff","5a3c563b61f26a24d84b3b4fe89e626afa82e87f35e1ccd51bd9e39acd6097c3")
t=t.replace("BETA22_","BETA23_")
t=t.replace("beta22-regression","beta23-regression")
start=t.index('    if build.get("baseBeta21Sha256")')
end=t.index("\n\n    report = {",start)
replacement='''    if build.get("baseBeta22Sha256") != "1843eec1e4c0a98b67fcbf74d98f55cef8a51ac33eadff95c07b3010d72f1fff":
        raise AssertionError("Build report beta.22 baseline mismatch")
    if build.get("changedClasses") != [
        "com/akashic/thepatcher/AkashicThePatcherMod.class",
    ]:
        raise AssertionError("Unexpected changed class set")
    if build.get("addedClasses") != []:
        raise AssertionError("Unexpected added classes")
    if build.get("removedClasses") != sorted([
        "dev/akashic/asynciconcompat/graphics/WorldRenderStateProbe.class",
        "dev/akashic/asynciconcompat/mixins/MixinCeleritasWorldStateProbe.class",
    ]):
        raise AssertionError("Unexpected removed class set")
'''
dst.write_text(t[:start]+replacement+t[end:],encoding="utf-8",newline="\n")
print("BETA23_REGRESSION_SCRIPT_PATCHED")