package io.jmix.samples.cluster.tests;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import io.jmix.core.UnconstrainedDataManager;
import io.jmix.samples.cluster.entity.User;
import io.jmix.samples.cluster.test_system.impl.SynchronizedListAppender;
import io.jmix.samples.cluster.test_system.model.TestContext;
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
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

@Component("cluster_EntityCacheTest")
//@ClusterTest(description = "Checks entity cache")//todo deal with it
//TODO USE ANOTHER ENTITY. MAYBE USER HAS BEEN QUERIED AT SOME
public class EntityCacheTest implements InitializingBean {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(EntityCacheTest.class);


    SynchronizedListAppender appender;

    protected TransactionTemplate transaction;
    @PersistenceContext
    protected EntityManager entityManager;

    @Autowired
    protected UnconstrainedDataManager dataManager;

    private JpaCache cache;

    protected UUID userId = UUID.fromString("ae90ad94-abad-44bc-b851-95e4eee3dea2");

    @Autowired//todo constructor-injection?
    protected void setTransactionManager(PlatformTransactionManager transactionManager) {
        transaction = new TransactionTemplate(transactionManager);
        transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        cache = (JpaCache) entityManager.getEntityManagerFactory().getCache();

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

    public void clearAll() {
        cache.clearQueryCache();//todo vs. evict
        appender.clear();
        try {//todo remove when test will be fixed
            Thread.sleep(20000);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    /*public void afterSuite(Object... entities) {//todo remove
        cache.clear();
        appender.clear();
    }*/

    @Step(order = 0)//todo not works for all pods? //todo shared cache? session problem?
    public void testFind(TestContext context) throws Exception {//todo make sure that user is not loaded somewhere else and not existed in cache already
        //clearAll();//todo enable after timeout will be removed

        appender.clear();

        loadUserAlone();

        assertThat(appender.filterMessages(m -> m.contains("> SELECT")).count()).isEqualTo(1);
        appender.clear();

        loadUserAlone();

        assertThat(appender.filterMessages(m -> m.contains("> SELECT")).count()).isEqualTo(0);

        clearAll();
    }

    @Step(order = 1, nodes = "1")
    public void testFindOne(TestContext context) throws Exception {
        clearAll();

        loadUserAlone();

        assertThat(appender.filterMessages(m -> m.contains("> SELECT")).count()).isEqualTo(1);

        appender.clear();//todo @AfterEach
        User user = loadUserAlone();

        assertThat(appender.filterMessages(m -> m.contains("> SELECT")).count()).isEqualTo(0);

    }

    @Step(order = 2, nodes = "2")
    public void testFindTwo(TestContext context) throws Exception {
        Thread.sleep(20000);
        appender.clear();
        User user = loadUserAlone();

        assertThat(appender.filterMessages(m -> m.contains("> SELECT")).count()).isEqualTo(0);//todo

        /*cache.evict(user, true);//todo not works in cluster
        cache.clear();//todo will it work?*/
        clearAll();

        log.info("Cache contains user: {}", cache.contains(user));
        context.put("user", user);
    }

    @Step(order = 3, nodes = "1")
    public void testEvicted(TestContext context) throws Exception {
        appender.clear();
        log.info("Cache contains user: {}", cache.contains(context.get("user")));

        loadUserAlone();

        assertThat(appender.filterMessages(m -> m.contains("> SELECT")).count()).isEqualTo(1);
    }

    private User loadUserAlone() {//todo through dataManager?
        AtomicReference<User> user = new AtomicReference<>();
        transaction.executeWithoutResult(status -> {
            user.set(entityManager.find(User.class, this.userId));
            assertThat(user.get()).isNotNull();
        });
        return user.get();
    }
}
