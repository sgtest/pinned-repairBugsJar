/***********************************************************************************************************************
 * Copyright (C) 2010-2013 by the Stratosphere project (http://stratosphere.eu)
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 **********************************************************************************************************************/

package eu.stratosphere.test.iterative.nephele.danglingpagerank;

import eu.stratosphere.api.java.record.io.TextInputFormat;
import eu.stratosphere.configuration.Configuration;
import eu.stratosphere.test.iterative.nephele.ConfigUtils;
import eu.stratosphere.types.DoubleValue;
import eu.stratosphere.types.LongValue;
import eu.stratosphere.types.Record;

import java.util.regex.Pattern;

public class DanglingPageGenerateRankInputFormat extends TextInputFormat {

	private static final long serialVersionUID = 1L;

	private DoubleValue initialRank;

	private static final Pattern SEPARATOR = Pattern.compile("[, \t]");

	@Override
	public void configure(Configuration parameters) {
		long numVertices = ConfigUtils.asLong("pageRank.numVertices", parameters);
		initialRank = new DoubleValue(1 / (double) numVertices);
		super.configure(parameters);
	}

	@Override
	public Record readRecord(Record target, byte[] bytes, int offset, int numBytes) {
		String str = new String(bytes, offset, numBytes);

		String[] tokens = SEPARATOR.split(str);

		long vertexID = Long.parseLong(tokens[0]);
		boolean isDangling = tokens.length > 1 && Integer.parseInt(tokens[1]) == 1;

		target.clear();
		target.addField(new LongValue(vertexID));
		target.addField(initialRank);
		target.addField(new BooleanValue(isDangling));

		return target;
	}
}
