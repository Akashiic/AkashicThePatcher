public final class CompilerDisableProbe {
    private static volatile int sink;

    private static int before(int x) {
        return x * 31 + 7;
    }

    private static int during(int x) {
        return x * 37 + 11;
    }

    private static int after(int x) {
        return x * 41 + 13;
    }

    public static void main(String[] args) throws Exception {
        int v = 0;
        for (int i = 0; i < 200000; i++) v += before(i);
        sink = v;
        System.out.println("MARK_DISABLE");
        Compiler.disable();
        v = 0;
        for (int i = 0; i < 1000000; i++) v += during(i);
        sink = v;
        Thread.sleep(100L);
        System.out.println("MARK_ENABLE");
        Compiler.enable();
        v = 0;
        for (int i = 0; i < 200000; i++) v += after(i);
        sink = v;
        System.out.println("DONE " + sink);
    }
}