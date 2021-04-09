package org.springframework.data.querydsl.binding;

import java.beans.PropertyDescriptor;
import java.lang.reflect.Field;
import java.util.Optional;

import javax.persistence.EntityManager;
import javax.persistence.Id;
import javax.persistence.PersistenceException;
import javax.persistence.Query;

import org.springframework.beans.BeanWrapper;
import org.springframework.beans.BeanWrapperImpl;
import org.springframework.content.commons.utils.BeanUtils;
import org.springframework.content.commons.utils.DomainObjectUtils;
import org.springframework.core.convert.ConversionService;
import org.springframework.data.querydsl.ABACContext;
import org.springframework.data.querydsl.EntityContext;
import org.springframework.data.querydsl.EntityManagerContext;
import org.springframework.data.querydsl.QuerydslPredicateExecutor;
import org.springframework.data.querydsl.QuerydslRepositoryInvokerAdapter;
import org.springframework.data.repository.core.EntityInformation;
import org.springframework.data.repository.support.RepositoryInvoker;
import org.springframework.data.rest.webmvc.ResourceNotFoundException;
import org.springframework.format.support.DefaultFormattingConversionService;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.DefaultTransactionDefinition;
import org.springframework.util.Assert;

import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.types.Predicate;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.Expressions;
import com.querydsl.core.types.dsl.PathBuilder;

import be.heydari.lib.expressions.BoolPredicate;
import be.heydari.lib.expressions.Disjunction;
import lombok.Getter;

public class XenitQuerydslRepositoryInvokerAdapter extends QuerydslRepositoryInvokerAdapter {

    private QuerydslPredicateExecutor<Object> executor;
    private Predicate predicate;

    private ConversionService conversionService = new DefaultFormattingConversionService();

    public XenitQuerydslRepositoryInvokerAdapter(RepositoryInvoker delegate, QuerydslPredicateExecutor<Object> executor, Predicate predicate) {
        super(delegate, executor, predicate);
        this.executor = executor;
        this.predicate = predicate;
    }

    @Override
    public <T> Optional<T> invokeFindById(Object id) {

        BooleanBuilder builder = new BooleanBuilder();

        Class<?> subjectType = EntityContext.getCurrentEntityContext().getJavaType();
        PathBuilder entityPath = new PathBuilder(subjectType, toAlias(subjectType));
        BooleanExpression idExpr = idExpr(conversionService.convert(id, Long.class), entityPath);
        Assert.notNull(idExpr, "id expression cannot be null");
        builder.and(idExpr);
        builder.and(predicate);

        return (Optional<T>) executor.findOne(builder.getValue());
    }

//  We decided this implementation is just plain wrong.  When saving an entity we think we would like to perform an insert into ... select
//  in order to apply the abac policies.
//
//  But, the subject of an `insert into` where clause differs from that of a select (or delete).  In our example a findById accepts
//  a clause 'broker.id=1'.  A insert into needs to accept something like 'id=1' as the sub-select is on the Broker relation directly.  I
//  assume that OPA policies will be written such that save requests send abac policy disjunctions with the right subject type?
//
    @Transactional
    @Override
    public <T> T invokeSave(T object) {

        Disjunction abacContext = ABACContext.getCurrentAbacContext();
        if (abacContext == null) {
            return super.invokeSave(object);
        }

        EntityManager em = EntityManagerContext.getCurrentEntityContext().getEm();
        TransactionManager tm = EntityManagerContext.getCurrentEntityContext().getTm();

        TransactionStatus status = null;
        try {

            if (tm != null) {
                status = ((PlatformTransactionManager)tm).getTransaction(new DefaultTransactionDefinition());
            }

            EntityInformation ei = EntityContext.getCurrentEntityContext();
            if (ei.isNew(object)) {

                InsertQueryModel qm = new InsertQueryModel(object, abacContext);
                Query q = em.createQuery(qm.toString());
                int n = q.executeUpdate();

                T savedEntity = refetchEntity(object, em);

                if (status != null && status.isCompleted() == false) {
                    ((PlatformTransactionManager)tm).commit(status);
                }

                return savedEntity;
            } else {
                UpdateQueryModel qm = new UpdateQueryModel(object, abacContext);
                Query q = em.createQuery(qm.toString());
                int n = q.executeUpdate();

                if (status != null && status.isCompleted() == false) {
                    ((PlatformTransactionManager)tm).commit(status);
                }

                if (n == 1) {
                    return object;
                } else {
                    throw new ResourceNotFoundException();
                }
            }
        } catch (Exception e) {

            if (status != null && status.isCompleted() == false) {
                ((PlatformTransactionManager)tm).rollback(status);
            }

            throw new PersistenceException(e);
        }
    }

    private <T> T refetchEntity(T object, EntityManager em) {
        Query q;
        String sql = String.format("from %s where id=(select max(id)from %s)", object.getClass().getName(), object.getClass().getName());
        q = em.createQuery(sql, object.getClass());
        T savedEntity = (T) q.getSingleResult();
        return savedEntity;
    }

    @Getter
    private static class InsertQueryModel {

        private Object entity;
        private final String attributeList;
        private final String subSelectList;
        private String subSelectRelation;
        private String subSelectIdAttributeName;
        private String subSelectIdValue;

        public InsertQueryModel(Object entity, Disjunction abacContext)
                throws IllegalArgumentException, IllegalAccessException {

            this.entity = entity;
            this.attributeList = buildAttributeList(entity);
            this.subSelectList = buildSubSelectList(entity);
            buildSubSelect(entity, abacContext);
        }

        @Override
        public String toString() {
            return String.format("insert into %s(%s) select %s from %s where %s=%s", entity.getClass().getName(), getAttributeList(), getSubSelectList(), getSubSelectRelation(), getSubSelectIdAttributeName(), getSubSelectIdValue());
        }

        private String buildAttributeList(Object o) throws IllegalArgumentException, IllegalAccessException {

            StringBuilder attrList = new StringBuilder();
            int i=0;

            BeanWrapper wrapper = new BeanWrapperImpl(o);

            for (PropertyDescriptor descriptor : wrapper.getPropertyDescriptors()) {

                if (descriptor.getName().equals("class")) {
                    continue;
                }

                if (wrapper.getPropertyValue(descriptor.getName()) != null) {
                    if (i > 0) {
                        attrList.append(",");
                    }
                    attrList.append(descriptor.getName());
                    i++;
                }
            }
            return attrList.toString();
        }

        private String buildSubSelectList(Object o) {

            StringBuilder attrList = new StringBuilder();
            int i=0;

            BeanWrapper wrapper = new BeanWrapperImpl(o);

            for (PropertyDescriptor descriptor : wrapper.getPropertyDescriptors()) {

                if (descriptor.getName().equals("class")) {
                    continue;
                }

                Object val = wrapper.getPropertyValue(descriptor.getName());
                if (val != null) {
                    if (i > 0) {
                        attrList.append(",");
                    }
                    if (val instanceof String) {
                        attrList.append("'");
                    }
                    attrList.append(val);
                    if (val instanceof String) {
                        attrList.append("'");
                    }
                    attrList.append(" as ");
                    attrList.append(descriptor.getName());
                    i++;
                }
            }
            return attrList.toString();
        }

        private void buildSubSelect(Object entity, Disjunction abacContext) {
            BeanWrapper wrapper = new BeanWrapperImpl(entity);

            if (abacContext.getConjunctivePredicates().size() > 1) {
                throw new IllegalStateException("Multiple conjuctive predicates not supported");
            }

            if (abacContext.getConjunctivePredicates().get(0).getPredicates().size() > 1) {
                throw new IllegalStateException("Multiple predicates not supported");
            }

            BoolPredicate<?> predicate = abacContext.getConjunctivePredicates().get(0).getPredicates().get(0);

            String property = predicate.getLeft().getColumn();
            String[] subProperties = property.split("\\.");
            if (subProperties.length > 2) {
                throw new IllegalArgumentException("Property paths longer than 2 not supported");
            }

            subSelectRelation = wrapper.getPropertyType(subProperties[0]).getName();
            subSelectIdAttributeName = subProperties[1];
            subSelectIdValue = predicate.getRight().getValue().toString();
        }
    }

    private static class UpdateQueryModel {

        private Object entity;
        private final String attributeList;
        private final Object whereClause;

        public UpdateQueryModel(Object entity, Disjunction abacContext)
                throws Exception {

            this.entity = entity;
            attributeList = buildAttributeList(entity, abacContext);
            whereClause = buildWhereClause(entity, abacContext);

        }

        @Override
        public String toString() {
            return String.format("update %s set %s where %s", entity.getClass().getName(), attributeList, whereClause);

        }

        private String buildAttributeList(Object o, Disjunction abacContext)
                throws Exception {

            StringBuilder attrList = new StringBuilder();
            int i=0;

            BeanWrapper wrapper = new BeanWrapperImpl(o);

            for (PropertyDescriptor descriptor : wrapper.getPropertyDescriptors()) {

                if (descriptor.getName().equals("class")) {
                    continue;
                }

                Object val = wrapper.getPropertyValue(descriptor.getName());
                if (val != null) {
                    Class<?> type = wrapper.getPropertyType(descriptor.getName());
                    if (!type.isPrimitive() && !type.equals(String.class)) {
                        continue;
                    }

                    if (i > 0) {
                        attrList.append(",");
                    }
                    attrList.append(descriptor.getName());
                    attrList.append("=");
                    if (val instanceof String) {
                        attrList.append("'");
                    }
                    attrList.append(val);
                    if (val instanceof String) {
                        attrList.append("'");
                    }
                    i++;
                }
            }

            return attrList.toString();
        }

        private String buildWhereClause(Object o, Disjunction abacContext)
                throws IllegalArgumentException, IllegalAccessException {

            BeanWrapper wrapper = new BeanWrapperImpl(o);

            Field f = DomainObjectUtils.getIdField(o.getClass());
            String idFieldName = f.getName();
            Object idValue = wrapper.getPropertyValue(idFieldName);

            if (abacContext.getConjunctivePredicates().size() > 1) {
                throw new IllegalStateException("Multiple conjuctive predicates not supported");
            }

            if (abacContext.getConjunctivePredicates().get(0).getPredicates().size() > 1) {
                throw new IllegalStateException("Multiple predicates not supported");
            }

            BoolPredicate<?> predicate = abacContext.getConjunctivePredicates().get(0).getPredicates().get(0);

            String property = predicate.getLeft().getColumn();
            Object value = predicate.getRight().getValue();

            return String.format("%s=%s and %s=%s", idFieldName, idValue.toString(), property, value.toString());
        }
    }

//    No implementation required.  When performing a delete through the REST API the Controller first does a findById call
//    that applies the abac policies
//
//    @Override
//    public void invokeDeleteById(Object id) {
//
//        Disjunction abacContext = ABACContext.getCurrentAbacContext();
//        if (abacContext == null) {
//            super.invokeDeleteById(id);
//        }
//
//        try {
//            Optional<Object> object = this.invokeFindById(conversionService.convert(id, Long.class));
//            object.ifPresent((it) -> {
//                enforceAbacAttributes(object, abacContext);
//                super.invokeDeleteById(conversionService.convert(id, Long.class));
//            });
//        } catch (Throwable t) {
//            int i=0;
//        }
//    }

    private String toAlias(Class<?> subjectType) {

        char c[] = subjectType.getSimpleName().toCharArray();
        c[0] = Character.toLowerCase(c[0]);
        return new String(c);
    }

    private BooleanExpression idExpr(Object id, PathBuilder entityPath) {
        Field idField = BeanUtils.findFieldWithAnnotation(EntityContext.getCurrentEntityContext().getJavaType(), Id.class);
        PathBuilder idPath = entityPath.get(idField.getName(), id.getClass());
        return idPath.eq(Expressions.constant(id));
    }
}
