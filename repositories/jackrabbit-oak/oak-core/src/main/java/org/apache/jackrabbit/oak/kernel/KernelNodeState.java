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
package org.apache.jackrabbit.oak.kernel;

import org.apache.jackrabbit.mk.api.MicroKernel;
import org.apache.jackrabbit.mk.json.JsopReader;
import org.apache.jackrabbit.mk.json.JsopTokenizer;
import org.apache.jackrabbit.oak.api.CoreValue;
import org.apache.jackrabbit.oak.api.CoreValueFactory;
import org.apache.jackrabbit.oak.api.PropertyState;
import org.apache.jackrabbit.oak.plugins.memory.MemoryChildNodeEntry;
import org.apache.jackrabbit.oak.spi.state.AbstractNodeState;
import org.apache.jackrabbit.oak.spi.state.ChildNodeEntry;
import org.apache.jackrabbit.oak.spi.state.NodeState;
import org.apache.jackrabbit.oak.util.Iterators;
import org.apache.jackrabbit.oak.util.PagedIterator;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

/**
 * Basic {@link NodeState} implementation based on the {@link MicroKernel}
 * interface. This class makes an attempt to load data lazily.
 */
class KernelNodeState extends AbstractNodeState {

    /**
     * Maximum number of child nodes kept in memory.
     */
    static final int MAX_CHILD_NODE_NAMES = 1000;

    private final MicroKernel kernel;
    private final CoreValueFactory valueFactory;

    private final String path;

    private final String revision;

    private Map<String, PropertyState> properties;

    private long childNodeCount = -1;

    private Map<String, NodeState> childNodes; // TODO: WeakReference?

    /**
     * Create a new instance of this class representing the node at the
     * given {@code path} and {@code revision}. It is an error if the
     * underlying Microkernel does not contain such a node.
     *
     * @param kernel
     * @param valueFactory
     * @param path
     * @param revision
     */
    public KernelNodeState(MicroKernel kernel, CoreValueFactory valueFactory, String path, String revision) {
        this.kernel = kernel;
        this.valueFactory = valueFactory;
        this.path = path;
        this.revision = revision;
    }

    private synchronized void init() {
        if (properties == null) {
            String json = kernel.getNodes(
                    path, revision, 0, 0, MAX_CHILD_NODE_NAMES, null);

            JsopReader reader = new JsopTokenizer(json);
            reader.read('{');
            properties = new LinkedHashMap<String, PropertyState>();
            childNodes = new LinkedHashMap<String, NodeState>();
            do {
                String name = reader.readString();
                reader.read(':');
                if (":childNodeCount".equals(name)) {
                    childNodeCount =
                            Long.valueOf(reader.read(JsopTokenizer.NUMBER));
                } else if (reader.matches('{')) {
                    reader.read('}');
                    String childPath = path + '/' + name;
                    if ("/".equals(path)) {
                        childPath = '/' + name;
                    }
                    childNodes.put(name, new KernelNodeState(kernel, valueFactory, childPath, revision));
                } else if (reader.matches('[')) {
                    properties.put(name, new PropertyStateImpl(name, CoreValueMapper.listFromJsopReader(reader, valueFactory)));
                } else {
                    CoreValue cv = CoreValueMapper.fromJsopReader(reader, valueFactory);
                    properties.put(name, new PropertyStateImpl(name, cv));
                }
            } while (reader.matches(','));
            reader.read('}');
            reader.read(JsopTokenizer.END);
        }
    }

    @Override
    public long getPropertyCount() {
        init();
        return properties.size();
    }

    @Override
    public PropertyState getProperty(String name) {
        init();
        return properties.get(name);
    }

    @Override
    public Iterable<? extends PropertyState> getProperties() {
        init();
        return properties.values();
    }

    @Override
    public long getChildNodeCount() {
        init();
        return childNodeCount;
    }

    @Override
    public NodeState getChildNode(String name) {
        init();
        NodeState child = childNodes.get(name);
        if (child == null && childNodeCount > MAX_CHILD_NODE_NAMES) {
            String childPath = getChildPath(name);
            if (kernel.nodeExists(childPath, revision)) {
                child = new KernelNodeState(kernel, valueFactory, childPath, revision);
            }
        }
        return child;
    }

    @Override
    public Iterable<? extends ChildNodeEntry> getChildNodeEntries() {
        return (new Iterable<ChildNodeEntry>() {
            @Override
            public Iterator<ChildNodeEntry> iterator() {
                return Iterators.flatten(
                    new PagedIterator<ChildNodeEntry>(MAX_CHILD_NODE_NAMES) {
                        @Override
                        protected Iterator<? extends ChildNodeEntry> getPage(long pos, int size) {
                            return getChildNodeEntries(pos, size);
                        }
                });
            }
        });
    }

    //------------------------------------------------------------< internal >---

    String getRevision() {
        return revision;
    }

    String getPath() {
        return path;
    }

    //------------------------------------------------------------< private >---

    private Iterator<? extends ChildNodeEntry> getChildNodeEntries(
            long offset, int count) {
        init();
        boolean all;
        if (count == -1) {
            count = Integer.MAX_VALUE;
            all = true;
            if (childNodeCount > count) {
                throw new RuntimeException("Too many child nodes");
            }
        }
        else {
            all = false;
        }

        List<ChildNodeEntry> entries = new ArrayList<ChildNodeEntry>();

        if (offset < childNodes.size()) {
            Iterator<Entry<String, NodeState>> iterator =
                    childNodes.entrySet().iterator();
            while (offset > 0) {
                iterator.next();
                offset--;
            }
            while (count > 0 && iterator.hasNext()) {
                entries.add(new MemoryChildNodeEntry(iterator.next()));
                count--;
            }
            offset = childNodes.size();
        }

        if (count > 0 && childNodeCount > MAX_CHILD_NODE_NAMES) {
            String json = kernel.getNodes(
                    path, revision, 0, offset, all ? -1 : count, null);

            JsopReader reader = new JsopTokenizer(json);
            reader.read('{');
            do {
                String name = reader.readString();
                reader.read(':');
                if (reader.matches('{')) {
                    reader.read('}');
                    String childPath = getChildPath(name);
                    NodeState child = new KernelNodeState(
                            kernel, valueFactory, childPath, revision);
                    entries.add(new MemoryChildNodeEntry(name, child));
                } else {
                    reader.read();
                }
            } while (reader.matches(','));
            reader.read('}');
            reader.read(JsopTokenizer.END);
        }

        return entries.iterator();
    }

    private String getChildPath(String name) {
        if ("/".equals(path)) {
            return '/' + name;
        } else {
            return path + '/' + name;
        }
    }

    private List<CoreValue> readArray(JsopReader reader) {
        List<CoreValue> values = new ArrayList<CoreValue>();
        while (!reader.matches(']')) {
            values.add(CoreValueMapper.fromJsopReader(reader, valueFactory));
            reader.matches(',');
        }
        return values;
    }
}