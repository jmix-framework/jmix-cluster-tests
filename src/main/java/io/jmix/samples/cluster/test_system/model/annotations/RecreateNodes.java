package io.jmix.samples.cluster.test_system.model.annotations;

import java.lang.annotation.*;


@Target({ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Inherited
public @interface RecreateNodes {
    int order();
}
