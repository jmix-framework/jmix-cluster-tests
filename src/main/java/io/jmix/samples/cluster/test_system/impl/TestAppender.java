package io.jmix.samples.cluster.test_system.impl;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Stream;

public class TestAppender extends AppenderBase<ILoggingEvent> {

    private List<String> messages = Collections.synchronizedList(new ArrayList<>());


    public List<String> getMessages() {
        return messages;
    }

    public void clear() {
        messages.clear();
    }

    public Stream<String> filterMessages(Predicate<String> predicate) {
        return messages.stream().filter(predicate);
    }

    @Override
    protected void append(ILoggingEvent eventObject) {
        messages.add(String.format("%s [%s] %s - %s",
                new SimpleDateFormat("HH:mm:ss.SSS").format(new Date(eventObject.getTimeStamp())),//todo thread safety/optimisation
                eventObject.getThreadName(),
                eventObject.getLoggerName(),
                eventObject));
    }

    public void setMessages(List<String> messages) {
        this.messages = messages;
    }
}