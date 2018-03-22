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

import org.apache.activemq.artemis.spi.core.security.jaas.PropertiesLoginModule;
import org.jboss.logging.Logger;

import javax.security.auth.Subject;
import javax.security.auth.callback.CallbackHandler;
import javax.security.auth.login.LoginException;
import javax.security.auth.spi.LoginModule;
import java.util.Map;

public class KapuaLoginModule extends PropertiesLoginModule {

    private static final Logger logger = Logger.getLogger(KapuaLoginModule.class);

    public void initialize(Subject subject, CallbackHandler callbackHandler, Map<String, ?> sharedState, Map<String, ?> options) {
        super.initialize(subject, callbackHandler, sharedState, options);
    }

    public boolean login() throws LoginException {
        return super.login();
    }

    public boolean commit() throws LoginException {
        return super.commit();
    }

    public boolean abort() throws LoginException {
        return super.abort();
    }

    public boolean logout() throws LoginException {
        return super.logout();
    }
}
