/**
 * Copyright 2022 ConsenSys Software Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License. You may obtain
 * a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software dis-
 * tributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package evmtesttool;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiPredicate;

import org.apache.commons.cli.*;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import evmtesttool.core.StateTest;
import evmtesttool.core.Trace;
import evmtesttool.core.TraceTest;
import evmtesttool.core.Transaction;
import evmtesttool.core.WorldState;
import evmtesttool.evms.Geth;

public class Main {
	private final Path inFile;
	private final PrintStream out = System.out;
	private final int timeout = 10; // in seconds;
	private boolean prettify = false;
	private BiPredicate<String,StateTest.Instance> filter = (f,i) -> true;

	public Main(String filename) {
		this.inFile = Path.of(filename);
	}

	private Main setPrettify(boolean flag) {
		this.prettify = flag;
		return this;
	}

	private Main setFilter(BiPredicate<String,StateTest.Instance> filter) {
		this.filter = this.filter.and(filter);
		return this;
	}

	public void run() throws IOException, JSONException {
		// Read contents of fixture file
		String contents = Files.readString(inFile);
		JSONObject json = convertState2TraceTest(new JSONObject(contents));
		if(prettify) {
			out.print(json.toString(2));
		} else {
			out.print(json.toString());
		}
		out.flush();
	}

	public JSONObject convertState2TraceTest(JSONObject stfile) throws JSONException {
		Geth geth = new Geth().setTimeout(timeout * 1000);
		JSONObject json = new JSONObject();
		// Convert
		for (StateTest st : StateTest.fromJSON(stfile)) {
			WorldState state = st.getPreState();
			Map<String, List<TraceTest.Instance>> forks = new HashMap<>();
			for (String fork : st.getForks()) {
				ArrayList<TraceTest.Instance> instances = new ArrayList<>();
				for (StateTest.Instance inst : st.getInstances(fork)) {
					if (filter.test(fork, inst)) {
						System.out.println("Generating test " + inst);
						Transaction tx = inst.instantiate();
						Trace t = geth.execute(state, tx);
						// Test can convert transaction to JSON, and then back again.
						instances.add(new TraceTest.Instance(tx, t, tx.expectation));
					}
					if (instances.size() != 0) {
						forks.put(fork, instances);
					}
				}
			}
			TraceTest tt = new TraceTest(state, forks);
			json.put(st.getName(),tt.toJSON());
		}
		return json;
	}

	// =====================================================================================
	// Command-Line Interface
	// =====================================================================================

	private static final Option[] OPTIONS = new Option[] {
			// What options do we need?
			new Option("fork", true, "Restrict to a particular fork (e.g. 'Berlin')."),
			new Option("prettify", false, "Output \"pretty\" json"),
	};

	public static CommandLine parseCommandLine(String[] args) {
		// Configure command-line options.
		Options options = new Options();
		for(Option o : OPTIONS) { options.addOption(o); }
		CommandLineParser parser = new DefaultParser();
		// use to read Command Line Arguments
		HelpFormatter formatter = new HelpFormatter();  // // Use to Format
		try {
			return parser.parse(options, args);  //it will parse according to the options and parse option value
		} catch (ParseException e) {
			System.out.println(e.getMessage());
			formatter.printHelp("evmtesttool", options);
			System.exit(1);
			return null;
		}
	}

	public static void main(String[] args) throws IOException, JSONException {
		// Parse command-line arguments.
		CommandLine cmd = parseCommandLine(args);
		// Continue processing remaining arguments.
		args = cmd.getArgs();
		//
		for(String st : args) {
			run(cmd,st);
		}
	}

	public static void run(CommandLine cmd, String filename) throws IOException, JSONException {
		Main m = new Main(filename);
		if(cmd.hasOption("prettify")) {
			m = m.setPrettify(true);
		}
		if(cmd.hasOption("fork")) {
			System.out.println("SETTING FILTER");
			String fork = cmd.getOptionValue("fork");
			m.setFilter((f,i) -> f.equals(fork));
		}
		m.run();
	}
}
