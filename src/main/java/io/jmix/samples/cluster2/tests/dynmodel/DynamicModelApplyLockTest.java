package io.jmix.samples.cluster2.tests.dynmodel;

import io.jmix.core.Metadata;
import io.jmix.dynmodel.DynamicModelApplyLockedException;
import io.jmix.pessimisticlock.entity.LockInfo;
import io.jmix.samples.cluster2.test_system.model.TestContext;
import io.jmix.samples.cluster2.test_system.model.annotations.AfterTest;
import io.jmix.samples.cluster2.test_system.model.annotations.BeforeTest;
import io.jmix.samples.cluster2.test_system.model.annotations.ClusterTest;
import io.jmix.samples.cluster2.test_system.model.annotations.Step;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

/**
 * Checks that the dynamic model apply lock is cluster-wide: while one node holds it, an apply started
 * on another node is refused, and it succeeds again once the lock is released.
 * <p>
 * The lock is taken directly through the pessimistic lock add-on instead of by starting a real apply,
 * because a step has to return before the next step runs on the other node, and a real apply releases
 * the lock when it returns.
 */
@Component("cluster_DynamicModelApplyLockTest")
@ClusterTest(initNodes = {"1", "2"}, cleanStart = true,
        description = "Checks the dynamic model apply lock is cluster-wide")
public class DynamicModelApplyLockTest {

    public static final String CUSTOMER_ENTITY = "ClusterLockCustomer";
    public static final String NAME_ATTRIBUTE = "name";

    static final String MODEL = """
            model:
              basePackage: "io.jmix.samples.cluster2.dynmod"
              entities:
              - name: "ClusterLockCustomer"
                attributes:
                - name: "name"
                  javaClass: "java.lang.String"
                  length: 100
            """;

    private static final Logger log = LoggerFactory.getLogger(DynamicModelApplyLockTest.class);

    @Autowired
    private DynamicModelTestSupport support;
    @Autowired
    private Metadata metadata;

    @BeforeTest
    public void removeLeftovers(TestContext context) {
        support.removeAllSettings();
    }

    @Step(order = 0, nodes = "1", description = "Take the apply lock on node 1")
    public void takeLockOnNode1(TestContext context) {
        LockInfo alreadyHeldBy = support.takeApplyLock();

        assertThat(alreadyHeldBy)
                .describedAs("the apply lock was already held before the test took it")
                .isNull();
        assertThat(support.getApplyLockInfo()).isNotNull();
    }

    @Step(order = 1, nodes = "2", description = "Check node 2 refuses to apply while node 1 holds the lock")
    public void applyIsRefusedOnNode2(TestContext context) {
        LockInfo lockInfo = support.getApplyLockInfo();
        assertThat(lockInfo)
                .describedAs("node 2 does not see the lock taken on node 1, so the locks cache is not shared")
                .isNotNull();
        log.info("Node 2 sees the apply lock held by '{}' since {}", lockInfo.getUsername(), lockInfo.getSince());

        assertThatExceptionOfType(DynamicModelApplyLockedException.class)
                .isThrownBy(() -> support.applyModel(MODEL))
                .matches(e -> e.getUsername() != null, "the refusal names the user holding the lock");

        assertThat(metadata.findClass(CUSTOMER_ENTITY))
                .describedAs("the refused apply must not have changed the metadata")
                .isNull();
    }

    @Step(order = 2, nodes = "1", description = "Release the apply lock on node 1")
    public void releaseLockOnNode1(TestContext context) {
        support.releaseApplyLock();

        assertThat(support.getApplyLockInfo()).isNull();
    }

    @Step(order = 3, nodes = "2", description = "Check node 2 can apply once the lock is released")
    public void applySucceedsOnNode2(TestContext context) {
        UUID settingsId = support.applyModel(MODEL);

        assertThat(metadata.findClass(CUSTOMER_ENTITY)).isNotNull();
        assertThat(metadata.getClass(CUSTOMER_ENTITY).findProperty(NAME_ATTRIBUTE)).isNotNull();
        assertThat(support.getActiveSettingsId()).isEqualTo(settingsId);
    }

    @Step(order = 4, nodes = "1", description = "Check the model applied by node 2 reached node 1")
    public void modelReachedNode1(TestContext context) {
        support.awaitAttribute(CUSTOMER_ENTITY, NAME_ATTRIBUTE);
    }

    @AfterTest
    public void cleanUp(TestContext context) {
        // The lock is left behind if a step failed between taking and releasing it, and it would then
        // block every later apply until it expires.
        if (support.getApplyLockInfo() != null) {
            support.releaseApplyLock();
        }
        support.removeAllInstances(CUSTOMER_ENTITY);
        support.removeAllSettings();
    }
}
