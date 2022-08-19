package io.jmix.samples.cluster.tests.health_check;

import io.jmix.samples.cluster.test_system.model.TestContext;
import io.jmix.samples.cluster.test_system.model.annotations.ClusterTest;
import io.jmix.samples.cluster.test_system.model.annotations.Step;
import org.slf4j.Logger;
import org.springframework.stereotype.Component;

@Component("cluster_secondAnnotatedTest")
@ClusterTest(description = "Second test")
public class SecondAnnotatedTest {
    private static final Logger log = org.slf4j.LoggerFactory.getLogger(SecondAnnotatedTest.class);

    @Step(order = 1, nodes = "1")
    public boolean setupOne(TestContext context) {
        log.info("annotated test 2, step 1");
        return true;
    }

    @Step(order = 2, nodes = "2")
    public boolean doTwo(TestContext context) {
        log.info("annotated test 2, step 2");
        throw new RuntimeException("This test failed with exception by design.");
    }
}
