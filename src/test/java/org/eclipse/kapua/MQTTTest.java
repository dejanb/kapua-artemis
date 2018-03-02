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
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.jboss.logging.Logger;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.lang.management.ManagementFactory;

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
        logger.info("Connecting");
        MqttClient client = new MqttClient("tcp://0.0.0.0:1883", "kapua-device1", new MemoryPersistence());
        MqttConnectOptions opts = new MqttConnectOptions();
        opts.setUserName("kapua-device1");
        opts.setPassword("kapua-password".toCharArray());
        try {
            client.connect(opts);
        } catch (Throwable t) {
            t.getCause().printStackTrace();
            t.printStackTrace();
        }

        logger.info("Connected");

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

    //test send message mqtt->mqtt

    //test send message mqtt->jms (header)
}
