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

import org.apache.jackrabbit.oak.query.index.FilterImpl;
import org.apache.jackrabbit.oak.spi.Filter;

public class SameNodeJoinConditionImpl extends JoinConditionImpl {

    private final String selector1Name;
    private final String selector2Name;
    private final String selector2Path;
    private SelectorImpl selector1;
    private SelectorImpl selector2;

    public SameNodeJoinConditionImpl(String selector1Name, String selector2Name,
            String selector2Path) {
        this.selector1Name = selector1Name;
        this.selector2Name = selector2Name;
        this.selector2Path = selector2Path;
    }

    public String getSelector1Name() {
        return selector1Name;
    }

    public String getSelector2Name() {
        return selector2Name;
    }

    public String getSelector2Path() {
        return selector2Path;
    }

    @Override
    boolean accept(AstVisitor v) {
        return v.visit(this);
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append("ISSAMENODE(");
        builder.append(getSelector1Name());
        builder.append(", ");
        builder.append(getSelector2Name());
        if (selector2Path != null) {
            builder.append(", ");
            builder.append(quotePath(selector2Path));
        }
        builder.append(')');
        return builder.toString();
    }

    public void bindSelector(SourceImpl source) {
        selector1 = source.getSelector(selector1Name);
        if (selector1 == null) {
            throw new IllegalArgumentException("Unknown selector: " + selector1Name);
        }
        selector2 = source.getSelector(selector2Name);
        if (selector2 == null) {
            throw new IllegalArgumentException("Unknown selector: " + selector2Name);
        }
    }

    @Override
    public boolean evaluate() {
        String p1 = selector1.currentPath();
        String p2 = selector2.currentPath();
        return p1.equals(p2);
    }

    @Override
    public void apply(FilterImpl f) {
        String p1 = selector1.currentPath();
        String p2 = selector2.currentPath();
        if (f.getSelector() == selector1) {
            f.restrictPath(p2, Filter.PathRestriction.EXACT);
        }
        if (f.getSelector() == selector2) {
            f.restrictPath(p1, Filter.PathRestriction.EXACT);
        }
    }

}
