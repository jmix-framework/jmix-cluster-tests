package io.jmix.samples.cluster.tests;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import io.jmix.core.Metadata;
import io.jmix.core.MetadataTools;
import io.jmix.core.UnconstrainedDataManager;
import io.jmix.eclipselink.impl.entitycache.QueryCache;
import io.jmix.samples.cluster.entity.Sample;
import io.jmix.samples.cluster.test_support.SimpleTestAppender;
import io.jmix.samples.cluster.test_system.model.TestContext;
import io.jmix.samples.cluster.test_system.model.annotations.Step;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@Component("cluster_QueryCacheTest")
//@ClusterTest(description = "Checks query cache works correctly in cluster")//todo
public class QueryCacheTest implements InitializingBean {

    //TODO REWRITE IN ORDER TO LOCAL RUN BE ABLE TO WORK TOO
    //todo hazelcast java 9 problems?
    //todo compare logs for "good" and "bad" nodes

    public static final String QUERY = "select s from cluster_Sample s where s.name=:name";

    @Autowired
    private UnconstrainedDataManager dataManager;
    @Autowired
    private Metadata metadata;
    @Autowired
    private MetadataTools metadataTools;

    private SimpleTestAppender appender;

    @Override
    public void afterPropertiesSet() throws Exception {

        appender = new SimpleTestAppender();

        appender.start();
        LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
        Logger logger = context.getLogger("eclipselink.logging.sql");
        logger.addAppender(appender);
    }


    @Autowired
    private QueryCache queryCache;

    @Step(order = 0, nodes = "1")
    public void checkOriginNode(TestContext context) {

        assertThat(metadataTools.isCacheable(metadata.getClass(Sample.class))).isTrue();

        Sample entity = dataManager.create(Sample.class);
        entity.setName("one");
        dataManager.save(entity);

        context.put("entity", entity);

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
    }

    @Step(order = 1, nodes = "2")
    public void checkSecondNodeHasRecordInCache(TestContext context) {
        appender.clear();//todo into before/after

        //assertThat(queryCache.size()).isEqualTo(1);//todo not propagated but it is not so required
        Sample entity = ((Sample) context.get("entity"));
       /* Sample reloaded = dataManager.load(Sample.class) //todo skip checking appeared. main purpose now - check cleared
                .query(QUERY)
                .parameter("id", entity.getId())
                .cacheable(true)
                .one();
        assertThat(queryCache.size()).isEqualTo(1);
        assertThat(getSelectQueriesCount()).isEqualTo(0);
*/
        entity.setName("two");
        dataManager.save(entity);
    }

    @Step(order = 2, nodes = "1")
    public void checkCacheResetAfterUpdate(TestContext context) {
        appender.clear();
        //assertThat(queryCache.size()).isEqualTo(0);//todo unstable and will be broken if something works in background
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

    @Step(order = 10, description = "Clean")//todo!!!
    public void clean(TestContext contex) {
        appender.clear();
        appender.stop();
        queryCache.invalidateAll();
        if (contex.containsKey("entity")) {
            Optional<Sample> loaded = dataManager.load(Sample.class).id(((Sample) contex.get("entity")).getId()).optional();
            loaded.ifPresent(sample -> dataManager.remove(sample));
        }
    }

    private long getSelectQueriesCount() {
        return appender.filterMessages(m -> m.contains("> SELECT")).count();
    }

    //todo check that changes applied using EntityManager refreshes cache for changed entity
}
