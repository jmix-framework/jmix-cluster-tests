package io.jmix.samples.cluster.test_system.model;

import io.jmix.samples.cluster.test_system.model.step.TestStep;

import java.util.List;

public class TestInfo {
    //todo group to resources
    private List<String> nodeNames; //todo rename?

    //todo
    private String beanName;//todo

    transient private ClusterTest test;//todo

    private List<TestStep> beforeSteps;
    private List<TestStep> steps;
    private List<TestStep> afterSteps;

    private Class<? extends ClusterTest> testClass;

}
