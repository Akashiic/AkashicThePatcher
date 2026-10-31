import com.akashic.elnendlessids.hook.ElnEndlessIdsHooks;
import net.minecraft.block.Block;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;

/**
 * Behaviour of ElnEndlessIdsHooks against test doubles of Block/ExtendedBlockStorage.
 *   javac -d out tools/hooktest/HookTest.java tools/hooktest/net/.../*.java src/main/java/.../hook/ElnEndlessIdsHooks.java
 *   java -cp out HookTest
 */
public class HookTest {
    static int failures;

    static void eq(int expected, int actual, String what) {
        boolean ok = expected == actual;
        System.out.println((ok ? "  ok   " : "  FAIL ") + what + " = " + actual + (ok ? "" : " (expected " + expected + ")"));
        if (!ok) {
            failures++;
        }
    }

    static int key(Block b, int meta) {
        ExtendedBlockStorage s = new ExtendedBlockStorage();
        s.block = b;
        s.meta = meta;
        return ElnEndlessIdsHooks.scannerBlockKey(s, 1, 2, 3);
    }

    /** Electrical Age's checkIsOre index after the patch: oreMapIndex(oreMapBlockId(block) + (meta << 12)). */
    static int index(Block b, int meta) {
        return ElnEndlessIdsHooks.oreMapIndex(ElnEndlessIdsHooks.oreMapBlockId(b) + (meta << 12));
    }

    public static void main(String[] a) {
        System.out.println("scannerBlockKey");
        eq(0, key(new Block(0, false), 0), "air");
        eq(0, key(null, 0), "null block");
        eq(56, key(new Block(56, true), 0), "diamond ore (56:0)");
        eq(15 + (3 << 12), key(new Block(15, true), 3), "id 15 meta 3");
        eq(4095 + (15 << 12), key(new Block(4095, true), 15), "id 4095 meta 15");
        eq(300 + (2 << 12), key(new Block(300, true), 2), "id 300 meta 2 (Eln itself dropped the high byte here)");
        eq(1 + (15 << 12), key(new Block(1, true), 0xFF), "meta above 15 is masked");
        eq(1, key(new Block(5000, true), 7), "EndlessIDs id 5000, opaque -> stone key");
        eq(20, key(new Block(70000, false), 0), "EndlessIDs id 70000, translucent -> glass key");
        System.out.println("checkIsOre index");
        eq(56, index(new Block(56, true), 0), "diamond ore (56:0)");
        eq(4095 + (15 << 12), index(new Block(4095, true), 15), "id 4095 meta 15 (last entry)");
        eq(0, index(new Block(5000, true), 0), "EndlessIDs id 5000 meta 0 (was 5000 = id 904 meta 1)");
        eq(0, index(new Block(30000, true), 15), "EndlessIDs id 30000 meta 15 (was 91440, past the end)");
        eq(0, index(new Block(10, true), 16), "meta 16 (was 65546, past the end)");
        eq(0, index(new Block(10, true), 0xFFFF), "16-bit meta");
        System.out.println(failures == 0 ? "HOOKTEST PASS" : "HOOKTEST FAIL " + failures);
        System.exit(failures == 0 ? 0 : 1);
    }
}
