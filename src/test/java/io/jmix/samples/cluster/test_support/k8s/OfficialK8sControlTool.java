package io.jmix.samples.cluster.test_support.k8s;

import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.apis.AppsV1Api;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.models.V1Pod;
import io.kubernetes.client.openapi.models.V1PodList;
import io.kubernetes.client.openapi.models.V1Scale;
import io.kubernetes.client.openapi.models.V1ScaleSpec;
import io.kubernetes.client.util.Config;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.List;
import java.util.Objects;

/**
 * @deprecated doesn't work with namespaces
 */
@Deprecated
public class OfficialK8sControlTool extends BaseK8sControlTool<V1Pod> {

    private CoreV1Api coreApi;
    private AppsV1Api appApi;

    //todo 1) test different cases when cluster is not available or some port closed
    public OfficialK8sControlTool(boolean debugMode) {
        super(debugMode);
    }

    public OfficialK8sControlTool() {
        super(false);
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

    @Override
    protected String podName(V1Pod pod) {
        return Objects.requireNonNull(pod.getMetadata()).getName();
    }

    @Override
    protected PodBridge forwardPorts(String podName, int port, int localPort, @Nullable Integer debugPort, @Nullable Integer debugLocalPort) {
        return CliPodBridge.establish(
                podName,
                Integer.toString(localPort),
                Integer.toString(port),
                debugLocalPort != null ? Integer.toString(debugLocalPort) : null,
                debugPort != null ? Integer.toString(debugPort) : null);
    }


    @Override
    protected void initClient() {
        ApiClient client = null;
        try {
            client = Config.defaultClient();
        } catch (IOException e) {
            throw new RuntimeException("Cannot connect to kubernetes cluster", e);
        }
        coreApi = new CoreV1Api(client);
        appApi = new AppsV1Api(client);

    }

    @Override
    protected int getCurrentScale() {
        try {
            V1ScaleSpec scaleSpec = appApi
                    .readNamespacedDeploymentScale("sample-app", NAMESPACE, "true")
                    .getSpec();
            return (scaleSpec == null || scaleSpec.getReplicas() == null) ? 0 : scaleSpec.getReplicas();
        } catch (ApiException e) {
            throw new RuntimeException("Cannot load deployment scale", e);
        }
    }

    @Override
    protected void doScale(int size) {
        try {
            V1Scale scale = appApi.readNamespacedDeploymentScale("sample-app", NAMESPACE, "true");
            scale.getSpec().setReplicas(size);
            appApi.replaceNamespacedDeploymentScale(APP_NAME, NAMESPACE, scale, "true", null, null, null);
        } catch (ApiException e) {
            throw new RuntimeException("Cannot scale deployment", e);
        }
    }
}
