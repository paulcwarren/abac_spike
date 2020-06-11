package com.example.abac_spike;

import com.querydsl.core.QueryResults;
import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.core.types.Predicate;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.PathBuilder;
import com.querydsl.jpa.impl.JPAQuery;
import com.querydsl.jpa.impl.JPAQueryFactory;
import org.springframework.content.commons.utils.BeanUtils;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.provider.PersistenceProvider;
import org.springframework.data.jpa.repository.support.CrudMethodMetadata;
import org.springframework.data.jpa.repository.support.JpaEntityInformation;
import org.springframework.data.jpa.repository.support.JpaRepositoryImplementation;
import org.springframework.data.jpa.repository.support.SimpleJpaRepository;
import org.springframework.data.querydsl.QuerydslPredicateExecutor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;

import javax.persistence.EntityManager;
import javax.persistence.Id;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Optional;

public class QueryAugmentingRepository<T, ID> implements JpaRepositoryImplementation<T, ID>, QuerydslPredicateExecutor {

    private final JpaEntityInformation<T, ?> entityInformation;
    private final EntityManager em;
    private final PersistenceProvider provider;
    private final SimpleJpaRepository delegate;

    private CrudMethodMetadata metadata;

    public QueryAugmentingRepository(JpaEntityInformation<T, ?> entityInformation, EntityManager entityManager) {

        Assert.notNull(entityInformation, "JpaEntityInformation must not be null!");
        Assert.notNull(entityManager, "EntityManager must not be null!");

        this.entityInformation = entityInformation;
        this.em = entityManager;
        this.provider = PersistenceProvider.fromEntityManager(entityManager);
        this.delegate = new SimpleJpaRepository(entityInformation, entityManager);
    }

    @Override
    public Optional findOne(Predicate predicate) {
        return Optional.empty();
    }

    @Override
    public Iterable findAll(Predicate predicate) {
        return null;
    }

    @Override
    public Iterable findAll(Predicate predicate, Sort sort) {
        return null;
    }

    @Override
    public Page findAll(Predicate predicate, Pageable pageable) {
        return null;
    }

    @Override
    public long count(Predicate predicate) {
        return 0;
    }

    @Override
    public boolean exists(Predicate predicate) {
        return false;
    }

    @Override
    public Iterable findAll(OrderSpecifier[] orders) {
        return null;
    }

    @Override
    public Iterable findAll(Predicate predicate, OrderSpecifier[] orders) {
        return null;
    }

    @Override
    public void setRepositoryMethodMetadata(CrudMethodMetadata crudMethodMetadata) {
        this.metadata = crudMethodMetadata;
        this.delegate.setRepositoryMethodMetadata(crudMethodMetadata);
    }

    @Override
    public List<T> findAll() {
        return null;
    }

    @Override
    public List<T> findAll(Sort sort) {
        return null;
    }

    @Override
    public Page<T> findAll(Pageable pageable) {
        String abacContext = AbacSpikeApplication.AbacContext.getCurrentAbacContext();

        String[] abacContextFilterSpec = abacContext.split(" ");

        PathBuilder entityPath = new PathBuilder(this.entityInformation.getJavaType(), "entity");

        BooleanExpression abacExpr = null;
        PathBuilder abacPath = entityPath.get(abacContextFilterSpec[0], String.class);
        if (abacContextFilterSpec[1].equals("=")) {
            abacExpr = abacPath.eq(abacContextFilterSpec[2]);
        }

        JPAQueryFactory queryFactory = new JPAQueryFactory(em);
        JPAQuery q = queryFactory.selectFrom(entityPath);
        if (abacExpr != null) {
            q.where(abacExpr);
        }

        q.offset(pageable.getOffset());
        q.limit(pageable.getPageSize());

        QueryResults results = q.fetchResults();
        return new PageImpl(results.getResults(), pageable, results.getTotal());
    }

    @Override
    public List<T> findAllById(Iterable<ID> ids) {
        return null;
    }

    @Override
    public long count() {
        return 0;
    }

    @Override
    public void deleteById(ID id) {
        int i=0;
    }

    @Override
    public void delete(T entity) {
        int i=0;
    }

    @Override
    public void deleteAll(Iterable<? extends T> entities) {
        int i=0;
    }

    @Override
    public void deleteAll() {
        int i=0;
    }

    @Transactional
    @Override
    public <S extends T> S save(S entity) {
        return (S) this.delegate.save(entity);
    }

    @Override
    public <S extends T> List<S> saveAll(Iterable<S> entities) {
        return null;
    }

    @Override
    public Optional<T> findById(ID id) {
        String abacContext = AbacSpikeApplication.AbacContext.getCurrentAbacContext();

        String[] abacContextFilterSpec = abacContext.split(" ");

        PathBuilder entityPath = new PathBuilder(this.entityInformation.getJavaType(), "entity");

        Field idField = BeanUtils.findFieldWithAnnotation(this.entityInformation.getJavaType(), Id.class);
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

        return (Optional<T>) Optional.ofNullable(q.fetchOne());
    }

    @Override
    public boolean existsById(ID id) {
        return false;
    }

    @Override
    public void flush() {
        int i=0;
    }

    @Override
    public <S extends T> S saveAndFlush(S entity) {
        return null;
    }

    @Override
    public void deleteInBatch(Iterable<T> entities) {
        int i=0;
    }

    @Override
    public void deleteAllInBatch() {
        int i=0;
    }

    @Override
    public T getOne(ID id) {
        return null;
    }

    @Override
    public <S extends T> Optional<S> findOne(Example<S> example) {
        return Optional.empty();
    }

    @Override
    public <S extends T> List<S> findAll(Example<S> example) {
        return null;
    }

    @Override
    public <S extends T> List<S> findAll(Example<S> example, Sort sort) {
        return null;
    }

    @Override
    public <S extends T> Page<S> findAll(Example<S> example, Pageable pageable) {
        return null;
    }

    @Override
    public <S extends T> long count(Example<S> example) {
        return 0;
    }

    @Override
    public <S extends T> boolean exists(Example<S> example) {
        return false;
    }

    @Override
    public Optional<T> findOne(Specification<T> spec) {
        return Optional.empty();
    }

    @Override
    public List<T> findAll(Specification<T> spec) {
        return null;
    }

    @Override
    public Page<T> findAll(Specification<T> spec, Pageable pageable) {
        return null;
    }

    @Override
    public List<T> findAll(Specification<T> spec, Sort sort) {
        return null;
    }

    @Override
    public long count(Specification<T> spec) {
        return 0;
    }
}
