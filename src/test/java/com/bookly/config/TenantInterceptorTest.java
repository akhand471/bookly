package com.bookly.config;

import com.bookly.security.TenantContext;
import jakarta.persistence.EntityManager;
import org.hibernate.Filter;
import org.hibernate.Session;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * Unit tests for TenantInterceptor.
 *
 * Uses a hand-rolled EntityManager stub to avoid Mockito's known difficulty
 * stubbing generic methods like EntityManager.unwrap(Class<T>) on JPA proxy interfaces.
 */
@ExtendWith(MockitoExtension.class)
class TenantInterceptorTest {

    @Mock private Session session;
    @Mock private Filter hibernateFilter;

    private TenantInterceptor interceptor;

    @BeforeEach
    void setUp() {
        TenantContext.clear();
        // Hand-rolled EntityManager that returns our session mock from unwrap()
        EntityManager entityManager = new StubEntityManager(session);
        interceptor = new TenantInterceptor(entityManager);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void preHandle_tenantContextSet_enablesHibernateFilterWithCorrectParam() throws Exception {
        UUID tenantId = UUID.randomUUID();
        TenantContext.setCurrentTenant(tenantId);
        when(session.enableFilter("tenantFilter")).thenReturn(hibernateFilter);

        boolean result = interceptor.preHandle(null, null, new Object());

        assertThat(result).isTrue();
        verify(session).enableFilter("tenantFilter");
        verify(hibernateFilter).setParameter("businessId", tenantId);
    }

    @Test
    void preHandle_noTenantContext_doesNotEnableFilter() throws Exception {
        // TenantContext is empty (public endpoint, no JWT)
        boolean result = interceptor.preHandle(null, null, new Object());

        assertThat(result).isTrue();
        verifyNoInteractions(session);
    }

    // ── Minimal EntityManager stub ─────────────────────────────────────────────

    /**
     * Minimal EntityManager that returns the given Session from unwrap().
     * All other methods throw UnsupportedOperationException.
     */
    private static class StubEntityManager implements EntityManager {

        private final Session session;

        StubEntityManager(Session session) {
            this.session = session;
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> T unwrap(Class<T> cls) {
            if (Session.class.isAssignableFrom(cls)) {
                return (T) session;
            }
            throw new IllegalArgumentException("Cannot unwrap to " + cls);
        }

        // All other EntityManager methods are unused in these tests
        @Override public void persist(Object entity) { throw new UnsupportedOperationException(); }
        @Override public <T> T merge(T entity) { throw new UnsupportedOperationException(); }
        @Override public void remove(Object entity) { throw new UnsupportedOperationException(); }
        @Override public <T> T find(Class<T> entityClass, Object primaryKey) { throw new UnsupportedOperationException(); }
        @Override public <T> T find(Class<T> entityClass, Object primaryKey, java.util.Map<String, Object> properties) { throw new UnsupportedOperationException(); }
        @Override public <T> T find(Class<T> entityClass, Object primaryKey, jakarta.persistence.LockModeType lockMode) { throw new UnsupportedOperationException(); }
        @Override public <T> T find(Class<T> entityClass, Object primaryKey, jakarta.persistence.LockModeType lockMode, java.util.Map<String, Object> properties) { throw new UnsupportedOperationException(); }
        @Override public <T> T getReference(Class<T> entityClass, Object primaryKey) { throw new UnsupportedOperationException(); }
        @Override public void flush() { throw new UnsupportedOperationException(); }
        @Override public void setFlushMode(jakarta.persistence.FlushModeType flushMode) { throw new UnsupportedOperationException(); }
        @Override public jakarta.persistence.FlushModeType getFlushMode() { throw new UnsupportedOperationException(); }
        @Override public void lock(Object entity, jakarta.persistence.LockModeType lockMode) { throw new UnsupportedOperationException(); }
        @Override public void lock(Object entity, jakarta.persistence.LockModeType lockMode, java.util.Map<String, Object> properties) { throw new UnsupportedOperationException(); }
        @Override public void refresh(Object entity) { throw new UnsupportedOperationException(); }
        @Override public void refresh(Object entity, java.util.Map<String, Object> properties) { throw new UnsupportedOperationException(); }
        @Override public void refresh(Object entity, jakarta.persistence.LockModeType lockMode) { throw new UnsupportedOperationException(); }
        @Override public void refresh(Object entity, jakarta.persistence.LockModeType lockMode, java.util.Map<String, Object> properties) { throw new UnsupportedOperationException(); }
        @Override public void clear() { throw new UnsupportedOperationException(); }
        @Override public void detach(Object entity) { throw new UnsupportedOperationException(); }
        @Override public boolean contains(Object entity) { throw new UnsupportedOperationException(); }
        @Override public jakarta.persistence.LockModeType getLockMode(Object entity) { throw new UnsupportedOperationException(); }
        @Override public void setProperty(String propertyName, Object value) { throw new UnsupportedOperationException(); }
        @Override public java.util.Map<String, Object> getProperties() { throw new UnsupportedOperationException(); }
        @Override public jakarta.persistence.Query createQuery(String qlString) { throw new UnsupportedOperationException(); }
        @Override public <T> jakarta.persistence.TypedQuery<T> createQuery(jakarta.persistence.criteria.CriteriaQuery<T> criteriaQuery) { throw new UnsupportedOperationException(); }
        @Override public jakarta.persistence.Query createQuery(jakarta.persistence.criteria.CriteriaUpdate updateQuery) { throw new UnsupportedOperationException(); }
        @Override public jakarta.persistence.Query createQuery(jakarta.persistence.criteria.CriteriaDelete deleteQuery) { throw new UnsupportedOperationException(); }
        @Override public <T> jakarta.persistence.TypedQuery<T> createQuery(String qlString, Class<T> resultClass) { throw new UnsupportedOperationException(); }
        @Override public jakarta.persistence.Query createNamedQuery(String name) { throw new UnsupportedOperationException(); }
        @Override public <T> jakarta.persistence.TypedQuery<T> createNamedQuery(String name, Class<T> resultClass) { throw new UnsupportedOperationException(); }
        @Override public jakarta.persistence.Query createNativeQuery(String sqlString) { throw new UnsupportedOperationException(); }
        @Override public jakarta.persistence.Query createNativeQuery(String sqlString, Class resultClass) { throw new UnsupportedOperationException(); }
        @Override public jakarta.persistence.Query createNativeQuery(String sqlString, String resultSetMapping) { throw new UnsupportedOperationException(); }
        @Override public jakarta.persistence.StoredProcedureQuery createNamedStoredProcedureQuery(String name) { throw new UnsupportedOperationException(); }
        @Override public jakarta.persistence.StoredProcedureQuery createStoredProcedureQuery(String procedureName) { throw new UnsupportedOperationException(); }
        @Override public jakarta.persistence.StoredProcedureQuery createStoredProcedureQuery(String procedureName, Class... resultClasses) { throw new UnsupportedOperationException(); }
        @Override public jakarta.persistence.StoredProcedureQuery createStoredProcedureQuery(String procedureName, String... resultSetMappings) { throw new UnsupportedOperationException(); }
        @Override public void joinTransaction() { throw new UnsupportedOperationException(); }
        @Override public boolean isJoinedToTransaction() { throw new UnsupportedOperationException(); }
        @Override public Object getDelegate() { throw new UnsupportedOperationException(); }
        @Override public void close() { throw new UnsupportedOperationException(); }
        @Override public boolean isOpen() { throw new UnsupportedOperationException(); }
        @Override public jakarta.persistence.EntityTransaction getTransaction() { throw new UnsupportedOperationException(); }
        @Override public jakarta.persistence.EntityManagerFactory getEntityManagerFactory() { throw new UnsupportedOperationException(); }
        @Override public jakarta.persistence.criteria.CriteriaBuilder getCriteriaBuilder() { throw new UnsupportedOperationException(); }
        @Override public jakarta.persistence.metamodel.Metamodel getMetamodel() { throw new UnsupportedOperationException(); }
        @Override public <T> jakarta.persistence.EntityGraph<T> createEntityGraph(Class<T> rootType) { throw new UnsupportedOperationException(); }
        @Override public jakarta.persistence.EntityGraph<?> createEntityGraph(String graphName) { throw new UnsupportedOperationException(); }
        @Override public jakarta.persistence.EntityGraph<?> getEntityGraph(String graphName) { throw new UnsupportedOperationException(); }
        @Override public <T> java.util.List<jakarta.persistence.EntityGraph<? super T>> getEntityGraphs(Class<T> entityClass) { throw new UnsupportedOperationException(); }
    }
}
