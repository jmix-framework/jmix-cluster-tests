package io.jmix.samples.cluster.tests;

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

import javax.sql.DataSource;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Component("cluster_QueryCacheTest")
@ClusterTest(description = "Checks query cache works correctly in cluster")
public class QueryCacheTest implements InitializingBean {
    public static final String QUERY = "select s from cluster_Sample s where s.name=:name";

    @Autowired
    private UnconstrainedDataManager dataManager;

    private SynchronizedListAppender appender;

    @Autowired
    private DataSource dataSource;

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
    public void checkOriginNode(TestContext context) {

        Sample entity = dataManager.create(Sample.class);
        entity.setName("one");
        dataManager.save(entity);

        queryCache.invalidateAll();
        appender.clear();
        List<Sample> reloaded = dataManager.load(Sample.class)
                .query(QUERY)
                .parameter("name", "one")
                .cacheable(true)
                .list();

        assertThat(reloaded.size()).isEqualTo(1);
        assertThat(queryCache.size()).isEqualTo(1);
        assertThat(getSelectQueriesCount()).isEqualTo(1);

        appender.clear();
        reloaded = dataManager.load(Sample.class)
                .query(QUERY)
                .parameter("name", "one")
                .cacheable(true)
                .list();

        assertThat(reloaded.size()).isEqualTo(1);
        assertThat(queryCache.size()).isEqualTo(1);
        assertThat(getSelectQueriesCount()).isEqualTo(0);

        context.put("entity", entity);
    }

    @Step(order = 1, nodes = "2")
    public void checkSecondNodeHasRecordInCache(TestContext context) {
        Sample entity = ((Sample) context.get("entity"));
        entity.setName("two");
        dataManager.save(entity);
    }

    @Step(order = 2, nodes = "1")
    public void checkCacheResetAfterUpdate(TestContext context) {
        appender.clear();
        Sample entity = ((Sample) context.get("entity"));
        List<Sample> reloaded = dataManager.load(Sample.class)
                .query(QUERY)
                .parameter("name", "one")
                .cacheable(true)
                .list();

        assertThat(reloaded.size()).isEqualTo(0);
        assertThat(queryCache.size()).isEqualTo(1);
        assertThat(getSelectQueriesCount()).isEqualTo(1);
    }

    @BeforeTest
    public void before() {
        clean();
        appender.start();
    }

    @AfterTest
    public void after() {
        clean();
        appender.stop();
    }

    public void clean() {
        appender.clear();
        queryCache.invalidateAll();
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        jdbcTemplate.update("delete from CLUSTER_SAMPLE");
    }

    private long getSelectQueriesCount() {
        return appender.filterMessages(m -> m.contains("> SELECT")).count();
    }

}
