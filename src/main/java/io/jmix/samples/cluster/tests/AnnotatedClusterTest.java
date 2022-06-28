package io.jmix.samples.cluster.tests;

import io.jmix.samples.cluster.test_system.impl.BaseClusterTest;
import io.jmix.samples.cluster.test_system.model.TestContext;
import io.jmix.samples.cluster.test_system.model.annotations.TestStep;
import org.springframework.stereotype.Component;

@Component
public class AnnotatedClusterTest extends BaseClusterTest {

    @TestStep(order = 1, nodes = "1")
    public boolean doStep1(TestContext context){
        System.out.println("annotated test 1, step 1");
        return true;
    }

    @TestStep(order = 2, nodes = "2")
    public boolean doStep2(TestContext context){
        System.out.println("annotated test 1, step 2");
        return true;
    }
}
