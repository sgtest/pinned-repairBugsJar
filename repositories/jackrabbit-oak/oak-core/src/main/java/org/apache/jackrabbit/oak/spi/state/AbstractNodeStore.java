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
package org.apache.jackrabbit.oak.spi.state;

import org.apache.jackrabbit.oak.api.PropertyState;

import java.util.HashSet;
import java.util.Set;

public abstract class AbstractNodeStore implements NodeStore {

    @Override
    public void compare(NodeState before, NodeState after, NodeStateDiff diff) {
        compareProperties(before, after, diff);
        compareChildNodes(before, after, diff);
    }

    /**
     * Compares the properties of the given two node states.
     *
     * @param before node state before changes
     * @param after node state after changes
     * @param diff handler of node state differences
     */
    protected void compareProperties(
            NodeState before, NodeState after, NodeStateDiff diff) {
        Set<String> beforeProperties = new HashSet<String>();

        for (PropertyState beforeProperty : before.getProperties()) {
            String name = beforeProperty.getName();
            PropertyState afterProperty = after.getProperty(name);
            if (afterProperty == null) {
                diff.propertyDeleted(beforeProperty);
            } else {
                beforeProperties.add(name);
                if (!beforeProperty.equals(afterProperty)) {
                    diff.propertyChanged(beforeProperty, afterProperty);
                }
            }
        }

        for (PropertyState afterProperty : after.getProperties()) {
            if (!beforeProperties.contains(afterProperty.getName())) {
                diff.propertyAdded(afterProperty);
            }
        }
    }

    /**
     * Compares the child nodes of the given two node states.
     *
     * @param before node state before changes
     * @param after node state after changes
     * @param diff handler of node state differences
     */
    protected void compareChildNodes(
            NodeState before, NodeState after, NodeStateDiff diff) {
        Set<String> beforeChildNodes = new HashSet<String>();

        for (ChildNodeEntry beforeCNE : before.getChildNodeEntries()) {
            String name = beforeCNE.getName();
            NodeState beforeChild = beforeCNE.getNodeState();
            NodeState afterChild = after.getChildNode(name);
            if (afterChild == null) {
                diff.childNodeDeleted(name, beforeChild);
            } else {
                beforeChildNodes.add(name);
                if (!beforeChild.equals(afterChild)) {
                    diff.childNodeChanged(name, beforeChild, afterChild);
                }
            }
        }

        for (ChildNodeEntry afterChild : after.getChildNodeEntries()) {
            String name = afterChild.getName();
            if (!beforeChildNodes.contains(name)) {
                diff.childNodeAdded(name, afterChild.getNodeState());
            }
        }
    }

}
