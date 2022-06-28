package io.jmix.samples.cluster.test_system.model.step;

public class AbstractTestStep implements TestStep{
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
