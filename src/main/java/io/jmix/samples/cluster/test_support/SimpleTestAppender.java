package io.jmix.samples.cluster.test_support;

import ch.qos.logback.classic.spi.ILoggingEvent;
import io.jmix.samples.cluster.test_system.impl.SynchronizedListAppender;

public class SimpleTestAppender extends SynchronizedListAppender {

    @Override
    protected void append(ILoggingEvent eventObject) {
        messages.add(eventObject.getMessage() == null ? "" : eventObject.getMessage());
    }
}
