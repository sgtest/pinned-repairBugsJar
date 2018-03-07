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
package org.apache.jackrabbit.oak.query.ast;

import org.apache.jackrabbit.oak.commons.PathUtils;
import org.apache.jackrabbit.oak.query.index.FilterImpl;
import org.apache.jackrabbit.oak.spi.Filter;

public class ChildNodeImpl extends ConstraintImpl {

    private final String selectorName;
    private final String parentPath;
    private SelectorImpl selector;

    public ChildNodeImpl(String selectorName, String parentPath) {
        this.selectorName = selectorName;
        this.parentPath = parentPath;
    }

    public String getSelectorName() {
        return selectorName;
    }

    public String getParentPath() {
        return parentPath;
    }

    @Override
    boolean accept(AstVisitor v) {
        return v.visit(this);
    }

    @Override
    public String toString() {
        return "ISCHILDNODE(" + selectorName + ", " + quotePath(parentPath) + ')';
    }

    public void bindSelector(SourceImpl source) {
        selector = source.getSelector(selectorName);
        if (selector == null) {
            throw new IllegalArgumentException("Unknown selector: " + selectorName);
        }
    }

    @Override
    public boolean evaluate() {
        String p = selector.currentPath();
        // the parent of the root is the root,
        // so we need to special case this
        return !PathUtils.denotesRoot(p) && PathUtils.getParentPath(p).equals(parentPath);
    }

    @Override
    public void apply(FilterImpl f) {
        if (selector == f.getSelector()) {
            f.restrictPath(parentPath, Filter.PathRestriction.DIRECT_CHILDREN);
        }
    }

}