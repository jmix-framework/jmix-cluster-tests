package io.jmix.samples.cluster;

import com.google.common.base.Strings;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.event.ApplicationStartedEvent;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.DependsOn;
import org.springframework.context.annotation.Primary;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;
import org.springframework.jmx.support.ConnectorServerFactoryBean;
import org.springframework.remoting.rmi.RmiRegistryFactoryBean;

import javax.management.MalformedObjectNameException;
import javax.sql.DataSource;

@SpringBootApplication
public class SampleClusterApplication {

    @Autowired
    private Environment environment;

    public static void main(String[] args) {
        SpringApplication.run(SampleClusterApplication.class, args);
    }

    @Bean
    @Primary
    @ConfigurationProperties("main.datasource")
    DataSourceProperties dataSourceProperties() {
        return new DataSourceProperties();
    }

    @Bean
    @Primary
    @ConfigurationProperties("main.datasource.hikari")
    DataSource dataSource(DataSourceProperties dataSourceProperties) {
        return dataSourceProperties.initializeDataSourceBuilder().build();
    }

    @Bean("serverConnector")//todo?
    @DependsOn("rmiRegistry")
    ConnectorServerFactoryBean serverConnector() throws MalformedObjectNameException {//todo  exception in signature?
        ConnectorServerFactoryBean bean = new ConnectorServerFactoryBean();
        bean.setObjectName("connector:name=rmi");
        bean.setServiceUrl("service:jmx:rmi://localhost/jndi/rmi://localhost:10099/myconnector");
        return bean;
    }

    @Bean("rmiRegistry")
    RmiRegistryFactoryBean rmiRegistry() {//todo deal with deprecation
        RmiRegistryFactoryBean bean = new RmiRegistryFactoryBean();
        bean.setPort(10099);
        return bean;
    }

    @EventListener
    public void printApplicationUrl(ApplicationStartedEvent event) {
        LoggerFactory.getLogger(SampleClusterApplication.class).info("Application started at "
                + "http://localhost:"
                + environment.getProperty("local.server.port")
                + Strings.nullToEmpty(environment.getProperty("server.servlet.context-path")));
    }

    //todo setup jmx export manually to setup url correctly?
}
