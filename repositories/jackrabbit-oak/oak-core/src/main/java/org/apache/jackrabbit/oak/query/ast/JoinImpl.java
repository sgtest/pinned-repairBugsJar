/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with this
 * work for additional information regarding copyright ownership. The ASF
 * licenses this file to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0 Unless required by applicable law
 * or agreed to in writing, software distributed under the License is
 * distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the specific language
 * governing permissions and limitations under the License.
 */
package org.apache.jackrabbit.oak.query.ast;

import org.apache.jackrabbit.mk.api.MicroKernel;
import org.apache.jackrabbit.mk.simple.NodeImpl;
import org.apache.jackrabbit.oak.query.Query;

public class JoinImpl extends SourceImpl {

    private final JoinConditionImpl joinCondition;
    private JoinType joinType;
    private SourceImpl left;
    private SourceImpl right;

    private boolean leftNeedExecute, rightNeedExecute;
    private boolean leftNeedNext;
    private boolean foundJoinedRow;
    private boolean end;
    private String revisionId;

    public JoinImpl(SourceImpl left, SourceImpl right, JoinType joinType,
            JoinConditionImpl joinCondition) {
        this.left = left;
        this.right = right;
        this.joinType = joinType;
        this.joinCondition = joinCondition;
    }

    public JoinConditionImpl getJoinCondition() {
        return joinCondition;
    }

    public String getJoinType() {
        return joinType.toString();
    }

    public SourceImpl getLeft() {
        return left;
    }

    public SourceImpl getRight() {
        return right;
    }

    @Override
    boolean accept(AstVisitor v) {
        return v.visit(this);
    }

    @Override
    public String getPlan() {
        return left.getPlan() + ' ' + joinType.name() + " JOIN " + right.getPlan();
    }

    @Override
    public String toString() {
        return left + " " + joinType.name() + " JOIN " + right;
    }

    @Override
    public void init(Query qom) {
        switch (joinType) {
        case INNER:
            left.setQueryConstraint(queryConstraint);
            right.setQueryConstraint(queryConstraint);
            right.setJoinCondition(joinCondition);
            left.init(qom);
            right.init(qom);
            break;
        case LEFT_OUTER:
            left.setQueryConstraint(queryConstraint);
            right.setOuterJoin(true);
            right.setQueryConstraint(queryConstraint);
            right.setJoinCondition(joinCondition);
            left.init(qom);
            right.init(qom);
            break;
        case RIGHT_OUTER:
            right.setQueryConstraint(queryConstraint);
            left.setOuterJoin(true);
            left.setQueryConstraint(queryConstraint);
            left.setJoinCondition(joinCondition);
            right.init(qom);
            left.init(qom);
            // TODO right outer join: verify whether converting
            // to left outer join is always correct (given the current restrictions)
            joinType = JoinType.LEFT_OUTER;
            // swap left and right
            SourceImpl temp = left;
            left = right;
            right = temp;
            break;
        }
    }

    @Override
    public void prepare(MicroKernel mk) {
        left.prepare(mk);
        right.prepare(mk);
    }

    @Override
    public SelectorImpl getSelector(String selectorName) {
        SelectorImpl s = left.getSelector(selectorName);
        if (s == null) {
            s = right.getSelector(selectorName);
        }
        return s;
    }

    @Override
    public void execute(String revisionId) {
        this.revisionId = revisionId;
        leftNeedExecute = true;
        end = false;
    }

    @Override
    public boolean next() {
        if (end) {
            return false;
        }
        if (leftNeedExecute) {
            left.execute(revisionId);
            leftNeedExecute = false;
            leftNeedNext = true;
        }
        while (true) {
            if (leftNeedNext) {
                if (!left.next()) {
                    end = true;
                    return false;
                }
                leftNeedNext = false;
                rightNeedExecute = true;
            }
            if (rightNeedExecute) {
                right.execute(revisionId);
                foundJoinedRow = false;
                rightNeedExecute = false;
            }
            if (!right.next()) {
                leftNeedNext = true;
            } else {
                if (joinCondition.evaluate()) {
                    foundJoinedRow = true;
                    return true;
                }
            }
            // for an outer join, if no matching result was found,
            // one row returned (with all values set to null)
            if (right.outerJoin && leftNeedNext && !foundJoinedRow) {
                return true;
            }
        }
    }

    @Override
    public String currentPath() {
        // TODO
        return left.currentPath();
    }

    @Override
    public NodeImpl currentNode() {
        return null;
    }

}
