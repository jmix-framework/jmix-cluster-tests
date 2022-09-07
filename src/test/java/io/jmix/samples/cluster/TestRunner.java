package io.jmix.samples.cluster;

import io.jmix.core.DevelopmentException;
import io.jmix.samples.cluster.test_support.jmx.JmxOperations;
import io.jmix.samples.cluster.test_support.k8s.Fabric8K8sControlTool;
import io.jmix.samples.cluster.test_support.k8s.K8sControlTool;
import io.jmix.samples.cluster.test_support.k8s.PodBridge;
import io.jmix.samples.cluster.test_system.model.TestContext;
import io.jmix.samples.cluster.test_system.model.TestInfo;
import io.jmix.samples.cluster.test_system.model.TestResult;
import io.jmix.samples.cluster.test_system.model.step.ControlStep;
import io.jmix.samples.cluster.test_system.model.step.PodStep;
import io.jmix.samples.cluster.test_system.model.step.TestStep;
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


public class TestRunner {//todo move cluster tests to separate test in order to run without cl parameter
    public static final String TEST_SIZE_ATTRIBUTE = "Size";
    public static final String READY_ATTRIBUTE = "Ready";

    public static final String TEST_LIST_ATTRIBUTE = "Tests";
    public static final String TEST_RUN_OPERATION = "runTest";
    public static final String BEFORE_TEST_RUN_OPERATION = "runBeforeTestAction";
    public static final String AFTER_TEST_RUN_OPERATION = "runAfterTestAction";

    public static final int APP_STARTUP_TIMEOUT_SEC = 120;
    public static final int APP_STARTUP_CHECK_PERIOD_SEC = 10;
    private static final Logger log = LoggerFactory.getLogger(TestRunner.class);

    public static final boolean localMode = false;
    public static final boolean debugPods = false;

    @Test
    @Order(1)
    void testScalingProcess() throws Exception {
        try (K8sControlTool k8s = new Fabric8K8sControlTool()) {
            k8s.scalePods(3);
            waitAppsReady(k8s.getPodBridges());

            k8s.scalePods(1);
            waitAppsReady(k8s.getPodBridges());

            k8s.scalePods(2);
            waitAppsReady(k8s.getPodBridges());
        }
    }

    @Test
    @Order(2)
    void checkK8sApi() throws Exception {
        try (K8sControlTool k8s = new Fabric8K8sControlTool()) {
            k8s.scalePods(3);

            List<PodBridge> podBridges = k8s.getPodBridges();
            waitAppsReady(k8s.getPodBridges());

            List<TestInfo> common = null;
            for (PodBridge bridge : podBridges) {
                List<TestInfo> tests = loadTests(bridge.getPort()).collect(Collectors.toList());
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

    public static void waitAppsReady(List<PodBridge> bridges) {//todo refactor
        //todo async?
        for (PodBridge bridge : bridges) {
            log.info("Waiting port '{}' for pod '{}'...", bridge.getPort(), bridge.getName());
            boolean sucess = false;
            long startTime = System.currentTimeMillis();
            RuntimeException lastException = null;
            while (!sucess) {
                try {
                    sucess = JmxOperations.getAttribute(bridge.getPort(), READY_ATTRIBUTE);
                } catch (RuntimeException e) {
                    lastException = e;
                }
                if (System.currentTimeMillis() - startTime > APP_STARTUP_TIMEOUT_SEC * 1000) {
                    if (lastException == null) {
                        throw new RuntimeException(
                                String.format("Cannot access app on pod '%s' through port %s: timeout reached",
                                        bridge.getName(),
                                        bridge.getPort()));
                    } else {
                        throw new RuntimeException(
                                String.format("Cannot access app on pod '%s' through port %s: timeout reached. See nested exception.",
                                        bridge.getName(),
                                        bridge.getPort()),
                                lastException);
                    }

                }

                try {
                    if (!sucess) {
                        Thread.sleep(APP_STARTUP_CHECK_PERIOD_SEC * 1000);
                    }
                } catch (InterruptedException e) {
                    throw new RuntimeException("Error during waiting app check period", e);//todo another message
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
        try (K8sControlTool k8s = new Fabric8K8sControlTool(debugPods)) {//todo sync->clean->sync again? check order!!(+ use cleanStart option during tool creation)
            //todo reuse forwarders?
            if (info.isCleanStart()) {
                log.info("Clean start required. Stopping all pods.");
                k8s.scalePods(0);
                //todo restart db pod too?
            }

            log.info("Init nodes {}", info.getInitNodes());

            k8s.scalePods(info.getInitNodes().size());
            waitAppsReady(k8s.getPodBridges());

            Map<String, String> portsByNames = new HashMap<>();
            Iterator<String> ports = k8s.getPorts().iterator();
            for (String name : requiredPods) {
                portsByNames.put(name, ports.next());
            }
            log.info("Pod ports mapped:{}", portsByNames);


            log.info("Executing before test action...");

            TestResult beforeTestResult = runTestAction(
                    portsByNames.values().iterator().next(),
                    BEFORE_TEST_RUN_OPERATION,
                    new Object[]{info.getBeanName(), null},
                    new String[]{String.class.getName(), TestContext.class.getName()},
                    new PrintParams("          |- ")
                            .errorMessage("BeforeTest action failed with error"));

            TestContext testContext;

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
                        nodes = portsByNames.keySet();
                    for (String node : nodes) {
                        log.info("    Invoking step {} for node {} ...", step.getOrder(), node);
                        TestResult result = runTestAction(
                                portsByNames.get(node),
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
                                        portsByNames.values().iterator().next(),
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
                                if (portsByNames.containsKey(nodName)) {
                                    throw new DevelopmentException("Pod with name '" + nodName + "' has been already created");//todo ?!
                                }
                                k8s.scalePods(k8s.getPodCount() + 1);
                                List<PodBridge> podPorts = k8s.getPodBridges();
                                for (PodBridge bridge : podPorts) {
                                    if (!portsByNames.containsValue(bridge.getPort())) {
                                        portsByNames.put(nodName, bridge.getPort());
                                        log.info("    Node {} has been added. Related pod:{}:{}", nodName, bridge.getName(), bridge.getPort());
                                    }
                                }
                            }
                            waitAppsReady(k8s.getPodBridges());
                            break;
                        case RECREATE_ALL://todo implement or remove completely
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
        if (localMode) {
            log.debug("Local Mode enabled: use local port {} instead of pod port {} ", K8sControlTool.INNER_JMX_PORT, port);
            port = K8sControlTool.INNER_JMX_PORT;
        }
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

    //todo not static?
    static Stream<TestInfo> loadTests() throws Exception {
        try (K8sControlTool k8s = new Fabric8K8sControlTool()) {
            if (k8s.getPodCount() < 1) {
                k8s.scalePods(1);
            }
            waitAppsReady(k8s.getPodBridges());
            return loadTests(localMode ? K8sControlTool.INNER_JMX_PORT : k8s.getPorts().iterator().next());
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
