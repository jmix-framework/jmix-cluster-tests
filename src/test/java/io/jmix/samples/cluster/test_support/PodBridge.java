package io.jmix.samples.cluster.test_support;

import java.io.IOException;

public class PodBridge {//todo PodConnector? PodConnectInfo?
    //todo forward debug port too by setting
    private final String name;
    private final Process forwarder;
    private final String port;


    private PodBridge(String name, Process forwarder, String port) {
        this.name = name;
        this.forwarder = forwarder;
        this.port = port;
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
        forwarder.destroy();//todo wait? check?
        //todo seal after destroying
    }

    public static PodBridge establish(String podName, String localPort, String targetPort) {
        try {
            Process process = new ProcessBuilder("kubectl", "port-forward", "pods/" + podName, localPort + ":" + targetPort)//todo use .command() if it will not work
                    //todo redirect later to logs when logs will be added (for now it is shown in general test log but not for current test)
                    .redirectOutput(ProcessBuilder.Redirect.INHERIT)
                    .redirectErrorStream(true)
                    .start();
            return new PodBridge(podName, process, localPort);
        } catch (IOException e) {
            throw new RuntimeException(String.format("Cannot forward port for pod '%s'", podName), e);
        }
    }
}
