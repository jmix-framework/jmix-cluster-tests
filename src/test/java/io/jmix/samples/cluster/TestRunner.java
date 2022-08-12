package io.jmix.samples.cluster;

import io.jmix.samples.cluster.test_support.K8sControlTool;
import io.jmix.samples.cluster.test_system.model.TestContext;
import io.jmix.samples.cluster.test_system.model.TestInfo;
import io.jmix.samples.cluster.test_system.model.TestResult;
import io.jmix.samples.cluster.test_system.model.TestStepException;
import io.jmix.samples.cluster.test_system.model.step.PodStep;
import io.jmix.samples.cluster.test_system.model.step.TestStep;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.slf4j.Logger;

import javax.management.*;
import javax.management.remote.JMXConnector;
import javax.management.remote.JMXConnectorFactory;
import javax.management.remote.JMXServiceURL;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class TestRunner {
    public static final String JMX_SERVICE_CUSTOM_URL = "service:jmx:jmxmp://localhost:%s";
    public static final String CLUSTER_TEST_BEAN_NAME = "jmix.cluster:type=ClusterTestBean";
    public static final String TEST_SIZE_ATTRIBUTE = "Size";
    public static final String TEST_LIST_ATTRIBUTE = "Tests";
    public static final String TEST_RUN_OPERATION = "runTest";

    public static final int APP_STARTUP_TIMEOUT_SEC = 120;
    public static final int APP_STARTUP_CHECK_PERIOD_SEC = 10;
    private static final Logger log = org.slf4j.LoggerFactory.getLogger(TestRunner.class);//todo

    public static final boolean localMode = false;

    @Test
    @Order(1)
    void testScalingProcess() throws Exception {
        try (K8sControlTool k8s = new K8sControlTool()) {
            k8s.scalePods(3);
            waitAppsReady(k8s.getPodPorts());

            k8s.scalePods(1);
            waitAppsReady(k8s.getPodPorts());

            k8s.scalePods(2);
            waitAppsReady(k8s.getPodPorts());
        }
    }

    @Test
    @Order(2)
    void checkK8sApi() throws Exception {
        try (K8sControlTool k8s = new K8sControlTool()) {
            k8s.scalePods(3);

            LinkedHashMap<String, String> podPorts = k8s.getPodPorts();
            waitAppsReady(k8s.getPodPorts());

            List<TestInfo> common = null;
            for (String port : podPorts.values()) {
                List<TestInfo> tests = loadTests(port).collect(Collectors.toList());
                assertNotNull(tests);
                assertFalse(tests.isEmpty());
                if (common == null) {
                    common = tests;
                    continue;
                }
                assertThat(tests, is(equalTo(common)));
            }

            k8s.scalePods(2);
        }
    }

    public static void waitAppsReady(Map<String, String> podPorts) {//todo refactor
        //todo async?
        for (Map.Entry<String, String> podPort : podPorts.entrySet()) {
            System.out.println("Waiting port '" + podPort.getValue() + "' for pod '" + podPort.getKey() + "'...");
            AtomicBoolean sucess = new AtomicBoolean(false);
            long startTime = System.currentTimeMillis();
            RuntimeException lastException = null;
            while (!sucess.get()) {
                try {
                    doInJmxConnection(podPort.getValue(), ((connection, objectName) -> {
                        long size = (long) connection.getAttribute(objectName, TEST_SIZE_ATTRIBUTE);
                        if (size > 0) {
                            sucess.set(true);
                        } else {
                            throw new RuntimeException("No tests for pod '" + podPort.getKey() + "'");
                        }
                    }));
                } catch (RuntimeException e) {
                    lastException = e;
                }
                if (System.currentTimeMillis() - startTime > APP_STARTUP_TIMEOUT_SEC * 1000) {
                    if (lastException == null) {
                        throw new RuntimeException(
                                String.format("Cannot access app on pod '%s' through port %s: timeout reached",
                                        podPort.getKey(),
                                        podPort.getValue()));
                    } else {
                        throw new RuntimeException(
                                String.format("Cannot access app on pod '%s' through port %s: timeout reached. See nested exception.",
                                        podPort.getKey(),
                                        podPort.getValue()),
                                lastException);
                    }

                }

                try {
                    if (!sucess.get()) {
                        Thread.sleep(APP_STARTUP_CHECK_PERIOD_SEC * 1000);
                    }
                } catch (InterruptedException e) {
                    throw new RuntimeException("Error during waiting app check period", e);//todo another message
                }
            }
            log.info("App on pod {} accessible. Waiting time: {} seconds",
                    podPort.getKey(),
                    ((double) System.currentTimeMillis() - startTime) / 1000);
        }
    }


    //todo run single test
    @Order(20)
    @ParameterizedTest(name = "[{index}]: {arguments}")
    @MethodSource("loadTests")
    void clusterTests(TestInfo info) throws Throwable {
        assertNotNull(info);
        log.info("Starting test {}", info);//todo normal logs

        Set<String> requiredPods = info.getPodNames();
        log.info("{} app instances required: {}", requiredPods.size(), requiredPods);
        try (K8sControlTool k8s = new K8sControlTool()) {
            if (info.isEagerInitPods()) {
                log.info("Eager pod initialization required, scaling...");
                k8s.scalePods(requiredPods.size());
                waitAppsReady(k8s.getPodPorts());
            }

            Map<String, String> portsByNames = new HashMap<>();
            Iterator<String> ports = k8s.getPorts().iterator();
            for (String name : requiredPods) {
                portsByNames.put(name, ports.next());
            }
            log.info("Pod ports mapped:{}", portsByNames);

            List<TestStep> steps = info.getSteps();
            log.info("Executing test steps...");
            TestContext testContext = new TestContext();
            for (TestStep step : steps) {
                log.info("  Executing step {}...", step);
                if (step instanceof PodStep) {
                    Collection<String> nodes = ((PodStep) step).getNodes();
                    if (nodes.isEmpty())
                        nodes = requiredPods;
                    for (String node : nodes) {
                        log.info("    Invoking step {} for node {} ...", step.getOrder(), node);
                        AtomicReference<TestResult> resultRef = new AtomicReference<>(null);
                        TestContext finalTestContext = testContext;
                        doInJmxConnection(localMode ? K8sControlTool.INNER_JMX_PORT : portsByNames.get(node),
                                (conn, objectName) -> {
                                    resultRef.set((TestResult) conn.invoke(objectName,
                                            TEST_RUN_OPERATION,
                                            new Object[]{info.getBeanName(), step.getOrder(), finalTestContext},
                                            new String[]{String.class.getName(), int.class.getName(), TestContext.class.getName()}));

                                });
                        if (resultRef.get() != null) {
                            TestResult result = resultRef.get();
                            StringBuilder builder = new StringBuilder();
                            for (String logRecord : result.getLogs()) {
                                builder.append("          |- ")
                                        .append(logRecord)
                                        .append("\n");
                            }
                            log.info("      Node {} logs:\n{}", node, builder);
                            if (result.isSuccessfully()) {
                                testContext = result.getContext();//updating context
                                log.info("    Step {} for node {} finished sucessfully.", step.getOrder(), node);
                                log.info("    Test Context: {}", testContext);
                            } else {
                                Throwable throwable = result.getException();
                                if (throwable instanceof TestStepException)
                                    throwable = throwable.getCause();
                                log.error("    Step {} for node {} finished with error.", step.getOrder(), node);
                                throw throwable;
                            }
                        }

                    }

                } else {
                    throw new RuntimeException("Not implemented yet!");
                }
                log.info("  Step {} finished sucessfully!", step);
            }
            log.info("Test {} finished sucessfully!", info);
        }
    }


    @Test
    void singleClusterTest() throws Throwable {
        String testBeanName = System.getProperty("testBeanName");
        List<TestInfo> testInfos = loadTests()
                .filter(info -> info.getBeanName().equals(testBeanName))
                .collect(Collectors.toList());
        if (testInfos.size() < 1) {
            throw new IllegalArgumentException("No such test for bean " + testBeanName);
        }

        TestInfo info = testInfos.iterator().next();
        log.info("Running single test: {}", info);
        clusterTests(info);//todo check that test can be run without problems because of parametrized
    }

    //todo not static
    static Stream<TestInfo> loadTests() throws Exception {
        try (K8sControlTool k8s = new K8sControlTool()) {//todo scale to 0 than to 1? (in order to kill all orphan port-forward processes?)
            if (k8s.getPodCount() < 1) {
                k8s.scalePods(1);
            }
            waitAppsReady(k8s.getPodPorts());
            return loadTests(localMode ? K8sControlTool.INNER_JMX_PORT : k8s.getPodPorts().values().iterator().next());
        }

    }

    static Stream<TestInfo> loadTests(String port) throws IOException, MalformedObjectNameException, ReflectionException, InstanceNotFoundException, IntrospectionException, AttributeNotFoundException, MBeanException {
        JMXServiceURL url = new JMXServiceURL(String.format(JMX_SERVICE_CUSTOM_URL, port));

        MBeanServerConnection connection;
        try (JMXConnector connector = JMXConnectorFactory.connect(url)) {
            connection = connector.getMBeanServerConnection();
            ObjectName beanName = new ObjectName(CLUSTER_TEST_BEAN_NAME);
            MBeanInfo info = connection.getMBeanInfo(beanName);
            System.out.println(info);
            List<TestInfo> result = (List<TestInfo>) connection.getAttribute(beanName, TEST_LIST_ATTRIBUTE);
            System.out.println(result);
            return result.stream();
        }
    }


    static void doInJmxConnection(String port, JMXAction action) {
        try {
            JMXServiceURL url = new JMXServiceURL(String.format(JMX_SERVICE_CUSTOM_URL, port));
            try (JMXConnector connector = JMXConnectorFactory.connect(url)) {
                MBeanServerConnection connection = connector.getMBeanServerConnection();
                ObjectName beanName = new ObjectName(CLUSTER_TEST_BEAN_NAME);
                action.accept(connection, beanName);
            }
        } catch (IOException | MalformedObjectNameException | MBeanException | AttributeNotFoundException |
                 InstanceNotFoundException |
                 ReflectionException e) {
            //todo check MBeanException

            throw new RuntimeException(String.format("Cannot connect to pod by port %s.", port), e);
        }
    }

    protected interface JMXAction {
        void accept(MBeanServerConnection connection, ObjectName beanName)
                throws IOException, MBeanException, AttributeNotFoundException, InstanceNotFoundException, ReflectionException;
    }


}
