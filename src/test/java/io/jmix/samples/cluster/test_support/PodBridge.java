package io.jmix.samples.cluster.test_support;

import javax.annotation.Nullable;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Collections;

public class PodBridge {
    public static final String PID_FILE_NAME = "forwarders.txt";

    private final String name;
    private final Process forwarder;
    private final Process debugForwarder;
    private final String port;
    private final String debugPort;


    private PodBridge(String name, Process forwarder, String port, @Nullable Process debugForwarder, @Nullable String debugPort) {
        this.name = name;
        this.forwarder = forwarder;
        this.port = port;
        this.debugForwarder = debugForwarder;
        this.debugPort = debugPort;
    }

    public String getName() {
        return name;
    }

    public Process getForwarder() {
        return forwarder;
    }

    public String getPort() {
        return port;
    }

    public void destroy() {
        forwarder.destroy();
        if (debugForwarder != null)
            debugForwarder.destroy();
        //todo seal after destroying
    }

    @Nullable
    public Process getDebugForwarder() {
        return debugForwarder;
    }

    @Nullable
    public String getDebugPort() {
        return debugPort;
    }

    public static PodBridge establish(String podName, String localPort, String targetPort) {
        return establish(podName, localPort, targetPort, null, null);
    }

    public static PodBridge establish(String podName, String localPort, String targetPort, @Nullable String localDebugPort, @Nullable String appDebugPort) {
        try {
            Process process = startForwarding(podName, localPort, targetPort);

            Process debugProcess = (localDebugPort != null && appDebugPort != null)
                    ? startForwarding(podName, localDebugPort, appDebugPort)
                    : null;

            return new PodBridge(podName, process, localPort, debugProcess, localDebugPort);
        } catch (IOException e) {
            throw new RuntimeException(String.format("Cannot forward port for pod '%s'", podName), e);
        }
    }

    private static Process startForwarding(String podName, String localPort, String targetPort) throws IOException {
        Process process = new ProcessBuilder("kubectl", "port-forward", "pods/" + podName, localPort + ":" + targetPort)
                .redirectOutput(ProcessBuilder.Redirect.INHERIT)
                .redirectErrorStream(true)
                .start();
        //todo wait message about forwarding?
        long pid = process.pid();

        if (!Files.exists(Paths.get(PID_FILE_NAME))) {
            Files.createFile(Paths.get(PID_FILE_NAME));
        }

        Files.write(Paths.get(PID_FILE_NAME),
                Collections.singleton(Long.toString(pid)),
                StandardOpenOption.APPEND);

        return process;
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
