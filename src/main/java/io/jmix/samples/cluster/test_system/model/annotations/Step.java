package io.jmix.samples.cluster.test_system.model.annotations;

import java.lang.annotation.*;

@Target({ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Inherited
public @interface Step {
    int order();//todo?(Integer, def val?)

    /**
     * Node names to run this step on.
     * Empty value means that step will be performed on all existing at step time nodes.
     */
    String[] nodes() default {};//todo RESERVED name for ALL nodes (or using empty value for it is enough?)
    //todo parameter for subsequent or parallel running

    String description() default "";
}
