package io.jmix.samples.cluster;

import io.fabric8.kubernetes.api.model.Namespace;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PodList;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import io.jmix.core.Metadata;
import io.jmix.core.pessimisticlocking.LockInfo;
import io.jmix.core.pessimisticlocking.LockManager;
import io.jmix.core.security.SystemAuthenticator;
import io.jmix.samples.cluster.entity.Sample;
import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.Configuration;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.models.V1Pod;
import io.kubernetes.client.openapi.models.V1PodBuilder;
import io.kubernetes.client.openapi.models.V1PodList;
import io.kubernetes.client.util.Config;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.stream.Collectors;

import static io.jmix.samples.cluster.test_support.k8s.K8sControlTool.NAMESPACE;
import static io.jmix.samples.cluster.test_support.k8s.K8sControlTool.POD_LABEL_SELECTOR;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class SampleClusterApplicationTests {
    @Autowired
    Metadata metadata;
    @Autowired
    LockManager lockManager;
    @Autowired
    SystemAuthenticator authenticator;

    private static final Logger log = LoggerFactory.getLogger(SampleClusterApplicationTests.class);

    //@Test
    void contextLoads() {
    }


    //@Test
    void testListCreateDeletePods() throws IOException, ApiException {
        ApiClient client = Config.defaultClient();
        Configuration.setDefaultApiClient(client);

        CoreV1Api api = new CoreV1Api();

        log.info("All pods:");
        V1PodList v1PodList =
                api.listPodForAllNamespaces(null, null, null, null, null, null, null, null, null, null);
        for (V1Pod item : v1PodList.getItems()) {
            log.info(item.getMetadata().getName());
        }

        List<V1Pod> appPods = v1PodList.getItems().stream()
                .filter(item -> "sample-app".equals(item.getMetadata().getLabels().get("app")))
                .collect(Collectors.toList());

        log.info("\n\nAPP Pods:");
        for (V1Pod item : appPods) {
            log.info(item.getMetadata().getName());
        }

        V1Pod appPod = appPods.iterator().next();
        V1Pod newPod = new V1PodBuilder()
                .withNewMetadata()
                .withName("sample-app")
                .withLabels(Collections.singletonMap("app", "sample-app"))
                .endMetadata()
                .withSpec(appPod.getSpec())
                .build();

        api.createNamespacedPod("jmix-cluster-tests", newPod, null, null, null, null);

        v1PodList =
                api.listPodForAllNamespaces(null, null, null, null, null, null, null, null, null, null);
        for (V1Pod item : v1PodList.getItems()) {
            log.info(item.getMetadata().getName() + " - " + item.getMetadata().getNamespace());
        }


        api.deleteNamespacedPod("sample-app", NAMESPACE, "true", null, null, null, null, null);
    }

    //@Test
    void tryFabric8Client() throws InterruptedException, IOException {
        try (KubernetesClient client = new KubernetesClientBuilder().build()) {
            log.info("Namespaces: {}" + client.namespaces().list().toString());

            log.info("Scale to 1");
            client.apps().deployments().inNamespace(NAMESPACE).withName("sample-app").scale(1);
            PodList pods = client.pods()
                    .inNamespace(NAMESPACE)
                    .withLabel(POD_LABEL_SELECTOR)
                    .withField("status.phase", "Running")
                    .list();

            PodList allPods = client.pods()
                    .inNamespace(NAMESPACE)
                    .withLabel(POD_LABEL_SELECTOR)
                    .list();


            for (Pod pod : pods.getItems()) {
                assertThat(pod.getStatus().getPhase()).isEqualTo("Running");
            }

            log.info("Pods:\n{}", pods.getItems().stream().map(p -> p.getMetadata().getName() + " - " + p.getStatus().getPhase()).collect(Collectors.joining("\n")));
            log.info("AllPods:\n{}", allPods.getItems().stream().map(p -> p.getMetadata().getName() + " - " + p.getStatus().getPhase()).collect(Collectors.joining("\n")));

            log.info("Scale to 3");
            client.apps().deployments().inNamespace(NAMESPACE).withName("sample-app").scale(3);
            pods = client.pods()
                    .inNamespace(NAMESPACE)
                    .withLabel(POD_LABEL_SELECTOR)
                    .withField("status.phase", "Running")
                    .list();

            allPods = client.pods()
                    .inNamespace(NAMESPACE)
                    .withLabel(POD_LABEL_SELECTOR)
                    .list();


            for (Pod pod : pods.getItems()) {
                assertThat(pod.getStatus().getPhase()).isEqualTo("Running");
            }

            log.info("Pods:\n{}", pods.getItems().stream().map(p -> p.getMetadata().getName() + " - " + p.getStatus().getPhase()).collect(Collectors.joining("\n")));
            log.info("AllPods:\n{}", allPods.getItems().stream().map(p -> p.getMetadata().getName() + " - " + p.getStatus().getPhase()).collect(Collectors.joining("\n")));



            /*LocalPortForward lpf = client.pods().inNamespace("default").withName("sample-app-76d54b85f4-62t5h ").portForward(8080,8080);

            log.info("Port forwarded....");
            Thread.sleep(10000);
            log.info("Port forwarding finished");
            lpf.close();*/
        }
    }

    //@Test
    void testEntityLock() {
        authenticator.begin();
        try {
            Sample entity = metadata.create(Sample.class);
            entity.setName("Test name");


            LockInfo lockInfo = lockManager.lock(entity);

            assertNull(lockInfo);

            lockInfo = lockManager.lock(entity);

            assertNotNull(lockInfo);

            lockManager.unlock(entity);
            lockInfo = lockManager.getLockInfo("cluster_Sample", entity.getId().toString());

            assertNull(lockInfo);
        } finally {
            authenticator.end();
        }
    }


    //@Test
    void testRemoteClusterConnection() {

        try (KubernetesClient kubernetesClient = new KubernetesClientBuilder().build()) {
            List<Namespace> namespaces = kubernetesClient.namespaces().list().getItems();
            log.warn("Namespaces.size {}", namespaces.size());
            kubernetesClient.nodes().list().getItems().forEach(n -> log.warn("node:{}", n));
            kubernetesClient.pods().inNamespace("jmix-cluster-tests").list().getItems().forEach(p -> log.warn("pod: {}", p));
        }
    }

    @Test
    void checkVariables() {
        log.info("kubeconfig(env): {}", System.getenv("kubeconfig"));
        log.info("kubeconfig(prop): {}", System.getProperty("kubeconfig"));
        log.info("KUBECONFIG(env): {}", System.getenv("KUBECONFIG"));
        log.info("KUBECONFIG(prop): {}", System.getProperty("KUBECONFIG"));

        Map<String, String> enviorntmentVars = System.getenv();
        enviorntmentVars.entrySet().forEach(e -> log.info("env> {}", e));

        Properties properties = System.getProperties();
        properties.entrySet().forEach(p -> log.info("prop> {}", p));
    }


}
