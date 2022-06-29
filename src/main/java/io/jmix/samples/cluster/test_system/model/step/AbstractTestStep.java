package io.jmix.samples.cluster.test_system.model.step;

import java.io.Serializable;

public class AbstractTestStep implements TestStep, Serializable {
    //todo final
    protected int order;//todo Integer? serialization?

    public AbstractTestStep(int order) {
        this.order = order;
    }

    @Override
    public int getOrder() {
        return order;
    }
}
