package io.jmix.samples.cluster.tests;

import io.jmix.notifications.NotificationManager;
import io.jmix.notifications.channel.impl.InAppNotificationChannel;
import io.jmix.samples.cluster.test_support.CollectingUserSessionNotifier;
import io.jmix.samples.cluster.test_system.model.annotations.ClusterTest;
import io.jmix.samples.cluster.test_system.model.annotations.Step;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Component("cluster_InAppNotificationsTest")
@ClusterTest(description = "To make sure that notification created on one node will be displayed on ui for another node")
public class InAppNotificationsTest {

    private static final Logger log = LoggerFactory.getLogger(InAppNotificationsTest.class);

    @Autowired
    private InAppNotificationChannel inAppNotificationChannel;
    @Autowired
    private CollectingUserSessionNotifier userSessionNotifier;
    @Autowired
    private NotificationManager notificationManager;

    @Step(order = 0)
    public void prepareNotifier() {
        userSessionNotifier.clear();
    }

    @Step(order = 1, nodes = "1")
    public void stepOne() {
        userSessionNotifier.clear();
        log.info("Sending notification on node 1");
        notificationManager.createNotification()
                .withSubject("T1")
                .withRecipientUsernames("admin")
                .toChannels(inAppNotificationChannel)
                .withBody("test notification")
                .send();

        //there are two notifications: first: for current node, second: because of event passed back to the same node
        assertThat(userSessionNotifier.getNotifications()).hasSameElementsAs(Arrays.asList("admin", "admin"));
    }

    @Step(order = 2, nodes = "2")
    public void stepTwo() {
        log.info("Node 2 must be notified");
        assertThat(userSessionNotifier.getNotifications()).hasSameElementsAs(List.of("admin"));
    }

}
