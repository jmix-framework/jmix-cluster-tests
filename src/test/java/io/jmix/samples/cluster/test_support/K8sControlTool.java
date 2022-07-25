package io.jmix.samples.cluster.test_support;

import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.apis.AppsV1Api;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.models.V1Pod;
import io.kubernetes.client.openapi.models.V1PodList;
import io.kubernetes.client.openapi.models.V1Scale;
import io.kubernetes.client.util.Config;

import java.io.IOException;
import java.util.*;

public class K8sControlTool implements AutoCloseable {
    public static final String NAMESPACE = "default";
    public static final String APP_NAME = "sample-app";
    public static final String POD_LABEL_SELECTOR = "app=" + APP_NAME;

    public static final int FIRST_PORT = 49001;
    public static final String INNER_JMX_PORT = "9875";

    private CoreV1Api coreApi;
    private AppsV1Api appApi;
    private int nextPort = FIRST_PORT;

    //todo watch pods created/removed?
    private Map<String, PodBridge> bridges = new HashMap<>();


    //todo 1) test different cases when cluster is not available or some port closed
    // 2) test for remote connection
    public K8sControlTool() {
        ApiClient client = null;
        try {
            client = Config.defaultClient();
        } catch (IOException e) {
            throw new RuntimeException("Cannot connect to kubernetes cluster", e);
        }
        coreApi = new CoreV1Api(client);
        appApi = new AppsV1Api(client);

        syncPods();
        Runtime.getRuntime().addShutdownHook(new Thread(this::destroy));
    }

    public int getPodCount() {
        return bridges.size();
    }

    public LinkedHashMap<String, String> getPorts() {//todo rework?
        LinkedHashMap<String, String> result = new LinkedHashMap<>();
        for (Map.Entry<String, PodBridge> entry : bridges.entrySet()) {
            result.put(entry.getKey(), entry.getValue().getPort());
        }
        return result;
    }

    public void scalePods(int size) {
        try {
            V1Scale scale = appApi.readNamespacedDeploymentScale("sample-app", "default", "true");
            System.out.println(String.format("Scaling deployment: %s -> %s", scale.getSpec().getReplicas(), size));
            scale.getSpec().setReplicas(size);//todo replace vs patch?
            appApi.replaceNamespacedDeploymentScale(APP_NAME, NAMESPACE, scale, "true", null, null, null);
            //todo!!! await for scale to appear
        } catch (ApiException e) {
            throw new RuntimeException("Cannot scale deployment", e);
        }
        syncPods();
    }

    protected void syncPods() {
        List<V1Pod> pods = loadPods();
        List<String> obsolete = new LinkedList<>(bridges.keySet());
        //add absent pod bridges
        for (V1Pod pod : pods) {
            String podName = Objects.requireNonNull(pod.getMetadata()).getName();
            if (bridges.containsKey(podName)) {
                obsolete.remove(podName);
                continue;//todo [last] verify carefully that it is the same pod but not just random name part collision
            }
            String currentPort = Integer.toString(nextPort++);
            bridges.put(podName, PodBridge.establish(podName, currentPort, INNER_JMX_PORT));
        }
        //remove obsolete bridges
        for (String podName : obsolete) {
            bridges.get(podName).destroy();
            bridges.remove(podName);
        }
    }

    protected void awaitPodsReady() {
        //todo (JMX needed)
    }

    protected List<V1Pod> loadPods() {
        try {
            V1PodList v1PodList =
                    coreApi.listPodForAllNamespaces(
                            null,
                            null,
                            null,
                            POD_LABEL_SELECTOR,
                            null,
                            null,
                            null,
                            null,
                            null,
                            null);
            return v1PodList.getItems();
        } catch (ApiException e) {
            throw new RuntimeException("Cannot load app pods", e);
        }
    }

    public void destroy() {
        bridges.values().forEach(PodBridge::destroy);
        bridges.clear();
    }

    @Override
    public void close() throws Exception {
        destroy();
    }
}
