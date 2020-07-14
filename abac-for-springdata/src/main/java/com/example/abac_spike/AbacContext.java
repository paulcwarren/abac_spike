package com.example.abac_spike;

public class AbacContext {

    private static ThreadLocal<String> currentAbacContext = new InheritableThreadLocal<String>();

    public static String getCurrentAbacContext() {
        return currentAbacContext.get();
    }

    public static void setCurrentAbacContext(String tenant) {
        currentAbacContext.set(tenant);
    }

    public static void clear() {
        currentAbacContext.set(null);
    }
}
