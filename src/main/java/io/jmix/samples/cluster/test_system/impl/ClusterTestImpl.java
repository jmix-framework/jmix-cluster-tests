package io.jmix.samples.cluster.test_system.impl;

import io.jmix.samples.cluster.test_system.model.TestInfo;
import io.jmix.samples.cluster.test_system.model.step.TestAction;
import io.jmix.samples.cluster.test_system.model.step.TestAfterAction;

import java.util.Collections;
import java.util.Map;

public class ClusterTestImpl {//todo WITHOUT extension!!
    protected TestInfo testInfo;

    protected Map<Integer, TestAction> stepActions;

    protected TestAction beforeTest;
    protected TestAfterAction afterTest;
    protected TestAction beforeStep;
    protected TestAfterAction afterStep;

    public ClusterTestImpl(
            Map<Integer, TestAction> stepActions,
            TestInfo info,
            TestAction beforeStep,
            TestAfterAction afterStep,
            TestAction beforeTest,
            TestAfterAction afterTest
    ) {
        this.stepActions = Collections.unmodifiableMap(stepActions);
        this.testInfo = info;
        this.beforeStep = beforeStep;
        this.afterStep = afterStep;
        this.beforeTest = beforeTest;
        this.afterTest = afterTest;
    }

    public TestAction getAction(int stepOrder) {
        return stepActions.get(stepOrder);
    }

    public Map<Integer, TestAction> getStepActions() {
        return stepActions;
    }

    public TestInfo getTestInfo() {
        return testInfo;
    }

    public TestAction getBeforeTest() {
        return beforeTest;
    }

    public TestAfterAction getAfterTest() {
        return afterTest;
    }

    public TestAction getBeforeStep() {
        return beforeStep;
    }

    public TestAfterAction getAfterStep() {
        return afterStep;
    }
}
