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
import org.apache.activemq.artemis.core.postoffice.QueueBinding;
import org.apache.activemq.artemis.core.server.ServerConsumer;
import org.apache.activemq.artemis.core.server.ServerSession;
import org.apache.activemq.artemis.core.server.plugin.ActiveMQServerPlugin;
import org.apache.activemq.artemis.core.transaction.Transaction;
import org.apache.activemq.artemis.spi.core.security.jaas.PropertiesLoader;
import org.apache.activemq.artemis.spi.core.security.jaas.RolePrincipal;
import org.apache.activemq.artemis.spi.core.security.jaas.UserPrincipal;
import org.apache.activemq.artemis.utils.HashProcessor;
import org.apache.activemq.artemis.utils.PasswordMaskingUtil;
import org.jboss.logging.Logger;
import sun.java2d.pipe.SpanShapeRenderer;

import javax.security.auth.login.FailedLoginException;
import java.security.Principal;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

public class KapuaPlugin implements ActiveMQServerPlugin {

    private static final Logger logger = Logger.getLogger(KapuaPlugin.class);

    private AuthenticationService authenticationService = new AuthenticationService();
    private DeviceConnectionService deviceConnectionService = DeviceConnectionService.getInstance();

    public static String ACCESS_TOKEN = "kapua-access-token";
    public static String DEVICE_ID = "device-id";

    private Properties users = new Properties();
    private Map<String, Set<String>> roles = new HashMap<String, Set<String>>();

    //TODO concurrency?
    private Map<String, ServerSession> sessions = new HashMap<String, ServerSession>();

    public KapuaPlugin() {
        //TODO load from the file
        users.put("default-tenant", "admin");
    }

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
                // cloud authentication
                String user = session.getUsername();
                String password = users.getProperty(user);

                if (password == null) {
                    throw new FailedLoginException("Cloud user does not exist: " + user);
                }

                HashProcessor hashProcessor = PasswordMaskingUtil.getHashProcessor(password);

                if (!hashProcessor.compare(session.getPassword().toCharArray(), password)) {
                    throw new FailedLoginException("Password does not match for cloud user: " + user);
                }
            }
            sessions.put(session.getName(), session);
        } catch (Exception e) {
            logger.warn("Error creating a broker session", e);
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

    public void afterCreateConsumer(ServerConsumer consumer) throws ActiveMQException {
        ServerSession session = sessions.get(consumer.getSessionName());
        if (session == null) {
            throw new ActiveMQException("wrong session");
        }

        if (session.getRemotingConnection().getProtocolName().equals("MQTT")) {
            //TODO handle devices
        } else {
            String[] addressTokens = consumer.getQueueAddress().toString().split("/");
            if (addressTokens.length < 2) {
                throw new ActiveMQException("Address is not properly formatted: " + consumer.getQueueAddress());
            }
            if (!(addressTokens[0].equals("telemetry") || addressTokens[0].equals("t")) || !addressTokens[1].equals(session.getUsername())) {
                throw new ActiveMQException("Tenant " + session.getUsername() + " not allowed to consume from " + consumer.getQueueAddress());
            }
        }

    }
}
