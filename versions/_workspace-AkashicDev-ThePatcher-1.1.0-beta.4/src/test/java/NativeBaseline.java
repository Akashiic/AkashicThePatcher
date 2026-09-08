import java.lang.reflect.InvocationTargetException;
public final class NativeBaseline {
    public static void main(String[] args) throws Exception {
        Class<?> cls = Class.forName("org.dimdev.dimdoors.core.PocketManager");
        try {
            cls.getMethod("getDimensionData", int.class).invoke(null, 0);
            throw new AssertionError("Original unloaded lookup unexpectedly succeeded");
        } catch (InvocationTargetException e) {
            Throwable t = e.getCause();
            if (!(t instanceof NullPointerException)) throw e;
            StackTraceElement first = t.getStackTrace()[0];
            if (!first.getClassName().equals(cls.getName()) || first.getLineNumber() != 494)
                throw new AssertionError(first);
            System.out.println("ORIGINAL_DIMDOORS_NPE_REPRODUCED=" + first);
        }
    }
}
