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

import org.jboss.logging.Logger;

import java.util.HashMap;

public class DeviceConnectionService {

    private static final Logger logger = Logger.getLogger(DeviceConnectionService.class);

    private static DeviceConnectionService instance;

    HashMap<String, String> connectedDevices = new HashMap<String, String>();

    private DeviceConnectionService() {}

    public static DeviceConnectionService getInstance() {
        if (instance == null) {
            instance = new DeviceConnectionService();
        }
        return instance;
    }


    public void connect(String clientId, String clientIp, String protocol) {
        logger.info("Device " + clientId + " connected (" + clientIp + ", " + protocol + ")");
        connectedDevices.put(clientId, clientIp);
    }

    public void disconnect(String clientId) {
        logger.info("Device " + clientId + " disconnected");
        connectedDevices.remove(clientId);
    }

    public HashMap<String, String> getConnectedDevices() {
        return connectedDevices;
    }
}
