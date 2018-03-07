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
package org.apache.accumulo.core.data;

import java.util.Arrays;

/**
 * A single column and value pair within a mutation
 * 
 */

public class ColumnUpdate {
  
  private byte[] columnFamily;
  private byte[] columnQualifier;
  private byte[] columnVisibility;
  private long timestamp;
  private boolean hasTimestamp;
  private byte[] val;
  private boolean deleted;
  
  public ColumnUpdate(byte[] cf, byte[] cq, byte[] cv, boolean hasts, long ts, boolean deleted, byte[] val) {
    this.columnFamily = cf;
    this.columnQualifier = cq;
    this.columnVisibility = cv;
    this.hasTimestamp = hasts;
    this.timestamp = ts;
    this.deleted = deleted;
    this.val = val;
  }
  
  /**
   * @deprecated use setTimestamp(long);
   * @param timestamp
   */
  public void setSystemTimestamp(long timestamp) {
    if (hasTimestamp)
      throw new IllegalStateException("Cannot set system timestamp when user set a timestamp");
  }
  
  public boolean hasTimestamp() {
    return hasTimestamp;
  }
  
  /**
   * Returns the column
   * 
   */
  public byte[] getColumnFamily() {
    return columnFamily;
  }
  
  public byte[] getColumnQualifier() {
    return columnQualifier;
  }
  
  public byte[] getColumnVisibility() {
    return columnVisibility;
  }
  
  public long getTimestamp() {
    return this.timestamp;
  }
  
  public boolean isDeleted() {
    return this.deleted;
  }
  
  public byte[] getValue() {
    return this.val;
  }
  
  public String toString() {
    return new String(Arrays.toString(columnFamily)) + ":" + new String(Arrays.toString(columnQualifier)) + " ["
        + new String(Arrays.toString(columnVisibility)) + "] " + (hasTimestamp ? timestamp : "NO_TIME_STAMP") + " " + Arrays.toString(val) + " " + deleted;
  }

  @Override
  public boolean equals(Object obj) {
    if (!(obj instanceof ColumnUpdate))
      return false;
    ColumnUpdate upd = (ColumnUpdate)obj;
    return Arrays.equals(getColumnFamily(), upd.getColumnFamily()) &&
        Arrays.equals(getColumnQualifier(), upd.getColumnQualifier()) &&
        Arrays.equals(getColumnVisibility(), upd.getColumnVisibility()) &&
        isDeleted() == upd.isDeleted() &&
        Arrays.equals(getValue(), upd.getValue()) &&
        hasTimestamp() == upd.hasTimestamp() &&
        getTimestamp() == upd.getTimestamp();
  }
  
}
