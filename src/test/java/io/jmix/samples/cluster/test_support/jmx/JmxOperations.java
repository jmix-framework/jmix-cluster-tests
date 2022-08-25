package io.jmix.samples.cluster.test_support.jmx;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.management.*;
import javax.management.remote.JMXConnector;
import javax.management.remote.JMXConnectorFactory;
import javax.management.remote.JMXServiceURL;
import java.io.IOException;

public class JmxOperations {//todo fluent (when customisation will be needed)
    public static final String JMX_SERVICE_CUSTOM_URL = "service:jmx:jmxmp://localhost:%s";
    public static final String CLUSTER_TEST_BEAN_NAME = "jmix.cluster:type=ClusterTestBean";


    private static final Logger log = LoggerFactory.getLogger(JmxOperations.class);

    public static Object doInJmxConnection(String port, JMXAction action) {
        try {
            JMXServiceURL url = new JMXServiceURL(String.format(JMX_SERVICE_CUSTOM_URL, port));
            try (JMXConnector connector = JMXConnectorFactory.connect(url)) {
                MBeanServerConnection connection = connector.getMBeanServerConnection();
                ObjectName beanName = new ObjectName(CLUSTER_TEST_BEAN_NAME);
                return action.accept(connection, beanName);
            }
        } catch (IOException | MalformedObjectNameException | MBeanException | AttributeNotFoundException |
                 InstanceNotFoundException |
                 ReflectionException e) {
            throw new RuntimeException(String.format("Cannot connect to pod by port %s.", port), e);
        }
    }

    public static <T> T getAttribute(String port, String attributeName) {
        return (T) doInJmxConnection(port, (connection, beanName) -> connection.getAttribute(beanName, attributeName));
    }

    public static <T> T invoke(String port, String operationName, Object[] params, String[] types) {
        return (T) doInJmxConnection(port,
                (conn, objectName) -> conn.invoke(objectName, operationName, params, types));
    }

    public interface JMXAction {
        Object accept(MBeanServerConnection connection, ObjectName beanName)
                throws IOException, MBeanException, AttributeNotFoundException, InstanceNotFoundException, ReflectionException;
    }
}
