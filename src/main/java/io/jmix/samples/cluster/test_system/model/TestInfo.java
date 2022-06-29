package io.jmix.samples.cluster.test_system.model;

import io.jmix.samples.cluster.test_system.model.step.TestStep;

import java.io.Serializable;
import java.util.List;

public class TestInfo implements Serializable {
    //todo group to resources
    private List<String> nodeNames; //todo rename?

    //todo
    private String beanName;//todo

    transient private ClusterTest test;//todo

    //private List<TestStep> beforeSteps;
    private List<TestStep> steps;
    //private List<TestStep> afterSteps;

    private Class<? extends ClusterTest> testClass;

    private String description;//todo and name?

    public TestInfo(List<TestStep> steps, String description) {
        this.steps = steps;
        this.description = description;

    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    @Override
    public String toString() {
        return "TestInfo:" + description;//todo
    }
}
