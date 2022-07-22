package io.jmix.samples.cluster;

import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.Configuration;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.models.V1Pod;
import io.kubernetes.client.openapi.models.V1PodBuilder;
import io.kubernetes.client.openapi.models.V1PodList;
import io.kubernetes.client.util.Config;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@SpringBootTest
class SampleClusterApplicationTests {

    @Test
    void contextLoads() {
    }


    @Test
    void testListCreateDeletePods() throws IOException, ApiException {
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

        V1Pod appPod = appPods.iterator().next();
        V1Pod newPod = new V1PodBuilder()
                .withNewMetadata()
                .withName("sample-app")
                .withLabels(Collections.singletonMap("app", "sample-app"))
                .endMetadata()
                .withSpec(appPod.getSpec())
                .build();

        api.createNamespacedPod("default", newPod, null, null, null, null);

        v1PodList =
                api.listPodForAllNamespaces(null, null, null, null, null, null, null, null, null, null);
        for (V1Pod item : v1PodList.getItems()) {
            System.out.println(item.getMetadata().getName() + " - " + item.getMetadata().getNamespace());
        }


        api.deleteNamespacedPod("sample-app", "default", "true", null, null, null, null, null);
    }

   /* @Test
    void testPortForwarding() throws IOException, ApiException, InterruptedException {//todo remove

        K8sControlTool k8s = new K8sControlTool();

        ApiClient client = Config.defaultClient();
        Configuration.setDefaultApiClient(client);

        CoreV1Api api = new CoreV1Api();

        V1PodList v1PodList = api.listNamespacedPod("default", "true",
                null,
                null,
                null,
                "app=sample-app",
                null,
                null,
                null,
                null,
                null);

        int port = 49001;
        List<Process> portForwarders = new LinkedList<>();

        for (V1Pod pod : v1PodList.getItems()) {
            int curPort = port++;
            String podName = Objects.requireNonNull(pod.getMetadata()).getName();
            System.out.println("Redirecting jmx for pod '" + podName + "' to " + curPort);//todo nullability of metadata and name or assume that it is not possible?
            k8s.forwardPort(podName,Integer.toString(curPort),"9875");
        }

        System.out.println("Waiting 20 seconds...");
        Thread.sleep(20000);

        System.out.println("Stopping port forwarding..");
        k8s.destroy();
    }*/


}
