package io.jmix.samples.cluster.tests;

import io.jmix.core.DataManager;
import io.jmix.core.Metadata;
import io.jmix.core.pessimisticlocking.LockInfo;
import io.jmix.core.pessimisticlocking.LockManager;
import io.jmix.core.security.SystemAuthenticator;
import io.jmix.samples.cluster.entity.Sample;
import io.jmix.samples.cluster.test_system.impl.BaseClusterTest;
import io.jmix.samples.cluster.test_system.model.TestContext;
import io.jmix.samples.cluster.test_system.model.annotations.ClusterTestProperties;
import io.jmix.samples.cluster.test_system.model.annotations.TestStep;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

@Component("cluster_EntityLockTest")
@ClusterTestProperties(description = "Checks entity lock cluster propagation")
public class EntityLockTest extends BaseClusterTest {
    public static final String ENTITY_INSTANCE_NAME = "LockTestEntity";

    @Autowired
    Metadata metadata;
    @Autowired
    LockManager lockManager;
    @Autowired
    SystemAuthenticator authenticator;
    @Autowired
    DataManager dataManager;

    @TestStep(order = 0, nodes = "1")
    public boolean testStandalone(TestContext context) {
        authenticator.begin();
        try {
            Sample entity = metadata.create(Sample.class);
            entity.setName("AnotherEntity");


            LockInfo lockInfo = lockManager.lock(entity);

            assertNull(lockInfo);

            lockInfo = lockManager.lock(entity);

            assertNotNull(lockInfo);

            lockManager.unlock(entity);
            lockInfo = lockManager.getLockInfo("cluster_Sample", entity.getId().toString());

            assertNull(lockInfo);
        } finally {
            authenticator.end();
        }
        return true;
    }

    @TestStep(order = 1, nodes = "1")
    public boolean createEntity(TestContext context) {

        List<Sample> entities = dataManager.unconstrained().load(Sample.class)
                .query("select e from cluster_Sample e where e.name = :name")
                .parameter("name", ENTITY_INSTANCE_NAME)
                .list();
        dataManager.unconstrained().remove(entities);

        Sample entity = dataManager.create(Sample.class);
        entity.setName(ENTITY_INSTANCE_NAME);
        dataManager.unconstrained().save(entity);
        return true;
    }

    @TestStep(order = 2, nodes = "2")
    public boolean lockEntity(TestContext context) {
        authenticator.begin();
        try {
            Sample entity = dataManager.load(Sample.class)
                    .query("select e from cluster_Sample e where e.name = :name")
                    .parameter("name", ENTITY_INSTANCE_NAME)
                    .one();

            LockInfo lockInfo = lockManager.lock(entity);

            assertNull(lockInfo);

        } finally {
            authenticator.end();
        }
        return true;
    }

    @TestStep(order = 3, nodes = "3")
    public boolean checkEntityLocked(TestContext context) {
        authenticator.begin();
        try {
            Sample entity = dataManager.load(Sample.class)
                    .query("select e from cluster_Sample e where e.name = :name")
                    .parameter("name", ENTITY_INSTANCE_NAME)
                    .one();

            LockInfo lockInfo = lockManager.lock(entity);

            assertNotNull(lockInfo);

            lockManager.unlock(entity);
            lockInfo = lockManager.getLockInfo("cluster_Sample", entity.getId().toString());

            assertNull(lockInfo);
        } finally {
            authenticator.end();
        }
        return true;
    }

    @TestStep(order = 4, nodes = "1")
    public boolean checkEntityUnlocked(TestContext context) {
        authenticator.begin();
        try {
            Sample entity = dataManager.load(Sample.class)
                    .query("select e from cluster_Sample e where e.name = :name")
                    .parameter("name", ENTITY_INSTANCE_NAME)
                    .one();

            lockManager.unlock(entity);
            LockInfo lockInfo = lockManager.getLockInfo("cluster_Sample", entity.getId().toString());

            assertNull(lockInfo);
        } finally {
            authenticator.end();
        }
        return true;
    }


    void assertNull(Object object) {
        if (object != null) {
            throw new RuntimeException(String.format("Assertion failed, %s is not null", object));
        }
    }

    void assertNotNull(Object object) {
        if (object == null) {
            throw new RuntimeException(String.format("Assertion failed: null"));
        }
    }
}
