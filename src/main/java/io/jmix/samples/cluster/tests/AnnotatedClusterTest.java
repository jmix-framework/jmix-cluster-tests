package io.jmix.samples.cluster.tests;

import io.jmix.samples.cluster.test_system.model.TestContext;
import io.jmix.samples.cluster.test_system.model.annotations.ClusterTest;
import io.jmix.samples.cluster.test_system.model.annotations.Step;
import org.springframework.stereotype.Component;

@Component("cluster_annotatedClusterTest")
@ClusterTest(description = "First test, sample, eager init pods")
public class AnnotatedClusterTest {

    @Step(order = 1, nodes = "1")
    public boolean doStep1(TestContext context) {
        System.out.println("annotated test 1, step 1");
        return true;
    }

    @Step(order = 2, nodes = "2")
    public boolean doStep2(TestContext context) {
        System.out.println("annotated test 1, step 2");
        return true;
    }

    @Step(order = 3, nodes = {"1", "2"})
    public boolean doStep3(TestContext context) {
        System.out.println("annotated test 1, step 3");

        return true;
    }
}
