package io.jmix.samples.cluster.tests.jmixcaches;

import io.jmix.core.DataManager;
import io.jmix.core.Metadata;
import io.jmix.core.pessimisticlocking.LockInfo;
import io.jmix.core.pessimisticlocking.LockManager;
import io.jmix.core.security.SystemAuthenticator;
import io.jmix.samples.cluster.entity.Sample;
import io.jmix.samples.cluster.test_system.model.TestContext;
import io.jmix.samples.cluster.test_system.model.annotations.AddNode;
import io.jmix.samples.cluster.test_system.model.annotations.ClusterTest;
import io.jmix.samples.cluster.test_system.model.annotations.Step;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;


@Component("cluster_EntityLockTest")
@ClusterTest(initNodes = {"1", "2"}, description = "Checks entity lock cluster propagation")
public class EntityLockTest {
    public static final String ENTITY_INSTANCE_NAME = "LockTestEntity";

    @Autowired
    Metadata metadata;
    @Autowired
    LockManager lockManager;
    @Autowired
    SystemAuthenticator authenticator;
    @Autowired
    DataManager dataManager;

    @Step(order = 0)
    public boolean testStandalone(TestContext context) {
        authenticator.begin();
        try {
            Sample entity = metadata.create(Sample.class);
            entity.setName("AnotherEntity");


            LockInfo lockInfo = lockManager.lock(entity);

            assertThat(lockInfo).isNull();

            lockInfo = lockManager.lock(entity);

            assertThat(lockInfo).isNotNull();

            lockManager.unlock(entity);
            lockInfo = lockManager.getLockInfo("cluster_Sample", entity.getId().toString());

            assertThat(lockInfo).isNull();
        } finally {
            authenticator.end();
        }
        return true;
    }

    @Step(order = 1, nodes = "1")
    public boolean createEntity(TestContext context) {

        List<Sample> entities = dataManager.unconstrained().load(Sample.class)
                .query("select e from cluster_Sample e where e.name = :name")
                .parameter("name", ENTITY_INSTANCE_NAME)
                .list();
        dataManager.unconstrained().remove(entities);

        Sample entity = dataManager.create(Sample.class);
        entity.setName(ENTITY_INSTANCE_NAME);
        dataManager.unconstrained().save(entity);
        context.put("entity", entity);
        return true;
    }

    @Step(order = 2, nodes = "2")
    public boolean lockEntity(TestContext context) {//todo @Authenticated
        authenticator.begin();
        try {
            Sample entity = (Sample) context.get("entity");

            LockInfo lockInfo = lockManager.lock(entity);

            assertThat(lockInfo).isNull();

        } finally {
            authenticator.end();
        }
        return true;
    }

    @Step(order = 3, nodes = "1")
    public boolean checkEntityLocked(TestContext context) {
        authenticator.begin();
        try {
            Sample entity = (Sample) context.get("entity");

            LockInfo lockInfo = lockManager.lock(entity);

            assertThat(lockInfo).isNotNull();

        } finally {
            authenticator.end();
        }
        return true;
    }

    @AddNode(order = 4, names = "3")
    @Step(order = 5, nodes = "3")
    public boolean checkEntityLockedOnNewNodeAndUnlock(TestContext context) {
        authenticator.begin();
        try {
            Sample entity = (Sample) context.get("entity");

            LockInfo lockInfo = lockManager.lock(entity);

            assertThat(lockInfo).isNotNull();

            lockManager.unlock(entity);
            lockInfo = lockManager.getLockInfo("cluster_Sample", entity.getId().toString());

            assertThat(lockInfo).isNull();
        } finally {
            authenticator.end();
        }
        return true;
    }

    @Step(order = 6, nodes = "1")
    public boolean checkEntityUnlocked(TestContext context) {
        authenticator.begin();
        try {
            Sample entity = dataManager.load(Sample.class)//todo use context!!
                    .query("select e from cluster_Sample e where e.name = :name")
                    .parameter("name", ENTITY_INSTANCE_NAME)
                    .one();

            lockManager.unlock(entity);
            LockInfo lockInfo = lockManager.getLockInfo("cluster_Sample", entity.getId().toString());

            assertThat(lockInfo).isNull();
        } finally {
            authenticator.end();
        }
        return true;
    }

}
