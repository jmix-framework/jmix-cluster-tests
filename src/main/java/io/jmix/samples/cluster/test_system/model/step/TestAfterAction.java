package io.jmix.samples.cluster.test_system.model.step;

import io.jmix.samples.cluster.test_system.model.TestContext;
import io.jmix.samples.cluster.test_system.model.TestStepException;

public class TestAfterAction implements TestAction {
    private TestAction action;
    private boolean doAlways;

    public TestAfterAction(TestAction action, boolean doAlways) {
        this.action = action;
        this.doAlways = doAlways;
    }

    public void setDoAlways(boolean doAlways) {
        this.doAlways = doAlways;
    }

    public boolean isDoAlways() {
        return doAlways;
    }

    @Override
    public void doAction(TestContext context) throws TestStepException {
        action.doAction(context);
    }
}
