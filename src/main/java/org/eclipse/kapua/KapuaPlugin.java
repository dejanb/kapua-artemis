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

import org.apache.activemq.artemis.api.core.ActiveMQException;
import org.apache.activemq.artemis.api.core.ActiveMQExceptionType;
import org.apache.activemq.artemis.api.core.Message;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.core.server.ServerSession;
import org.apache.activemq.artemis.core.server.plugin.ActiveMQServerPlugin;
import org.apache.activemq.artemis.core.transaction.Transaction;
import org.jboss.logging.Logger;

import java.util.Arrays;

public class KapuaPlugin implements ActiveMQServerPlugin {

    private static final Logger logger = Logger.getLogger(KapuaPlugin.class);

    private AuthenticationService authenticationService = new AuthenticationService();
    private DeviceConnectionService deviceConnectionService = DeviceConnectionService.getInstance();

    public static String ACCESS_TOKEN = "kapua-access-token";
    public static String DEVICE_ID = "device-id";

    /**
     *
     * Interception point when clients connect to the broker.
     * It can be used to do authentication and triggering device connectivity events
     *
     * @param session
     * @throws ActiveMQException
     */
    public void afterCreateSession(ServerSession session) throws ActiveMQException {
        String clientId = session.getRemotingConnection().getClientID();
        logger.debug("Created a session: " + session);
        try {
            if (session.getRemotingConnection().getProtocolName().equals("MQTT")) {
                String accessToken = authenticationService.login(session.getUsername(), session.getPassword(), session.getRemotingConnection().getClientID());
                logger.info("Device " + clientId + " authenticated ");
                session.addMetaData(ACCESS_TOKEN, accessToken);
                session.addMetaData(DEVICE_ID, session.getRemotingConnection().getClientID());
                deviceConnectionService.connect(clientId, session.getRemotingConnection().getRemoteAddress(), session.getRemotingConnection().getProtocolName());
            } else {
                logger.info("Skipping authentication for non-mqtt connections: " + session.getRemotingConnection().getProtocolName());
            }
        } catch (Exception e) {
            logger.warn("Error connecting a device", e);
            throw new ActiveMQException("Error connecting a device", e, ActiveMQExceptionType.DISCONNECTED);
        }
    }

    /**
     * Interception point when client disconnects from the broker.
     *
     * @param session
     * @param failed
     * @throws ActiveMQException
     */
    public void beforeCloseSession(ServerSession session, boolean failed) throws ActiveMQException {
        logger.debug("Session closing: " + session);
        deviceConnectionService.disconnect(session.getRemotingConnection().getClientID());
    }

    public void beforeSend(ServerSession session, Transaction tx, Message message, boolean direct, boolean noAutoCreateQueue) throws ActiveMQException {
        logger.info("sending " + session.getMetaData(ACCESS_TOKEN) + " " + session.getRemotingConnection().getClientID());
        if (session.getRemotingConnection().getProtocolName().equals("MQTT")) {
            String[] addressTokens = message.getAddress().split("/");
            System.out.println(Arrays.asList(addressTokens));
            if (addressTokens.length != 3) {
                throw new ActiveMQException("Address is not properly formatted: " + message.getAddress());
            }
            // telemetry
            if (addressTokens[0] == "t") {
                //TODO check tenant
                if (addressTokens[2] != session.getMetaData(DEVICE_ID)) {
                    throw new ActiveMQException("Device is not allowed to send to: " + message.getAddress());
                }
                //TODO Routing
                //message.setAddress("telemetry/DEFAULT_TENANT");
            }
            //TODO handle other flows (events, alerts, ...)

            message.setAnnotation(new SimpleString(ACCESS_TOKEN), session.getMetaData(ACCESS_TOKEN));
            message.setAnnotation(new SimpleString(DEVICE_ID), session.getMetaData(DEVICE_ID));
        } else {
            //TODO handle C2D flow (C&C, ...)
        }
    }
}
