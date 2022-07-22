package io.jmix.samples.cluster.test_system.impl;

import io.jmix.samples.cluster.test_system.model.ClusterTest;
import io.jmix.samples.cluster.test_system.model.TestContext;
import io.jmix.samples.cluster.test_system.model.step.PodStep;
import io.jmix.samples.cluster.test_system.model.step.TestStep;

import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

public class BaseClusterTest implements ClusterTest {//todo WITHOUT extension!!

    protected List<TestStep> steps = new LinkedList<>();//todo control uniqueness of step.order

    @Override
    public List<String> getNodeNames() {
        return Arrays.asList("1", "2");
    }//todo remove

    @Override
    public List<TestStep> getSteps() {
        return steps;//todo wrap?
    }

    public void setSteps(List<TestStep> steps) {//todo protect in order init bean only can change steps
        this.steps = steps;
    }

    @Override//todo some reflection instead of single method?
    public boolean doStep(TestContext context, PodStep step) {
        return step.getAction().doStep(context);
    }

}
