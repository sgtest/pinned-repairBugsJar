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
package org.apache.accumulo.core.util;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map.Entry;

import org.apache.accumulo.core.Constants;
import org.apache.accumulo.core.client.Connector;
import org.apache.accumulo.core.client.Instance;
import org.apache.accumulo.core.client.Scanner;
import org.apache.accumulo.core.client.ZooKeeperInstance;
import org.apache.accumulo.core.client.impl.Tables;
import org.apache.accumulo.core.conf.AccumuloConfiguration;
import org.apache.accumulo.core.conf.ConfigurationCopy;
import org.apache.accumulo.core.conf.Property;
import org.apache.accumulo.core.data.Key;
import org.apache.accumulo.core.data.KeyExtent;
import org.apache.accumulo.core.data.Value;
import org.apache.commons.cli.BasicParser;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.hadoop.io.Text;
import org.apache.log4j.Logger;

public class Merge {
  
  public static class MergeException extends Exception {
    private static final long serialVersionUID = 1L;
    
    MergeException(Exception ex) {
      super(ex);
    }
  };
  
  private static final Logger log = Logger.getLogger(Merge.class);
  
  protected void message(String format, Object... args) {
    log.info(String.format(format, args));
  }
  
  public void start(String[] args) throws MergeException, ParseException {
    String keepers = "localhost";
    String instance = "instance";
    String table = null;
    long goalSize = -1;
    String user = "root";
    byte[] password = "secret".getBytes();
    boolean force = false;
    Text begin = null;
    Text end = null;
    
    Options options = new Options();
    options.addOption("k", "keepers", true, "ZooKeeper list");
    options.addOption("i", "instance", true, "instance name");
    options.addOption("t", "table", true, "table to merge");
    options.addOption("s", "size", true, "merge goal size");
    options.addOption("u", "user", true, "user");
    options.addOption("p", "password", true, "password");
    options.addOption("f", "force", false, "merge small tablets even if merging them to larger tablets might cause a split");
    options.addOption("b", "begin", true, "start tablet");
    options.addOption("e", "end", true, "end tablet");
    CommandLine commandLine = new BasicParser().parse(options, args);
    if (commandLine.hasOption("k")) {
      keepers = commandLine.getOptionValue("k");
    }
    if (commandLine.hasOption("i")) {
      instance = commandLine.getOptionValue("i");
    }
    if (commandLine.hasOption("t")) {
      table = commandLine.getOptionValue("t");
    }
    if (commandLine.hasOption("s")) {
      goalSize = AccumuloConfiguration.getMemoryInBytes(commandLine.getOptionValue("s"));
    }
    if (commandLine.hasOption("u")) {
    	table = commandLine.getOptionValue("u");
    }
    if (commandLine.hasOption("p")) {
        password = commandLine.getOptionValue("p").getBytes();
    }
    if (commandLine.hasOption("f")) {
      force = true;
    }
    if (commandLine.hasOption("b")) {
      begin = new Text(commandLine.getOptionValue("b"));
    }
    if (commandLine.hasOption("e")) {
    	end = new Text(commandLine.getOptionValue("e"));
    }
    if (table == null) {
      System.err.println("Specify the table to merge");
      return;
    }
    Instance zki = new ZooKeeperInstance(instance, keepers);
    try {
      Connector conn = zki.getConnector(user, password);
      
      if (!conn.tableOperations().exists(table)) {
        System.err.println("table " + table + " does not exist");
        return;
      }
      if (goalSize < 1) {
        AccumuloConfiguration tableConfig = new ConfigurationCopy(conn.tableOperations().getProperties(table));
        goalSize = tableConfig.getMemoryInBytes(Property.TABLE_SPLIT_THRESHOLD);
      }
      
      message("Merging tablets in table %s to %d bytes", table, goalSize);
      mergomatic(conn, table, begin, end, goalSize, force);
    } catch (Exception ex) {
      throw new MergeException(ex);
    }
  }
  
  public static void main(String[] args) throws MergeException, ParseException {
    Merge merge = new Merge();
    merge.start(args);
  }
  
  public static class Size {
    public Size(KeyExtent extent, long size) {
      this.extent = extent;
      this.size = size;
    }
    
    KeyExtent extent;
    long size;
  }
  
  public void mergomatic(Connector conn, String table, Text start, Text end, long goalSize, boolean force) throws MergeException {
    try {
      if (table.equals(Constants.METADATA_TABLE_NAME)) {
        throw new IllegalArgumentException("cannot merge tablets on the metadata table");
      }
      List<Size> sizes = new ArrayList<Size>();
      long totalSize = 0;
      // Merge any until you get larger than the goal size, and then merge one less tablet
      Iterator<Size> sizeIterator = getSizeIterator(conn, table, start, end);
      while (sizeIterator.hasNext()) {
        Size next = sizeIterator.next();
        totalSize += next.size;
        sizes.add(next);
        if (totalSize > goalSize) {
          totalSize = mergeMany(conn, table, sizes, goalSize, force, false);
        }
      }
      if (sizes.size() > 1)
        mergeMany(conn, table, sizes, goalSize, force, true);
    } catch (Exception ex) {
      throw new MergeException(ex);
    }
  }
  
  protected long mergeMany(Connector conn, String table, List<Size> sizes, long goalSize, boolean force, boolean last) throws MergeException {
    // skip the big tablets, which will be the typical case
    while (!sizes.isEmpty()) {
      if (sizes.get(0).size < goalSize)
        break;
      sizes.remove(0);
    }
    if (sizes.isEmpty()) {
      return 0;
    }
    
    // collect any small ones
    long mergeSize = 0;
    int numToMerge = 0;
    for (int i = 0; i < sizes.size(); i++) {
      if (mergeSize + sizes.get(i).size > goalSize) {
        numToMerge = i;
        break;
      }
      mergeSize += sizes.get(i).size;
    }
    
    if (numToMerge > 1) {
      mergeSome(conn, table, sizes, numToMerge);
    } else {
      if (numToMerge == 1 && sizes.size() > 1) {
        // here we have the case of a merge candidate that is surrounded by candidates that would split
        if (force) {
          mergeSome(conn, table, sizes, 2);
        } else {
          sizes.remove(0);
        }
      }
    }
    if (numToMerge == 0 && sizes.size() > 1 && last) {
      // That's the last tablet, and we have a bunch to merge
      mergeSome(conn, table, sizes, sizes.size());
    }
    long result = 0;
    for (Size s : sizes) {
      result += s.size;
    }
    return result;
  }
  
  protected void mergeSome(Connector conn, String table, List<Size> sizes, int numToMerge) throws MergeException {
    merge(conn, table, sizes, numToMerge);
    for (int i = 0; i < numToMerge; i++) {
      sizes.remove(0);
    }
  }
  
  protected void merge(Connector conn, String table, List<Size> sizes, int numToMerge) throws MergeException {
    try {
      Text start = sizes.get(0).extent.getPrevEndRow();
      Text end = sizes.get(numToMerge - 1).extent.getEndRow();
      message("Merging %d tablets from (%s to %s]", numToMerge, start == null ? "-inf" : start, end == null ? "+inf" : end);
      conn.tableOperations().merge(table, start, end);
    } catch (Exception ex) {
      throw new MergeException(ex);
    }
  }
  
  protected Iterator<Size> getSizeIterator(Connector conn, String tablename, Text start, Text end) throws MergeException {
    // open up the !METADATA table, walk through the tablets.
    String tableId;
    Scanner scanner;
    try {
      tableId = Tables.getTableId(conn.getInstance(), tablename);
      scanner = conn.createScanner(Constants.METADATA_TABLE_NAME, Constants.NO_AUTHS);
    } catch (Exception e) {
      throw new MergeException(e);
    }
    scanner.setRange(new KeyExtent(new Text(tableId), end, start).toMetadataRange());
    scanner.fetchColumnFamily(Constants.METADATA_DATAFILE_COLUMN_FAMILY);
    Constants.METADATA_PREV_ROW_COLUMN.fetch(scanner);
    final Iterator<Entry<Key,Value>> iterator = scanner.iterator();
    
    Iterator<Size> result = new Iterator<Size>() {
      Size next = fetch();
      
      @Override
      public boolean hasNext() {
        return next != null;
      }
      
      private Size fetch() {
        long tabletSize = 0;
        while (iterator.hasNext()) {
          Entry<Key,Value> entry = iterator.next();
          Key key = entry.getKey();
          if (key.getColumnFamily().equals(Constants.METADATA_DATAFILE_COLUMN_FAMILY)) {
            String[] sizeEntries = new String(entry.getValue().get()).split(",");
            if (sizeEntries.length == 2) {
              tabletSize += Long.parseLong(sizeEntries[0]);
            }
          } else if (Constants.METADATA_PREV_ROW_COLUMN.hasColumns(key)) {
            KeyExtent extent = new KeyExtent(key.getRow(), entry.getValue());
            return new Size(extent, tabletSize);
          }
        }
        return null;
      }
      
      @Override
      public Size next() {
        Size result = next;
        next = fetch();
        return result;
      }
      
      @Override
      public void remove() {
        throw new UnsupportedOperationException();
      }
    };
    return result;
  }
  
}
