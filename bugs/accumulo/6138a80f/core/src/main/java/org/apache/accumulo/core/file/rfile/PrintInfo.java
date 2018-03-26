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
package org.apache.accumulo.core.file.rfile;

import java.util.ArrayList;

import org.apache.accumulo.core.conf.AccumuloConfiguration;
import org.apache.accumulo.core.data.ByteSequence;
import org.apache.accumulo.core.data.Key;
import org.apache.accumulo.core.data.Range;
import org.apache.accumulo.core.data.Value;
import org.apache.accumulo.core.file.FileUtil;
import org.apache.accumulo.core.file.blockfile.impl.CachableBlockFile;
import org.apache.accumulo.core.file.rfile.RFile.Reader;
import org.apache.commons.cli.BasicParser;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;

public class PrintInfo {
  public static void main(String[] args) throws Exception {
    Configuration conf = new Configuration();
    @SuppressWarnings("deprecation")
    //Not for client use
    FileSystem fs = FileUtil.getFileSystem(conf, AccumuloConfiguration.getSiteConfiguration());
    
    Options opts = new Options();
    Option dumpKeys = new Option("d", "dump", false, "dump the key/value pairs");
    opts.addOption(dumpKeys);
    Option histogramOption = new Option("h", "histogram", false, "print a histogram of the key-value sizes");
    opts.addOption(histogramOption);
    
    CommandLine commandLine = null;
    try {
      commandLine = new BasicParser().parse(opts, args);
      if (commandLine.getArgs().length == 0) {
        throw new ParseException("No files were given");
      }
      
    } catch (ParseException e) {
      System.err.println("Failed to parse command line : " + e.getMessage());
      System.err.println();
      HelpFormatter formatter = new HelpFormatter();
      formatter.printHelp("rfile-info <rfile> {<rfile>}", opts);
      System.exit(-1);
    }
    
    boolean dump = commandLine.hasOption(dumpKeys.getOpt());
    boolean doHistogram = commandLine.hasOption(histogramOption.getOpt());
    long countBuckets[] = new long[11];
    long sizeBuckets[] = new long[countBuckets.length];
    long totalSize = 0;

    for (String arg : commandLine.getArgs()) {
      
      Path path = new Path(arg);
      CachableBlockFile.Reader _rdr = new CachableBlockFile.Reader(fs, path, conf, null, null);
      Reader iter = new RFile.Reader(_rdr);
      
      iter.printInfo();
      System.out.println();
      org.apache.accumulo.core.file.rfile.bcfile.PrintInfo.main(new String[] {arg});
      
      if (doHistogram || dump) {
        iter.seek(new Range((Key) null, (Key) null), new ArrayList<ByteSequence>(), false);
        while (iter.hasTop()) {
          Key key = iter.getTopKey();
          Value value = iter.getTopValue();
          if (dump)
            System.out.println(key + " -> " + value);
          if (doHistogram) {
            long size = key.getSize() + value.getSize();
            int bucket = (int) Math.log10(size);
            countBuckets[bucket]++;
            sizeBuckets[bucket] += size;
            totalSize += size;
          }
          iter.next();
        }
      }
      iter.close();
      if (doHistogram) {
        System.out.println("Up to size      count      %-age");
        for (int i = 1; i < countBuckets.length; i++) {
          System.out.println(String.format("%11.0f : %10d %6.2f%%", Math.pow(10, i), countBuckets[i], sizeBuckets[i] * 100. / totalSize));
        }
      }
    }
  }
}
