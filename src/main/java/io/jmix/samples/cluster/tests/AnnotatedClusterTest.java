package io.jmix.samples.cluster.tests;

import io.jmix.samples.cluster.test_system.model.TestContext;
import io.jmix.samples.cluster.test_system.model.annotations.ClusterTest;
import io.jmix.samples.cluster.test_system.model.annotations.Step;
import org.slf4j.Logger;
import org.springframework.stereotype.Component;

@Component("cluster_annotatedClusterTest")
@ClusterTest(description = "First test, sample, eager init pods. Checks that context works.")
public class AnnotatedClusterTest {

    private static final Logger log = org.slf4j.LoggerFactory.getLogger(AnnotatedClusterTest.class);

    @Step(order = 1, nodes = "1")
    public boolean doStep1(TestContext context) {
        log.info("annotated test 1, step 1");
        context.put("a", "b");
        return true;
    }

    @Step(order = 2, nodes = "2")
    public boolean doStep2(TestContext context) {
        assert context.get("a") == "b";//todo!
        context.put("c", "d");
        log.info("annotated test 1, step 2");
        return true;
    }

    @Step(order = 3, nodes = {"1", "2"})
    public boolean doStep3(TestContext context) {
        assert context.get("a") == "b";//todo!
        assert context.get("c") == "d";//todo!
        context.put("e", "f");
        log.info("annotated test 1, step 3");

        return true;
    }
}
