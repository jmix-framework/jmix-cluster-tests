package io.jmix.samples.cluster.test_system.model;

import io.jmix.samples.cluster.test_system.model.annotations.ClusterTest;
import io.jmix.samples.cluster.test_system.model.step.PodStep;
import io.jmix.samples.cluster.test_system.model.step.TestStep;

import java.io.Serializable;
import java.util.*;

public class TestInfo implements Serializable {//todo immutability
    private static final long serialVersionUID = -8002207034814424879L;

    //todo group to resources
    private Set<String> podNames = new HashSet<>();

    private String beanName;

    private List<TestStep> steps;

    private String description = "";
    private Set<String> initNodes;
    private boolean cleanStart;

    private boolean alwaysRunAfterTestAction;

    public TestInfo(String beanName,
                    List<TestStep> steps,
                    ClusterTest properties,
                    boolean alwaysRunAfterTestAction) {
        this.beanName = beanName;
        this.steps = steps;
        for (TestStep step : steps) {
            if (step instanceof PodStep) {
                podNames.addAll(((PodStep) step).getNodes());
            }
        }

        podNames = Collections.unmodifiableSet(podNames);

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

        this.alwaysRunAfterTestAction = alwaysRunAfterTestAction;
    }

    public String getDescription() {
        return description;
    }

    public Set<String> getPodNames() {
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

    public boolean isAlwaysRunAfterTestAction() {
        return alwaysRunAfterTestAction;
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
