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
package org.apache.accumulo.core.iterators;

import java.io.IOException;
import java.util.Collection;
import java.util.Map;

import org.apache.accumulo.core.data.ByteSequence;
import org.apache.accumulo.core.data.Key;
import org.apache.accumulo.core.data.Range;
import org.apache.accumulo.core.data.Value;
import org.apache.log4j.Logger;

public class DebugIterator extends WrappingIterator {
  
  private String prefix;
  
  private static final Logger log = Logger.getLogger(DebugIterator.class);
  
  public DebugIterator() {}
  
  public DebugIterator deepCopy(IteratorEnvironment env) {
    return new DebugIterator(this, env);
  }
  
  private DebugIterator(DebugIterator other, IteratorEnvironment env) {
    setSource(other.getSource().deepCopy(env));
    prefix = other.prefix;
  }
  
  public DebugIterator(String prefix, SortedKeyValueIterator<Key,Value> source) {
    this.prefix = prefix;
    this.setSource(source);
  }
  
  @Override
  public Key getTopKey() {
    Key wc = super.getTopKey();
    log.debug(prefix + " getTopKey() --> " + wc);
    return wc;
  }
  
  @Override
  public Value getTopValue() {
    Value w = super.getTopValue();
    log.debug(prefix + " getTopValue() --> " + w);
    return w;
  }
  
  @Override
  public boolean hasTop() {
    boolean b = super.hasTop();
    log.debug(prefix + " hasTop() --> " + b);
    return b;
  }
  
  @Override
  public void seek(Range range, Collection<ByteSequence> columnFamilies, boolean inclusive) throws IOException {
    log.debug(prefix + " seek(" + range + ", " + columnFamilies + ", " + inclusive + ")");
    super.seek(range, columnFamilies, inclusive);
  }
  
  @Override
  public void init(SortedKeyValueIterator<Key,Value> source, Map<String,String> options, IteratorEnvironment env) throws IOException {
    super.init(source, options, env);
  }
}
