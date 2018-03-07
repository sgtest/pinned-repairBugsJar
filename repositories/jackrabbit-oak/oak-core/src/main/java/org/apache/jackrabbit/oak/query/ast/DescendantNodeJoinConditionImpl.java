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

public class DescendantNodeJoinConditionImpl extends JoinConditionImpl {

    private final String descendantSelectorName;
    private final String ancestorSelectorName;
    private SelectorImpl descendantSelector;
    private SelectorImpl ancestorSelector;

    public DescendantNodeJoinConditionImpl(String descendantSelectorName,
            String ancestorSelectorName) {
        this.descendantSelectorName = descendantSelectorName;
        this.ancestorSelectorName = ancestorSelectorName;
    }

    public String getDescendantSelectorName() {
        return descendantSelectorName;
    }

    public String getAncestorSelectorName() {
        return ancestorSelectorName;
    }

    @Override
    boolean accept(AstVisitor v) {
        return v.visit(this);
    }

    @Override
    public String toString() {
        String descendant = getDescendantSelectorName();
        String ancestor = getAncestorSelectorName();
        return "ISDESCENDANTNODE(" + descendant + ", " + ancestor + ')';
    }

    public void bindSelector(SourceImpl source) {
        descendantSelector = source.getSelector(descendantSelectorName);
        if (descendantSelector == null) {
            throw new IllegalArgumentException("Unknown selector: " + descendantSelectorName);
        }
        ancestorSelector = source.getSelector(ancestorSelectorName);
        if (ancestorSelector == null) {
            throw new IllegalArgumentException("Unknown selector: " + ancestorSelectorName);
        }
    }

    @Override
    public boolean evaluate() {
        String a = ancestorSelector.currentPath();
        String d = descendantSelector.currentPath();
        return PathUtils.isAncestor(a, d);
    }

    @Override
    public void apply(FilterImpl f) {
        String d = descendantSelector.currentPath();
        String a = ancestorSelector.currentPath();
        if (d != null && f.getSelector() == ancestorSelector) {
            f.restrictPath(PathUtils.getParentPath(d), Filter.PathRestriction.PARENT);
        }
        if (a != null && f.getSelector() == descendantSelector) {
            f.restrictPath(a, Filter.PathRestriction.DIRECT_CHILDREN);
        }
    }

}
