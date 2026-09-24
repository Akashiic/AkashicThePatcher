import hashlib, json, os, pathlib, struct, subprocess, tempfile, zipfile

ROOT = pathlib.Path(r"C:\Users\User\Desktop\Codex\Akashic-ThePatcher-1.1.0-beta.16-ForestryUniversal")
CAND = ROOT / "releases/!Akashic-ThePatcher-1.1.0-beta.16-ForestryUniversal.jar"
BASE = ROOT / "base-beta15.jar"
FORESTRY = ROOT / "Forestry-4.11.36-pinned.jar"
EXPECTED = "3e0bb92a0e7f78f01a15a555c80e5640d64a6145252eb06078d778821e2afffa"
FORESTRY_SHA = "78daf273c4d112324ea7fb3ad9fbac99ecd088c1b78982811deebd58da712d6f"

JAVA = pathlib.Path(r"C:\Program Files\Java\jdk-1.8\bin\java.exe")
JAR = pathlib.Path(r"C:\Program Files\Java\jdk-1.8\bin\jar.exe")
JAVAP = pathlib.Path(r"C:\Program Files\Java\jdk-1.8\bin\javap.exe")
LAUNCHWRAPPER = pathlib.Path(r"C:\Users\User\AppData\Roaming\.technic\libraries\net\minecraft\launchwrapper\1.12\launchwrapper-1.12.jar")
ASM = pathlib.Path(r"C:\Users\User\AppData\Roaming\.technic\libraries\org\ow2\asm\asm-all\5.0.3\asm-all-5.0.3.jar")
SRG_MC = pathlib.Path(r"C:\Users\User\Desktop\Codex\Akashic-Hodgepodge-AsyncCompat\build\rfg\srg_merged_minecraft.jar")
GUAVA = pathlib.Path(r"C:\Users\User\AppData\Roaming\.technic\libraries\com\google\guava\guava\17.0\guava-17.0.jar")
AUTHLIB = pathlib.Path(r"C:\Users\User\AppData\Roaming\.technic\libraries\com\mojang\authlib\1.5.21\authlib-1.5.21.jar")
LANG3 = pathlib.Path(r"C:\Users\User\AppData\Roaming\.technic\libraries\org\apache\commons\commons-lang3\3.3.2\commons-lang3-3.3.2.jar")
LOG4J_API = pathlib.Path(r"C:\Users\User\AppData\Roaming\.technic\libraries\org\apache\logging\log4j\log4j-api\2.0-beta9-fixed\log4j-api-2.0-beta9-fixed.jar")
LOG4J_CORE = pathlib.Path(r"C:\Users\User\AppData\Roaming\.technic\libraries\org\apache\logging\log4j\log4j-core\2.0-beta9-fixed\log4j-core-2.0-beta9-fixed.jar")

TOOL_OUT = ROOT / "runs/transform-tool"
EQ_OUT = ROOT / "runs/equivalence"

def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()

def major(data):
    return struct.unpack_from(">H", data, 6)[0]

def run(args, timeout=60):
    proc = subprocess.run(
        [str(x) for x in args],
        stdout=subprocess.PIPE,
        stderr=subprocess.STDOUT,
        check=True,
        timeout=timeout,
    )
    return proc.stdout.decode("utf-8", errors="replace")

assert sha(CAND) == EXPECTED
assert sha(FORESTRY) == FORESTRY_SHA

with zipfile.ZipFile(CAND) as z:
    assert z.testzip() is None
    data = {n: z.read(n) for n in z.namelist() if not n.endswith("/")}
with zipfile.ZipFile(BASE) as z:
    base = {n: z.read(n) for n in z.namelist() if not n.endswith("/")}

changed = sorted(
    n for n in base if n.endswith(".class") and n in data and base[n] != data[n]
)
added = sorted(
    n for n in data if n.endswith(".class") and n not in base
)
assert changed == sorted([
    "com/akashic/thepatcher/AkashicThePatcherMod.class",
    "com/akashic/thepatcher/core/AkashicThePatcherLoadingPlugin.class",
    "com/akashic/thepatcher/module/forestry/ForestryC2RuntimeGuard.class",
])
assert added == sorted([
    "com/akashic/thepatcher/module/forestry/ForestryGenomeCompat.class",
    "com/akashic/thepatcher/module/forestry/ForestryGenomeC2Transformer.class",
])

for name, blob in data.items():
    if name.endswith(".class"):
        assert major(blob) <= 52, name

for entry in [
    "dev/akashic/asynciconcompat/CompatHooks.class",
    "dev/akashic/asynciconcompat/CompatState.class",
    "dev/akashic/asynciconcompat/mixins/MixinTextureMapHybridAsync.class",
    "dev/akashic/asynciconcompat/mixins/MixinStitcherLayoutCache.class",
    "dev/akashic/asynciconcompat/mixins/MixinTextureUtilUploadProfiler.class",
    "dev/akashic/asynciconcompat/mixins/MixinAngelicaMipmapProfiler.class",
    "dev/akashic/asynciconcompat/profile/TextureUploadProfiler.class",
    "dev/akashic/asynciconcompat/cache/StitchLayoutCache.class",
]:
    assert data[entry] == base[entry], entry

loader = data["com/akashic/thepatcher/core/AkashicThePatcherLoadingPlugin.class"]
assert loader.count(b"ForestryGenomeC2Transformer") == 1

guard = data["com/akashic/thepatcher/module/forestry/ForestryC2RuntimeGuard.class"]
assert b"bundled zero-config Genome.getChromosomes rewrite is armed" in guard

# Apply the transformer from the final candidate to the exact pinned Forestry.
with tempfile.TemporaryDirectory(prefix="beta16-target-", dir=ROOT / "runs") as td:
    td = pathlib.Path(td)
    out_class = td / "forestry/core/genetics/Genome.class"
    transform_cp = os.pathsep.join([
        str(TOOL_OUT),
        str(CAND),
        str(LAUNCHWRAPPER),
        str(ASM),
    ])
    transform_output = run([
        JAVA, "-Xverify:all", "-cp", transform_cp,
        "com.akashic.compatlab.tools.RunForestryTransform",
        FORESTRY, out_class,
    ])
    assert "property=true" in transform_output
    assert "Universal Genome.getChromosomes rewrite applied." in transform_output

    jar_root = td / "jarroot/forestry/core/genetics"
    jar_root.mkdir(parents=True)
    (jar_root / "Genome.class").write_bytes(out_class.read_bytes())
    wrapper_jar = td / "wrapper.jar"
    subprocess.run([
        str(JAR), "cf", str(wrapper_jar),
        "-C", str(td / "jarroot"), "forestry/core/genetics/Genome.class",
    ], check=True, stdout=subprocess.PIPE, stderr=subprocess.STDOUT)
    javap_text = run([
        JAVAP, "-classpath", wrapper_jar, "-c", "-p",
        "forestry.core.genetics.Genome",
    ])
    start = javap_text.index(
        "private static forestry.api.genetics.IChromosome[] getChromosomes")
    end = javap_text.index(
        "protected static forestry.api.genetics.IAllele getActiveAllele",
        start)
    wrapper = javap_text[start:end]
    assert "ForestryGenomeCompat.loadChromosomes" in wrapper
    assert "Chromosome.loadChromosomeFromNBT" not in wrapper
    assert "areturn" in wrapper

# Behavioral equivalence using helper from FINAL candidate.
eq_cp = os.pathsep.join([
    str(EQ_OUT),
    str(CAND),
    str(FORESTRY),
    str(SRG_MC),
    str(GUAVA),
    str(AUTHLIB),
    str(LANG3),
    str(LOG4J_API),
    str(LOG4J_CORE),
])
eq_output = run([
    JAVA, "-Xverify:all", "-cp", eq_cp,
    "com.akashic.compatlab.tools.ForestryGenomeCompatEquivalenceTest",
])
assert "PASS cases=2" in eq_output

# Fail-closed fingerprint: mutate only the Slot string.
with zipfile.ZipFile(FORESTRY) as z:
    raw_genome = z.read("forestry/core/genetics/Genome.class")
assert b"Slot" in raw_genome
mutated = raw_genome.replace(b"Slot", b"Slox")
with tempfile.TemporaryDirectory(prefix="beta16-failclosed-", dir=ROOT / "runs") as td:
    td = pathlib.Path(td)
    mut_jar = td / "mutated.jar"
    with zipfile.ZipFile(mut_jar, "w", zipfile.ZIP_DEFLATED) as z:
        z.writestr("forestry/core/genetics/Genome.class", mutated)
    out_class = td / "forestry/core/genetics/Genome.class"
    fail_output = run([
        JAVA, "-cp", transform_cp,
        "com.akashic.compatlab.tools.RunForestryTransform",
        mut_jar, out_class,
    ])
    assert "UNIVERSAL PATCH REFUSED" in fail_output
    assert out_class.read_bytes() == mutated

report = {
    "schema": 1,
    "allPassed": True,
    "candidate": CAND.name,
    "candidateSha256": sha(CAND),
    "forestrySha256": sha(FORESTRY),
    "checks": {
        "zipCrc": "PASS",
        "classDelta": "PASS",
        "java8ClassMajor": "PASS",
        "performancePipelineBitIdentity": "PASS",
        "transformerRegisteredExactlyOnce": "PASS",
        "finalCandidateTransformsPinnedForestry": "PASS",
        "fatalMethodBecomesTinyDelegate": "PASS",
        "behavioralEquivalenceEmptyAndInvalidSlots": "PASS",
        "fingerprintFailClosed": "PASS",
        "fingerprintFailureReturnsOriginalBytes": "PASS",
    },
    "changedClasses": changed,
    "addedClasses": added,
    "transformOutput": transform_output.strip(),
    "equivalenceOutput": eq_output.strip(),
}
(ROOT / "reports/targeted-gates.json").write_text(
    json.dumps(report, indent=2, sort_keys=True) + "\n",
    encoding="utf-8",
)
print("THEPATCHER_BETA16_TARGETED_GATES PASS sha256=" + sha(CAND))