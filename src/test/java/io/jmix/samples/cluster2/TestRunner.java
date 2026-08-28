package io.jmix.samples.cluster2;

import io.jmix.core.DevelopmentException;
import io.jmix.samples.cluster2.test_support.jmx.JmxOperations;
import io.jmix.samples.cluster2.test_support.k8s.K8sControlTool;
import io.jmix.samples.cluster2.test_support.k8s.PodBridge;
import io.jmix.samples.cluster2.test_system.model.TestContext;
import io.jmix.samples.cluster2.test_system.model.TestInfo;
import io.jmix.samples.cluster2.test_system.model.TestResult;
import io.jmix.samples.cluster2.test_system.model.step.ControlStep;
import io.jmix.samples.cluster2.test_system.model.step.PodStep;
import io.jmix.samples.cluster2.test_system.model.step.TestStep;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assumptions.assumeTrue;


public class TestRunner {
    public static final String READY_ATTRIBUTE = "Ready";

    public static final String TEST_LIST_ATTRIBUTE = "Tests";
    public static final String TEST_RUN_OPERATION = "runTest";
    public static final String BEFORE_TEST_RUN_OPERATION = "runBeforeTestAction";
    public static final String AFTER_TEST_RUN_OPERATION = "runAfterTestAction";

    public static final int APP_STARTUP_TIMEOUT_SEC = 120;
    public static final int APP_STARTUP_CHECK_PERIOD_SEC = 10;
    private static final Logger log = LoggerFactory.getLogger(TestRunner.class);

    /**
     * Enables debug port-forwarding for application pods.
     * <p>
     * When {@code true}, pod debug port {@code 5006} is forwarded to local ports
     * starting from {@code 50001}. The default {@code false} is the normal test
     * mode; enable this only for manual debugging.
     * <p>
     * Set it from the command line instead of editing this file:
     * {@code ./gradlew test -DdebugPods=true ...}
     */
    public static final boolean debugPods = Boolean.getBoolean("debugPods");


    @Disabled
    @Test
    @Order(0)
    void scaleForwardAndSleepForewer() throws Exception {
        try (K8sControlTool k8s = new K8sControlTool(debugPods)) {

            k8s.scalePods(2);
            waitAppsReady(k8s.getPodBridges());

            log.info("Scaling process tested successfully. Sleep until shutdown manually");
            while (true) {
                Thread.sleep(5 * 60 * 1000);
            }
        }

    }


    //@Test
    //@Order(1)
    void testScalingProcess() throws Exception {
        try (K8sControlTool k8s = new K8sControlTool(debugPods)) {
            k8s.scalePods(3);
            waitAppsReady(k8s.getPodBridges());

            k8s.scalePods(1);
            waitAppsReady(k8s.getPodBridges());

            k8s.scalePods(2);
            waitAppsReady(k8s.getPodBridges());
        }
    }

    //@Test
    //@Order(2)
    void checkK8sApi() throws Exception {
        try (K8sControlTool k8s = new K8sControlTool(debugPods)) {
            k8s.scalePods(3);

            List<PodBridge> podBridges = k8s.getPodBridges();
            waitAppsReady(k8s.getPodBridges());

            List<TestInfo> common = null;
            for (PodBridge bridge : podBridges) {
                List<TestInfo> tests = loadTests(bridge.getJmxPort()).collect(Collectors.toList());
                assertNotNull(tests);
                assertFalse(tests.isEmpty());
                if (common == null) {
                    common = tests;
                    continue;
                }
                assertThat(tests).hasSameElementsAs(common);
            }

            k8s.scalePods(2);
        }
    }

    public static void waitAppsReady(List<PodBridge> bridges) {
        for (PodBridge bridge : bridges) {
            log.info("Waiting port '{}' for pod '{}'...", bridge.getJmxPort(), bridge.getName());
            boolean sucess = false;
            long startTime = System.currentTimeMillis();
            RuntimeException lastException = null;
            while (!sucess) {
                try {
                    sucess = JmxOperations.getAttribute(bridge.getJmxPort(), READY_ATTRIBUTE);
                } catch (RuntimeException e) {
                    lastException = e;
                }
                if (System.currentTimeMillis() - startTime > APP_STARTUP_TIMEOUT_SEC * 1000) {
                    if (lastException == null) {
                        throw new RuntimeException(
                                String.format("Cannot access app on pod '%s' through port %s: timeout reached",
                                        bridge.getName(),
                                        bridge.getJmxPort()));
                    } else {
                        throw new RuntimeException(
                                String.format("Cannot access app on pod '%s' through port %s: timeout reached. See nested exception.",
                                        bridge.getName(),
                                        bridge.getJmxPort()),
                                lastException);
                    }

                }

                try {
                    if (!sucess) {
                        Thread.sleep(APP_STARTUP_CHECK_PERIOD_SEC * 1000);
                    }
                } catch (InterruptedException e) {
                    throw new RuntimeException("Error during waiting app check period", e);
                }
            }
            log.info("App on pod {} accessible. Waiting time: {} seconds",
                    bridge.getName(),
                    ((double) System.currentTimeMillis() - startTime) / 1000);
        }
    }

    @Order(20)
    @ParameterizedTest(name = "[{index}]: {arguments}")
    @MethodSource("loadTests")
    void clusterTests(TestInfo info) throws Throwable {
        assertNotNull(info);
        log.info("Starting test {}", info);

        Set<String> requiredPods = info.getInitNodes();
        log.info("{} app instances required: {}", requiredPods.size(), requiredPods);
        try (K8sControlTool k8s = new K8sControlTool(debugPods)) {
            if (info.isCleanStart()) {
                log.info("Clean start required. Stopping all pods.");
                k8s.scalePods(0);
            }

            log.info("Init nodes {}", info.getInitNodes());

            k8s.scalePods(info.getInitNodes().size());
            waitAppsReady(k8s.getPodBridges());

            TestContext testContext = new TestContext();
            Map<String, String> portsByNames = new HashMap<>();
            Iterator<PodBridge> bridgeIterator = k8s.getPodBridges().iterator();
            for (String name : requiredPods) {
                PodBridge bridge = bridgeIterator.next();
                portsByNames.put(name, bridge.getJmxPort());
                testContext.registerNode(name, bridge.getNodeIp(), bridge.getJmxPort());
            }
            log.info("JMX ports mapped:{}", portsByNames);


            log.info("Executing before test action...");

            TestResult beforeTestResult = runTestAction(
                    testContext.getNodeMap().values().iterator().next().getJmxPort(),
                    BEFORE_TEST_RUN_OPERATION,
                    new Object[]{info.getBeanName(), testContext},
                    new String[]{String.class.getName(), TestContext.class.getName()},
                    new PrintParams("          |- ")
                            .errorMessage("BeforeTest action failed with error"));


            if (beforeTestResult.isSuccessfully()) {
                testContext = beforeTestResult.getContext();
            } else {
                throw beforeTestResult.getException();
            }

            List<TestStep> steps = info.getSteps();
            log.info("Executing test steps...");

            for (TestStep step : steps) {
                log.info("  Executing step {}...", step);
                if (step instanceof PodStep) {
                    Collection<String> nodes = ((PodStep) step).getNodes();
                    if (nodes.isEmpty())
                        nodes = testContext.getNodeNames();
                    for (String node : nodes) {
                        log.info("    Invoking step {} for node {} ...", step.getOrder(), node);
                        TestResult result = runTestAction(
                                testContext.getNodeInfo(node).getJmxPort(),
                                TEST_RUN_OPERATION,
                                new Object[]{info.getBeanName(), step.getOrder(), testContext},
                                new String[]{String.class.getName(), int.class.getName(), TestContext.class.getName()},
                                new PrintParams("          |- ")
                                        .logMessage("      Node " + node + " logs:")
                                        .errorMessage("    Step " + step.getOrder() + " for node " + node + " finished with error."));

                        testContext = result.getContext();
                        if (result.isSuccessfully()) {
                            log.info("    Step {} for node {} finished sucessfully.", step.getOrder(), node);

                        } else {
                            Throwable throwable = result.getException();
                            if (info.isAlwaysRunAfterTestAction()) {
                                log.info("    Running mandatory @AfterTest actions..");
                                runTestAction(
                                        testContext.getNodeMap().values().iterator().next().getJmxPort(),
                                        AFTER_TEST_RUN_OPERATION,
                                        new Object[]{info.getBeanName(), testContext},
                                        new String[]{String.class.getName(), TestContext.class.getName()},
                                        new PrintParams("          |- ")
                                                .errorMessage("    AfterTest action failed with error"));
                            }
                            throw throwable;
                        }
                    }

                } else if (step instanceof ControlStep) {
                    ControlStep controlStep = (ControlStep) step;
                    switch (controlStep.getOperation()) {
                        case ADD:
                            for (String nodName : controlStep.getNodeNames()) {
                                if (testContext.getNodeNames().contains(nodName)) {
                                    throw new DevelopmentException("Pod with name '" + nodName + "' has been already created");
                                }
                                k8s.scalePods(k8s.getPodCount() + 1);
                                List<PodBridge> bridges = k8s.getPodBridges();
                                Set<String> existingJmxPorts = testContext.getNodeMap().values().stream()
                                        .map(TestContext.NodeInfo::getJmxPort)
                                        .collect(Collectors.toSet());
                                for (PodBridge bridge : bridges) {
                                    if (!existingJmxPorts.contains(bridge.getJmxPort())) {
                                        testContext.registerNode(nodName, bridge.getNodeIp(), bridge.getJmxPort());
                                        log.info("    Node {} has been added. Related pod:{}:{}", nodName, bridge.getName(), bridge.getJmxPort());
                                    }
                                }
                            }
                            waitAppsReady(k8s.getPodBridges());
                            break;
                        case RECREATE_ALL:
                            throw new RuntimeException("Not implemented yet");
                    }
                } else {
                    throw new RuntimeException("Not implemented yet!");
                }
                log.info("  Step {} finished sucessfully!", step);
            }
            log.info("  Running AfterTest action...");

            TestResult afterResult = runTestAction(
                    portsByNames.values().iterator().next(),
                    AFTER_TEST_RUN_OPERATION,
                    new Object[]{info.getBeanName(), testContext},
                    new String[]{String.class.getName(), TestContext.class.getName()},
                    new PrintParams("          |- ")
                            .errorMessage("    AfterTest action failed with error"));

            if (!afterResult.isSuccessfully()) {
                throw afterResult.getException();
            }

            log.info("Test {} finished sucessfully!", info);
        }
    }

    protected TestResult runTestAction(String port, String operationName, Object[] params, String[] types, PrintParams printParams) {
        TestResult result = JmxOperations.invoke(port, operationName, params, types);

        StringBuilder builder = new StringBuilder();
        for (String logRecord : result.getLogs()) {
            builder.append(printParams.logPrefix)
                    .append(logRecord)
                    .append("\n");
        }

        log.info(printParams.logMessage + "\n{}", builder);

        log.info("Test Context: {}", result.getContext());
        if (!result.isSuccessfully()) {
            log.error(printParams.errorMessage, result.getException());
        }
        return result;
    }


    @Test
    void singleClusterTest() throws Throwable {
        String testBeanName = System.getProperty("testBeanName");
        assumeTrue(testBeanName != null && !testBeanName.isBlank(), "testBeanName is not set");

        List<TestInfo> testInfos = loadTests()
                .filter(info -> info.getBeanName().equals(testBeanName))
                .collect(Collectors.toList());
        if (testInfos.size() < 1) {
            throw new IllegalArgumentException("No such test for bean " + testBeanName);
        }

        TestInfo info = testInfos.iterator().next();
        log.info("Single test mode for {}", info);
        clusterTests(info);
    }

    static Stream<TestInfo> loadTests() throws Exception {
        try (K8sControlTool k8s = new K8sControlTool(debugPods)) {
            if (k8s.getPodCount() < 1) {
                k8s.scalePods(1);
            }
            waitAppsReady(k8s.getPodBridges());
            return loadTests(k8s.getPorts().iterator().next());
        }
    }

    public static Stream<TestInfo> loadTests(String port) {
        return (JmxOperations.<List<TestInfo>>getAttribute(port, TEST_LIST_ATTRIBUTE)).stream();
    }


    private static class PrintParams {
        String logPrefix = "";
        String logMessage = "";
        String errorMessage = "";

        public PrintParams() {
        }

        public PrintParams(String logPrefix) {
            this.logPrefix = logPrefix;
        }

        public PrintParams logPrefix(String logPrefix) {
            this.logPrefix = logPrefix;
            return this;
        }

        public PrintParams logMessage(String logMessage) {
            this.logMessage = logMessage;
            return this;
        }

        public PrintParams errorMessage(String errorMessage) {
            this.errorMessage = errorMessage;
            return this;
        }
    }
}
