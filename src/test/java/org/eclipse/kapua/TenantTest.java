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

import org.eclipse.hono.client.TenantClient;
import org.eclipse.kapua.hono.KapuaTenantClient;
import org.junit.Test;

import static org.junit.Assert.*;

public class TenantTest {

    @Test
    public void testHonoRegistrationClient() throws Throwable {

        TenantClient tenantClient = new KapuaTenantClient();
        tenantClient.get("test-tenant").map(
                result -> {
                    assertEquals("test-tenant", result.getTenantId());
                    assertTrue(result.isEnabled());
                    return result;
                }
        ).otherwise(
                error -> {
                    fail(error.getMessage());
                    return null;
                }
        );

    }
}
