package io.jmix.samples.cluster.tests;

import io.jmix.samples.cluster.test_system.model.ClusterTest;
import io.jmix.samples.cluster.test_system.model.step.PodStep;
import io.jmix.samples.cluster.test_system.model.TestContext;
import io.jmix.samples.cluster.test_system.model.step.TestStep;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class SmokeClusterTest implements ClusterTest {
    @Override
    public List<String> getNodeNames() {
        return Arrays.asList("1", "2");
    }

    @Override//todo abstract base class with boilerplate code for step generation
    public List<TestStep> getSteps() {
        List<TestStep> steps = new ArrayList<>(3);
        steps.add(new PodStep(0,"1", this::stepA1));
        steps.add(new PodStep(1,"1", this::stepB1));
        steps.add(new PodStep(2,"2", this::stepB2));
        return steps;
    }

    @Override//todo some reflection instead of single method?
    public boolean doStep(TestContext context, PodStep step) {
        return step.getAction().doStep(context);
    }

    public boolean stepA1(TestContext context) {
        return true;
    }

    public boolean stepB1(TestContext context) {
        return true;
    }

    public boolean stepB2(TestContext context) {
        return true;
    }
}
