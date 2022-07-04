package io.jmix.samples.cluster.test_system.model;

import io.jmix.samples.cluster.test_system.model.annotations.ClusterTestProperties;
import io.jmix.samples.cluster.test_system.model.step.PodStep;
import io.jmix.samples.cluster.test_system.model.step.TestStep;

import javax.annotation.Nullable;
import java.io.Serializable;
import java.util.LinkedHashSet;
import java.util.List;

public class TestInfo implements Serializable {
    //todo group to resources
    private LinkedHashSet<String> podNames = new LinkedHashSet<>();

    private String beanName;

    //TODO: beforeTest/beforeAll/afterTest/afterAll
    private List<TestStep> steps;

    private String description = "";
    private boolean eagerInitPods = false;

    public TestInfo(String beanName, List<TestStep> steps, @Nullable ClusterTestProperties properties) {
        this.beanName = beanName;
        this.steps = steps;
        for (TestStep step : steps) {
            if (step instanceof PodStep) {
                podNames.addAll(((PodStep) step).getNodes());
            }
        }
        if (properties != null) {
            description = properties.description();
            eagerInitPods = properties.eagerInitPods();
        }
    }

    public String getDescription() {
        return description;
    }

    public LinkedHashSet<String> getPodNames() {
        return podNames;
    }

    public List<TestStep> getSteps() {
        return steps;
    }

    public String getBeanName() {
        return beanName;
    }

    @Override
    public String toString() {
        return String.format("Cluster test '%s':`%s", beanName, description);
    }
}
