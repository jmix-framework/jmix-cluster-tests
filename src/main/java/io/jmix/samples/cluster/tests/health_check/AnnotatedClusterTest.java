package io.jmix.samples.cluster.tests.health_check;

import io.jmix.samples.cluster.test_system.model.TestContext;
import io.jmix.samples.cluster.test_system.model.annotations.AfterTest;
import io.jmix.samples.cluster.test_system.model.annotations.BeforeTest;
import io.jmix.samples.cluster.test_system.model.annotations.ClusterTest;
import io.jmix.samples.cluster.test_system.model.annotations.Step;
import org.slf4j.Logger;
import org.springframework.stereotype.Component;

import static org.assertj.core.api.Assertions.assertThat;

@Component("cluster_annotatedClusterTest")
@ClusterTest(cleanStart = true,
        description = "Check context works")
public class AnnotatedClusterTest {

    private static final Logger log = org.slf4j.LoggerFactory.getLogger(AnnotatedClusterTest.class);

    @BeforeTest
    public void doBefore(TestContext context) {
        log.info("@BeforeTest method invoked");
        context.put("initial", "exists");
        //throw new RuntimeException("@BeforeTest method failed by design");//enable to check @BeforeTest failing
    }

    @Step(order = 1, nodes = "1")
    public boolean doStep1(TestContext context) {
        log.info("annotated test 1, step 1");
        assertThat(context.get("initial")).isEqualTo("exists");
        context.put("a", "b");
        return true;
    }

    @Step(order = 2, nodes = "2")
    public boolean doStep2(TestContext context) {
        assertThat(context.get("a")).isEqualTo("b");
        context.put("c", "d");
        log.info("annotated test 1, step 2");
        return true;
    }

    @Step(order = 3, nodes = {"1", "2"})
    public boolean doStep3() {
        log.info("annotated test 1, step 3");

        return true;
    }

    @Step(order = 4)
    public boolean doStep4(TestContext context) {
        assertThat(context.get("a")).isEqualTo("b");
        assertThat(context.get("c")).isEqualTo("d");

        log.info("annotated test 1, step 3 [check that it works without context]");
        return true;
    }

    //@Step(order = 5)//enable to check test failing
    public void doStep5() {
        throw new RuntimeException("Test failed by design");
    }

    @AfterTest
    public void doAfter() {
        log.info("@AfterTest method invoked");
        //throw new RuntimeException("@AfterTest method failed by design");//enable to check @AfterTest failing
    }
}
