package io.jmix.samples.cluster.test_system.model.step;

import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

//todo javadocs here and in another places too
public class ControlStep extends AbstractTestStep {
    private static final long serialVersionUID = 7023310829995649007L;

    public enum Operation {
        ADD,
        RECREATE_ALL
    }

    private final Operation operation;

    private final List<String> nodeNames;

    public ControlStep(int order, Operation operation, @Nullable String[] nodeNames) {
        super(order);
        this.operation = operation;
        if (operation == Operation.ADD) {
            this.nodeNames = Arrays.asList(Objects.requireNonNull(nodeNames));
        } else {
            this.nodeNames = null;
        }
    }

    public Operation getOperation() {
        return operation;
    }

    public List<String> getNodeNames() {
        return nodeNames;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ControlStep)) return false;
        if (!super.equals(o)) return false;
        ControlStep that = (ControlStep) o;
        return operation == that.operation && Objects.equals(nodeNames, that.nodeNames);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), operation, nodeNames);
    }

    @Override
    public String toString() {
        return "ControlStep{ " + operation +
                " nodes: " + (nodeNames == null ? "all" : nodeNames) +
                '}';
    }
}
