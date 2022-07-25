package io.jmix.samples.cluster;

import io.jmix.samples.cluster.test_support.K8sControlTool;
import io.jmix.samples.cluster.test_system.model.TestInfo;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import javax.management.*;
import javax.management.remote.JMXConnector;
import javax.management.remote.JMXConnectorFactory;
import javax.management.remote.JMXServiceURL;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.*;

public class TestRunner {
    public static final String JMX_SERVICE_CUSTOM_URL = "service:jmx:jmxmp://localhost:%s";
    public static final String CLUSTER_TEST_BEAN_NAME = "jmix.cluster:type=ClusterTestBean";

    @Test
    @Order(1)
    void checkK8sApi() throws Exception {
        try (K8sControlTool k8s = new K8sControlTool()) {
            k8s.scalePods(3);

            //Thread.sleep(20000);//todo await by polling info and checking

            LinkedHashMap<String, String> podPorts = k8s.getPorts();

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
    void clusterTests(TestInfo info) {
        assertNotNull(info);
        System.out.println("Starting test " + info);//todo normal logs


        //todo process test according to requirements and steps


    }

    //todo not static
    static Stream<TestInfo> loadTests() throws IOException, MalformedObjectNameException, ReflectionException, InstanceNotFoundException, IntrospectionException, AttributeNotFoundException, MBeanException {
        return loadTests("9875");//todo get port from cluster
    }

    static Stream<TestInfo> loadTests(String port) throws IOException, MalformedObjectNameException, ReflectionException, InstanceNotFoundException, IntrospectionException, AttributeNotFoundException, MBeanException {
        JMXServiceURL url = new JMXServiceURL(String.format(JMX_SERVICE_CUSTOM_URL, port));

        MBeanServerConnection connection;
        try (JMXConnector connector = JMXConnectorFactory.connect(url)) {
            connection = connector.getMBeanServerConnection();
            ObjectName beanName = new ObjectName(CLUSTER_TEST_BEAN_NAME);
            MBeanInfo info = connection.getMBeanInfo(beanName);
            System.out.println(info);
            List<TestInfo> result = (List<TestInfo>) connection.getAttribute(beanName, "Tests");
            System.out.println(result);
            return result.stream();
        }
    }


}
