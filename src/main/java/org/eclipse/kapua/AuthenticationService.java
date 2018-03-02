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

import java.util.UUID;

public class AuthenticationService {

    public String login(String username, String password, String clientId) throws Exception {

        if (clientId.startsWith("kapua-") && username.startsWith("kapua-")) {
            if (password.startsWith("kapua-")) {
                return UUID.randomUUID().toString();
            } else {
                throw new Exception("Wrong password");
            }
        } else {
            throw new Exception("Unknown device");
        }
    }

}
