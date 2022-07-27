package io.jmix.samples.cluster;

import io.jmix.samples.cluster.test_support.K8sControlTool;
import io.jmix.samples.cluster.test_system.model.TestInfo;
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
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.*;

public class TestRunner {
    public static final String JMX_SERVICE_CUSTOM_URL = "service:jmx:jmxmp://localhost:%s";
    public static final String CLUSTER_TEST_BEAN_NAME = "jmix.cluster:type=ClusterTestBean";
    public static final String TEST_SIZE_ATTRIBUTE = "Size";
    public static final String TEST_LIST_ATTRIBUTE = "Tests";
    public static final String TEST_RUN_OPERATION = "runTest";

    public static final int APP_STARTUP_TIMEOUT_SEC = 90;//todo increase
    public static final int APP_STARTUP_CHECK_PERIOD_SEC = 10;
    private static final Logger log = org.slf4j.LoggerFactory.getLogger(TestRunner.class);//todo

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


    @Test
    @Order(10)
    void checkTestsLoaded() throws ReflectionException, MalformedObjectNameException, AttributeNotFoundException, InstanceNotFoundException, IntrospectionException, MBeanException, IOException {
        Stream<TestInfo> testInfos = loadTests("49003");
        assertNotNull(testInfos);
        assertTrue(testInfos.findAny().isPresent());
        //todo check consistency: each pod returns the same set of tests
    }

    //todo run single test
    @Order(20)
    @ParameterizedTest(name = "[{index}]: {arguments}")
    @MethodSource("loadTests")
    void clusterTests(TestInfo info) throws Exception {
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
            for (TestStep step : steps) {
                log.info("  Executing step {}...", step);
                if (step instanceof PodStep) {
                    for (String node : ((PodStep) step).getNodes()) {
                        log.info("    Invoking node {} step...", node);
                        doInJmxConnection(portsByNames.get(node), (conn, objectName) -> {
                            boolean result = (boolean) conn.invoke(objectName,
                                    TEST_RUN_OPERATION,
                                    new Object[]{info, step.getOrder()},
                                    new String[]{TestInfo.class.getName(), int.class.getName()});
                            if (!result) {//todo make sure exception thrown away to fail test and not processed by some wraper
                                throw new RuntimeException(String.format("Test %s failed on step %s for node %s",
                                        info,
                                        step.getOrder(),
                                        node));
                            }
                        });
                        log.info("    Step for node {} finished sucessfully.", node);
                    }

                } else {
                    throw new RuntimeException("Not implemented yet!");
                }
                log.info("  Step {} finished sucessfully!", step);
            }
            log.info("Test {} finished sucessfully!", info);
        }
    }

    //todo not static
    static Stream<TestInfo> loadTests() throws Exception {
        try (K8sControlTool k8s = new K8sControlTool()) {
            if (k8s.getPodCount() < 1) {
                k8s.scalePods(1);
                waitAppsReady(k8s.getPodPorts());
            }
            return loadTests(k8s.getPodPorts().values().iterator().next());

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
            throw new RuntimeException(String.format("Cannot connect to pod by port %s.", port), e);
        }
    }

    protected interface JMXAction {
        void accept(MBeanServerConnection connection, ObjectName beanName)
                throws IOException, MBeanException, AttributeNotFoundException, InstanceNotFoundException, ReflectionException;
    }


}
