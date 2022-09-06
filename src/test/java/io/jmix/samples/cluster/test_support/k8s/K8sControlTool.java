package io.jmix.samples.cluster.test_support.k8s;

import java.util.List;

public interface K8sControlTool extends AutoCloseable {
    String NAMESPACE = "default";
    String APP_NAME = "sample-app";
    String POD_LABEL_SELECTOR = "app=" + APP_NAME;
    String POD_STATUS_SELECTOR = "status.phase=Running";
    int SCALE_TIMEOUT_MS = 120 * 1000;
    int SCALE_CHECKING_PERIOUD_MS = 1000;
    int FIRST_PORT = 49001;//todo remove string constants an rename int constants?
    int FIRST_DEBUG_PORT = 50001;

    int INT_INNER_JMX_PORT = 9875;
    String INNER_JMX_PORT = Integer.toString(INT_INNER_JMX_PORT);
    int INT_INNER_DEBUG_PORT = 5006;
    String INNER_DEBUG_PORT = Integer.toString(INT_INNER_DEBUG_PORT);

    int getPodCount();

    List<PodBridge> getPodBridges();

    List<String> getPorts();

    void scalePods(int size);

    void destroy();

    @Override
    void close() throws Exception;
}
