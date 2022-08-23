package io.jmix.samples.cluster.test_system.model;

import java.io.Serializable;
import java.util.List;

public class TestResult implements Serializable {
    private static final long serialVersionUID = -3105884104042261255L;
    private List<String> logs;
    private Throwable exception;
    private TestContext context;


    private boolean successfully = true;

    public List<String> getLogs() {
        return logs;
    }

    public void setLogs(List<String> logs) {
        this.logs = logs;
    }

    public Throwable getException() {
        return exception;
    }

    public void setException(Throwable exception) {
        this.exception = exception;
    }

    public boolean isSuccessfully() {
        return successfully;
    }

    public void setSuccessfully(boolean successfully) {
        this.successfully = successfully;
    }

    public TestContext getContext() {
        return context;
    }

    public void setContext(TestContext context) {
        this.context = context;
    }

    @Override
    public String toString() {
        return "TestResult{" +
                "   successfully=" + successfully + ",\n" +
                "   exception=" + exception + ",\n" +
                "   context=" + context + ",\n" +

                "logs=" + String.join("\n", logs) + "\n" +
                '}';
    }
}
