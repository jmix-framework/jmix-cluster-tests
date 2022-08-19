package io.jmix.samples.cluster.tests;


import com.haulmont.cuba.core.app.ConfigStorageAPI;
import io.jmix.samples.cluster.test_system.model.TestContext;
import io.jmix.samples.cluster.test_system.model.annotations.ClusterTest;
import io.jmix.samples.cluster.test_system.model.annotations.Step;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import static org.assertj.core.api.Assertions.assertThat;

@Component("cluster_ConfigStorageCacheTest")
@ClusterTest(description = "Checks config storage cache is up to date in cluster")
public class ConfigStorageCacheTest {
    private static final String KEY = "oneKey";

    @Autowired
    private ConfigStorageAPI configStorage;

    @Step(order = 0, nodes = "1", description = "create setting")
    public void createSetting(TestContext context) {
        configStorage.setDbProperty(KEY, "oneVal");
    }

    @Step(order = 1, nodes = "2")
    public void updateSetting(TestContext context) {
        assertThat(configStorage.getDbProperty(KEY)).isEqualTo("oneVal");
        configStorage.setDbProperty(KEY, "updatedOneVal");
    }

    @Step(order = 2, nodes = "1")
    public void checkPropertyUpdatedOnAnotherNodes(TestContext context) {
        assertThat(configStorage.getDbProperty(KEY)).isEqualTo("updatedOneVal");
    }

}
