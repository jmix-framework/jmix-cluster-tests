package io.jmix.samples.cluster.test_system.model.step;

import io.jmix.samples.cluster.test_system.model.TestContext;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

//todo? divide to internal and external objects in order to avoid serialization issues?
public class PodStep extends AbstractTestStep {
    private static final long serialVersionUID = -7068551058455006687L;
    private List<String> nodes;
    transient private StepAction action;

    public PodStep(int order, String node, StepAction action) {
        this(order,new String[]{node},action);
    }

    public PodStep(int order, String[] nodes, StepAction action) {
        super(order);
        this.nodes = Arrays.asList(nodes);
        this.action = action;
    }

    public List<String> getNodes() {
        return nodes;
    }

    public void setNodes(List<String> nodes) {
        this.nodes = nodes;
    }

    public StepAction getAction() {
        return action;
    }

    public void setAction(StepAction action) {
        this.action = action;
    }

    public interface StepAction {
        boolean doStep(TestContext context);
    }

    @Override
    public boolean equals(Object o) {//todo consider actions (develop serialization for methodInvocation/scaling/ui task)
        if (this == o) return true;
        if (!(o instanceof PodStep)) return false;
        PodStep podStep = (PodStep) o;
        return nodes.equals(podStep.nodes);
    }

    @Override
    public int hashCode() {
        return Objects.hash(nodes);
    }

    @Override
    public String toString() {
        return "PodStep{" +
                "nodes=" + nodes +
                ", action=" + action +
                ", order=" + order +
                '}';
    }
}
