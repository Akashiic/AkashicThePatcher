from pathlib import Path
src=Path(r"C:\Users\User\Desktop\Codex\Akashic-ThePatcher-1.1.0-beta.23-VertexCompatIsolation\tools\beta23-inherited-regression.py")
dst=Path(r"C:\Users\User\Desktop\Codex\Akashic-ThePatcher-1.1.0-beta.24-TerrainStagingFallback\tools\beta24-inherited-regression.py")
t=src.read_text(encoding="utf-8")
t=t.replace("Akashic-ThePatcher-1.1.0-beta.23-VertexCompatIsolation","Akashic-ThePatcher-1.1.0-beta.24-TerrainStagingFallback")
t=t.replace("!Akashic-ThePatcher-1.1.0-beta.23-VertexCompatIsolation.jar","!Akashic-ThePatcher-1.1.0-beta.24-TerrainStagingFallback.jar")
t=t.replace("5a3c563b61f26a24d84b3b4fe89e626afa82e87f35e1ccd51bd9e39acd6097c3","41c66f3567a66544118e71ed0ab60343fa352c56b89836f8668af959376f5922")
t=t.replace("BETA23_","BETA24_")
t=t.replace("beta23-regression","beta24-regression")
t=t.replace("beta.23 VertexCompatIsolation candidate","beta.24 TerrainStagingFallback candidate")
start=t.index('    if build.get("baseBeta22Sha256")')
end=t.index("\n\n    report = {",start)
replacement='''    if build.get("baseBeta23Sha256") != "5a3c563b61f26a24d84b3b4fe89e626afa82e87f35e1ccd51bd9e39acd6097c3":
        raise AssertionError("Build report beta.23 baseline mismatch")
    if build.get("changedClasses") != [
        "com/akashic/thepatcher/AkashicThePatcherMod.class",
    ]:
        raise AssertionError("Unexpected changed class set")
    if build.get("addedClasses") != [
        "dev/akashic/asynciconcompat/mixins/MixinRenderRegionManagerStagingFallback.class",
    ]:
        raise AssertionError("Unexpected added class set")
    if build.get("removedClasses") != []:
        raise AssertionError("Unexpected removed class set")
'''
dst.write_text(t[:start]+replacement+t[end:],encoding="utf-8",newline="\n")
print("BETA24_REGRESSION_SCRIPT_PATCHED")