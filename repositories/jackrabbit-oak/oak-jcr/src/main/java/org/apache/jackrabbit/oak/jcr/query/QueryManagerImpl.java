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
package org.apache.jackrabbit.oak.jcr.query;

import java.text.ParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map.Entry;
import javax.jcr.Node;
import javax.jcr.RepositoryException;
import javax.jcr.Value;
import javax.jcr.query.InvalidQueryException;
import javax.jcr.query.Query;
import javax.jcr.query.QueryManager;
import javax.jcr.query.QueryResult;
import javax.jcr.query.qom.QueryObjectModelFactory;
import org.apache.jackrabbit.oak.api.CoreValue;
import org.apache.jackrabbit.oak.api.QueryEngine;
import org.apache.jackrabbit.oak.api.Result;
import org.apache.jackrabbit.oak.jcr.SessionDelegate;
import org.apache.jackrabbit.oak.jcr.query.qom.QueryObjectModelFactoryImpl;

/**
 * The implementation of the corresponding JCR interface.
 */
public class QueryManagerImpl implements QueryManager {

    private final QueryObjectModelFactoryImpl qomFactory;
    private final QueryEngine queryEngine;
    private final SessionDelegate sessionDelegate;
    private final HashSet<String> supportedQueryLanguages = new HashSet<String>();

    public QueryManagerImpl(SessionDelegate sessionDelegate) {
        this.sessionDelegate = sessionDelegate;
        qomFactory = new QueryObjectModelFactoryImpl(this, sessionDelegate.getValueFactory());
        queryEngine = sessionDelegate.getQueryEngine();
        supportedQueryLanguages.addAll(queryEngine.getSupportedQueryLanguages());
    }

    @Override
    public QueryImpl createQuery(String statement, String language) throws RepositoryException {
        if (!supportedQueryLanguages.contains(language)) {
            throw new InvalidQueryException("The specified language is not supported: " + language);
        }
        return new QueryImpl(this, statement, language);
    }

    @Override
    public QueryObjectModelFactory getQOMFactory() {
        return qomFactory;
    }

    @Override
    public Query getQuery(Node node) throws RepositoryException {
        // TODO getQuery(Node node): is it needed?
        throw new RepositoryException("Feature not implemented");
    }

    @Override
    public String[] getSupportedQueryLanguages() throws RepositoryException {
        ArrayList<String> list = new ArrayList<String>(queryEngine.getSupportedQueryLanguages());
        // JQOM is supported in this level only (converted to JCR_SQL2)
        list.add(Query.JCR_JQOM);
        // create a new instance each time because the array is mutable
        // (the caller could modify it)
        return list.toArray(new String[list.size()]);
    }

    public List<String> parse(String statement, String language) throws InvalidQueryException {
        try {
            return queryEngine.getBindVariableNames(statement, language);
        } catch (ParseException e) {
            throw new InvalidQueryException(e);
        }
    }

    public QueryResult executeQuery(String statement, String language,
            HashMap<String, Value> bindVariableMap, long limit, long offset) throws RepositoryException {
        try {
            HashMap<String, CoreValue> bindMap = convertMap(bindVariableMap);
            Result r = queryEngine.executeQuery(statement, language, bindMap);
            return new QueryResultImpl(sessionDelegate, r);
        } catch (IllegalArgumentException e) {
            throw new InvalidQueryException(e);
        } catch (ParseException e) {
            throw new InvalidQueryException(e);
        }
    }

    private HashMap<String, CoreValue> convertMap(HashMap<String, Value> bindVariableMap) {
        HashMap<String, CoreValue> map = new HashMap<String, CoreValue>();
        for (Entry<String, Value> e : bindVariableMap.entrySet()) {
            map.put(e.getKey(), sessionDelegate.getValueFactory().getCoreValue(e.getValue()));
        }
        return map;
    }

    SessionDelegate getSessionDelegate() {
        return sessionDelegate;
    }

    void ensureIsAlive() throws RepositoryException {
        // check session status
        if (!sessionDelegate.isAlive()) {
            throw new RepositoryException("This session has been closed.");
        }
    }

}
