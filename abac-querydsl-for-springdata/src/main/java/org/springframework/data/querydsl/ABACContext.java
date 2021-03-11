package org.springframework.data.querydsl;

import be.heydari.lib.expressions.Disjunction;

public class ABACContext {

    private static ThreadLocal<Disjunction> currentAbacContext = new InheritableThreadLocal<Disjunction>();

    public static Disjunction getCurrentAbacContext() {
        return currentAbacContext.get();
    }

    public static void setCurrentAbacContext(Disjunction tenant) {
        currentAbacContext.set(tenant);
    }

    public static void clear() {
        currentAbacContext.set(null);
    }
}
