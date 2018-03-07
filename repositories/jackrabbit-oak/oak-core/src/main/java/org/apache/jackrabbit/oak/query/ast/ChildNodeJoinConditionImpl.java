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

public class ChildNodeJoinConditionImpl extends JoinConditionImpl {

    private final String childSelectorName;
    private final String parentSelectorName;
    private SelectorImpl childSelector;
    private SelectorImpl parentSelector;

    public ChildNodeJoinConditionImpl(String childSelectorName, String parentSelectorName) {
        this.childSelectorName = childSelectorName;
        this.parentSelectorName = parentSelectorName;
    }

    public String getChildSelectorName() {
        return childSelectorName;
    }

    public String getParentSelectorName() {
        return parentSelectorName;
    }

    @Override
    boolean accept(AstVisitor v) {
        return v.visit(this);
    }

    @Override
    public String toString() {
        String child = getChildSelectorName();
        String parent = getParentSelectorName();
        return "ISCHILDNODE(" + child + ", " + parent + ')';
    }

    public void bindSelector(SourceImpl source) {
        parentSelector = source.getSelector(parentSelectorName);
        if (parentSelector == null) {
            throw new IllegalArgumentException("Unknown selector: " + parentSelector);
        }
        childSelector = source.getSelector(childSelectorName);
        if (childSelector == null) {
            throw new IllegalArgumentException("Unknown selector: " + childSelectorName);
        }
    }

    @Override
    public boolean evaluate() {
        String p = parentSelector.currentPath();
        String c = childSelector.currentPath();
        // the parent of the root is the root,
        // so we need to special case this
        return !PathUtils.denotesRoot(c) && PathUtils.getParentPath(c).equals(p);
    }

    @Override
    public void apply(FilterImpl f) {
        String p = parentSelector.currentPath();
        String c = childSelector.currentPath();
        if (f.getSelector() == parentSelector && c != null) {
            f.restrictPath(PathUtils.getParentPath(c), Filter.PathRestriction.EXACT);
        }
        if (f.getSelector() == childSelector && p != null) {
            f.restrictPath(p, Filter.PathRestriction.DIRECT_CHILDREN);
        }
    }

}