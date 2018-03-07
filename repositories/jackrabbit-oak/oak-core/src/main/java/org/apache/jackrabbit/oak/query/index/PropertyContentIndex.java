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
package org.apache.jackrabbit.oak.query.index;

import java.util.Iterator;
import org.apache.jackrabbit.mk.api.MicroKernel;
import org.apache.jackrabbit.mk.index.PropertyIndex;
import org.apache.jackrabbit.mk.simple.NodeImpl;
import org.apache.jackrabbit.oak.api.CoreValue;
import org.apache.jackrabbit.oak.spi.Cursor;
import org.apache.jackrabbit.oak.spi.Filter;
import org.apache.jackrabbit.oak.spi.QueryIndex;

/**
 * An index that stores the index data in a {@code MicroKernel}.
 */
public class PropertyContentIndex implements QueryIndex {

    private final MicroKernel mk;
    private final PropertyIndex index;

    public PropertyContentIndex(MicroKernel mk, PropertyIndex index) {
        this.mk = mk;
        this.index = index;
    }

    @Override
    public double getCost(Filter filter) {
        String propertyName = index.getPropertyName();
        Filter.PropertyRestriction restriction = filter.getPropertyRestriction(propertyName);
        if (restriction == null) {
            return Double.MAX_VALUE;
        }
        if (restriction.first != restriction.last) {
            // only support equality matches (for now)
            return Double.MAX_VALUE;
        }
        boolean unique = index.isUnique();
        return unique ? 2 : 20;
    }

    @Override
    public String getPlan(Filter filter) {
        String propertyName = index.getPropertyName();
        Filter.PropertyRestriction restriction = filter.getPropertyRestriction(propertyName);
        return "propertyIndex \"" + restriction.propertyName + " " + restriction.toString() + '"';
    }

    @Override
    public Cursor query(Filter filter, String revisionId) {
        String propertyName = index.getPropertyName();
        Filter.PropertyRestriction restriction = filter.getPropertyRestriction(propertyName);
        if (restriction == null) {
            throw new IllegalArgumentException("No restriction for " + propertyName);
        }
        CoreValue first = restriction.first;
        String f = first == null ? null : first.toString();
        Iterator<String> it = index.getPaths(f, revisionId);
        return new ContentCursor(mk, revisionId, it);
    }


    @Override
    public String getIndexName() {
        return index.getName();
    }

    /**
     * The cursor to for this index.
     */
    static class ContentCursor implements Cursor {

        private final MicroKernel mk;
        private final String revisionId;
        private final Iterator<String> it;

        private String currentPath;
        private NodeImpl currentNode;

        public ContentCursor(MicroKernel mk, String revisionId, Iterator<String> it) {
            this.mk = mk;
            this.revisionId = revisionId;
            this.it = it;
        }

        @Override
        public NodeImpl currentNode() {
            // TODO same code as in TraversingCursor
            if (currentNode == null) {
                if (currentPath == null) {
                    return null;
                }
                currentNode = NodeImpl.parse(mk.getNodes(currentPath, revisionId));
                currentNode.setPath(currentPath);
            }
            return currentNode;
        }

        @Override
        public String currentPath() {
            return currentPath;
        }

        @Override
        public boolean next() {
            if (it.hasNext()) {
                currentPath = it.next();
                return true;
            }
            return false;
        }
    }

}
