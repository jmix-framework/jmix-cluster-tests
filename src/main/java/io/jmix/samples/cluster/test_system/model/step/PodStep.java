package io.jmix.samples.cluster.test_system.model.step;

import io.jmix.samples.cluster.test_system.model.TestContext;

import java.util.Arrays;
import java.util.List;

public class PodStep extends AbstractTestStep {
    private List<String> nodes;
    private StepAction action;

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

    public interface StepAction{
        boolean doStep(TestContext context);
    }

}
