package io.jmix.samples.cluster.tests.jmixcaches;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import io.jmix.core.UnconstrainedDataManager;
import io.jmix.eclipselink.impl.entitycache.QueryCache;
import io.jmix.samples.cluster.entity.Sample;
import io.jmix.samples.cluster.test_support.SimpleTestAppender;
import io.jmix.samples.cluster.test_system.impl.SynchronizedListAppender;
import io.jmix.samples.cluster.test_system.model.TestContext;
import io.jmix.samples.cluster.test_system.model.annotations.AfterTest;
import io.jmix.samples.cluster.test_system.model.annotations.BeforeTest;
import io.jmix.samples.cluster.test_system.model.annotations.ClusterTest;
import io.jmix.samples.cluster.test_system.model.annotations.Step;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.sql.DataSource;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Component("cluster_QueryAndEntityCacheTest")
@ClusterTest(description = "Checks entity cache works correctly with query cache in cluster")
public class QueryAndEntityCacheTest implements InitializingBean {
    public static final String ALL_QUERY = "select s from cluster_Sample s where s.name like concat(:name,'%')";
    private static final org.slf4j.Logger log = LoggerFactory.getLogger(QueryAndEntityCacheTest.class);

    @Autowired
    private UnconstrainedDataManager dataManager;

    private SynchronizedListAppender appender;

    @Autowired
    private DataSource dataSource;

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    public void afterPropertiesSet() {
        appender = new SimpleTestAppender();

        LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
        Logger logger = context.getLogger("eclipselink.logging.sql");
        logger.addAppender(appender);
    }

    @Autowired
    private QueryCache queryCache;

    @Step(order = 0, nodes = "1")
    public void loadOnFirstNode(TestContext context) {
        //load first time to put query into query cache
        List<Sample> testEntities = dataManager.load(Sample.class)
                .query(ALL_QUERY)
                .parameter("name", "test_")
                .cacheable(true)
                .list();

        assertThat(testEntities.size()).isEqualTo(10);
    }

    @Step(order = 1, nodes = "2")
    public void loadOnSecondNode(TestContext context) {
        appender.start();
        try {
            appender.clear();
            List<Sample> testEntities = dataManager.load(Sample.class)
                    .query(ALL_QUERY)
                    .parameter("name", "test_")
                    .cacheable(true)
                    .list();

            assertThat(testEntities.size()).isEqualTo(10);

            assertThat(getSelectQueriesCount()).isEqualTo(1);
            assertThat(appender.getMessages().get(0).contains("(ID IN (?,?,?,?,?,?,?,?,?,?)")).isTrue();//the only query is a batch load query

            appender.clear();
            List<Sample> loadedSecondTime = dataManager.load(Sample.class)
                    .query(ALL_QUERY)
                    .parameter("name", "test_")
                    .cacheable(true)
                    .list();

            assertThat(loadedSecondTime.size()).isEqualTo(10);
            assertThat(getSelectQueriesCount()).isEqualTo(0);//entities present in entity cache. Thus, no requests to db is performed
        } finally {
            appender.stop();
        }
    }

    @BeforeTest
    public void before() {
        cleanData();
        for (int i = 0; i < 10; i++) {
            Sample entity = dataManager.create(Sample.class);
            entity.setName("test_" + i);
            dataManager.save(entity);
        }
        cleanCachesAndAppenders();
    }

    @AfterTest
    public void after() {
        cleanCachesAndAppenders();
        cleanData();
        appender.stop();
    }

    public void cleanData() {
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        jdbcTemplate.update("delete from CLUSTER_SAMPLE");
    }

    public void cleanCachesAndAppenders() {
        appender.clear();
        queryCache.invalidateAll();
        entityManager.getEntityManagerFactory().getCache().evictAll();
    }

    private long getSelectQueriesCount() {
        return appender.filterMessages(m -> m.contains("> SELECT")).count();
    }
}
