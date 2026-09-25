package com.akashic.forbiddenbackhand.core;

public final class BackhandGuardHooks {
    private static final Object LOCK = new Object();
    private static boolean connected;
    private static boolean local;
    private static String connectionType = "DISCONNECTED";
    private static long generation;

    public static void setState(boolean c, boolean l, String type) {
        synchronized (LOCK) {
            connected = c;
            local = l;
            connectionType = type;
            generation++;
        }
    }
}
