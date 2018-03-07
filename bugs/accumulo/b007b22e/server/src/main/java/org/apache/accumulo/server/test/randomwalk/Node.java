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
package org.apache.accumulo.server.test.randomwalk;

import java.util.Properties;

import org.apache.log4j.Logger;

/**
 * Represents a point in graph of RandomFramework
 */
public abstract class Node {
  
  protected final Logger log = Logger.getLogger(this.getClass());
  long progress = System.currentTimeMillis();
  
  /**
   * Visits node
   * 
   * @param state
   *          Random walk state passed between nodes
   * @throws Exception
   */
  public abstract void visit(State state, Properties props) throws Exception;
  
  @Override
  public boolean equals(Object o) {
    return toString().equals(o.toString());
  }
  
  @Override
  public String toString() {
    return this.getClass().getName();
  }
  
  @Override
  public int hashCode() {
    return toString().hashCode();
  }
  
  synchronized public void makingProgress() {
    progress = System.currentTimeMillis();
  }
  
  synchronized public long lastProgress() {
    return progress;
  }
}
