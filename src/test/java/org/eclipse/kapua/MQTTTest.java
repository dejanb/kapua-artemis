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
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageListener;
import javax.jms.Session;
import javax.jms.TextMessage;
import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.util.AbstractMap;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;

public class MQTTTest {

    private static final Logger logger = Logger.getLogger(MQTTTest.class);

    private ActiveMQServer broker;


    @Before
    public void before() throws Exception {
        broker = ActiveMQServers.newActiveMQServer("file:src/etc/broker.xml", ManagementFactory.getPlatformMBeanServer(), new ActiveMQJAASSecurityManager("kapua"));
        //TODO figure out how to set proper JAAS plugin
        broker.getConfiguration().setSecurityEnabled(false);
        broker.start();
        Thread.sleep(3000);
    }

    @After
    public void after() throws Exception {
        broker.stop();
    }

    @Test
    public void testConnect() throws Exception {
        DefaultMqttListener listener = new DefaultMqttListener();
        MqttClient client = connect(listener);

        Thread.sleep(1000);

        logger.info(DeviceConnectionService.getInstance().getConnectedDevices().keySet());

        Assert.assertNotNull(DeviceConnectionService.getInstance().getConnectedDevices().get("kapua-device1"));

        client.disconnect();

        Thread.sleep(1000);

        logger.info("Disconnected");

        Assert.assertNull(DeviceConnectionService.getInstance().getConnectedDevices().get("kapua-device1"));
    }

    //test failed

    //test connect/distrupt

    //test send message mqtt->jms (header)
    @Test
    public void testSendMqttToMqtt() throws Exception {

        DefaultMqttListener listener = new DefaultMqttListener();
        MqttClient client = connect(listener);

        client.subscribe("telemetry/test");

        client.publish("telemetry/test", "Test".getBytes(), 0, false);

        Thread.sleep(1000);
        Assert.assertTrue(listener.received.get() == 1);

    }

    @Test
    public void testSendMqttToJMS() throws Exception {

        JmsConnectionFactory cf = new JmsConnectionFactory("amqp://localhost:5672");
        Connection connection = cf.createConnection("admin", "admin");
        connection.start();
        Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
        MessageConsumer consumer = session.createConsumer(session.createTopic("telemetry.test"));
        DefaultJmsListener listener = new DefaultJmsListener();
        consumer.setMessageListener(listener);

        MqttClient client = connect(null);
        client.publish("telemetry/test", "Test".getBytes(), 0, false);

        Thread.sleep(1000);

        Assert.assertTrue(listener.received.get() == 1);
        logger.info("!!!!!ACCESS TOKEN " + listener.messageQ.take().getValue().getStringProperty("kapua_access_token"));

    }

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
