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
package org.apache.jackrabbit.oak.api;

import java.io.Closeable;

/**
 * Authentication session for accessing (TODO: a workspace inside) a content
 * repository.
 * <p>
 * - retrieving information from persistent layer (MK) that are accessible to
 *   a given JCR session
 *
 * - validate information being written back to the persistent layer. this includes
 *   permission evaluation, node type and name constraints etc.
 *
 * - Provide access to authentication and authorization related information
 *
 * - The implementation of this and all related interfaces are intended to only
 *   hold the state of the persistent layer at a given revision without any
 *   session-related state modifications.
 * <p>
 * TODO: describe how this interface is intended to handle validation:
 * nt, names, ac, constraints...
 * <p>
 * This interface is thread-safe.
 */
public interface ContentSession extends Closeable {

    /**
     * This methods provides access to information related to authentication
     * and authorization of this content session. Multiple calls to this method
     * may return different instances which are guaranteed to be equal wrt.
     * to {@link Object#equals(Object)}.
     *
     * @return  immutable {@link AuthInfo} instance
     */
    AuthInfo getAuthInfo();

    /**
     * The name of the workspace this {@code ContentSession} instance has
     * been created for. If no workspace name has been specified during
     * repository login this method will return the name of the default
     * workspace.
     *
     * @return name of the workspace this instance has been created for or
     * {@code null} if this content session is repository bound.
     */
    String getWorkspaceName();

    /**
     * The current root as seen by this content session. Use {@link Root#commit()} to
     * atomically apply the changes made in that subtree the underlying Microkernel.
     *
     * @return  the current root
     */
    Root getCurrentRoot();

    /**
     * Get the query engine.
     *
     * @return the query engine
     */
    QueryEngine getQueryEngine();

    /**
     * Returns the internal value factory.
     *
     * @return the internal value factory.
     */
    CoreValueFactory getCoreValueFactory();

    // TODO : add versioning operations

}