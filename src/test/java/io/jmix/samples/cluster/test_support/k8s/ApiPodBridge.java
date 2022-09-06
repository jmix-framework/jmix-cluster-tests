package io.jmix.samples.cluster.test_support.k8s;

import io.fabric8.kubernetes.client.LocalPortForward;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

public class ApiPodBridge implements PodBridge {
    private static final Logger log = LoggerFactory.getLogger(ApiPodBridge.class);

    private final String name;
    private final String port;
    private final String debugPort;

    private final LocalPortForward portForward;
    private final LocalPortForward debugPortForward;


    public ApiPodBridge(String name, LocalPortForward portForward, @Nullable LocalPortForward debugPortForward) {
        this.name = name;
        this.portForward = portForward;
        this.debugPortForward = debugPortForward;
        port = Integer.toString(portForward.getLocalPort());
        debugPort = debugPortForward != null ? Integer.toString(debugPortForward.getLocalPort()) : null;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String getPort() {
        return port;
    }

    @Nullable
    @Override
    public String getDebugPort() {
        return debugPort;
    }

    public void destroy() {//todo!!!

        try {
            portForward.close();
            if (debugPortForward != null)
                debugPortForward.close();
        } catch (IOException e) {
            log.warn("Cannot close bridge '{}' port forwarders", name, e);
        }

    }

    @Override
    public String toString() {
        return "PodBridge{" +
                "pod='" + name + '\'' +
                ", port='" + port + '\'' +
                ", debugPort='" + debugPort + '\'' +
                '}';
    }
}
