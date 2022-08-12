package io.jmix.samples.cluster.tests;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import io.jmix.core.DataManager;
import io.jmix.samples.cluster.entity.User;
import io.jmix.samples.cluster.test_system.impl.SynchronizedListAppender;
import io.jmix.samples.cluster.test_system.model.TestContext;
import io.jmix.samples.cluster.test_system.model.annotations.ClusterTest;
import io.jmix.samples.cluster.test_system.model.annotations.Step;
import org.eclipse.persistence.jpa.JpaCache;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;

@Component("cluster_EntityCacheTest")
@ClusterTest(description = "Checks entity cache")
public class EntityCacheTest implements InitializingBean {
    SynchronizedListAppender appender;

    protected TransactionTemplate transaction;
    @PersistenceContext
    protected EntityManager entityManager;
    @Autowired
    protected DataManager dataManager;

    private JpaCache cache;

    protected User user;

    @Autowired
    protected void setTransactionManager(PlatformTransactionManager transactionManager) {
        transaction = new TransactionTemplate(transactionManager);
        transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Override
    public void afterPropertiesSet() throws Exception {//todo another user?
        cache = (JpaCache) entityManager.getEntityManagerFactory().getCache();

        user = dataManager.unconstrained()
                .load(User.class)
                .query("select u from cluster_User u where u.username='test'")
                .one();

        appender = new SynchronizedListAppender() {
            @Override
            protected void append(ILoggingEvent eventObject) {
                messages.add(eventObject.getMessage() == null ? "" : eventObject.getMessage());
            }
        };

        appender.start();
        LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
        Logger logger = context.getLogger("eclipselink.logging.sql");
        logger.addAppender(appender);
    }

    public void beforeEachSequence() {//todo
        cache.clear();
    }

    public void afterEachSequence() {//todo
        cache.clear();
    }

    @Step(order = 0, nodes = "1")//todo not works for all pods? //todo shared cache? session problem?
    public void testFind(TestContext context) throws Exception {//todo make sure that user is not loaded somewhere else and not existed in cache already
        beforeEachSequence();

        appender.clear();

        loadUserAlone();

        assertThat(appender.filterMessages(m -> m.contains("> SELECT")).count(), is(1L));
        appender.clear();

        loadUserAlone();

        assertThat(appender.filterMessages(m -> m.contains("> SELECT")).count(), is(0L));

        afterEachSequence();
    }

    @Step(order = 1, nodes = "1")
    public void testFindOne(TestContext context) throws Exception {//todo make sure that user is not loaded somewhere else and not existed in cache already
        beforeEachSequence();

        appender.clear();

        loadUserAlone();

        assertThat(appender.filterMessages(m -> m.contains("> SELECT")).count(), is(1L));

    }

    @Step(order = 2, nodes = "2")
    public void testFindTwo(TestContext context) throws Exception {//todo make sure that user is not loaded somewhere else and not existed in cache already

        appender.clear();
        loadUserAlone();

        assertThat(appender.filterMessages(m -> m.contains("> SELECT")).count(), is(0L));//todo different queries?
    }

    private void loadUserAlone() {//todo through dataManager?
        transaction.executeWithoutResult(status -> {
            User user = entityManager.find(User.class, this.user.getId());
            assertThat(user, notNullValue());
        });

    }


}
