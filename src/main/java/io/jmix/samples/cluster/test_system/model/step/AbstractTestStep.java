package io.jmix.samples.cluster.test_system.model.step;

import java.io.Serializable;
import java.util.Objects;

public class AbstractTestStep implements TestStep, Serializable {

    protected int order;

    public AbstractTestStep(int order) {
        this.order = order;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof AbstractTestStep)) return false;
        AbstractTestStep that = (AbstractTestStep) o;
        return order == that.order;
    }

    @Override
    public int hashCode() {
        return Objects.hash(order);
    }

    @Override
    public int getOrder() {
        return order;
    }
}
