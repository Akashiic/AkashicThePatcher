from pathlib import Path
p=Path(r"C:\Users\User\Desktop\Codex\Akashic-Hodgepodge-AsyncCompat\src\main\java\dev\akashic\asynciconcompat\profile\TextureUploadProfiler.java")
t=p.read_text(encoding="utf-8")
if "private static long mipmapGenerateNanos;" not in t:
    t=t.replace(
        "    private static long glNanos;\n",
        "    private static long glNanos;\n"
        "    private static long mipmapGenerateNanos;\n"
        "    private static long mipmapGenerateStartNanos;\n"
    )
if "private static int mipmapChecks;" not in t:
    t=t.replace(
        "    private static int glCalls;\n",
        "    private static int glCalls;\n"
        "    private static int mipmapChecks;\n"
        "    private static int mipmapGenerateCalls;\n"
    )
if "mipmapGenerateNanos = 0L;" not in t:
    t=t.replace(
        "        glNanos = 0L;\n",
        "        glNanos = 0L;\n"
        "        mipmapGenerateNanos = 0L;\n"
        "        mipmapGenerateStartNanos = 0L;\n"
    )
if "mipmapChecks = 0;" not in t:
    t=t.replace(
        "        glCalls = 0;\n",
        "        glCalls = 0;\n"
        "        mipmapChecks = 0;\n"
        "        mipmapGenerateCalls = 0;\n"
    )
p.write_text(t,encoding="utf-8",newline="\n")
print("FIELDS_AND_RESETS_FIXED")