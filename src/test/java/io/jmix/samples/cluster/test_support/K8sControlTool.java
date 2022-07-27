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
import java.util.stream.Collectors;

public class K8sControlTool implements AutoCloseable {
    public static final String NAMESPACE = "default";
    public static final String APP_NAME = "sample-app";
    public static final String POD_LABEL_SELECTOR = "app=" + APP_NAME;
    public static final String POD_STATUS_SELECTOR = "status.phase=Running";

    public static final int SCALE_TIMEOUT_MS = 120 * 1000;
    public static final int SCALE_CHECKING_PERIOUD_MS = 1000;

    public static final int FIRST_PORT = 49001;
    public static final String INNER_JMX_PORT = "9875";

    private CoreV1Api coreApi;
    private AppsV1Api appApi;
    private static int nextPort = FIRST_PORT;

    //todo watch pods created/removed?
    private Map<String, PodBridge> bridges = new HashMap<>();


    //todo 1) test different cases when cluster is not available or some port closed
    // 2) test for remote connection
    public K8sControlTool() {//todo bean? singleton?
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

    public LinkedHashMap<String, String> getPodPorts() {//todo rework?
        LinkedHashMap<String, String> result = new LinkedHashMap<>();
        for (Map.Entry<String, PodBridge> entry : bridges.entrySet()) {
            result.put(entry.getKey(), entry.getValue().getPort());
        }
        return result;
    }

    public List<String> getPorts() {//todo replace getPodPorts().values() by this method
        return bridges.values().stream().map(PodBridge::getPort).collect(Collectors.toList());
    }

    public void scalePods(int size) {
        try {
            V1Scale scale = appApi.readNamespacedDeploymentScale("sample-app", "default", "true");
            System.out.println(String.format("Scaling deployment: %s -> %s", scale.getSpec().getReplicas(), size));
            scale.getSpec().setReplicas(size);//todo replace vs patch?
            appApi.replaceNamespacedDeploymentScale(APP_NAME, NAMESPACE, scale, "true", null, null, null);
            //todo!!! await for scale to appear
            awaitScaling(size);
            System.out.println("Deployment sucessfully scaled");
        } catch (ApiException e) {
            throw new RuntimeException("Cannot scale deployment", e);
        }
        syncPods();
    }

    protected void awaitScaling(int desiredSize) {
        long startTime = System.currentTimeMillis();
        while (loadRunningPods().size() != desiredSize) {
            if (System.currentTimeMillis() - startTime > SCALE_TIMEOUT_MS) {
                throw new RuntimeException(
                        String.format("Scaling wait time out: deployment has not been scaled during %s seconds",
                                SCALE_TIMEOUT_MS / 1000));
            }
            try {
                Thread.sleep(SCALE_CHECKING_PERIOUD_MS);
            } catch (InterruptedException e) {
                throw new RuntimeException("Problem while waiting for deployment to be scaled", e);
            }
        }
    }

    protected void syncPods() {
        List<V1Pod> pods = loadRunningPods();
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

    protected List<V1Pod> loadRunningPods() {
        try {
            V1PodList v1PodList =
                    coreApi.listPodForAllNamespaces(
                            null,
                            null,
                            POD_STATUS_SELECTOR,
                            POD_LABEL_SELECTOR,
                            null,
                            null,
                            null,
                            null,
                            null,//todo investigate timeout
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
