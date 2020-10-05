package com.example.abac_spike;

import static java.lang.String.format;

import java.beans.PropertyDescriptor;
import java.lang.reflect.Field;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.persistence.EntityManager;
import javax.persistence.Id;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.From;
import javax.persistence.criteria.Predicate;
import javax.persistence.criteria.Root;

import be.heydari.lib.converters.jpql.JPQLUtils;
import be.heydari.lib.converters.querydsl.QueryDslUtils;
import be.heydari.lib.expressions.Disjunction;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.beans.BeanWrapper;
import org.springframework.beans.BeanWrapperImpl;
import org.springframework.beans.InvalidPropertyException;
import org.springframework.beans.NullValueInNestedPathException;
import org.springframework.content.commons.utils.BeanUtils;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.repository.core.EntityInformation;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.util.Assert;

import com.querydsl.core.QueryResults;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.ComparablePath;
import com.querydsl.core.types.dsl.Expressions;
import com.querydsl.core.types.dsl.PathBuilder;
import com.querydsl.jpa.impl.JPADeleteClause;
import com.querydsl.jpa.impl.JPAQuery;
import com.querydsl.jpa.impl.JPAQueryFactory;

import lombok.Getter;
import lombok.Setter;

@Aspect
public class QueryAugmentingABACAspect {

    private static final String ID_MUST_NOT_BE_NULL = "The given id must not be null!";

    private final EntityManager em;
    private final PlatformTransactionManager ptm;

    public QueryAugmentingABACAspect(EntityManager em, PlatformTransactionManager ptm) {
        this.em = em;
        this.ptm = ptm;
    }

    @Around("execution(* org.springframework.data.repository.CrudRepository.findById(..))")
    public Object findById(ProceedingJoinPoint jp) throws Throwable {
        // Emad
        Disjunction abacContext = ABACContext.getCurrentAbacContext();
        if (abacContext == null) {
            return jp.proceed(jp.getArgs());
        }

        Object id = jp.getArgs()[0];
        Assert.notNull(id, ID_MUST_NOT_BE_NULL);

        PathBuilder entityPath = new PathBuilder(EntityContext.getCurrentEntityContext().getJavaType(), "entity");

        //String[] abacContextFilterSpec = abacContext.split(" ");

        BooleanExpression idExpr = idExpr(id, entityPath);
        // Emad
        //BooleanExpression abacExpr = abacExpr(abacContextFilterSpec, entityPath);
        BooleanExpression abacExpr = QueryDslUtils.from(abacContext,entityPath,EntityContext.getCurrentEntityContext().getJavaType());


        idExpr = idExpr.and(abacExpr);

        JPAQueryFactory queryFactory = new JPAQueryFactory(em);
        JPAQuery q = queryFactory.selectFrom(entityPath);
        if (idExpr != null) {
            q.where(idExpr);
        }

        return Optional.ofNullable(q.fetchOne());
    }

    @Around("execution(* org.springframework.data.repository.PagingAndSortingRepository.findAll(org.springframework.data.domain.Pageable))")
    public Object findAll(ProceedingJoinPoint jp) throws Throwable {

        // Emad
        Disjunction abacContext = ABACContext.getCurrentAbacContext();
        if (abacContext == null) {
            return jp.proceed(jp.getArgs());
        }

        BooleanExpression abacExpr = null;

        Pageable pageable = (Pageable) jp.getArgs()[0];
        PathBuilder entityPath = new PathBuilder(EntityContext.getCurrentEntityContext().getJavaType(), "entity");

        // Emad
        // abacExpr = abacExpr(abacContext.split(" "), entityPath);
        abacExpr = QueryDslUtils.from(abacContext, entityPath, EntityContext.getCurrentEntityContext().getJavaType());

        JPAQueryFactory queryFactory = new JPAQueryFactory(em);
        JPAQuery q = queryFactory.selectFrom(entityPath);
        if (abacExpr != null) {
            q.where(abacExpr);
        }

        if (pageable.isPaged()) {
            q.offset(pageable.getOffset());
            q.limit(pageable.getPageSize());
        }
        if (pageable.getSort().isSorted()) {
            for (int i = 0; i < pageable.getSort().toList().size(); i++) {
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

        // Emad
        Disjunction abacContext = ABACContext.getCurrentAbacContext();
        //String[] abacContextFilterSpec = abacContext.split(" ");

        QueryAST ast = QueryAST.fromQueryString((String) joinPoint.getArgs()[0]);
        // Emad
        if (abacContext != null) {
            String abacContextFilterSpec = JPQLUtils.from(abacContext, ast.getAlias());
            ast.setWhere(abacContextFilterSpec);
        }

        /*
        if (ast.getWhere() == null) {
           ast.setWhere(parseFilterSpec(abacContextFilterSpec, ast.getAlias()));
        } else {
            Pattern pattern = Pattern.compile("^.*(?<field>" + abacContextFilterSpec[0] + ").*$");
            Matcher matcher = pattern.matcher(ast.getWhere());

            // only add if the field is not already in the where clause
            if (!matcher.find()) {
                ast.setWhere(format("%s and %s", ast.getWhere(), parseFilterSpec(abacContextFilterSpec, ast.getAlias())));
            }
        }*/

        return joinPoint.proceed(new String[]{ast.toString()});
    }

    @Before("execution(* javax.persistence.EntityManager.createQuery(javax.persistence.criteria.CriteriaQuery))")
    public void createQueryFromCriteriaQuery(JoinPoint joinPoint) {

        // Emad
        Disjunction abacContext = ABACContext.getCurrentAbacContext();
        //String[] abacContextFilterSpec = abacContext.split(" ");

        Object o = null;
        try {
            Object[] args = joinPoint.getArgs();
            CriteriaQuery cq = (CriteriaQuery) args[0];
            Predicate existingPredicate = cq.getRestriction();

            CriteriaBuilder cb = ((EntityManager) joinPoint.getTarget()).getCriteriaBuilder();
            Root<?> r = (Root<?>) cq.getRoots().toArray()[0];

            Predicate abacPredicate = null;
            if (abacContextFilterSpec[1].equals("=")) {

                String[] pathSegments = abacContextFilterSpec[0].split("\\.");
                From f = r;
                int i=0;
                for (; i < pathSegments.length - 1; i++) {
                    f = f.join(pathSegments[i]);
                }

                abacPredicate = cb.equal(f.get(pathSegments[pathSegments.length - 1]), typedValueFromConstant(abacContextFilterSpec[2]));
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

    @Around("execution(* org.springframework.data.repository.CrudRepository.save(..))")
    public Object save(ProceedingJoinPoint jp) throws Throwable {

        String abacContext = ABACContext.getCurrentAbacContext();
        if (abacContext == null) {
            return jp.proceed(jp.getArgs());
        }

        EntityInformation ei = EntityContext.getCurrentEntityContext();

        String[] abacContextFilterSpec = null;
        abacContextFilterSpec = abacContext.split(" ");

        Object entity = jp.getArgs()[0];

        if (ei.isNew(entity)) {
            setAbacAttributes(entity, abacContextFilterSpec);
        } else {
            enforceAbacAttributes(entity, abacContextFilterSpec);
        }

        return jp.proceed();
    }

    @Around("execution(* org.springframework.data.repository.CrudRepository.deleteById(..))")
    public void deleteById(ProceedingJoinPoint jp) throws Throwable {

        String abacContext = ABACContext.getCurrentAbacContext();
        if (abacContext == null) {
            jp.proceed(jp.getArgs());
        }

        Object id = jp.getArgs()[0];
        Assert.notNull(id, ID_MUST_NOT_BE_NULL);

        PathBuilder entityPath = new PathBuilder(EntityContext.getCurrentEntityContext().getJavaType(), "entity");

        String[] abacContextFilterSpec = abacContext.split(" ");

        BooleanExpression idExpr = idExpr(id, entityPath);
        BooleanExpression abacExpr = abacExpr(abacContextFilterSpec, entityPath);
        if (abacExpr != null) {
            idExpr = idExpr.and(abacExpr);
        }

        JPAQueryFactory queryFactory = new JPAQueryFactory(em);
        JPADeleteClause q = queryFactory.delete(entityPath);
        if (idExpr != null) {
            q = q.where(idExpr);
        }

        TransactionStatus status = ptm.getTransaction(TransactionDefinition.withDefaults());
        try {
            q.execute();

            if (status != null && status.isCompleted() == false) {
                ptm.commit(status);
            }
        } catch (Exception e) {
            ptm.rollback(status);
        }
    }

    @Around("execution(* org.springframework.data.repository.CrudRepository.delete(..))")
    public void delete(ProceedingJoinPoint jp) throws Throwable {

        String abacContext = ABACContext.getCurrentAbacContext();
        if (abacContext == null) {
            jp.proceed(jp.getArgs());
        }

        Object entity = jp.getArgs()[0];
        Assert.notNull(entity, "Entity must not be null!");

        EntityInformation ei = EntityContext.getCurrentEntityContext();

        if (ei.isNew(entity)) {
            return;
        }

        enforceAbacAttributes(entity, abacContext.split(" "));

        jp.proceed();
    }

    Class<?> typeFromConstant(String s) {

        Class<?> type = String.class;
        if (s.endsWith("L")) {
            type = Long.class;
        } else if (s.endsWith("f")) {
            type = Float.class;
        } else if (s.endsWith("d")) {
            type = Double.class;
        } else {
            try {
                Integer.parseInt(s);
                type = Integer.class;
            } catch (NumberFormatException nfe) {}
        }

        return type;
    }

    Object typedValueFromConstant(String s) {

        if (s.endsWith("L")) {
            return Long.parseLong(s.replace("L", ""));
        } else if (s.endsWith("f")) {
            return Float.parseFloat(s.replace("f", ""));
        } else if (s.endsWith("d")) {
            return Double.parseDouble(s.replace("d", ""));
        } else {
            try {
                int i = Integer.parseInt(s);
                return i;
            } catch (NumberFormatException nfe) {}
        }
        return s;
    }

    String parseFilterSpec(String[] abacContextFilterSpec, String alias) {
        if (String.class.equals(typeFromConstant(abacContextFilterSpec[2]))) {
            return format("%s.%s %s '%s'", alias, abacContextFilterSpec[0], abacContextFilterSpec[1], abacContextFilterSpec[2]);
        } else {
            return format("%s.%s %s %s", alias, abacContextFilterSpec[0], abacContextFilterSpec[1], typedValueFromConstant(abacContextFilterSpec[2]));
        }
    }

    BooleanExpression idExpr(Object id, PathBuilder entityPath) {
        Field idField = BeanUtils.findFieldWithAnnotation(EntityContext.getCurrentEntityContext().getJavaType(), Id.class);
        PathBuilder idPath = entityPath.get(idField.getName(), id.getClass());
        return idPath.eq(id);
    }

    BooleanExpression abacExpr(String[] abacContextFilterSpec, PathBuilder entityPath) {
        BooleanExpression abacExpr = null;
        ComparablePath abacPath = entityPath.getComparable(abacContextFilterSpec[0], typeFromConstant(abacContextFilterSpec[2]));
        if (abacContextFilterSpec[1].equals(">")) {
            abacExpr = abacPath.eq(Expressions.constant(typedValueFromConstant(abacContextFilterSpec[2])));
        }
        return abacExpr;
    }

    Object setAbacAttributes(Object entity, String[] abacContextFilterSpec) {

        BeanWrapper wrapper = new BeanWrapperImpl(entity);
        try {
            PropertyDescriptor descriptor = wrapper.getPropertyDescriptor(abacContextFilterSpec[0]);
            descriptor.setValue(abacContextFilterSpec[0], abacContextFilterSpec[2]);
        } catch (InvalidPropertyException ipe) {}
        return entity;
    }

    void enforceAbacAttributes(Object entity, String[] abacContextFilterSpec) {
        BeanWrapper wrapper = new BeanWrapperImpl(entity);
        try {
            Object value = wrapper.getPropertyValue(abacContextFilterSpec[0]);
            if (!typedValueFromConstant(abacContextFilterSpec[2]).equals(value)) {
                throw new SecurityException();
            }
        } catch (NullValueInNestedPathException nvinpe) {
            throw new SecurityException();
        } catch (InvalidPropertyException ipe) {}
    }

    @Getter
    @Setter
    static class QueryAST {

        private String query;
        private String attrs;
        private String type;
        private String alias;
        private String where;
        private String orderBy;

        private QueryAST() {}

        @Override
        public String toString() {
            StringBuilder builder = new StringBuilder();
            builder.append(query);
            builder.append(" ");
            if (attrs != null) {
                builder.append(attrs);
                builder.append(" ");
            }
            builder.append("from ");
            builder.append(type);
            builder.append(" ");
            if (alias != null) {
                builder.append(alias);
                builder.append(" ");
            }
            if (this.getWhere() != null) {
                builder.append("where ");
                builder.append(where);
                builder.append(" ");
            }
            if (this.getOrderBy() != null) {
                builder.append("order by ");
                builder.append(orderBy);
            }
            return builder.toString().trim();
        }

        public static QueryAST fromQueryString(String query) {

            QueryAST ast = new QueryAST();

            int token_type = QUERY;
            String[] tokens = query.split(" |\\n");
            for (int i=0; i < tokens.length; i++) {

                String token = tokens[i];

                switch (token_type) {
                    case QUERY:
                        ast.setQuery(token);
                        token_type = ATTRS_OR_FROM;
                        break;
                    case ATTRS_OR_FROM:
                        if (token.equals("from")) {
                            token_type = TYPE;
                        } else {
                            ast.setAttrs(token);
                        }
                        break;
                    case TYPE:
                        ast.setType(token);
                        token_type = ALIAS_OR_WHERE_OR_ORDERBY;
                        break;
                    case ALIAS_OR_WHERE_OR_ORDERBY:
                        if (token.equals("where")) {
                            token_type = WHERE;
                        } else if (token.equals("order")) {
                            token_type = ORDERBY;
                        } else {
                            ast.setAlias(token);
                        }
                        break;
                    case WHERE:
                        String where = "";
                        do {
                            where = where + tokens[i] + " ";
                            i++;
                        } while (i < tokens.length && !tokens[i].equals("order"));
                        ast.setWhere(where.trim());
                        token_type = ORDERBY;
                        break;
                    case ORDERBY:
                        String orderby = "";

                        while (tokens[i].equals("order") || tokens[i].equals("by")) {
                            i++;  // skip
                        }
                        do {
                            orderby = orderby + tokens[i] + " ";
                            i++;
                        } while (i < tokens.length);
                        ast.setOrderBy(orderby.trim());
                        break;
                }
            }

            return ast;
        }

        static final int QUERY = 0;
        static final int ATTRS_OR_FROM = 1;
        static final int TYPE = 2;
        static final int ALIAS_OR_WHERE_OR_ORDERBY = 3;
        static final int WHERE = 4;
        static final int ORDERBY = 5;
    }
}
