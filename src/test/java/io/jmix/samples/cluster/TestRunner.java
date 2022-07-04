package io.jmix.samples.cluster;

import io.jmix.samples.cluster.test_system.model.TestInfo;
import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.Configuration;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.models.V1Pod;
import io.kubernetes.client.openapi.models.V1PodList;
import io.kubernetes.client.util.Config;
import org.junit.jupiter.api.Order;
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

    //todo format for pod ip
    public static final String JMX_SERVICE_URL = "service:jmx:rmi://localhost/jndi/rmi://localhost:10099/myconnector";
    public static final String CLUSTER_TEST_BEAN_NAME = "jmix.cluster:type=ClusterTestBean";

    @Test
    @Order(1)
    void checkK8SApi() throws IOException, ApiException {
        ApiClient client = Config.defaultClient();
        Configuration.setDefaultApiClient(client);

        CoreV1Api api = new CoreV1Api();

        V1PodList list =
                api.listPodForAllNamespaces(null, null, null, null, null, null, null, null, null, null);
        for (V1Pod item : list.getItems()) {
            System.out.println(item.getMetadata().getName());
        }
    }


    @Test
    @Order(10)
    void checkTestsLoaded() throws ReflectionException, MalformedObjectNameException, AttributeNotFoundException, InstanceNotFoundException, IntrospectionException, MBeanException, IOException {
        Stream<TestInfo> testInfos = loadTests();
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
