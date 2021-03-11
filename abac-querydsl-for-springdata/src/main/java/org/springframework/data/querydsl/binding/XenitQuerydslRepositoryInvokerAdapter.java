package org.springframework.data.querydsl.binding;

import java.lang.reflect.Field;
import java.util.Optional;

import javax.persistence.Id;

import org.springframework.beans.BeanWrapper;
import org.springframework.beans.BeanWrapperImpl;
import org.springframework.beans.InvalidPropertyException;
import org.springframework.beans.NullValueInNestedPathException;
import org.springframework.content.commons.utils.BeanUtils;
import org.springframework.core.convert.ConversionService;
import org.springframework.data.querydsl.ABACContext;
import org.springframework.data.querydsl.EntityContext;
import org.springframework.data.querydsl.QuerydslPredicateExecutor;
import org.springframework.data.querydsl.QuerydslRepositoryInvokerAdapter;
import org.springframework.data.repository.core.EntityInformation;
import org.springframework.data.repository.support.RepositoryInvoker;
import org.springframework.format.support.DefaultFormattingConversionService;
import org.springframework.util.Assert;

import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.types.Predicate;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.Expressions;
import com.querydsl.core.types.dsl.PathBuilder;

import be.heydari.lib.expressions.BoolPredicate;
import be.heydari.lib.expressions.Conjunction;
import be.heydari.lib.expressions.Disjunction;

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
//  But, the subject of an `insert into` where clause differ from those of a select (or delete) and therefore from the abac policies
//  disjunctions that a findById or delete receive.
//
//  I assume that OPA policies be written such that save requests send abac policy disjunctions with the right subject type?
//
    @Override
    public <T> T invokeSave(T object) {

        Disjunction abacContext = ABACContext.getCurrentAbacContext();
        if (abacContext == null) {
            return super.invokeSave(object);
        }

        EntityInformation ei = EntityContext.getCurrentEntityContext();

        if (ei.isNew(object) == false) {
            enforceAbacAttributes(object, abacContext);
        }

        return super.invokeSave(object);
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

    private void enforceAbacAttributes(Object entity, Disjunction abacContext) {
        BeanWrapper wrapper = new BeanWrapperImpl(entity);

        for (Conjunction conjunction : abacContext.getConjunctivePredicates()) {

            for (BoolPredicate predicate : conjunction.getPredicates()) {

                try {
                    String strProperty = predicate.getLeft().getColumn();
                    Object value = wrapper.getPropertyValue(strProperty);
                    if (!conversionService.convert(predicate.getRight().getValue(), wrapper.getPropertyType(strProperty)).equals(value)) {
                        throw new SecurityException();
                    }
                } catch (NullValueInNestedPathException nvinpe) {
                    throw new SecurityException();
                } catch (InvalidPropertyException ipe) {}
            }
        }
    }
}
