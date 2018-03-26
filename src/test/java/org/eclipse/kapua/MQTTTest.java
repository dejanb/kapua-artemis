/*******************************************************************************
 * Copyright (c) 2018 Red Hat Inc and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Dejan Bosanac - initial API and implementation
 ******************************************************************************/

package org.eclipse.kapua;

import org.apache.activemq.artemis.core.server.ActiveMQServer;
import org.apache.activemq.artemis.core.server.ActiveMQServers;
import org.apache.activemq.artemis.spi.core.security.ActiveMQJAASSecurityManager;
import org.apache.qpid.jms.JmsConnectionFactory;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.jboss.logging.Logger;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import javax.jms.Connection;
import javax.jms.Destination;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageListener;
import javax.jms.Session;
import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.util.AbstractMap;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;

public class MQTTTest {

    private static final Logger logger = Logger.getLogger(MQTTTest.class);

    private ActiveMQServer broker;

    @Before
    public void before() throws Exception {
        //TODO try to implement cloud security using security manager (after Artemis changes)
        System.setProperty("java.security.auth.login.config", "src/test/resources/login.config");
        ActiveMQJAASSecurityManager sec = new ActiveMQJAASSecurityManager("kapua");


        broker = ActiveMQServers.newActiveMQServer("file:src/etc/broker.xml", ManagementFactory.getPlatformMBeanServer(), sec);
        broker.getConfiguration().setSecurityEnabled(false);
        broker.start();
        broker.waitForActivation(3, TimeUnit.SECONDS);
    }

    @After
    public void after() throws Exception {
        broker.stop();
    }

    //TODO collocate tests
    @Test
    public void testDeviceConnect() throws Exception {
        DefaultMqttListener listener = new DefaultMqttListener();
        MqttClient client = connect(listener);

        Thread.sleep(1000);

        logger.info(DeviceConnectionService.getInstance().getConnectedDevices().keySet());

        assertNotNull(DeviceConnectionService.getInstance().getConnectedDevices().get("kapua-device1"));

        client.disconnect();

        Thread.sleep(1000);

        logger.info("Disconnected");

        assertNull(DeviceConnectionService.getInstance().getConnectedDevices().get("kapua-device1"));
    }

    @Test
    public void testDeviceDisconnect() throws Exception {
        DefaultMqttListener listener = new DefaultMqttListener();
        MqttClient client = connect(listener);

        Thread.sleep(1000);

        logger.info(DeviceConnectionService.getInstance().getConnectedDevices().keySet());

        assertNotNull(DeviceConnectionService.getInstance().getConnectedDevices().get("kapua-device1"));

        client.disconnectForcibly(0, 0, false);

        Thread.sleep(1000);

        logger.info("Disconnected");

        assertNull(DeviceConnectionService.getInstance().getConnectedDevices().get("kapua-device1"));
    }

    @Test
    public void testDeviceFailedConnect() throws Exception {
        boolean failed = false;
        MqttClient client = null;
        try {
            client = new MqttClient("tcp://0.0.0.0:1883", "unknown-device1", new MemoryPersistence());
            MqttConnectOptions opts = new MqttConnectOptions();
            opts.setUserName("unknown-device1");
            opts.setPassword("kapua-password".toCharArray());
            client.connect(opts);
        } catch (Exception e) {
            failed = true;
            logger.info("Failed login: " + e.getMessage());
        }

        if (!failed) {
            Assert.fail("Should have failed login");
        }

        assertFalse(client.isConnected());
        assertEquals(0, DeviceConnectionService.getInstance().getConnectedDevices().size());
    }

    @Test
    public void testCloudAuthentication() throws Exception {
        JmsConnectionFactory cf = new JmsConnectionFactory("amqp://localhost:5672?jms.validatePropertyNames=false");

        Connection connection1 = cf.createConnection("unknown", "unknown");
        boolean failed = false;
        try {
            connection1.start();
        } catch (Exception e) {
            failed = true;
        }

        if (!failed) {
            fail("Should have failed");
        }

        Connection connection = cf.createConnection("default-tenant", "admin");
        connection.start();
        Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);

        failed = false;
        try {
            //TODO check create address
            session.createConsumer(session.createTopic("telemetry/unknown-tenant"));
        } catch (Exception e) {
            failed = true;
        }

        if (!failed) {
            fail("Should have failed");
        }

        MessageConsumer consumer = session.createConsumer(session.createTopic("telemetry/default-tenant"));

    }

    //test send to the wrong path

    //test c&c

    @Test
    public void testTelemetry() throws Exception {

        JmsConnectionFactory cf = new JmsConnectionFactory("amqp://localhost:5672?jms.validatePropertyNames=false");
        Connection connection = cf.createConnection("default-tenant", "admin");
        connection.start();
        Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
        MessageConsumer consumer = session.createConsumer(session.createTopic("telemetry/default-tenant"));
        DefaultJmsListener listener = new DefaultJmsListener();
        consumer.setMessageListener(listener);

        MqttClient client = connect(null);
        client.publish("t/default-tenant/kapua-device1", "Test".getBytes(), 0, false);
        Thread.sleep(1000);
        assertTrue(client.isConnected());
        client.publish("random-topic", "Test".getBytes(), 0, false);
        Thread.sleep(1000);
        assertFalse(client.isConnected());
        client = connect(null);
        client.publish("t/unknown-tenant/kapua-device1", "Test".getBytes(), 0, false);
        Thread.sleep(1000);
        assertFalse(client.isConnected());


        Assert.assertTrue(listener.received.get() == 1);
        Message message = listener.messageQ.take().getValue();
        UUID token = UUID.fromString(message.getStringProperty(KapuaPlugin.ACCESS_TOKEN));
        assertNotNull(token);
        logger.info("Received access token: " + token);
        String deviceId = message.getStringProperty(KapuaPlugin.DEVICE_ID);
        assertEquals("kapua-device1", deviceId);

        //TODO test wrong address
    }


    // Helpers

    protected MqttClient connect(DefaultMqttListener listener) throws Exception {
        logger.info("Connecting");
        MqttClient client = new MqttClient("tcp://0.0.0.0:1883", "kapua-device1", new MemoryPersistence());
        MqttConnectOptions opts = new MqttConnectOptions();
        opts.setUserName("kapua-device1");
        opts.setPassword("kapua-password".toCharArray());
        client.setCallback(listener);
        client.connect(opts);

        logger.info("Connected");

        return client;
    }


    static class DefaultJmsListener implements MessageListener {
        final AtomicInteger received = new AtomicInteger();
        final BlockingQueue<AbstractMap.SimpleEntry<Destination, Message>> messageQ = new ArrayBlockingQueue<AbstractMap.SimpleEntry<Destination, Message>>(10);

        public void onMessage(Message message) {
            logger.info("Received JMS: " + message);
            received.incrementAndGet();
            try {
                messageQ.put(new AbstractMap.SimpleEntry(message.getJMSDestination(), message));
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }


    static class DefaultMqttListener implements MqttCallback {

        final AtomicInteger received = new AtomicInteger();
        final BlockingQueue<AbstractMap.SimpleEntry<String, String>> messageQ = new ArrayBlockingQueue<AbstractMap.SimpleEntry<String, String>>(10);

        public void connectionLost(Throwable cause) {
        }

        public void messageArrived(String topic, MqttMessage message) throws Exception {
            logger.info("Received MQTT: " + message);
            received.incrementAndGet();
            messageQ.put(new AbstractMap.SimpleEntry(topic, new String(message.getPayload(), StandardCharsets.UTF_8)));
        }

        public void deliveryComplete(IMqttDeliveryToken token) {
        }
    }
}
