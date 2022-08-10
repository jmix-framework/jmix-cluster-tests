package io.jmix.samples.cluster.test_system.model.annotations;

import java.lang.annotation.*;

@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Inherited
public @interface ClusterTest {
    boolean eagerInitPods() default true;

    String description() default "";//todo try AliasFor value
    //todo predefined nodes? or do it and other things in class itself?

    //todo clearStart
    //todo loggers config/filter
}
