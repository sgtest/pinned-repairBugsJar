/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.jackrabbit.oak.spi.commit;

import org.apache.jackrabbit.oak.api.CommitFailedException;
import org.apache.jackrabbit.oak.api.PropertyState;
import org.apache.jackrabbit.oak.spi.state.NodeState;

/**
 * Content change validator. An instance of this interface is used to
 * validate changes against a specific {@link NodeState}.
 */
public interface Validator {

    void propertyAdded(PropertyState after)
            throws CommitFailedException;

    void propertyChanged(PropertyState before, PropertyState after)
            throws CommitFailedException;

    void propertyDeleted(PropertyState before)
            throws CommitFailedException;

    Validator childNodeAdded(String name, NodeState after)
            throws CommitFailedException;

    Validator childNodeChanged(
            String name, NodeState before, NodeState after)
            throws CommitFailedException;

    Validator childNodeDeleted(String name, NodeState before)
            throws CommitFailedException;

}
