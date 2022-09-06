package io.jmix.samples.cluster.test_support.k8s;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * @param <T> Pod structure type
 */
public abstract class BaseK8sControlTool<T> implements K8sControlTool {

    private static final Logger log = LoggerFactory.getLogger(BaseK8sControlTool.class);


    protected Map<String, PodBridge> bridges = new HashMap<>();
    protected static int nextPort = FIRST_PORT;
    protected static int nextDebugPort = FIRST_DEBUG_PORT;

    protected boolean debugMode;

    public BaseK8sControlTool(boolean debugMode) {
        this.debugMode = debugMode;
        initClient();
        syncBridges();
        Runtime.getRuntime().addShutdownHook(new Thread(this::destroy));
    }

    protected void syncBridges() {
        log.debug("Synchronizing pod bridges");
        List<T> pods = loadRunningPods();
        List<String> obsolete = new LinkedList<>(bridges.keySet());
        //add absent pod bridges
        for (T pod : pods) {
            String podName = podName(pod);
            if (bridges.containsKey(podName)) {
                obsolete.remove(podName);
                continue;//todo [last] verify carefully that it is the same pod but not just name collision
            }
            PodBridge bridge = forwardPorts(podName,
                    INT_INNER_JMX_PORT,
                    nextPort++,
                    debugMode ? INT_INNER_DEBUG_PORT : null,
                    debugMode ? nextDebugPort++ : null);

            bridges.put(podName, bridge);
            log.info("FORWARDING: {}", bridge);
        }
        //remove obsolete bridges
        for (String podName : obsolete) {
            (bridges.get(podName)).destroy();
            bridges.remove(podName);
        }
        log.debug("Pod bridges synchronized");
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


    @Override
    public void scalePods(int size) {
        log.info("Scaling deployment: {} -> {}", getCurrentScale(), size);
        doScale(size);
        awaitScaling(size);
        log.info("Deployment sucessfully scaled");
        syncBridges();
    }

    @Override
    public void destroy() {
        for (PodBridge bridge : bridges.values()) {
            bridge.destroy();
        }
        bridges.clear();
    }

    @Override
    public int getPodCount() {
        return bridges.size();
    }

    @Override
    public List<PodBridge> getPodBridges() {
        return new LinkedList<>(bridges.values());
    }

    @Override
    public List<String> getPorts() {
        return bridges.values().stream().map(PodBridge::getPort).collect(Collectors.toList());
    }

    @Override
    public void close() {
        destroy();
    }

    protected abstract void initClient();


    protected abstract int getCurrentScale();

    protected abstract void doScale(int size);

    protected abstract List<T> loadRunningPods();

    protected abstract String podName(T pod);

    protected abstract PodBridge forwardPorts(String podName,
                                              int port,
                                              int localPort,
                                              @Nullable Integer debugPort,
                                              @Nullable Integer debugLocalPort);

}
