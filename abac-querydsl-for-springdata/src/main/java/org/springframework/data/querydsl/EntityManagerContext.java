package org.springframework.data.querydsl;

import javax.persistence.EntityManager;

import org.springframework.transaction.PlatformTransactionManager;

import lombok.Getter;

@Getter
public class EntityManagerContext {

    private static ThreadLocal<EntityManagerContext> currentEntityContext = new InheritableThreadLocal<EntityManagerContext>();
    private EntityManager em;
    private PlatformTransactionManager tm;

    public EntityManagerContext(EntityManager em, PlatformTransactionManager tm) {
        this.em = em;
        this.tm = tm;
    }

    public static EntityManagerContext getCurrentEntityContext() {
        return currentEntityContext.get();
    }

    public static void setCurrentEntityContext(EntityManager em, PlatformTransactionManager tm) {
        currentEntityContext.set(new EntityManagerContext(em, tm));
    }

    public static void clear() {
        currentEntityContext.set(null);
    }
}
