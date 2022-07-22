package io.jmix.samples.cluster.test_system;

import io.jmix.samples.cluster.test_system.impl.BaseClusterTest;
import io.jmix.samples.cluster.test_system.model.TestContext;
import io.jmix.samples.cluster.test_system.model.TestInfo;
import io.jmix.samples.cluster.test_system.model.annotations.ClusterTestProperties;
import io.jmix.samples.cluster.test_system.model.annotations.TestStep;
import io.jmix.samples.cluster.test_system.model.step.PodStep;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.jmx.export.annotation.*;
import org.springframework.stereotype.Component;
import org.springframework.util.ReflectionUtils;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;


@ManagedResource(description = "Entry point for cluster testing", objectName = "jmix.cluster:type=ClusterTestBean")
@Component("cluster_ClusterTestManagementFacade")
public class ClusterTestManagementFacade implements BeanPostProcessor {//todo other -aware?
    private List<TestInfo> testInfos = new LinkedList<>();//todo decide which

    private Map<String, BaseClusterTest> testsByNames = new HashMap<>();//todo?

    //todo healthCheck attribute/operation OR just call getTests()
    @ManagedAttribute(description = "ClusterTest size (example attribute)")//todo remove
    public long getSize() {
        return testInfos.size();
    }

    //todo types
    @ManagedAttribute(description = "Describes cluster test set")
    public List<TestInfo> getTests() {
        return testInfos;
    }

    @ManagedOperation(description = "Run test")
    @ManagedOperationParameters({
            @ManagedOperationParameter(name = "stepOrder", description = "Order of step in test")
    })
    public boolean runTest(TestInfo info, int stepOrder) {//todo pass context through jmx from system
        PodStep step = (PodStep) testsByNames.get(info.getBeanName())
                .getSteps().stream()
                .filter(t -> t.getOrder() == stepOrder)
                .findFirst()
                .get();//TODO!!

        return step.getAction().doStep(new TestContext());//TODO!!!
    }

    @Override
    public Object postProcessBeforeInitialization(Object bean, String beanName) throws BeansException {
        if (bean instanceof BaseClusterTest) {
            System.out.println("Cluster test found: " + beanName);//todo
            processTestAnnotations((BaseClusterTest) bean);
            ClusterTestProperties properties = AnnotatedElementUtils.findMergedAnnotation(bean.getClass(), ClusterTestProperties.class);
            testInfos.add(new TestInfo(beanName, ((BaseClusterTest) bean).getSteps(), properties));
            testsByNames.put(beanName, (BaseClusterTest) bean);
        }

        return bean;
    }

    private void processTestAnnotations(BaseClusterTest bean) {
        List<io.jmix.samples.cluster.test_system.model.step.TestStep> steps = new LinkedList<>();//todo step vs annotation name collision
        for (Method method : ReflectionUtils.getDeclaredMethods(bean.getClass())) {
            TestStep stepAnnotation = method.getAnnotation(TestStep.class);
            if (stepAnnotation != null) {
                PodStep.StepAction action = new PodStep.StepAction() {
                    @Override
                    public boolean doStep(TestContext context) {
                        try {
                            Object result = method.invoke(bean, context); //todo more safe way?
                            //todo smart detection of property types and "injection"
                            return result instanceof Boolean && (boolean) result;//todo result processing
                        } catch (IllegalAccessException | InvocationTargetException e) {
                            throw new RuntimeException(e);//todo correct error processing and returning of result
                        }
                    }
                };
                //todo how to organise invocation
                steps.add(new PodStep(stepAnnotation.order(), stepAnnotation.nodes(), action));
            }
            //todo the same for ControlStep and UiStep
        }
        steps.sort(Comparator.comparing(io.jmix.samples.cluster.test_system.model.step.TestStep::getOrder));
        bean.setSteps(steps);
    }
}