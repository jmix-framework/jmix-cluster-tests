package io.jmix.samples.cluster.test_support;

import io.jmix.notificationsui.event.VaadinSessionNotificationEventPublisher;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class CollectingUserSessionNotifier extends VaadinSessionNotificationEventPublisher {
    protected List<String> notifications = Collections.synchronizedList(new ArrayList<>());

    @Override
    public void notifyUserSession(String username) {
        notifications.add(username);
        super.notifyUserSession(username);
    }

    public List<String> getNotifications() {
        return notifications;
    }

    public void clear() {
        notifications.clear();
    }
}
