package io.jmix.samples.cluster;

import io.jmix.samples.cluster.test_system.model.TestInfo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import javax.management.*;
import javax.management.remote.JMXConnector;
import javax.management.remote.JMXConnectorFactory;
import javax.management.remote.JMXServiceURL;
import java.io.IOException;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestRunner {
    public static final String JMX_SERVICE_URL = "service:jmx:rmi://localhost/jndi/rmi://localhost:10099/myconnector";//todo format for pod ip
    public static final String CLUSTER_TEST_BEAN_NAME = "jmix.cluster:type=ClusterTestBean";

    @Test
    void checkTestsLoaded() throws ReflectionException, MalformedObjectNameException, AttributeNotFoundException, InstanceNotFoundException, IntrospectionException, MBeanException, IOException {
        Stream<TestInfo> testInfos = loadTests();
        assertNotNull(testInfos);
        assertTrue(testInfos.findAny().isPresent());
        //todo check consistency: each pod returns the same set of tests
    }

    @ParameterizedTest(name = "Cluster test [{index}]: {arguments}")
    @MethodSource("loadTests")
    void clusterTests(TestInfo info) {
        assertNotNull(info);
        System.out.println(info.getDescription());//todo logs

        //todo process test according to requirements and steps


    }

    //todo not static
    static Stream<TestInfo> loadTests() throws IOException, MalformedObjectNameException, ReflectionException, InstanceNotFoundException, IntrospectionException, AttributeNotFoundException, MBeanException {
        JMXServiceURL url = new JMXServiceURL(JMX_SERVICE_URL);

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
