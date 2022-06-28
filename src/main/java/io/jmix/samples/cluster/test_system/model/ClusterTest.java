package io.jmix.samples.cluster.test_system.model;


import io.jmix.samples.cluster.test_system.model.step.PodStep;
import io.jmix.samples.cluster.test_system.model.step.TestStep;

import java.util.List;

public interface ClusterTest {//todo annotations!!!!!!!

    List<String> getNodeNames();
    //todo uniqueness of steps and reusing steps to define and to do step;
    //todo use Enum?
    List<TestStep> getSteps();

    boolean doStep(TestContext context, PodStep step);

}
