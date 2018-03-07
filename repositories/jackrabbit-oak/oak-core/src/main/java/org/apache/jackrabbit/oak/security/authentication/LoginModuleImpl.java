/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.jackrabbit.oak.security.authentication;

import org.apache.jackrabbit.oak.spi.security.principal.PrincipalProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.Credentials;
import javax.jcr.GuestCredentials;
import javax.jcr.SimpleCredentials;
import javax.security.auth.Subject;
import javax.security.auth.callback.Callback;
import javax.security.auth.callback.CallbackHandler;
import javax.security.auth.callback.UnsupportedCallbackException;
import javax.security.auth.login.LoginException;
import javax.security.auth.spi.LoginModule;
import java.io.IOException;
import java.security.Principal;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * LoginModuleImpl...
 */
public class LoginModuleImpl implements LoginModule {

    /**
     * logger instance
     */
    private static final Logger log = LoggerFactory.getLogger(LoginModuleImpl.class);

    private Subject subject;
    private CallbackHandler callbackHandler;

    private Set<Credentials> credentials;
    private String principalName;

    //--------------------------------------------------------< LoginModule >---
    @Override
    public void initialize(Subject subject, CallbackHandler callbackHandler, Map<String, ?> sharedState, Map<String, ?> options) {
        // TODO

        this.subject = subject;
        this.callbackHandler = callbackHandler;
    }

    @Override
    public boolean login() throws LoginException {
        // TODO
        credentials = retrieveCredentials();
        if (supportsCredentials()) {
            principalName = retrievePrincipalName();
            return true;
        } else {
            return false;
        }
    }

    @Override
    public boolean commit() throws LoginException {
        // TODO

        subject.getPrincipals().addAll(retrievePrincipals(principalName));
        subject.getPublicCredentials().addAll(credentials);
        return true;
    }

    @Override
    public boolean abort() throws LoginException {
        // TODO
        return true;
    }

    @Override
    public boolean logout() throws LoginException {
        // TODO
        return true;
    }

    //--------------------------------------------------------------------------

    private Set<Credentials> retrieveCredentials() {
        Set<Credentials> credentials = new HashSet<Credentials>();

        try {
            CredentialsCallback callback = new CredentialsCallback();
            callbackHandler.handle(new Callback[]{callback});
            Credentials creds = callback.getCredentials();
            if (creds != null) {
                credentials.add(creds);
            }
        } catch (UnsupportedCallbackException e) {
            log.warn(e.getMessage());
        } catch (IOException e) {
            log.error(e.getMessage());
        }

        if (credentials.isEmpty()) {
            credentials.addAll(subject.getPublicCredentials(SimpleCredentials.class));
            credentials.addAll(subject.getPublicCredentials(GuestCredentials.class));
        }
        return credentials;
    }

    private boolean supportsCredentials() {
        for (Credentials creds : credentials) {
            if (creds instanceof SimpleCredentials) {
                return true;
            } else if (creds instanceof GuestCredentials) {
                return true;
            }
        }
        return false;
    }

    private String retrievePrincipalName() {
        for (Credentials creds : credentials) {
            if (creds instanceof SimpleCredentials) {
                return ((SimpleCredentials) creds).getUserID();
            } else if (creds instanceof GuestCredentials) {
                return "anonymous";
            }
        }

        return null;
    }

    private Set<Principal> retrievePrincipals(String principalName) {
        Set<Principal> principals = new HashSet<Principal>();
        try {
            PrincipalProviderCallback principalCallBack = new PrincipalProviderCallback();
            callbackHandler.handle(new Callback[] {principalCallBack});
            PrincipalProvider pp = principalCallBack.getPrincipalProvider();
            if (pp != null) {
                Principal p = pp.getPrincipal(principalName);
                if (p != null) {
                    principals.add(p);
                    principals.addAll(pp.getGroupMembership(p));
                }
            }

        } catch (IOException e) {
            log.warn(e.getMessage());
        } catch (UnsupportedCallbackException e) {
            log.warn(e.getMessage());
        }

        return principals;
    }
}