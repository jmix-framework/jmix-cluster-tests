package io.jmix.samples.cluster.tests;

import io.jmix.samples.cluster.test_system.impl.BaseClusterTest;
import io.jmix.samples.cluster.test_system.model.TestContext;
import io.jmix.samples.cluster.test_system.model.annotations.ClusterTestProperties;
import io.jmix.samples.cluster.test_system.model.annotations.TestStep;
import org.springframework.stereotype.Component;

@Component("cluster_secondAnnotatedTest")
@ClusterTestProperties(description = "Second test")
public class SecondAnnotatedTest extends BaseClusterTest {

    @TestStep(order = 1, nodes = "1")
    public boolean setupOne(TestContext context){
        System.out.println("annotated test 2, step 1");
        return true;
    }

    @TestStep(order = 2, nodes = "2")
    public boolean doTwo(TestContext context){
        System.out.println("annotated test 1, step 2");
        //todo fix again
        return false;
    }
}
