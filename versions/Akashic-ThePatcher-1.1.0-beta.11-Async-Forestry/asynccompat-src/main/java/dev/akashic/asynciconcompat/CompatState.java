package dev.akashic.asynciconcompat;

public final class CompatState {
    private static volatile boolean schedulerObserved;

    private CompatState() {}

    public static void markSchedulerObserved() {
        schedulerObserved = true;
    }

    public static boolean isSchedulerObserved() {
        return schedulerObserved;
    }
}