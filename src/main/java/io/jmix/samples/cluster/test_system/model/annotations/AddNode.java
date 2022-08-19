package io.jmix.samples.cluster.test_system.model.annotations;

import java.lang.annotation.*;

@Target({ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Inherited
@Repeatable(AddNode.List.class)
//todo hardcode operation type?
public @interface AddNode {//todo

    int order();

    String[] names();

    @Target({ElementType.METHOD})
    @Retention(RetentionPolicy.RUNTIME)
    @Inherited
    @interface List {
        AddNode[] value();
    }
}
