/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.jackrabbit.oak.spi.security.authentication.external.impl;

import org.apache.felix.jaas.LoginModuleFactory;
import org.apache.felix.jaas.boot.ProxyLoginModule;
import org.apache.jackrabbit.api.security.user.Authorizable;
import org.apache.jackrabbit.api.security.user.UserManager;
import org.apache.jackrabbit.oak.Oak;
import org.apache.jackrabbit.oak.api.ContentSession;
import org.apache.jackrabbit.oak.spi.security.authentication.external.ExternalIdentityProviderManager;
import org.apache.jackrabbit.oak.spi.security.authentication.external.ExternalLoginTestBase;
import org.apache.jackrabbit.oak.spi.security.authentication.external.ExternalUser;
import org.apache.jackrabbit.oak.spi.security.authentication.external.SyncManager;
import org.apache.jackrabbit.oak.spi.security.authentication.external.impl.jmx.SynchronizationMBean;
import org.jetbrains.annotations.NotNull;
import org.junit.Test;

import javax.jcr.Repository;
import javax.jcr.SimpleCredentials;
import javax.security.auth.login.AppConfigurationEntry;
import javax.security.auth.login.Configuration;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;

public class ExternalLoginModuleFactoryTest extends ExternalLoginTestBase {

    @Override
    protected Oak withEditors(Oak oak) {
        super.withEditors(oak);
        //Just grab the whiteboard but do not register any manager here
        //This would ensure that LoginModule would only work if the required managers
        //are preset
        whiteboard = oak.getWhiteboard();
        return oak;
    }

    protected Configuration getConfiguration() {
        return new Configuration() {
            @Override
            public AppConfigurationEntry[] getAppConfigurationEntry(String s) {
                AppConfigurationEntry entry = new AppConfigurationEntry(
                        //Use ProxyLoginModule so that factory mode can be used
                        ProxyLoginModule.class.getName(),
                        AppConfigurationEntry.LoginModuleControlFlag.REQUIRED,
                        options);
                return new AppConfigurationEntry[]{entry};
            }
        };
    }

    @Test
    public void testSyncCreateUser() throws Exception {
        setUpJaasFactoryWithInjection();
        UserManager userManager = getUserManager(root);
        ContentSession cs = null;
        try {
            assertNull(userManager.getAuthorizable(USER_ID));

            cs = login(new SimpleCredentials(USER_ID, new char[0]));

            root.refresh();

            Authorizable a = userManager.getAuthorizable(USER_ID);
            assertNotNull(a);
            ExternalUser user = idp.getUser(USER_ID);
            assertNotNull(user);
            for (String prop : user.getProperties().keySet()) {
                assertTrue(a.hasProperty(prop));
            }
            assertEquals(TEST_CONSTANT_PROPERTY_VALUE, a.getProperty(TEST_CONSTANT_PROPERTY_NAME)[0].getString());
        } finally {
            if (cs != null) {
                cs.close();
            }
            options.clear();
        }
    }

    /**
     * Prepares the OSGi part with required services injected and configures
     * the factory in JAAS options which then delegates to ExternalLoginModuleFactory
     */
    private void setUpJaasFactoryWithInjection() {
        context.registerService(Repository.class, mock(Repository.class));
        context.registerService(SyncManager.class, new SyncManagerImpl(whiteboard));
        context.registerService(ExternalIdentityProviderManager.class, new ExternalIDPManagerImpl(whiteboard));

        final LoginModuleFactory lmf = context.registerInjectActivateService(ExternalLoginModuleFactory.class);
        options.put(ProxyLoginModule.PROP_LOGIN_MODULE_FACTORY, (ProxyLoginModule.BootLoginModuleFactory) lmf::createLoginModule);
    }

    @Test
    public void testMissingBundleContext() throws Exception {
        ExternalLoginModuleFactory factory = new ExternalLoginModuleFactory(null, null, null);

        factory.bindContentRepository(getContentRepository());
        factory.bindSecurityProvider(getSecurityProvider());

        assertNull(getMBeanRegistration());

        factory.unbindContentRepository(getContentRepository());
        factory.unbindSecurityProvider(getSecurityProvider());

        assertNull(getMBeanRegistration());
    }

    @Test
    public void testBindNullContentRepository() throws Exception {
        context.registerService(SyncManager.class, mock(SyncManager.class));
        context.registerService(ExternalIdentityProviderManager.class, mock(ExternalIdentityProviderManager.class));

        ExternalLoginModuleFactory factory = new ExternalLoginModuleFactory(null, null, null);
        context.registerInjectActivateService(factory);

        assertNull(getMBeanRegistration());

        factory.bindContentRepository(null);
        factory.bindSecurityProvider(getSecurityProvider());

        assertNull(getMBeanRegistration());

        factory.unbindContentRepository(null);
        factory.unbindSecurityProvider(getSecurityProvider());

        assertNull(getMBeanRegistration());
    }

    @Test
    public void testBindNullSecurityProvider() throws Exception {
        context.registerService(SyncManager.class, mock(SyncManager.class));
        context.registerService(ExternalIdentityProviderManager.class, mock(ExternalIdentityProviderManager.class));

        ExternalLoginModuleFactory factory = new ExternalLoginModuleFactory(null, null, null);
        context.registerInjectActivateService(factory);

        assertNull(getMBeanRegistration());

        factory.bindContentRepository(getContentRepository());
        factory.bindSecurityProvider(null);

        assertNull(getMBeanRegistration());

        factory.unbindContentRepository(getContentRepository());
        factory.unbindSecurityProvider(getSecurityProvider());

        assertNull(getMBeanRegistration());
    }

    @Test
    public void testMbeanRegistration() throws Exception {
        final ExternalLoginModuleFactory factory = registerAndGetExternalLoginModuleFactoryWithMocks();

        assertNull(getMBeanRegistration());

        factory.bindSecurityProvider(getSecurityProvider());
        factory.bindContentRepository(getContentRepository());

        SynchronizationMBean mbeanregistration = getMBeanRegistration();
        assertNotNull(mbeanregistration);

        factory.unbindContentRepository(getContentRepository());
        assertNull(getMBeanRegistration());

        factory.unbindSecurityProvider(getSecurityProvider());
        assertNull(getMBeanRegistration());
    }

    @NotNull
    private ExternalLoginModuleFactory registerAndGetExternalLoginModuleFactoryWithMocks() {
        context.registerService(SyncManager.class, mock(SyncManager.class));
        context.registerService(ExternalIdentityProviderManager.class, mock(ExternalIdentityProviderManager.class));

        ExternalLoginModuleFactory factory = context.registerInjectActivateService(ExternalLoginModuleFactory.class);
        return factory;
    }

    @Test
    public void testMBeanRegistrationAlreadyPresent() throws Exception {
        final ExternalLoginModuleFactory factory = registerAndGetExternalLoginModuleFactoryWithMocks();

        assertNull(getMBeanRegistration());

        factory.bindSecurityProvider(getSecurityProvider());
        factory.bindContentRepository(getContentRepository());

        SynchronizationMBean mbeanregistration = getMBeanRegistration();
        assertNotNull(mbeanregistration);

        factory.bindContentRepository(getContentRepository());
        assertSame(mbeanregistration, getMBeanRegistration());
    }

    private SynchronizationMBean getMBeanRegistration() throws Exception {
        return context.getService(SynchronizationMBean.class);
    }
}
