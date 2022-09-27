package io.jmix.samples.cluster.test_support.k8s;

import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PodList;
import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import io.fabric8.kubernetes.client.LocalPortForward;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Objects;

public class Fabric8K8sControlTool extends BaseK8sControlTool<Pod> {//todo add base abstract class to avoid code duplication

    private static final Logger log = LoggerFactory.getLogger(Fabric8K8sControlTool.class);

    private KubernetesClient client;

    public Fabric8K8sControlTool(boolean debugMode) {
        super(debugMode);
    }

    public Fabric8K8sControlTool() {
        this(false);
    }


    @Override
    protected void initClient() {
        KubernetesClientBuilder builder = new KubernetesClientBuilder();
        if (System.getenv(ENV_KUBECONFIG_CONTENT) != null) {
            builder.withConfig(Config.fromKubeconfig(System.getenv(ENV_KUBECONFIG_CONTENT)));
        }
        client = builder.build();
    }

    @Override
    protected String podName(Pod pod) {
        return pod.getMetadata().getName();
    }

    @Override
    protected PodBridge forwardPorts(String podName,
                                     int port,
                                     int localPort,
                                     @Nullable Integer debugPort,
                                     @Nullable Integer debugLocalPort) {
        LocalPortForward forward = client.pods()
                .inNamespace(NAMESPACE)
                .withName(podName)
                .portForward(port, localPort);

        LocalPortForward debugForward;
        if (debugLocalPort != null) {
            debugForward = client.pods()
                    .inNamespace(NAMESPACE)
                    .withName(podName)
                    .portForward(Objects.requireNonNull(debugPort), debugLocalPort);
        } else {
            debugForward = null;
        }

        return new ApiPodBridge(podName, forward, debugForward);
    }

    @Override
    protected List<Pod> loadRunningPods() {
        PodList pods = client.pods()
                .inNamespace(NAMESPACE)
                .withLabel(POD_LABEL_SELECTOR)
                .withField("status.phase", "Running")
                .list();
        return pods.getItems();
    }

    @Override
    public int getCurrentScale() {
        Integer oldScale = client.apps().deployments()
                .inNamespace(NAMESPACE)
                .withName(APP_NAME)
                .scale()
                .getSpec()
                .getReplicas();
        return oldScale != null ? oldScale : 0;
    }

    @Override
    protected void doScale(int size) {
        client.apps().deployments().inNamespace(NAMESPACE).withName(APP_NAME).scale(size);
    }
}
