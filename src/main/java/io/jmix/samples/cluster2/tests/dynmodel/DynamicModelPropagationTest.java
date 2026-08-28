package io.jmix.samples.cluster2.tests.dynmodel;

import io.jmix.core.Metadata;
import io.jmix.core.UnconstrainedDataManager;
import io.jmix.core.entity.EntityValues;
import io.jmix.core.querycondition.PropertyCondition;
import io.jmix.core.security.SystemAuthenticator;
import io.jmix.samples.cluster2.entity.Sample;
import io.jmix.samples.cluster2.test_system.model.TestContext;
import io.jmix.samples.cluster2.test_system.model.annotations.AfterTest;
import io.jmix.samples.cluster2.test_system.model.annotations.BeforeTest;
import io.jmix.samples.cluster2.test_system.model.annotations.ClusterTest;
import io.jmix.samples.cluster2.test_system.model.annotations.Step;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Checks that a dynamic model applied on one node reaches the other nodes of the cluster: the model
 * appears in their metadata, and they can read and write both the new dynamic entity and the new
 * dynamic attribute of a static entity.
 */
@Component("cluster_DynamicModelPropagationTest")
@ClusterTest(initNodes = {"1", "2"}, cleanStart = true,
        description = "Checks an applied dynamic model reaches the other cluster nodes")
public class DynamicModelPropagationTest {

    public static final String CUSTOMER_ENTITY = "ClusterPropCustomer";
    public static final String SAMPLE_ENTITY = "cluster_Sample";
    public static final String NAME_ATTRIBUTE = "name";
    public static final String CODE_ATTRIBUTE = "code";
    public static final String SAMPLE_NOTE_ATTRIBUTE = "clusterPropNote";

    public static final String CUSTOMER_INSTANCE_NAME = "created-on-node-2";
    public static final String CUSTOMER_CODE = "code-set-on-node-2";
    public static final String SAMPLE_INSTANCE_NAME = "DynamicModelPropagationTestSample";
    public static final String SAMPLE_NOTE = "note-set-on-node-2";

    public static final String SETTINGS_ID_V1 = "settingsIdV1";
    public static final String SETTINGS_ID_V2 = "settingsIdV2";

    /**
     * One dynamic entity and one dynamic attribute of the static {@code cluster_Sample} entity, so the
     * test covers both storage layouts: a dynamic table and a side table.
     */
    static final String MODEL_V1 = """
            model:
              basePackage: "io.jmix.samples.cluster2.dynmod"
              entities:
              - name: "ClusterPropCustomer"
                attributes:
                - name: "name"
                  javaClass: "java.lang.String"
                  length: 100
                  instanceName: true
              - name: "cluster_Sample"
                attributes:
                - name: "clusterPropNote"
                  javaClass: "java.lang.String"
                  length: 100
            """;

    /**
     * The same model with one more attribute on the dynamic entity, so the test also covers a second
     * apply reaching a node that already caught up once.
     */
    static final String MODEL_V2 = """
            model:
              basePackage: "io.jmix.samples.cluster2.dynmod"
              entities:
              - name: "ClusterPropCustomer"
                attributes:
                - name: "name"
                  javaClass: "java.lang.String"
                  length: 100
                  instanceName: true
                - name: "code"
                  javaClass: "java.lang.String"
                  length: 50
              - name: "cluster_Sample"
                attributes:
                - name: "clusterPropNote"
                  javaClass: "java.lang.String"
                  length: 100
            """;

    private static final Logger log = LoggerFactory.getLogger(DynamicModelPropagationTest.class);

    @Autowired
    private DynamicModelTestSupport support;
    @Autowired
    private Metadata metadata;
    @Autowired
    private UnconstrainedDataManager dataManager;
    @Autowired
    private SystemAuthenticator authenticator;

    @BeforeTest
    public void removeLeftovers(TestContext context) {
        cleanUp();
    }

    @Step(order = 0, nodes = "1", description = "Apply a model on node 1")
    public void applyModelOnNode1(TestContext context) {
        support.awaitApplyLockFree();

        UUID settingsId = support.applyModel(MODEL_V1);

        context.put(SETTINGS_ID_V1, settingsId);

        assertThat(metadata.findClass(CUSTOMER_ENTITY)).isNotNull();
        assertThat(metadata.getClass(CUSTOMER_ENTITY).findProperty(NAME_ATTRIBUTE)).isNotNull();
        assertThat(metadata.getClass(Sample.class).findProperty(SAMPLE_NOTE_ATTRIBUTE)).isNotNull();
        assertThat(support.getActiveSettingsId()).isEqualTo(settingsId);
    }

    @Step(order = 1, nodes = "2", description = "Check the model reached node 2 and write through it")
    public void useModelOnNode2(TestContext context) {
        UUID settingsId = (UUID) context.get(SETTINGS_ID_V1);

        support.awaitAttribute(CUSTOMER_ENTITY, NAME_ATTRIBUTE);
        support.awaitAttribute(SAMPLE_ENTITY, SAMPLE_NOTE_ATTRIBUTE);
        support.awaitAppliedSettings(settingsId);

        Object customer = support.createInstance(CUSTOMER_ENTITY);
        EntityValues.setValue(customer, NAME_ATTRIBUTE, CUSTOMER_INSTANCE_NAME);
        support.save(customer);

        authenticator.begin();
        try {
            Sample sample = dataManager.create(Sample.class);
            sample.setName(SAMPLE_INSTANCE_NAME);
            EntityValues.setValue(sample, SAMPLE_NOTE_ATTRIBUTE, SAMPLE_NOTE);
            dataManager.save(sample);
        } finally {
            authenticator.end();
        }

        log.info("Node 2 created an instance of '{}' and a '{}' with a dynamic attribute value",
                CUSTOMER_ENTITY, Sample.class.getSimpleName());
    }

    @Step(order = 2, nodes = "1", description = "Read what node 2 wrote back on node 1")
    public void readNode2DataOnNode1(TestContext context) {
        Object customer = loadTheOnlyCustomer();
        assertThat((String) EntityValues.getValue(customer, NAME_ATTRIBUTE)).isEqualTo(CUSTOMER_INSTANCE_NAME);

        Sample sample = loadTheOnlySample();
        assertThat((String) EntityValues.getValue(sample, SAMPLE_NOTE_ATTRIBUTE)).isEqualTo(SAMPLE_NOTE);
    }

    @Step(order = 3, nodes = "1", description = "Apply a second model version on node 1")
    public void applySecondModelOnNode1(TestContext context) {
        support.awaitApplyLockFree();

        UUID settingsId = support.applyModel(MODEL_V2);

        context.put(SETTINGS_ID_V2, settingsId);

        assertThat(settingsId).isNotEqualTo(context.get(SETTINGS_ID_V1));
        assertThat(metadata.getClass(CUSTOMER_ENTITY).findProperty(CODE_ATTRIBUTE)).isNotNull();
    }

    @Step(order = 4, nodes = "2", description = "Check the second model version reached node 2")
    public void useSecondModelOnNode2(TestContext context) {
        UUID settingsId = (UUID) context.get(SETTINGS_ID_V2);

        support.awaitAttribute(CUSTOMER_ENTITY, CODE_ATTRIBUTE);
        support.awaitAppliedSettings(settingsId);

        Object customer = loadTheOnlyCustomer();
        EntityValues.setValue(customer, CODE_ATTRIBUTE, CUSTOMER_CODE);
        support.save(customer);
    }

    @Step(order = 5, nodes = "1", description = "Read the attribute added by the second version on node 1")
    public void readSecondModelDataOnNode1(TestContext context) {
        Object customer = loadTheOnlyCustomer();

        assertThat((String) EntityValues.getValue(customer, NAME_ATTRIBUTE)).isEqualTo(CUSTOMER_INSTANCE_NAME);
        assertThat((String) EntityValues.getValue(customer, CODE_ATTRIBUTE)).isEqualTo(CUSTOMER_CODE);
    }

    @AfterTest
    public void cleanTables(TestContext context) {
        cleanUp();
    }

    protected void cleanUp() {
        support.removeAllInstances(CUSTOMER_ENTITY);
        removeSamples();
        support.removeAllSettings();
    }

    protected Object loadTheOnlyCustomer() {
        List<Object> customers = support.loadAllInstances(CUSTOMER_ENTITY);
        assertThat(customers).hasSize(1);
        return customers.get(0);
    }

    protected Sample loadTheOnlySample() {
        List<Sample> samples = authenticator.withSystem(() -> dataManager.load(Sample.class)
                .condition(PropertyCondition.equal("name", SAMPLE_INSTANCE_NAME))
                .list());
        assertThat(samples).hasSize(1);
        return samples.get(0);
    }

    protected void removeSamples() {
        authenticator.runWithSystem(() -> {
            List<Sample> samples = dataManager.load(Sample.class)
                    .condition(PropertyCondition.equal("name", SAMPLE_INSTANCE_NAME))
                    .list();
            if (!samples.isEmpty()) {
                dataManager.remove(samples);
            }
        });
    }
}
