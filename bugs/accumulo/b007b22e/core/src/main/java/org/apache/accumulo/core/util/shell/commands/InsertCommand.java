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
package org.apache.accumulo.core.util.shell.commands;

import java.io.IOException;
import java.util.ArrayList;
import java.util.concurrent.TimeUnit;

import org.apache.accumulo.core.client.AccumuloException;
import org.apache.accumulo.core.client.AccumuloSecurityException;
import org.apache.accumulo.core.client.BatchWriter;
import org.apache.accumulo.core.client.BatchWriterConfig;
import org.apache.accumulo.core.client.MutationsRejectedException;
import org.apache.accumulo.core.client.TableNotFoundException;
import org.apache.accumulo.core.conf.AccumuloConfiguration;
import org.apache.accumulo.core.data.ConstraintViolationSummary;
import org.apache.accumulo.core.data.KeyExtent;
import org.apache.accumulo.core.data.Mutation;
import org.apache.accumulo.core.data.Value;
import org.apache.accumulo.core.security.ColumnVisibility;
import org.apache.accumulo.core.tabletserver.thrift.ConstraintViolationException;
import org.apache.accumulo.core.util.shell.Shell;
import org.apache.accumulo.core.util.shell.Shell.Command;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.hadoop.io.Text;

public class InsertCommand extends Command {
  private Option insertOptAuths, timestampOpt;
  private Option timeoutOption;
  
  protected long getTimeout(final CommandLine cl) {
    if (cl.hasOption(timeoutOption.getLongOpt())) {
      return AccumuloConfiguration.getTimeInMillis(cl.getOptionValue(timeoutOption.getLongOpt()));
    }
    
    return Long.MAX_VALUE;
  }

  public int execute(final String fullCommand, final CommandLine cl, final Shell shellState) throws AccumuloException, AccumuloSecurityException, TableNotFoundException,
      IOException, ConstraintViolationException {
    shellState.checkTableState();
    
    final Mutation m = new Mutation(new Text(cl.getArgs()[0].getBytes(Shell.CHARSET)));
    final Text colf = new Text(cl.getArgs()[1].getBytes(Shell.CHARSET));
    final Text colq = new Text(cl.getArgs()[2].getBytes(Shell.CHARSET));
    final Value val = new Value(cl.getArgs()[3].getBytes(Shell.CHARSET));
    
    if (cl.hasOption(insertOptAuths.getOpt())) {
      final ColumnVisibility le = new ColumnVisibility(cl.getOptionValue(insertOptAuths.getOpt()));
      Shell.log.debug("Authorization label will be set to: " + le.toString());
      
      if (cl.hasOption(timestampOpt.getOpt()))
        m.put(colf, colq, le, Long.parseLong(cl.getOptionValue(timestampOpt.getOpt())), val);
      else
        m.put(colf, colq, le, val);
    } else if (cl.hasOption(timestampOpt.getOpt()))
      m.put(colf, colq, Long.parseLong(cl.getOptionValue(timestampOpt.getOpt())), val);
    else
      m.put(colf, colq, val);
    
    final BatchWriter bw = shellState.getConnector().createBatchWriter(shellState.getTableName(),
        new BatchWriterConfig().setMaxMemory(m.estimatedMemoryUsed()).setMaxWriteThreads(1).setTimeout(getTimeout(cl), TimeUnit.MILLISECONDS));
    bw.addMutation(m);
    try {
      bw.close();
    } catch (MutationsRejectedException e) {
      final ArrayList<String> lines = new ArrayList<String>();
      if (e.getAuthorizationFailures().isEmpty() == false) {
        lines.add("	Authorization Failures:");
      }
      for (KeyExtent extent : e.getAuthorizationFailures()) {
        lines.add("		" + extent);
      }
      if (e.getConstraintViolationSummaries().isEmpty() == false) {
        lines.add("	Constraint Failures:");
      }
      for (ConstraintViolationSummary cvs : e.getConstraintViolationSummaries()) {
        lines.add("		" + cvs.toString());
      }
      
      if (lines.size() == 0 || e.getUnknownExceptions() > 0) {
        // must always print something
        lines.add(" " + e.getClass().getName() + " : " + e.getMessage());
        if (e.getCause() != null)
          lines.add("   Caused by : " + e.getCause().getClass().getName() + " : " + e.getCause().getMessage());
      }

      shellState.printLines(lines.iterator(), false);
    }
    return 0;
  }
  
  @Override
  public String description() {
    return "inserts a record";
  }
  
  @Override
  public String usage() {
    return getName() + " <row> <colfamily> <colqualifier> <value>";
  }
  
  @Override
  public Options getOptions() {
    final Options o = new Options();
    insertOptAuths = new Option("l", "visibility-label", true, "formatted visibility");
    insertOptAuths.setArgName("expression");
    o.addOption(insertOptAuths);
    
    timestampOpt = new Option("ts", "timestamp", true, "timestamp to use for insert");
    timestampOpt.setArgName("timestamp");
    o.addOption(timestampOpt);
    
    timeoutOption = new Option(null, "timeout", true,
        "time before insert should fail if no data is written. If no unit is given assumes seconds.  Units d,h,m,s,and ms are supported.  e.g. 30s or 100ms");
    timeoutOption.setArgName("timeout");
    o.addOption(timeoutOption);

    return o;
  }
  
  @Override
  public int numArgs() {
    return 4;
  }
}
