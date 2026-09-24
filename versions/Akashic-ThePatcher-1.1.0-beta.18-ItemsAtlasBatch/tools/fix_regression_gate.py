from pathlib import Path
p=Path(r"C:\Users\User\Desktop\Codex\Akashic-ThePatcher-1.1.0-beta.18-ItemsAtlasBatch\tools\beta18-inherited-regression.py")
t=p.read_text(encoding="utf-8")
start=t.index('    if build.get("baseBeta15Sha256")')
end=t.index('\n\n    report = {', start)
replacement='''    if build.get("baseBeta17Sha256") != "18177c88421d675da07ab6dc7ed2b5d1437691d95b1b88eeec4c684814238cc7":
        raise AssertionError("Build report beta.17 baseline mismatch")
    if build.get("changedClasses") != [
        "com/akashic/thepatcher/AkashicThePatcherMod.class",
    ]:
        raise AssertionError("Unexpected changed class set")
    if build.get("addedClasses") != sorted([
        "dev/akashic/asynciconcompat/mixins/MixinTextureMapItemsUploadBatch.class",
        "dev/akashic/asynciconcompat/mixins/MixinTextureUtilItemsUploadBatch.class",
        "dev/akashic/asynciconcompat/upload/ItemsAtlasUploadBatcher.class",
    ]):
        raise AssertionError("Unexpected added class set")
'''
t=t[:start]+replacement+t[end:]
p.write_text(t,encoding="utf-8",newline="\n")
print("BETA18_REGRESSION_METADATA_GATE_FIXED")