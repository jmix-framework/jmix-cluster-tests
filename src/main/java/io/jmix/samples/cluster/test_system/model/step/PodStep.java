package io.jmix.samples.cluster.test_system.model.step;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

public class PodStep extends AbstractTestStep {
    private static final long serialVersionUID = -7068551058455006687L;
    private List<String> nodes;

    public PodStep(int order, String... nodes) {
        super(order);
        this.nodes = Arrays.asList(nodes);
    }

    public List<String> getNodes() {
        return nodes;
    }

    public void setNodes(List<String> nodes) {
        this.nodes = nodes;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof PodStep)) return false;
        PodStep podStep = (PodStep) o;
        return nodes.equals(podStep.nodes) && order == podStep.order;
    }

    @Override
    public int hashCode() {
        return Objects.hash(nodes);
    }

    @Override
    public String toString() {
        return "PodStep{" +
                "nodes=" + nodes +
                ", order=" + order +
                '}';
    }
}
