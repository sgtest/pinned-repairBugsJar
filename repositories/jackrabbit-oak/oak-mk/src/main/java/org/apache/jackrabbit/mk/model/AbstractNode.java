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
package org.apache.jackrabbit.mk.model;

import org.apache.jackrabbit.mk.store.Binding;
import org.apache.jackrabbit.mk.store.RevisionProvider;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

/**
 *
 */
public abstract class AbstractNode implements Node {

    protected RevisionProvider provider;
    
    protected HashMap<String, String> properties;

    protected ChildNodeEntries childEntries;

    protected AbstractNode(RevisionProvider provider) {
        this.provider = provider;
        this.properties = new HashMap<String, String>();
        this.childEntries = new ChildNodeEntriesMap();
    }

    protected AbstractNode(Node other, RevisionProvider provider) {
        this.provider = provider;
        if (other instanceof AbstractNode) {
            AbstractNode srcNode = (AbstractNode) other;
            this.properties = (HashMap<String, String>) srcNode.properties.clone();
            this.childEntries = (ChildNodeEntries) srcNode.childEntries.clone();
        } else {
            this.properties = new HashMap<String, String>(other.getProperties());
            if (other.getChildNodeCount() <= ChildNodeEntries.CAPACITY_THRESHOLD) {
                this.childEntries = new ChildNodeEntriesMap();
            } else {
                this.childEntries = new ChildNodeEntriesTree(provider);
            }
            for (Iterator<ChildNode> it = other.getChildNodeEntries(0, -1); it.hasNext(); ) {
                ChildNode cne = it.next();
                this.childEntries.add(cne);
            }
        }
    }

    public Map<String, String> getProperties() {
        return properties;
    }

    public ChildNode getChildNodeEntry(String name) {
        return childEntries.get(name);
    }

    public Iterator<String> getChildNodeNames(int offset, int count) {
        return childEntries.getNames(offset, count);
    }

    public int getChildNodeCount() {
        return childEntries.getCount();
    }

    public Iterator<ChildNode> getChildNodeEntries(int offset, int count) {
        return childEntries.getEntries(offset, count);
    }

    public void diff(Node other, NodeDiffHandler handler) {
        // compare properties

        Map<String, String> oldProps = getProperties();
        Map<String, String> newProps = other.getProperties();

        for (Map.Entry<String, String> entry : oldProps.entrySet()) {
            String name = entry.getKey();
            String val = oldProps.get(name);
            String newVal = newProps.get(name);
            if (newVal == null) {
                handler.propDeleted(name, val);
            } else {
                if (!val.equals(newVal)) {
                    handler.propChanged(name, val, newVal);
                }
            }
        }
        for (Map.Entry<String, String> entry : newProps.entrySet()) {
            String name = entry.getKey();
            if (!oldProps.containsKey(name)) {
                handler.propAdded(name, entry.getValue());
            }
        }

        // compare child node entries

        if (other instanceof AbstractNode) {
            // OAK-46: Efficient diffing of large child node lists

            // delegate to ChildNodeEntries implementation
            ChildNodeEntries otherEntries = ((AbstractNode) other).childEntries;
            for (Iterator<ChildNode> it = childEntries.getAdded(otherEntries); it.hasNext(); ) {
                handler.childNodeAdded(it.next());
            }
            for (Iterator<ChildNode> it = childEntries.getRemoved(otherEntries); it.hasNext(); ) {
                handler.childNodeDeleted(it.next());
            }
            for (Iterator<ChildNode> it = childEntries.getModified(otherEntries); it.hasNext(); ) {
                ChildNode old = it.next();
                ChildNode modified = otherEntries.get(old.getName());
                handler.childNodeChanged(old, modified.getId());
            }
            return;
        }

        for (Iterator<ChildNode> it = getChildNodeEntries(0, -1); it.hasNext(); ) {
            ChildNode child = it.next();
            ChildNode newChild = other.getChildNodeEntry(child.getName());
            if (newChild == null) {
                handler.childNodeDeleted(child);
            } else {
                if (!child.getId().equals(newChild.getId())) {
                    handler.childNodeChanged(child, newChild.getId());
                }
            }
        }
        for (Iterator<ChildNode> it = other.getChildNodeEntries(0, -1); it.hasNext(); ) {
            ChildNode child = it.next();
            if (getChildNodeEntry(child.getName()) == null) {
                handler.childNodeAdded(child);
            }
        }
    }

    public void serialize(Binding binding) throws Exception {
        final Iterator<Map.Entry<String, String>> iter = properties.entrySet().iterator();
        binding.writeMap(":props", properties.size(),
                new Binding.StringEntryIterator() {
                    @Override
                    public boolean hasNext() {
                        return iter.hasNext();
                    }
                    @Override
                    public Binding.StringEntry next() {
                        Map.Entry<String, String> entry = iter.next();
                        return new Binding.StringEntry(entry.getKey(), entry.getValue());
                    }
                    @Override
                    public void remove() {
                        throw new UnsupportedOperationException();
                    }
                });
        binding.write(":inlined", childEntries.inlined() ? 1 : 0);
        childEntries.serialize(binding);
    }
}
