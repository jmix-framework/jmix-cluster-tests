package io.jmix.samples.cluster.test_support.jmx;

import io.jmix.samples.cluster.test_system.model.TestInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.management.*;
import javax.management.remote.JMXConnector;
import javax.management.remote.JMXConnectorFactory;
import javax.management.remote.JMXServiceURL;
import java.io.IOException;
import java.util.List;
import java.util.stream.Stream;

public class JmxOperation {//todo fluent?
    public static final String JMX_SERVICE_CUSTOM_URL = "service:jmx:jmxmp://localhost:%s";
    public static final String CLUSTER_TEST_BEAN_NAME = "jmix.cluster:type=ClusterTestBean";
    public static final String TEST_LIST_ATTRIBUTE = "Tests";//todo move out after adaptation?

    private static final Logger log = LoggerFactory.getLogger(JmxOperation.class);


    public static void doInJmxConnection(String port, JMXAction action) {
        try {
            JMXServiceURL url = new JMXServiceURL(String.format(JMX_SERVICE_CUSTOM_URL, port));
            try (JMXConnector connector = JMXConnectorFactory.connect(url)) {
                MBeanServerConnection connection = connector.getMBeanServerConnection();
                ObjectName beanName = new ObjectName(CLUSTER_TEST_BEAN_NAME);
                action.accept(connection, beanName);
            }
        } catch (IOException | MalformedObjectNameException | MBeanException | AttributeNotFoundException |
                 InstanceNotFoundException |
                 ReflectionException e) {
            throw new RuntimeException(String.format("Cannot connect to pod by port %s.", port), e);
        }
    }

    //todo rewrite through "doInJmxConnection'
    public static Stream<TestInfo> loadTests(String port) throws IOException, MalformedObjectNameException, ReflectionException, InstanceNotFoundException, IntrospectionException, AttributeNotFoundException, MBeanException {
        JMXServiceURL url = new JMXServiceURL(String.format(JMX_SERVICE_CUSTOM_URL, port));

        MBeanServerConnection connection;
        try (JMXConnector connector = JMXConnectorFactory.connect(url)) {
            connection = connector.getMBeanServerConnection();
            ObjectName beanName = new ObjectName(CLUSTER_TEST_BEAN_NAME);
            MBeanInfo info = connection.getMBeanInfo(beanName);
            log.debug("MbeanInfo: {}", info);
            List<TestInfo> result = (List<TestInfo>) connection.getAttribute(beanName, TEST_LIST_ATTRIBUTE);
            return result.stream();
        }
    }

    public interface JMXAction {
        void accept(MBeanServerConnection connection, ObjectName beanName)
                throws IOException, MBeanException, AttributeNotFoundException, InstanceNotFoundException, ReflectionException;
    }
}
