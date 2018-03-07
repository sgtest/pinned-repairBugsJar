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
package org.apache.accumulo.core.security;

import java.util.ArrayList;
import java.util.Collection;

import org.apache.accumulo.core.security.ColumnVisibility.Node;

public class VisibilityEvaluator {
  private Authorizations auths;
  
  static Authorizations escape(Authorizations auths) {
    ArrayList<byte[]> retAuths = new ArrayList<byte[]>(auths.getAuthorizations().size());
    
    for (byte[] auth : auths.getAuthorizations())
      retAuths.add(escape(auth, false));
    
    return new Authorizations(retAuths);
  }
  
  public static byte[] escape(byte[] auth, boolean quote) {
    int escapeCount = 0;
    
    for (int i = 0; i < auth.length; i++)
      if (auth[i] == '"' || auth[i] == '\\')
        escapeCount++;
    
    if (escapeCount > 0 || quote) {
      byte[] escapedAuth = new byte[auth.length + escapeCount + (quote ? 2 : 0)];
      int index = quote ? 1 : 0;
      for (int i = 0; i < auth.length; i++) {
        if (auth[i] == '"' || auth[i] == '\\')
          escapedAuth[index++] = '\\';
        escapedAuth[index++] = auth[i];
      }
      
      if (quote) {
        escapedAuth[0] = '"';
        escapedAuth[escapedAuth.length - 1] = '"';
      }

      auth = escapedAuth;
    }
    return auth;
  }

  VisibilityEvaluator(Collection<byte[]> authorizations) {
    this(new Authorizations(authorizations));
  }
  
  /**
   * The VisibilityEvaluator computes a trie from the given Authorizations, that ColumnVisibility expressions can be evaluated against.
   */
  public VisibilityEvaluator(Authorizations authorizations) {
    this.auths = escape(authorizations);
  }
  
  public Authorizations getAuthorizations() {
    return new Authorizations(auths.getAuthorizations());
  }
  
  public boolean evaluate(ColumnVisibility visibility) throws VisibilityParseException {
    return evaluate(visibility.getExpression(), visibility.getParseTree());
  }
  
  private final boolean evaluate(final byte[] expression, final Node root) throws VisibilityParseException {
    switch (root.type) {
      case TERM:
        return auths.contains(root.getTerm(expression));
      case AND:
        if (root.children == null || root.children.size() < 2)
          throw new VisibilityParseException("AND has less than 2 children", expression, root.start);
        for (Node child : root.children) {
          if (!evaluate(expression, child))
            return false;
        }
        return true;
      case OR:
        if (root.children == null || root.children.size() < 2)
          throw new VisibilityParseException("OR has less than 2 children", expression, root.start);
        for (Node child : root.children) {
          if (evaluate(expression, child))
            return true;
        }
        return false;
      default:
        throw new VisibilityParseException("No such node type", expression, root.start);
    }
  }
}
