package io.jmix.samples.cluster;

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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

@SpringBootTest
class SampleClusterApplicationTests {
    @Autowired
    Metadata metadata;
    @Autowired
    LockManager lockManager;
    @Autowired
    SystemAuthenticator authenticator;

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

    @Test
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


}
