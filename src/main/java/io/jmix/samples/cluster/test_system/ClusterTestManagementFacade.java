package io.jmix.samples.cluster.test_system;

import ch.qos.logback.classic.LoggerContext;
import io.jmix.core.DevelopmentException;
import io.jmix.samples.cluster.test_system.impl.ClusterTestImpl;
import io.jmix.samples.cluster.test_system.impl.SynchronizedListAppender;
import io.jmix.samples.cluster.test_system.model.TestContext;
import io.jmix.samples.cluster.test_system.model.TestInfo;
import io.jmix.samples.cluster.test_system.model.TestResult;
import io.jmix.samples.cluster.test_system.model.TestStepException;
import io.jmix.samples.cluster.test_system.model.annotations.*;
import io.jmix.samples.cluster.test_system.model.step.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
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
public class ClusterTestManagementFacade implements BeanPostProcessor, InitializingBean, BeanFactoryAware {

    private static final Logger log = LoggerFactory.getLogger(ClusterTestManagementFacade.class);

    private BeanFactory beanFactory;

    private List<TestInfo> testInfos = new LinkedList<>();//todo immutability

    private Map<String, ClusterTestImpl> testsByNames = new HashMap<>();

    private SynchronizedListAppender appender;

    private volatile boolean ready = false;


    @Override
    public void afterPropertiesSet() throws Exception {
        appender = new SynchronizedListAppender();

        LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
        context.getLogger("io.jmix").addAppender(appender);//todo or list of loggers: io.jmix, org.eclipselink, etc.
        context.getLogger("org.eclipselink").addAppender(appender);
        context.getLogger("org.eclipse").addAppender(appender);
        context.getLogger("eclipselink").addAppender(appender);
    }


    @ManagedAttribute(description = "ClusterTest size (example attribute)")//todo remove
    public long getSize() {
        return testInfos.size();
    }

    @ManagedAttribute(description = "Returns true after ApplicationReadyEvent occurs")
    public boolean getReady() {
        return ready;
    }


    //todo types
    @ManagedAttribute(description = "Describes cluster test set")
    public List<TestInfo> getTests() {
        return testInfos;
    }

    @ManagedOperation(description = "Run test")
    @ManagedOperationParameters({
            @ManagedOperationParameter(name = "beanName", description = "Name of the bean containing test"),
            @ManagedOperationParameter(name = "stepOrder", description = "Order of step in test"),
            @ManagedOperationParameter(name = "context", description = "Test context to store objects between test")
    })
    public TestResult runTest(String beanName, int stepOrder, @Nullable TestContext context) throws TestStepException {
        TestResult result = new TestResult();
        ClusterTestImpl impl = testsByNames.get(beanName);
        appender.start();
        if (context == null)
            context = new TestContext();
        try {
            if (impl.getBeforeStep() != null) {
                impl.getBeforeStep().doAction(context);
            }
            TestAction action = impl.getAction(stepOrder);
            action.doAction(context);
        } catch (TestStepException e) {
            result.setException(e);
            result.setSuccessfully(false);
        } finally {
            if (impl.getAfterStep() != null && (impl.getAfterStep().isDoAlways() || result.isSuccessfully())) {
                impl.getAfterStep().doAction(context);
            }
            appender.stop();
            result.setLogs(new ArrayList<>(appender.getMessages()));
            result.setContext(context);
            appender.clear();
        }
        return result;
    }

    @ManagedOperation(description = "Run before test action")
    @ManagedOperationParameters({
            @ManagedOperationParameter(name = "beanName", description = "Name of the bean containing test"),
            @ManagedOperationParameter(name = "context", description = "Test context to store objects between test")
    })
    public TestResult runBeforeTestAction(String beanName, @Nullable TestContext context) {
        TestResult result = new TestResult();
        ClusterTestImpl impl = testsByNames.get(beanName);//todo deal with code duplication
        appender.start();
        if (context == null)
            context = new TestContext();
        try {
            if (impl.getBeforeTest() != null) {//todo! check on runner!
                impl.getBeforeTest().doAction(context);
            } else {
                log.info("No BeforeTest action found for test '{}'", beanName);
            }
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

    @ManagedOperation(description = "Run before test action")
    @ManagedOperationParameters({
            @ManagedOperationParameter(name = "beanName", description = "Name of the bean containing test"),
            @ManagedOperationParameter(name = "context", description = "Test context to store objects between test")
    })
    public TestResult runAfterTestAction(String beanName, @Nullable TestContext context) {
        TestResult result = new TestResult();
        ClusterTestImpl impl = testsByNames.get(beanName);
        appender.start();
        if (context == null)
            context = new TestContext();
        try {
            if (impl.getAfterTest() != null) {
                impl.getAfterTest().doAction(context);
            } else {//todo! check on runner!
                log.info("No AfterTest action found for test '{}'", beanName);
            }
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
        ClusterTest testAnnotation = bean.getClass().getAnnotation(ClusterTest.class);
        if (testAnnotation != null) {
            log.info("Cluster test found: " + beanName);
            ClusterTestImpl testImpl = processTestAnnotations(bean, beanName, testAnnotation);
            testInfos.add(testImpl.getTestInfo());
            testsByNames.put(beanName, testImpl);
        }

        return bean;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        log.warn("TDEBUG_CLUST: application READY");
        ready = true;
    }

    private ClusterTestImpl processTestAnnotations(Object targetBean, String beanName, ClusterTest annotation) {
        List<TestStep> steps = new LinkedList<>();
        Map<Integer, TestAction> actions = new HashMap<>();

        TestAction beforeStepAction = null;
        TestAfterAction afterStepAction = null;
        TestAction beforeTestAction = null;
        TestAfterAction afterTestAction = null;
        boolean alwaysRunAfterTestAction = false;//todo refactor better? (add also info about existence of afterTest in TestInfo)

        for (Method method : ReflectionUtils.getDeclaredMethods(targetBean.getClass())) {
            processStepAnnotation(targetBean, beanName, method, steps, actions);
            processControlAnnotations(method, steps);


            beforeStepAction = processAnnotatedMethod(beanName, method, BeforeStep.class, beforeStepAction != null);//todo make ClusterTestImpl sealable and move exception to setter?

            beforeTestAction = processAnnotatedMethod(beanName, method, BeforeTest.class, beforeTestAction != null);


            TestAction afterStepActionCandidate = processAnnotatedMethod(beanName, method, AfterStep.class, afterStepAction != null);
            if (afterStepActionCandidate != null) {
                afterStepAction = new TestAfterAction(
                        afterStepActionCandidate,
                        method.getAnnotation(AfterStep.class).alwaysRun());
            }

            TestAction afterTestActionCandidate = processAnnotatedMethod(beanName, method, AfterTest.class, afterTestAction != null);
            if (afterTestActionCandidate != null) {
                alwaysRunAfterTestAction = method.getAnnotation(AfterTest.class).alwaysRun();
                afterTestAction = new TestAfterAction(
                        afterTestActionCandidate,
                        alwaysRunAfterTestAction);

            }

        }

        steps.sort(Comparator.comparing(TestStep::getOrder));

        return new ClusterTestImpl(actions,
                new TestInfo(beanName, steps, annotation, alwaysRunAfterTestAction),
                beforeStepAction,
                afterStepAction,
                beforeTestAction,
                afterTestAction);
    }

    protected void processStepAnnotation(Object targetBean, String beanName, Method method, List<TestStep> steps, Map<Integer, TestAction> actions) {
        Step stepAnnotation = method.getAnnotation(Step.class);
        if (stepAnnotation != null) {

            TestAction action = createStepAction(beanName, method);
            steps.add(new PodStep(stepAnnotation.order(), stepAnnotation.nodes()));
            TestAction previousAction = actions.put(stepAnnotation.order(), action);
            if (previousAction != null) {//todo just log? or stop app (as now)?
                throw new DevelopmentException(
                        String.format("More than on test step with order %s found for test %s",
                                stepAnnotation.order(),
                                targetBean.getClass().getName()));
            }
        }
    }

    protected void processControlAnnotations(Method method, List<TestStep> steps) {
        Annotation[] annotations = method.getAnnotations();

        Arrays.stream(annotations).filter(a -> AddNode.class.equals(a.annotationType())).map(AddNode.class::cast)
                .forEach(a -> {
                    steps.add(new ControlStep(a.order(), ControlStep.Operation.ADD, a.names()));
                });
        Arrays.stream(annotations).filter(a -> RecreateNodes.class.equals(a.annotationType())).map(RecreateNodes.class::cast)
                .forEach(a -> {
                    steps.add(new ControlStep(a.order(), ControlStep.Operation.RECREATE_ALL, null));
                });

    }

    protected TestAction processAnnotatedMethod(String beanName, Method method, Class<? extends Annotation> annotationClass, boolean failIfExist) {
        Annotation annotation = method.getAnnotation(annotationClass);
        if (annotation != null) {
            if (failIfExist) {
                throw new DevelopmentException(String.format("Duplicated '%s' annotation in class %s",
                        annotationClass.getSimpleName(),
                        method.getDeclaringClass().getName()));
            }
            return createStepAction(beanName, method);
        }
        return null;
    }


    protected TestAction createStepAction(String beanName, Method method) {
        TestAction action = context -> {
            try {
                List<Object> argList = new LinkedList<>();
                for (Class<?> clazz : method.getParameterTypes()) {
                    if (clazz.isAssignableFrom(TestContext.class)) {//todo extensibility
                        argList.add(context);
                    } else {
                        argList.add(null);
                    }
                }
                method.invoke(beanFactory.getBean(beanName), argList.toArray());
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
        return action;
    }

    @Override
    public void setBeanFactory(BeanFactory beanFactory) throws BeansException {
        this.beanFactory = beanFactory;
    }
}