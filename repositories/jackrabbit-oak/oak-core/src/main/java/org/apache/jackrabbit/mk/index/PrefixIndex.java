/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.jackrabbit.mk.index;

import java.util.Iterator;
import org.apache.jackrabbit.mk.json.JsopReader;
import org.apache.jackrabbit.mk.json.JsopTokenizer;
import org.apache.jackrabbit.mk.simple.NodeImpl;

/**
 * An index for all values with a given prefix.
 */
public class PrefixIndex implements Index {

    private final Indexer indexer;
    private final BTree tree;
    private final String prefix;

    public PrefixIndex(Indexer indexer, String prefix) {
        this.indexer = indexer;
        this.prefix = prefix;
        this.tree = new BTree(indexer, "prefix:" + prefix, false);
        tree.setMinSize(10);
    }

    public static PrefixIndex fromNodeName(Indexer indexer, String nodeName) {
        if (!nodeName.startsWith("prefix:")) {
            return null;
        }
        String prefix = nodeName.substring("prefix:".length());
        return new PrefixIndex(indexer, prefix);
    }

    @Override
    public String getName() {
        return tree.getName();
    }

    @Override
    public void addOrRemoveNode(NodeImpl node, boolean add) {
        String nodePath = node.getPath();
        for (int i = 0, size = node.getPropertyCount(); i < size; i++) {
            String propertyName = node.getProperty(i);
            String value = node.getPropertyValue(i);
            addOrRemoveProperty(nodePath, propertyName, value, add);
        }
    }

    @Override
    public void addOrRemoveProperty(String nodePath, String propertyName,
            String value, boolean add) {
        JsopTokenizer t = new JsopTokenizer(value);
        if (t.matches(JsopReader.STRING)) {
            String v = t.getToken();
            if (v.startsWith(prefix)) {
                addOrRemove(nodePath, propertyName, v, add);
            }
        } else if (t.matches('[')) {
            if (!t.matches(']')) {
                do {
                    if (t.matches(JsopReader.STRING)) {
                        String v = t.getToken();
                        if (v.startsWith(prefix)) {
                            addOrRemove(nodePath, propertyName, v, add);
                        }
                    } else if (t.matches(JsopReader.FALSE)) {
                        // ignore
                    } else if (t.matches(JsopReader.TRUE)) {
                        // ignore
                    } else if (t.matches(JsopReader.NULL)) {
                        // ignore
                    } else if (t.matches(JsopReader.NUMBER)) {
                        // ignore
                    }
                } while (t.matches(','));
                t.read(']');
            }
        }
    }

    private void addOrRemove(String path, String propertyName, String value, boolean add) {
        String v = value.substring(prefix.length());
        if (add) {
            tree.add(v, path + "/" + propertyName);
        } else {
            tree.remove(v, path + "/" + propertyName);
        }
    }

    /**
     * Get an iterator over the paths for the given property value.
     *
     * @param value the value (including the prefix)
     * @param revision the revision
     * @return an iterator of the paths (an empty iterator if not found)
     * @throws IllegalArgumentException if the value doesn't start with the prefix
     */
    @Override
    public Iterator<String> getPaths(String value, String revision) {
        if (!value.startsWith(prefix)) {
            throw new IllegalArgumentException(
                    "The value doesn't start with \"" + prefix + "\": " + value);
        }
        String v = value.substring(prefix.length());
        indexer.updateUntil(revision);
        Cursor c = tree.findFirst(v);
        return new Cursor.RangeIterator(c, v);
    }

    @Override
    public boolean isUnique() {
        return tree.isUnique();
    }

}
