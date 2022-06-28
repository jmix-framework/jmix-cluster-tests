package io.jmix.samples.cluster.test_system;

import io.jmix.samples.cluster.test_system.impl.BaseClusterTest;
import io.jmix.samples.cluster.test_system.model.TestContext;
import io.jmix.samples.cluster.test_system.model.TestInfo;
import io.jmix.samples.cluster.test_system.model.annotations.TestStep;
import io.jmix.samples.cluster.test_system.model.step.PodStep;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.jmx.export.annotation.*;
import org.springframework.stereotype.Component;
import org.springframework.util.ReflectionUtils;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;


@ManagedResource(description = "Entry point for cluster testing", objectName = "jmix.cluster:type=ClusterTestBean")
@Component("cluster_ClusterTestManagementFacade")
public class ClusterTestManagementFacade implements ApplicationContextAware, BeanPostProcessor {//todo other -aware?
    private ApplicationContext applicationContext;

    private List<BaseClusterTest> tests = new LinkedList<>();
    private List<TestInfo> testInfos = new LinkedList<>();//todo decide which



    @ManagedAttribute(description = "ClusterTest size (example attribute)")//todo remove
    public long getSize() {
        return tests.size();
    }

    //todo types
    @ManagedAttribute(description = "Describes cluster test set")
    public List<TestInfo> getTests() {

        return Collections.emptyList();//todo!! build testInfos
    }
    @ManagedOperation(description = "Run test")
    @ManagedOperationParameters({
            @ManagedOperationParameter(name = "stepOrder", description = "Order of step in test")
    })
    public boolean runTest(/*TestInfo info,*/ int stepOrder){//todo pass context through jmx from system
        PodStep step = (PodStep) tests.get(0).getSteps().stream().filter(t->t.getOrder()==stepOrder).findFirst().get();//TODO!!

        return step.getAction().doStep(new TestContext());//TODO!!!
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }

    @Override
    public Object postProcessBeforeInitialization(Object bean, String beanName) throws BeansException {
        if (bean instanceof BaseClusterTest) {
            System.out.println("Cluster test found: " + beanName);//todo
            tests.add((BaseClusterTest) bean);
            processTestAnnotations((BaseClusterTest) bean);
        }

        return bean;
    }

    private void processTestAnnotations(BaseClusterTest bean) {
        for (Method method : ReflectionUtils.getDeclaredMethods(bean.getClass())) {
            TestStep stepAnnotation = method.getAnnotation(TestStep.class);
            if (stepAnnotation != null) {
                PodStep.StepAction action = new PodStep.StepAction() {
                    @Override
                    public boolean doStep(TestContext context) {
                        try {
                            Object result = method.invoke(bean,context); //todo more safe way?
                            //todo smart detection of property types and "injection"
                            return result instanceof Boolean && (boolean) result;//todo result processing
                        } catch (IllegalAccessException | InvocationTargetException e) {
                            throw new RuntimeException(e);//todo correct error processing and returning of result
                        }
                    }
                };
                //todo how to organise invocation
                bean.addStep(new PodStep(stepAnnotation.order(),stepAnnotation.nodes(),action));
            }
            //todo the same for ControlStep and UiStep
        }
    }
}