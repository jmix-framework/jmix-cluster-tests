package io.jmix.samples.cluster.test_support;

import javax.annotation.Nullable;
import java.io.IOException;

public class PodBridge {//todo PodConnector? PodConnectInfo?
    //todo forward debug port too by setting
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
    }//todo not needed?

    public String getPort() {
        return port;
    }

    public void destroy() {
        forwarder.destroy();//todo wait? check?
        if (debugForwarder != null)
            debugForwarder.destroy();
        //todo seal after destroying
    }

    @Nullable
    public Process getDebugForwarder() {
        return debugForwarder;
    }//todo not needed

    @Nullable
    public String getDebugPort() {
        return debugPort;
    }

    public static PodBridge establish(String podName, String localPort, String targetPort) {
        try {
            Process process = startForwarding(podName, localPort, targetPort);
            /*new ProcessBuilder("kubectl", "port-forward", "pods/" + podName, localPort + ":" + targetPort)//todo use .command() if it will not work
                    //todo redirect later to logs when logs will be added (for now it is shown in general test log but not for current test)
                    .redirectOutput(ProcessBuilder.Redirect.INHERIT)
                    .redirectErrorStream(true)
                    .start();
*/

            Thread.sleep(2000);//todo REMOVE!!! how to wait port forwarding? check output?
            return new PodBridge(podName, process, localPort, null, null);

        } catch (IOException | InterruptedException e) {
            throw new RuntimeException(String.format("Cannot forward port for pod '%s'", podName), e);
        }
    }

    public static PodBridge establish(String podName, String localPort, String targetPort, @Nullable String localDebugPort, @Nullable String appDebugPort) {
        try {
            Process process = startForwarding(podName, localPort, targetPort);
            /*new ProcessBuilder("kubectl", "port-forward", "pods/" + podName, localPort + ":" + targetPort)//todo use .command() if it will not work
                    //todo redirect later to logs when logs will be added (for now it is shown in general test log but not for current test)
                    .redirectOutput(ProcessBuilder.Redirect.INHERIT)
                    .redirectErrorStream(true)
                    .start();*/

            Process debugProcess = (localDebugPort != null && appDebugPort != null)
                    ? startForwarding(podName, localDebugPort, appDebugPort)
                    : null;
                    /*new ProcessBuilder("kubectl", "port-forward", "pods/" + podName, localDebugPort + ":" + appDebugPort)//todo use .command() if it will not work
                    //todo redirect later to logs when logs will be added (for now it is shown in general test log but not for current test)
                    .redirectOutput(ProcessBuilder.Redirect.INHERIT)
                    .redirectErrorStream(true)
                    .start();*/

            Thread.sleep(2000);//todo REMOVE!!! how to wait port forwarding? check output?
            return new PodBridge(podName, process, localPort, debugProcess, localDebugPort);
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException(String.format("Cannot forward port for pod '%s'", podName), e);
        }
    }

    private static Process startForwarding(String podName, String localPort, String targetPort) throws IOException {
        return new ProcessBuilder("kubectl", "port-forward", "pods/" + podName, localPort + ":" + targetPort)//todo use .command() if it will not work
                //todo redirect later to logs when logs will be added (for now it is shown in general test log but not for current test)
                .redirectOutput(ProcessBuilder.Redirect.INHERIT)
                .redirectErrorStream(true)
                .start();
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
