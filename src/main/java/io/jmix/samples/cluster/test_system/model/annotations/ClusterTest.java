package io.jmix.samples.cluster.test_system.model.annotations;

import java.lang.annotation.*;

@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Inherited
public @interface ClusterTest {
    String ALL_NODES = "_INIT_ALL_NODES";
    String NO_NODES = "_INIT_NO_NODES";

    boolean cleanStart() default false;

    String[] initNodes() default ALL_NODES;

    String description() default "";

    //todo loggers config/filter
    //todo enabled or ignore
    //todo app-properties substitution by test (with full rescaling[restarting] of pods)
}
