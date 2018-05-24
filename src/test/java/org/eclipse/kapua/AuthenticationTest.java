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

import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import org.eclipse.hono.client.CredentialsClient;
import org.eclipse.hono.config.ServiceConfigProperties;
import org.eclipse.hono.service.auth.device.Device;
import org.eclipse.hono.service.auth.device.UsernamePasswordAuthProvider;
import org.eclipse.hono.util.CredentialsResult;
import org.eclipse.kapua.broker.core.BrokerJAXBContextProvider;
import org.eclipse.kapua.commons.security.KapuaSecurityUtils;
import org.eclipse.kapua.commons.util.xml.JAXBContextProvider;
import org.eclipse.kapua.commons.util.xml.XmlUtil;
import org.eclipse.kapua.hono.KapuaCredentials;
import org.eclipse.kapua.hono.KapuaCredentialsClient;
import org.eclipse.kapua.hono.KapuaCredentialsService;
import org.eclipse.kapua.hono.KapuaHonoClient;
import org.eclipse.kapua.locator.KapuaLocator;
import org.eclipse.kapua.qa.steps.DBHelper;
import org.eclipse.kapua.service.account.Account;
import org.eclipse.kapua.service.account.AccountService;
import org.eclipse.kapua.service.authentication.CredentialsFactory;
import org.eclipse.kapua.service.authentication.LoginCredentials;
import org.eclipse.kapua.service.authentication.credential.CredentialListResult;
import org.eclipse.kapua.service.authentication.credential.CredentialService;
import org.eclipse.kapua.service.authentication.token.AccessToken;
import org.eclipse.kapua.service.user.User;
import org.eclipse.kapua.service.user.UserService;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.net.HttpURLConnection;

import static org.junit.Assert.*;

public class AuthenticationTest {

    private static final DBHelper database = new DBHelper();

    @BeforeClass
    public static void before() throws Exception {
        database.setup();
    }

    @AfterClass
    public static void after() throws Exception {
        database.deleteAll();
    }

    @Test
    public void testHonoAuthenticationProvider() {
        UsernamePasswordAuthProvider authProvider = new UsernamePasswordAuthProvider(new KapuaHonoClient(), new ServiceConfigProperties());
        final Future<Device> result = Future.future();
        authProvider.authenticate(KapuaCredentials.create("kapua-broker@kapua-sys", "kapua-password", true), result.completer());
        result.map(authenticatedDevice -> {
            assertNotNull(authenticatedDevice);
            assertEquals("kapua-broker", authenticatedDevice.getDeviceId());
            return null;
        }).otherwise(t -> {
            fail("Failed to authenticate: " + t.getMessage());
            return null;
        });
    }

    @Test
    public void testHonoCredentialsClient() {
        CredentialsClient credentialsClient = new KapuaCredentialsClient("kapua-sys");
        credentialsClient.get("password", "kapua-broker")
                .map(result -> {
                    assertNotNull(result);
                    assertEquals("kapua-broker", result.getDeviceId());
                    assertEquals(1, result.getSecrets().size());
                    return result;
                });
    }

    @Test
    public void testHonoCredentialsService() {
        KapuaCredentialsService credentialsService = new KapuaCredentialsService();
        credentialsService.setConfig(null);

        final Future<CredentialsResult<JsonObject>> result = Future.future();

        credentialsService.get("kapua-sys", "password", "kapua-broker", new JsonObject(), result.completer());
        result.map(response -> {
            switch(response.getStatus()) {
                case HttpURLConnection.HTTP_OK:
                    assertEquals("kapua-broker", response.getPayload().getString("device-id"));
                    return response.getPayload();
                default:
                    fail("Failed with response " + response.getStatus());
                    return null;
            }
        });
    }

    @Test
    public void testKapuaServices() throws KapuaException {
        KapuaLocator locator = KapuaLocator.getInstance();
        JAXBContextProvider brokerProvider = new BrokerJAXBContextProvider();
        XmlUtil.setContextProvider(brokerProvider);


        UserService userService = locator.getService(UserService.class);
        CredentialService credentialService = locator.getService(CredentialService.class);
        User user = KapuaSecurityUtils.doPrivileged(() -> userService.findByName("kapua-broker"));

        assertNotNull(user);


        AccountService accountService = locator.getService(AccountService.class);
        Account account = KapuaSecurityUtils.doPrivileged(() -> accountService.find(user.getScopeId()));

        assertNotNull(account);

        CredentialListResult credentials = KapuaSecurityUtils.doPrivileged(() -> credentialService.findByUserId(user.getScopeId(), user.getId()));
        assertNotNull(credentials.getFirstItem());
        assertNotNull(credentials.getFirstItem().getCredentialKey());
    }

    @Test
    public void testKapuaAAuthentication() {
        KapuaLocator locator = KapuaLocator.getInstance();
        JAXBContextProvider brokerProvider = new BrokerJAXBContextProvider();
        XmlUtil.setContextProvider(brokerProvider);

        org.eclipse.kapua.service.authentication.AuthenticationService authenticationService = locator.getService(org.eclipse.kapua.service.authentication.AuthenticationService.class);
        CredentialsFactory credentialsFactory = locator.getFactory(CredentialsFactory.class);
        LoginCredentials credentials = credentialsFactory.newUsernamePasswordCredentials("kapua-broker", "kapua-password");
        try {
            AccessToken accessToken = authenticationService.login(credentials);
            assertNotNull(accessToken);
        } catch (KapuaException ke) {
           fail("Failed to authenticate " + ke.getMessage());
        }
    }

}
