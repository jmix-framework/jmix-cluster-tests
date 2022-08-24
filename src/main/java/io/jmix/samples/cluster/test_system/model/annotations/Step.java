package io.jmix.samples.cluster.test_system.model.annotations;

import java.lang.annotation.*;

@Target({ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Inherited
public @interface Step {
    int order();

    /**
     * Node names to run this step on.
     * Empty value means that step will be performed on all existing at step time nodes.
     */
    String[] nodes() default {};//todo ALL_NODES reserved name or empty==all logic is enough?

    String description() default "";
    //todo groups of tests: group represents one test sequence
    //todo enabled or ignore
}
