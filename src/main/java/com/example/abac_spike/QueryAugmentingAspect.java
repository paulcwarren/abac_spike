package com.example.abac_spike;

import com.example.abac_spike.AbacSpikeApplication.EntityContext;
import com.querydsl.core.QueryResults;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.PathBuilder;
import com.querydsl.jpa.impl.JPAQuery;
import com.querydsl.jpa.impl.JPAQueryFactory;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.content.commons.utils.BeanUtils;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.support.JpaEntityInformation;

import javax.persistence.EntityManager;
import javax.persistence.Id;
import javax.persistence.PersistenceContext;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Predicate;
import javax.persistence.criteria.Root;
import java.lang.reflect.Field;
import java.util.Optional;

import static java.lang.String.format;

@Aspect
public class QueryAugmentingAspect {

    private final JpaEntityInformation ei;

    @PersistenceContext
    private EntityManager em;

    public QueryAugmentingAspect(JpaEntityInformation ei) {
        this.ei = ei;
    }

    @Around("execution(* org.springframework.data.repository.CrudRepository.findById(..))")
    public Object findById(ProceedingJoinPoint jp) {

        String abacContext = AbacSpikeApplication.AbacContext.getCurrentAbacContext();
        String[] abacContextFilterSpec = null;
        if (abacContext != null) {
            abacContextFilterSpec = abacContext.split(" ");
        }

        Object id = jp.getArgs()[0];

        PathBuilder entityPath = new PathBuilder(EntityContext.getCurrentEntityContext(), "entity");

        Field idField = BeanUtils.findFieldWithAnnotation(EntityContext.getCurrentEntityContext(), Id.class);
        PathBuilder idPath = entityPath.get(idField.getName(), id.getClass());
        BooleanExpression idExpr = idPath.eq(id);

        BooleanExpression abacExpr = null;
        PathBuilder abacPath = entityPath.get(abacContextFilterSpec[0], String.class);
        if (abacContextFilterSpec[1].equals("=")) {
            abacExpr = abacPath.eq(abacContextFilterSpec[2]);
        }

        idExpr.and(abacExpr);

        JPAQueryFactory queryFactory = new JPAQueryFactory(em);
        JPAQuery q = queryFactory.selectFrom(entityPath);
        if (idExpr != null) {
            q.where(idExpr);
        }

        return Optional.ofNullable(q.fetchOne());
    }

    @Around("execution(* org.springframework.data.repository.PagingAndSortingRepository.findAll(org.springframework.data.domain.Pageable))")
    public Object findAll(ProceedingJoinPoint jp) {

        BooleanExpression abacExpr = null;

        Pageable pageable = (Pageable) jp.getArgs()[0];
        PathBuilder entityPath = new PathBuilder(EntityContext.getCurrentEntityContext(), "entity");

        String abacContext = AbacSpikeApplication.AbacContext.getCurrentAbacContext();
        String[] abacContextFilterSpec = null;
        if (abacContext != null) {
            abacContextFilterSpec = abacContext.split(" ");

            PathBuilder abacPath = entityPath.get(abacContextFilterSpec[0], String.class);
            if (abacContextFilterSpec[1].equals("=")) {
                abacExpr = abacPath.eq(abacContextFilterSpec[2]);
            }
        }

        JPAQueryFactory queryFactory = new JPAQueryFactory(em);
        JPAQuery q = queryFactory.selectFrom(entityPath);
        if (abacExpr != null) {
            q.where(abacExpr);
        }

        q.offset(pageable.getOffset());
        q.limit(pageable.getPageSize());
        if (pageable.getSort().isSorted()) {
            for (int i=0; i < pageable.getSort().toList().size(); i++) {
                Sort.Order order = pageable.getSort().toList().get(i);
                if (order.isAscending()) {
                    q.orderBy(entityPath.getString(order.getProperty()).asc());
                } else {
                    q.orderBy(entityPath.getString(order.getProperty()).desc());
                }
            }
        }

        QueryResults results = q.fetchResults();
        return new PageImpl(results.getResults(), pageable, results.getTotal());
    }

    @Around("execution(* javax.persistence.EntityManager.createQuery(java.lang.String))")
    public Object createQueryFromString(ProceedingJoinPoint joinPoint) throws Throwable {

        String abacContext = AbacSpikeApplication.AbacContext.getCurrentAbacContext();
        String[] abacContextFilterSpec = abacContext.split(" ");

        Object[] args = joinPoint.getArgs();
        String query = (String) args[0];
        String updatedQuery = format("%s and %s", query, parseFilterSpec(abacContextFilterSpec));

        return joinPoint.proceed(new String[]{updatedQuery});
    }

    private String parseFilterSpec(String[] abacContextFilterSpec) {
        return format("d.%s %s '%s'", abacContextFilterSpec[0], abacContextFilterSpec[1], abacContextFilterSpec[2]);
    }

    @Before("execution(* javax.persistence.EntityManager.createQuery(javax.persistence.criteria.CriteriaQuery))")
    public void createQueryFromCriteriaQuery(JoinPoint joinPoint) {

        String abacContext = AbacSpikeApplication.AbacContext.getCurrentAbacContext();
        String[] abacContextFilterSpec = abacContext.split(" ");

        Object o = null;
        try {
            Object[] args = joinPoint.getArgs();
            CriteriaQuery cq = (CriteriaQuery) args[0];
            Predicate existingPredicate = cq.getRestriction();

            CriteriaBuilder cb = ((EntityManager)joinPoint.getTarget()).getCriteriaBuilder();
            Root<?> r = (Root<?>) cq.getRoots().toArray()[0];

            Predicate abacPredicate = null;
            if (abacContextFilterSpec[1].equals("=")) {
                abacPredicate = cb.equal(r.get(abacContextFilterSpec[0]), abacContextFilterSpec[2]);
            }

            Predicate newWherePredicate = existingPredicate;
            if (abacPredicate != null) {
                newWherePredicate = cb.and(existingPredicate, abacPredicate);
            }

            cq.where(newWherePredicate);
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }
}
