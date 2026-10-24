from pathlib import Path
p=Path(r"C:\Users\User\Desktop\Codex\Akashic-AtlasUploadBatcher\src\main\java\dev\akashic\asynciconcompat\upload\ItemsAtlasUploadBatcher.java")
t=p.read_text(encoding="utf-8")
t=t.replace(
    "    private static IntBuffer uploadBuffer;\n",
    "    private static IntBuffer uploadBuffer;\n"
    "    private static volatile boolean startupBatchConsumed;\n")
t=t.replace(
    "        if (!TARGET_PATH.equals(atlasPath)) return;\n",
    "        if (!TARGET_PATH.equals(atlasPath)) return;\n"
    "        if (startupBatchConsumed) {\n"
    "            LOG.info(\n"
    "                \"ItemsAtlasBatch bypass path={} reason=already-consumed\",\n"
    "                atlasPath);\n"
    "            return;\n"
    "        }\n"
    "        if (Minecraft.getMinecraft().theWorld != null) {\n"
    "            LOG.info(\n"
    "                \"ItemsAtlasBatch bypass path={} reason=world-loaded\",\n"
    "                atlasPath);\n"
    "            return;\n"
    "        }\n")
t=t.replace(
    "        try {\n"
    "            GL11.glBindTexture(",
    "        try {\n"
    "            startupBatchConsumed = true;\n"
    "            GL11.glBindTexture(")
p.write_text(t,encoding="utf-8",newline="\n")
print("ONE_SHOT_STARTUP_GUARD_ADDED")