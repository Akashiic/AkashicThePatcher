package makamys.coretweaks.tweak.crashhandler;

import java.util.ArrayList;
import java.util.List;

/**
 * Test reconstruction of the shape of CoreTweaks 0.3.4.7's CrashHandler.resetState() (not CoreTweaks' code): a
 * sequence of independent try/catch steps and a final report. The gate also transforms the real class from the
 * CoreTweaks jar (ASM only). Never shipped.
 */
public class CrashHandler {
    public static int steps;

    public static void resetState() {
        List<Exception> exceptions = new ArrayList<>();
        try {
            steps++;
        } catch (RuntimeException e) {
            exceptions.add(e);
        }
        try {
            if (Boolean.getBoolean("stub.coretweaks.stepFails")) {
                throw new IllegalStateException("step failed");
            }
            steps++;
        } catch (Exception e) {
            exceptions.add(e);
        }
        if (!exceptions.isEmpty()) {
            for (Exception e : exceptions) {
                System.out.println("CoreTweaks stub: Something went wrong while attempting to restore state: " + e.getMessage());
            }
        }
    }
}
