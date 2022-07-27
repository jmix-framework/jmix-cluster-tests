package io.jmix.samples.cluster.test_system.model.annotations;

import java.lang.annotation.*;

@Target({ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Inherited
public @interface TestStep {
    int order();//todo?(Integer, def val?)

    String[] nodes() default {};//todo parameter for subsequent or parallel running

    String description() default "";
}
