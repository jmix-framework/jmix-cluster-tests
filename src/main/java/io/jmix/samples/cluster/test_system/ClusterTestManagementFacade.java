package io.jmix.samples.cluster.test_system;

import ch.qos.logback.classic.LoggerContext;
import io.jmix.samples.cluster.test_system.impl.ClusterTestImpl;
import io.jmix.samples.cluster.test_system.impl.SynchronizedListAppender;
import io.jmix.samples.cluster.test_system.model.TestContext;
import io.jmix.samples.cluster.test_system.model.TestInfo;
import io.jmix.samples.cluster.test_system.model.TestResult;
import io.jmix.samples.cluster.test_system.model.TestStepException;
import io.jmix.samples.cluster.test_system.model.annotations.AddNode;
import io.jmix.samples.cluster.test_system.model.annotations.ClusterTest;
import io.jmix.samples.cluster.test_system.model.annotations.RecreateNodes;
import io.jmix.samples.cluster.test_system.model.annotations.Step;
import io.jmix.samples.cluster.test_system.model.step.ControlStep;
import io.jmix.samples.cluster.test_system.model.step.PodStep;
import io.jmix.samples.cluster.test_system.model.step.TestStep;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.jmx.export.annotation.*;
import org.springframework.stereotype.Component;
import org.springframework.util.ReflectionUtils;

import javax.annotation.Nullable;
import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;


@ManagedResource(description = "Entry point for cluster testing", objectName = "jmix.cluster:type=ClusterTestBean")
@Component("cluster_ClusterTestManagementFacade")
public class ClusterTestManagementFacade implements BeanPostProcessor, InitializingBean {

    private List<TestInfo> testInfos = new LinkedList<>();//todo decide which

    private Map<String, ClusterTestImpl> testsByNames = new HashMap<>();//todo?

    private SynchronizedListAppender appender;


    @Override
    public void afterPropertiesSet() throws Exception {
        appender = new SynchronizedListAppender();

        LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
        context.getLogger("io.jmix").addAppender(appender);//todo or list of loggers: io.jmix, org.eclipselink, etc.
        context.getLogger("org.eclipselink").addAppender(appender);
        context.getLogger("org.eclipse").addAppender(appender);
        context.getLogger("eclipselink").addAppender(appender);
    }


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
    @ManagedOperationParameters({//todo overloaded method without context for manual checking?
            @ManagedOperationParameter(name = "beanName", description = "Name of the bean containing test"),
            @ManagedOperationParameter(name = "stepOrder", description = "Order of step in test"),
            @ManagedOperationParameter(name = "context", description = "Test context to store objects between test")
    })
    public TestResult runTest(String beanName, int stepOrder, @Nullable TestContext context) throws TestStepException {
        //todo pass logger to out result during execution and before exception
        TestResult result = new TestResult();
        appender.start();
        if (context == null)
            context = new TestContext();
        try {
            PodStep step = (PodStep) testsByNames.get(beanName)
                    .getSteps().stream()
                    .filter(t -> t.getOrder() == stepOrder)
                    .findFirst()
                    .get();
            step.getAction().doStep(context);
        } catch (TestStepException e) {
            result.setException(e);
            result.setSuccessfully(false);
        } finally {
            appender.stop();
            result.setLogs(new ArrayList<>(appender.getMessages()));
            result.setContext(context);
            appender.clear();
        }
        return result;
    }

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        if (testsByNames.containsKey(beanName))//todo check
            return bean;

        ClusterTest testAnnotation = bean.getClass().getAnnotation(ClusterTest.class);
        if (testAnnotation != null) {
            System.out.println("Cluster test found: " + beanName);//todo logs
            ClusterTestImpl testImpl = new ClusterTestImpl();
            processTestAnnotations(testImpl, bean);
            testInfos.add(new TestInfo(beanName, testImpl.getSteps(), testAnnotation));
            testsByNames.put(beanName, testImpl);
        }

        return bean;
    }

    private void processTestAnnotations(ClusterTestImpl testImpl, Object targetBean) {
        List<TestStep> steps = new LinkedList<>();
        Set<String> knownPods = new HashSet<>();
        for (Method method : ReflectionUtils.getDeclaredMethods(targetBean.getClass())) {
            Step stepAnnotation = method.getAnnotation(Step.class);
            if (stepAnnotation != null) {
                knownPods.addAll(Arrays.asList(stepAnnotation.nodes()));
                PodStep.StepAction action = context -> {
                    try {//todo beanFactory to get bean for case of wrapping this bean further in e.g. slf4j/logging aspect or something else
                        Object result = method.invoke(targetBean, context);
                        //todo smart detection of property types and "injection"
                        //todo ALLOW TO NOT USE CONTEXT
                        return result instanceof Boolean && (boolean) result;//todo result processing
                    } catch (IllegalAccessException e) {


                        throw new RuntimeException(e);//todo correct error processing and returning of result
                    } catch (InvocationTargetException e) {
                        if (e.getTargetException() != null) {//todo recheck, does it used at all
                            throw new TestStepException(e.getTargetException());
                        } else {
                            throw new RuntimeException(e);
                        }
                    }
                };

                steps.add(new PodStep(stepAnnotation.order(), stepAnnotation.nodes(), action));
            }

            Annotation[] annotations = method.getAnnotations();

            Arrays.stream(annotations).filter(a -> AddNode.class.equals(a.annotationType())).map(AddNode.class::cast)
                    .forEach(a -> {
                        steps.add(new ControlStep(a.order(), ControlStep.Operation.ADD, a.names()));
                    });
            Arrays.stream(annotations).filter(a -> RecreateNodes.class.equals(a.annotationType())).map(RecreateNodes.class::cast)
                    .forEach(a -> {
                        steps.add(new ControlStep(a.order(), ControlStep.Operation.RECREATE_ALL, null));
                    });


            //todo the same for ControlStep and UiStep
        }
        //todo check uniquiness!!!
        steps.sort(Comparator.comparing(TestStep::getOrder));
        testImpl.setSteps(steps);
        testImpl.setPodNames(knownPods);
    }
}