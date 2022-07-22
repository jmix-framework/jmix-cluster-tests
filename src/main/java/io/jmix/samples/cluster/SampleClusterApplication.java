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
import org.springframework.context.annotation.Primary;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;
import org.springframework.jmx.support.ConnectorServerFactoryBean;

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

    @Bean("serverConnector")
    ConnectorServerFactoryBean serverConnector() throws MalformedObjectNameException {
        ConnectorServerFactoryBean bean = new ConnectorServerFactoryBean();
        bean.setObjectName("connector:name=jmxmp");
        bean.setServiceUrl("service:jmx:jmxmp://localhost:9875");
        return bean;
    }

    @EventListener
    public void printApplicationUrl(ApplicationStartedEvent event) {
        LoggerFactory.getLogger(SampleClusterApplication.class).info("Application started at "
                + "http://localhost:"
                + environment.getProperty("local.server.port")
                + Strings.nullToEmpty(environment.getProperty("server.servlet.context-path")));
    }

}
