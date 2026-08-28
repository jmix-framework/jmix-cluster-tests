package io.jmix.samples.cluster2.tests.dynmodel;

import io.jmix.core.Metadata;
import io.jmix.dynmodel.ConcurrentDynamicModelChangeException;
import io.jmix.samples.cluster2.test_system.model.TestContext;
import io.jmix.samples.cluster2.test_system.model.annotations.AfterTest;
import io.jmix.samples.cluster2.test_system.model.annotations.BeforeTest;
import io.jmix.samples.cluster2.test_system.model.annotations.ClusterTest;
import io.jmix.samples.cluster2.test_system.model.annotations.Step;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

/**
 * Checks the base record check across nodes: a node that applies a settings record which another node
 * has meanwhile replaced is refused, so the model change it calculated against the old record is never
 * run.
 */
@Component("cluster_DynamicModelConcurrentApplyTest")
@ClusterTest(initNodes = {"1", "2"}, cleanStart = true,
        description = "Checks an apply based on a settings record replaced by another node is refused")
public class DynamicModelConcurrentApplyTest {

    public static final String CUSTOMER_ENTITY = "ClusterConcurrentCustomer";
    public static final String NAME_ATTRIBUTE = "name";
    public static final String NODE_1_ATTRIBUTE = "appliedByNode1";
    public static final String NODE_2_ATTRIBUTE = "appliedByNode2";

    public static final String BASE_SETTINGS_ID = "baseSettingsId";

    static final String MODEL_BASE = """
            model:
              basePackage: "io.jmix.samples.cluster2.dynmod"
              entities:
              - name: "ClusterConcurrentCustomer"
                attributes:
                - name: "name"
                  javaClass: "java.lang.String"
                  length: 100
            """;

    static final String MODEL_FROM_NODE_1 = """
            model:
              basePackage: "io.jmix.samples.cluster2.dynmod"
              entities:
              - name: "ClusterConcurrentCustomer"
                attributes:
                - name: "name"
                  javaClass: "java.lang.String"
                  length: 100
                - name: "appliedByNode1"
                  javaClass: "java.lang.String"
                  length: 50
            """;

    static final String MODEL_FROM_NODE_2 = """
            model:
              basePackage: "io.jmix.samples.cluster2.dynmod"
              entities:
              - name: "ClusterConcurrentCustomer"
                attributes:
                - name: "name"
                  javaClass: "java.lang.String"
                  length: 100
                - name: "appliedByNode2"
                  javaClass: "java.lang.String"
                  length: 50
            """;

    @Autowired
    private DynamicModelTestSupport support;
    @Autowired
    private Metadata metadata;

    @BeforeTest
    public void removeLeftovers(TestContext context) {
        support.removeAllSettings();
    }

    @Step(order = 0, nodes = "2", description = "Apply the base model on node 2")
    public void applyBaseModelOnNode2(TestContext context) {
        support.awaitApplyLockFree();

        UUID settingsId = support.applyModel(MODEL_BASE);

        context.put(BASE_SETTINGS_ID, settingsId);

        assertThat(metadata.getClass(CUSTOMER_ENTITY).findProperty(NAME_ATTRIBUTE)).isNotNull();
    }

    @Step(order = 1, nodes = "1", description = "Apply another change on node 1, replacing the active record")
    public void applyOnNode1(TestContext context) {
        UUID baseSettingsId = (UUID) context.get(BASE_SETTINGS_ID);

        support.awaitAppliedSettings(baseSettingsId);
        support.awaitApplyLockFree();

        UUID settingsId = support.applyModel(MODEL_FROM_NODE_1);

        assertThat(settingsId).isNotEqualTo(baseSettingsId);
        assertThat(support.getActiveSettingsId()).isEqualTo(settingsId);
    }

    @Step(order = 2, nodes = "2",
            description = "Check node 2 is refused when it applies its own change on the replaced record")
    public void applyIsRefusedOnNode2(TestContext context) {
        UUID baseSettingsId = (UUID) context.get(BASE_SETTINGS_ID);

        // Node 2 has to be done catching up with node 1's model before it tries its own apply, or the
        // apply would be refused because the catch-up still holds the lock, not because of the stale
        // base record this step is about.
        support.awaitAttribute(CUSTOMER_ENTITY, NODE_1_ATTRIBUTE);
        support.awaitApplyLockFree();

        assertThatExceptionOfType(ConcurrentDynamicModelChangeException.class)
                .isThrownBy(() -> support.applyModelOnSettings(baseSettingsId, MODEL_FROM_NODE_2));

        assertThat(support.getActiveSettingsId())
                .describedAs("the refused apply must not have replaced the active record")
                .isNotEqualTo(baseSettingsId);
        assertThat(metadata.getClass(CUSTOMER_ENTITY).findProperty(NODE_2_ATTRIBUTE))
                .describedAs("the refused apply must not have changed the metadata")
                .isNull();
    }

    @Step(order = 3, nodes = "2", description = "Check node 2 can apply again after reloading the active record")
    public void applySucceedsAfterReloadOnNode2(TestContext context) {
        UUID settingsId = support.applyModel(MODEL_FROM_NODE_2);

        assertThat(support.getActiveSettingsId()).isEqualTo(settingsId);
        assertThat(metadata.getClass(CUSTOMER_ENTITY).findProperty(NODE_2_ATTRIBUTE)).isNotNull();
    }

    @AfterTest
    public void cleanUp(TestContext context) {
        support.removeAllInstances(CUSTOMER_ENTITY);
        support.removeAllSettings();
    }
}
