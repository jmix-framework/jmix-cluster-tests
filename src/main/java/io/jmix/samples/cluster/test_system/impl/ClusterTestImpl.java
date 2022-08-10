package io.jmix.samples.cluster.test_system.impl;

import io.jmix.samples.cluster.test_system.model.ClusterTest;
import io.jmix.samples.cluster.test_system.model.TestContext;
import io.jmix.samples.cluster.test_system.model.TestStepException;
import io.jmix.samples.cluster.test_system.model.step.PodStep;
import io.jmix.samples.cluster.test_system.model.step.TestStep;

import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

public class ClusterTestImpl implements ClusterTest {//todo WITHOUT extension!!

    protected List<TestStep> steps = new LinkedList<>();//todo control uniqueness of step.order
    //todo do we need it at all or TestInfo can process it itself without using bean field?
    protected Set<String> podNames = new HashSet<>();//todo to constructor

    @Override
    public Set<String> getPodNames() {
        return podNames;
    }//todo remove

    @Override
    public List<TestStep> getSteps() {
        return steps;//todo wrap?
    }

    //todo make available during creation only (constructor?)
    public void setSteps(List<TestStep> steps) {//todo protect in order init bean only can change steps
        this.steps = steps;
    }

    //todo make available during creation only (constructor?)
    public void setPodNames(Set<String> podNames) {
        this.podNames = podNames;
    }

    @Override//todo some reflection instead of single method?
    public boolean doStep(TestContext context, PodStep step) throws TestStepException {
        return step.getAction().doStep(context);
    }

}
