package io.jmix.samples.cluster.tests.jmixcaches;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import io.jmix.core.UnconstrainedDataManager;
import io.jmix.samples.cluster.entity.User;
import io.jmix.samples.cluster.test_support.SimpleTestAppender;
import io.jmix.samples.cluster.test_system.impl.SynchronizedListAppender;
import io.jmix.samples.cluster.test_system.model.TestContext;
import io.jmix.samples.cluster.test_system.model.annotations.AfterTest;
import io.jmix.samples.cluster.test_system.model.annotations.BeforeTest;
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
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

@Component("cluster_EntityCacheTest")
@ClusterTest(description = "Checks entity cache")
public class EntityCacheTest implements InitializingBean {

    private static final org.slf4j.Logger log = LoggerFactory.getLogger(EntityCacheTest.class);


    private SynchronizedListAppender appender;

    protected TransactionTemplate transaction;
    @PersistenceContext
    protected EntityManager entityManager;

    @Autowired
    protected UnconstrainedDataManager dataManager;

    private JpaCache cache;

    protected UUID userId = UUID.fromString("ae90ad94-abad-44bc-b851-95e4eee3dea2");

    @Autowired
    protected void setTransactionManager(PlatformTransactionManager transactionManager) {
        transaction = new TransactionTemplate(transactionManager);
        transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        cache = (JpaCache) entityManager.getEntityManagerFactory().getCache();
        appender = new SimpleTestAppender();

        LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
        Logger logger = context.getLogger("eclipselink.logging.sql");
        logger.addAppender(appender);
    }


    @BeforeTest
    public void beforeTest() {
        clearAll();
        appender.start();
    }


    @AfterTest
    public void afterTest() {
        clearAll();
        appender.stop();
    }

    public void clearAll() {
        cache.clear();
        appender.clear();
    }


    @Step(order = 1, nodes = "1")
    public void cacheEntityOnFirstNode(TestContext context) throws Exception {
        clearAll();

        loadUserAlone();
        assertThat(appender.filterMessages(m -> m.contains("> SELECT")).count()).isEqualTo(1);

        appender.clear();
        User user = loadUserAlone();
        assertThat(appender.filterMessages(m -> m.contains("> SELECT")).count()).isEqualTo(0);

        user.setLastName("First");
        user = dataManager.save(user);
        context.put("user", user);
    }

    @Step(order = 2, nodes = "2")
    public void updateEntityOnSecondNode(TestContext context) throws Exception {

        appender.clear();
        User user = loadUserAlone();
        assertThat(user.getLastName()).isEqualTo("First");

        user.setLastName("Second");
        dataManager.save(user);

        log.info("Cache contains user: {}", cache.contains(user));
        context.put("user", user);
    }

    @Step(order = 3, nodes = "1")
    public void checkCacheOnFirstNodeCleared(TestContext context) throws Exception {
        appender.clear();
        log.info("Cache contains user: {}", cache.contains(context.get("user")));

        User user = loadUserAlone();
        assertThat(user.getLastName()).isEqualTo("Second");
        assertThat(appender.filterMessages(m -> m.contains("> SELECT")).count()).isEqualTo(1);
    }

    private User loadUserAlone() {
        AtomicReference<User> user = new AtomicReference<>();
        transaction.executeWithoutResult(status -> {
            user.set(entityManager.find(User.class, this.userId));
            assertThat(user.get()).isNotNull();
        });
        return user.get();
    }
}
