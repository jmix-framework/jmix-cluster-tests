package io.jmix.samples.cluster2.tests.dynmodel;

import io.jmix.core.Metadata;
import io.jmix.core.UnconstrainedDataManager;
import io.jmix.core.metamodel.model.MetaClass;
import io.jmix.core.security.SystemAuthenticator;
import io.jmix.dynmodel.DynamicModelManager;
import io.jmix.dynmodel.DynamicModelMigration;
import io.jmix.dynmodel.DynamicModelSettingsService;
import io.jmix.dynmodel.entity.DynamicModelSettings;
import io.jmix.dynmodel.impl.cluster.DynamicModelClusterSupport;
import io.jmix.dynmodel.meta.DynamicModelDefinition;
import io.jmix.dynmodel.meta.DynamicModelDefinitionSerializer;
import io.jmix.pessimisticlock.LockManager;
import io.jmix.pessimisticlock.entity.LockInfo;
import io.jmix.pessimisticlock.entity.LockNotSupported;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

/**
 * Helper operations shared by the dynamic model cluster tests: applying a model the way the Admin UI
 * does, waiting for a model applied on another node to arrive, and cleaning up afterwards.
 */
@Component("cluster_DynamicModelTestSupport")
public class DynamicModelTestSupport {

    /**
     * Name of the cluster-wide apply lock, as taken by the dynamic model apply paths.
     */
    public static final String APPLY_LOCK_NAME = "dynmod_DynamicModelApply";

    /**
     * Id of the cluster-wide apply lock, as taken by the dynamic model apply paths.
     */
    public static final String APPLY_LOCK_ID = "global";

    /**
     * How long a step waits for a model applied on another node to arrive. A catch-up re-applies the
     * whole model including DDL, so it is not instant.
     */
    public static final long CATCH_UP_TIMEOUT_SEC = 90;

    private static final long POLL_PERIOD_MS = 500;

    private static final Logger log = LoggerFactory.getLogger(DynamicModelTestSupport.class);

    @Autowired
    private Metadata metadata;
    @Autowired
    private UnconstrainedDataManager dataManager;
    @Autowired
    private SystemAuthenticator authenticator;
    @Autowired
    private DynamicModelSettingsService settingsService;
    @Autowired
    private DynamicModelManager dynamicModelManager;
    @Autowired
    private DynamicModelDefinitionSerializer serializer;
    @Autowired
    private DynamicModelClusterSupport clusterSupport;
    @Autowired
    private LockManager lockManager;

    /**
     * Applies the model on this node the way the Admin UI does: calculate the migrations for the new
     * content, then apply and save it on top of the currently active settings record.
     *
     * @param modelYaml dynamic model definition
     * @return id of the settings record this apply created
     */
    public UUID applyModel(String modelYaml) {
        return authenticator.withSystem(() -> {
            DynamicModelSettings settings = settingsService.loadActive();
            settings.setContent(modelYaml);
            UUID settingsId = applyOnSettings(settings, modelYaml);
            log.info("Applied dynamic model, settings record {}", settingsId);
            return settingsId;
        });
    }

    /**
     * Applies the model on top of a settings record loaded by id. Used to apply a record that another
     * node has meanwhile replaced, which the base record check has to refuse.
     *
     * @param settingsId id of the settings record to start from
     * @param modelYaml  dynamic model definition
     * @return id of the settings record this apply created
     */
    public UUID applyModelOnSettings(UUID settingsId, String modelYaml) {
        return authenticator.withSystem(() -> {
            DynamicModelSettings settings = dataManager.load(DynamicModelSettings.class).id(settingsId).one();
            settings.setContent(modelYaml);
            return applyOnSettings(settings, modelYaml);
        });
    }

    /**
     * Returns the id of the currently active settings record.
     *
     * @return settings record id, or null if there is no active record
     */
    public UUID getActiveSettingsId() {
        return authenticator.withSystem(() -> dataManager.load(DynamicModelSettings.class)
                .query("e.active = true")
                .maxResults(1)
                .optional()
                .map(DynamicModelSettings::getId)
                .orElse(null));
    }

    /**
     * Returns the id of the settings record this node applied last.
     *
     * @return settings record id, or null if this node has not applied anything yet
     */
    public UUID getLastAppliedSettingsId() {
        return clusterSupport.getLastAppliedSettingsId();
    }

    /**
     * Waits until an attribute of an entity appears in this node's metadata.
     *
     * @param entityName    Jmix entity name
     * @param attributeName attribute name
     * @throws AssertionError if the attribute has not appeared within {@link #CATCH_UP_TIMEOUT_SEC}
     */
    public void awaitAttribute(String entityName, String attributeName) {
        await(() -> {
            MetaClass metaClass = metadata.findClass(entityName);
            return metaClass != null && metaClass.findProperty(attributeName) != null;
        }, String.format("attribute '%s' of entity '%s' did not appear in the metadata", attributeName, entityName));
        log.info("Attribute '{}' of entity '{}' is present in the metadata", attributeName, entityName);
    }

    /**
     * Waits until this node has applied the given settings record.
     *
     * @param settingsId id of the settings record applied on another node
     * @throws AssertionError if this node has not applied it within {@link #CATCH_UP_TIMEOUT_SEC}
     */
    public void awaitAppliedSettings(UUID settingsId) {
        await(() -> settingsId.equals(clusterSupport.getLastAppliedSettingsId()),
                String.format("this node did not apply the settings record %s; the last one it applied is %s",
                        settingsId, clusterSupport.getLastAppliedSettingsId()));
        log.info("This node has applied the settings record {}", settingsId);
    }

    /**
     * Waits until nobody holds the cluster-wide apply lock.
     * <p>
     * The admin apply path does not wait for the lock, it fails immediately. A node catching up with a
     * model applied elsewhere holds the lock while it re-applies, so a test that applies from one node
     * right after another node was told to catch up has to wait for that catch-up to let go of the lock
     * first, or it would be refused for a reason the test is not about.
     *
     * @throws AssertionError if the lock is still held after {@link #CATCH_UP_TIMEOUT_SEC}
     */
    public void awaitApplyLockFree() {
        await(() -> getApplyLockInfo() == null, "the dynamic model apply lock is still held");
    }

    /**
     * Takes the cluster-wide apply lock directly, without applying anything, so that another node's
     * apply has to find it held.
     *
     * @return the holder of the lock if it was already held, null if the lock was taken
     */
    public LockInfo takeApplyLock() {
        return authenticator.withSystem(() -> lockManager.lock(APPLY_LOCK_NAME, APPLY_LOCK_ID));
    }

    /**
     * Releases the cluster-wide apply lock taken by {@link #takeApplyLock()}.
     */
    public void releaseApplyLock() {
        authenticator.runWithSystem(() -> lockManager.unlock(APPLY_LOCK_NAME, APPLY_LOCK_ID));
    }

    /**
     * Returns the holder of the cluster-wide apply lock.
     *
     * @return lock holder, or null if the lock is not held
     */
    public LockInfo getApplyLockInfo() {
        LockInfo lockInfo = authenticator.withSystem(() -> lockManager.getLockInfo(APPLY_LOCK_NAME, APPLY_LOCK_ID));
        if (lockInfo instanceof LockNotSupported) {
            // No descriptor is registered for the apply lock, which means cluster support is off in this
            // application. Reported here so it does not show up later as a wait that never ends.
            throw new IllegalStateException("Lock descriptor '" + APPLY_LOCK_NAME + "' is not registered; "
                    + "check that jmix.dynmodel.cluster.enabled is true");
        }
        return lockInfo;
    }

    /**
     * Removes every dynamic model settings record, so the next node that starts or catches up finds no
     * active model and the next apply has no base record to be checked against.
     */
    public void removeAllSettings() {
        authenticator.runWithSystem(() -> {
            List<DynamicModelSettings> settings = dataManager.load(DynamicModelSettings.class).all().list();
            if (!settings.isEmpty()) {
                dataManager.remove(settings);
            }
            log.info("Removed {} dynamic model settings records", settings.size());
        });
    }

    /**
     * Removes every instance of a dynamic entity. Does nothing if the entity is not in the metadata,
     * which is the normal state before the model has been applied for the first time.
     *
     * @param entityName Jmix entity name of a dynamic entity
     */
    public void removeAllInstances(String entityName) {
        authenticator.runWithSystem(() -> {
            List<Object> instances = loadAllInstances(entityName);
            if (!instances.isEmpty()) {
                dataManager.remove(instances);
            }
            log.info("Removed {} instances of '{}'", instances.size(), entityName);
        });
    }

    /**
     * Loads every instance of a dynamic entity.
     *
     * @param entityName Jmix entity name of a dynamic entity
     * @return loaded instances, empty if the entity is not in this node's metadata
     */
    public List<Object> loadAllInstances(String entityName) {
        MetaClass metaClass = metadata.findClass(entityName);
        if (metaClass == null) {
            return List.of();
        }
        @SuppressWarnings("unchecked")
        Class<Object> javaClass = (Class<Object>) metaClass.getJavaClass();
        return dataManager.load(javaClass).all().list();
    }

    /**
     * Creates an instance of a dynamic entity.
     *
     * @param entityName Jmix entity name of a dynamic entity
     * @return created instance
     */
    public Object createInstance(String entityName) {
        return metadata.create(metadata.getClass(entityName));
    }

    /**
     * Saves an entity instance.
     *
     * @param instance instance to save
     * @return saved instance
     */
    public Object save(Object instance) {
        return authenticator.withSystem(() -> dataManager.save(instance));
    }

    private UUID applyOnSettings(DynamicModelSettings settings, String modelYaml) {
        DynamicModelDefinition modelDefinition = serializer.deserialize(modelYaml);
        List<DynamicModelMigration> migrations = dynamicModelManager.getMigrations(modelDefinition);
        log.info("Applying dynamic model with {} migrations: {}", migrations.size(), migrations);
        settingsService.applyAndSave(migrations, settings);
        return settings.getId();
    }

    private void await(Condition condition, String failureMessage) {
        long deadline = System.currentTimeMillis() + CATCH_UP_TIMEOUT_SEC * 1000;
        while (true) {
            if (condition.isMet()) {
                return;
            }
            if (System.currentTimeMillis() >= deadline) {
                throw new AssertionError(String.format(
                        "Waited %s seconds, but %s", CATCH_UP_TIMEOUT_SEC, failureMessage));
            }
            try {
                Thread.sleep(POLL_PERIOD_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Interrupted while waiting: " + failureMessage, e);
            }
        }
    }

    private interface Condition {
        boolean isMet();
    }
}
