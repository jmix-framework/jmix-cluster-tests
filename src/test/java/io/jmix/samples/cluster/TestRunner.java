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

   /* @Test
    @Order(0)
//todo remove
    void oldCheckK8SApi() throws IOException, ApiException, ReflectionException, MalformedObjectNameException, AttributeNotFoundException, InstanceNotFoundException, IntrospectionException, MBeanException {
        ApiClient client = Config.defaultClient();
        Configuration.setDefaultApiClient(client);

        CoreV1Api api = new CoreV1Api();

        System.out.println("All pods:");
        V1PodList v1PodList =
                api.listPodForAllNamespaces(null, null, null, null, null, null, null, null, null, null);
        for (V1Pod item : v1PodList.getItems()) {
            System.out.println(item.getMetadata().getName());
        }

        List<V1Pod> appPods = v1PodList.getItems().stream()
                .filter(item -> "sample-app".equals(item.getMetadata().getLabels().get("app")))
                .collect(Collectors.toList());

        System.out.println("\n\nAPP Pods:\n");
        for (V1Pod item : appPods) {
            System.out.println(item.getMetadata().getName());
        }


        scaleAppPods(1);


        v1PodList =
                api.listPodForAllNamespaces(null, null, null, "app=sample-app", null, null, null, null, null, null);
        for (V1Pod item : v1PodList.getItems()) {
            System.out.println(item.getMetadata().getName() + " - " + item.getMetadata().getNamespace());
        }

        V1Pod first = v1PodList.getItems().iterator().next();

        ProcessBuilder processBuilder = new ProcessBuilder();

        processBuilder.command("kubectl", "port-forward", "pods/" + first.getMetadata().getName(), "20001:9875");//todo deal with possible npe

        try {

            Process process = processBuilder.start();

            // blocked :(
            BufferedReader reader =
                    new BufferedReader(new InputStreamReader(process.getInputStream()));

            new Thread(
                    new Runnable() {
                        public void run() {
                            try {
                                String line;
                                while ((line = reader.readLine()) != null) {
                                    System.out.println(line);
                                }
                            } catch (IOException ex) {
                                ex.printStackTrace();
                            } catch (Exception ex) {
                                ex.printStackTrace();
                            }
                        }
                    })
                    .start();
            System.out.println("Just in case awaiting app to start");
            Thread.sleep(20000);//todo smart wait to startup of app
            System.out.println("Loading tests...");
            Stream<TestInfo> testInfos = loadTests("20001");
            assertNotNull(testInfos);
            assertTrue(testInfos.findAny().isPresent());
            System.out.println("SUCESS");

            process.destroy();

        } catch (IOException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }


        List<String> podJmxPorts = new LinkedList<>();


        //todo delete

        //////////////////////////////////////////////
        //todo:

        // 2)control pods (scale, list, request)

        scaleAppPods(1);
    }*/

    @Test
    @Order(1)
    void checkK8sApi() throws Exception {
        try (K8sControlTool k8s = new K8sControlTool()) {
            k8s.scalePods(3);

            //todo enable
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

    /*protected void scaleAppPods(int replicas) throws IOException, ApiException {//todo remove
        AppsV1Api api = new AppsV1Api(ClientBuilder.standard().build());

        V1Scale scale = api.readNamespacedDeploymentScale("sample-app", "default", "true");
        System.out.println(scale);
        scale.getSpec().setReplicas(replicas);
        System.out.println("\n\nSCALED:\n" + scale);//todo logs later
        //todo!!! replace vs patch!
        api.replaceNamespacedDeploymentScale("sample-app", "default", scale, "true", null, null, null);
    }*/


}
