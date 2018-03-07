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
package org.apache.jackrabbit.oak.jcr.security.user;

import org.apache.jackrabbit.api.security.principal.ItemBasedPrincipal;
import org.apache.jackrabbit.api.security.principal.JackrabbitPrincipal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.Item;
import javax.jcr.RepositoryException;
import java.security.Principal;

/**
 * ItemBasedPrincipalImpl...
 */
class ItemBasedPrincipalImpl implements ItemBasedPrincipal {

    /**
     * logger instance
     */
    private static final Logger log = LoggerFactory.getLogger(ItemBasedPrincipalImpl.class);

    private final String principalName;
    private final Item item;

    ItemBasedPrincipalImpl(String principalName, Item item) {
        this.principalName = principalName;
        this.item = item;
    }

    //-------------------------------------------------< ItemBasedPrincipal >---
    @Override
    public String getPath() throws RepositoryException {
        return item.getPath();
    }

    //----------------------------------------------------------< Principal >---
    @Override
    public String getName() {
        return principalName;
    }

    //-------------------------------------------------------------< Object >---
    /**
     * Two principals are equal, if their names are.
     * @see Object#equals(Object)
     */
    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj instanceof JackrabbitPrincipal) {
            return principalName.equals(((Principal) obj).getName());
        }
        return false;
    }

    /**
     * @return the hash code of the principals name.
     * @see Object#hashCode()
     */
    @Override
    public int hashCode() {
        return principalName.hashCode();
    }

    /**
     * @see Object#toString()
     */
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(getClass().getName()).append(':').append(principalName);
        return sb.toString();
    }
}