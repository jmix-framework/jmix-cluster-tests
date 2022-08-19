package io.jmix.samples.cluster.test_system.model;

import io.jmix.samples.cluster.test_system.model.annotations.ClusterTest;
import io.jmix.samples.cluster.test_system.model.step.PodStep;
import io.jmix.samples.cluster.test_system.model.step.TestStep;

import java.io.Serializable;
import java.util.*;

public class TestInfo implements Serializable {
    private static final long serialVersionUID = -8002207034814424879L;

    //todo group to resources
    private LinkedHashSet<String> podNames = new LinkedHashSet<>();//todo set only? no need for ordering?

    private String beanName;

    //TODO: beforeTest/beforeAll/afterTest/afterAll
    private List<TestStep> steps;

    private String description = "";
    private Set<String> initNodes;
    private boolean cleanStart;

    public TestInfo(String beanName, List<TestStep> steps, ClusterTest properties) {
        this.beanName = beanName;
        this.steps = steps;
        for (TestStep step : steps) {
            if (step instanceof PodStep) {
                podNames.addAll(((PodStep) step).getNodes());
            }
        }
        description = properties.description();

        if (properties.initNodes().length == 1) {
            switch (properties.initNodes()[0]) {
                case ClusterTest.ALL_NODES:
                    initNodes = new HashSet<>(new ArrayList<>(podNames));
                    break;
                case ClusterTest.NO_NODES:
                    initNodes = new HashSet<>();
                    break;
                default:
                    initNodes = new HashSet<>(Arrays.asList(properties.initNodes()));
            }
        } else {
            initNodes = new HashSet<>(Arrays.asList(properties.initNodes()));
        }

        cleanStart = properties.cleanStart();

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

    public Set<String> getInitNodes() {
        return initNodes;
    }

    public boolean isCleanStart() {
        return cleanStart;
    }

    @Override
    public boolean equals(Object o) {//todo check
        if (this == o) return true;
        if (!(o instanceof TestInfo)) return false;
        TestInfo testInfo = (TestInfo) o;
        return Objects.equals(initNodes, testInfo.initNodes) && podNames.equals(testInfo.podNames) && beanName.equals(testInfo.beanName) && steps.equals(testInfo.steps) && Objects.equals(description, testInfo.description);
    }

    @Override
    public int hashCode() { //todo check
        return Objects.hash(podNames, beanName, steps, description, initNodes);
    }

    @Override
    public String toString() {
        return String.format("Cluster test '%s':'%s'", beanName, description);
    }
}
