package io.jmix.samples.cluster.test_system.model.annotations;

import java.lang.annotation.*;

@Target({ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Inherited
@Repeatable(AddNode.List.class)
public @interface AddNode {

    int order();

    String[] names();

    @Target({ElementType.METHOD})
    @Retention(RetentionPolicy.RUNTIME)
    @Inherited
    @interface List {
        AddNode[] value();
    }
}
