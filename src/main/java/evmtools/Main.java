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
package evmtools;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiPredicate;

import org.apache.commons.cli.*;
import org.json.JSONException;
import org.json.JSONObject;

import evmtools.core.StateTest;
import evmtools.core.Trace;
import evmtools.core.TraceTest;
import evmtools.core.Transaction;
import evmtools.core.WorldState;
import evmtools.evms.Geth;
import evmtools.util.OutFile;

public class Main {
	private final Path inFile;
	private final OutFile out;
	private final int timeout = 10; // in seconds;
	private boolean prettify = false;
	private boolean abbreviate = true;
	private BiPredicate<String,StateTest.Instance> filter = (f,i) -> true;

	public Main(Path infile, OutFile out) {
		this.out = out;
		this.inFile = infile;
	}

	private Main prettify(boolean flag) {
		this.prettify = flag;
		return this;
	}

	private Main filter(BiPredicate<String,StateTest.Instance> filter) {
		this.filter = this.filter.and(filter);
		return this;
	}

	private Main abbreviate(boolean flag) {
		this.abbreviate = flag;
		return this;
	}

	public void run() throws IOException, JSONException {
		// Read contents of fixture file
		String contents = Files.readString(inFile);
		JSONObject json = convertState2TraceTest(new JSONObject(contents));
		if(prettify) {
			out.print(json.toString(2));
		} else {
			out.print(json.toString()); // crashes here.
		}
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
						Transaction tx = inst.instantiate();
						Trace t = geth.run(inst.getEnvironment(), state, tx);
						// Test can convert transaction to JSON, and then back again.
						instances.add(new TraceTest.Instance(tx, t, inst.expect));
					}
					if (instances.size() != 0) {
						forks.put(fork, instances);
					}
				}
			}
			TraceTest tt = new TraceTest(st.getName(), state, forks);
			json.put(st.getName(),tt.toJSON(abbreviate));
		}
		return json;
	}

	// =====================================================================================
	// Command-Line Interface
	// =====================================================================================

	private static String RED = "\u001b[31m";
	private static String GREEN = "\u001b[32m";
	private static String WHITE = "\u001b[37m";
	private static String YELLOW = "\u001b[33m";
	private static String RESET = "\u001b[0m";

	private static final Option[] OPTIONS = new Option[] {
			// What options do we need?
			new Option("dir", true, "Read all state tests from given dir"),
			new Option("out", true, "Directory to write generate files"),
			new Option("fork", true, "Restrict to a particular fork (e.g. 'Berlin')."),
			new Option("prettify", false, "Output \"pretty\" json"),
			new Option("abbreviate", true, "Enable/Disable hex string abbreviation (default is enabled)"),
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
			formatter.printHelp("evmtool", options);
			System.exit(1);
			return null;
		}
	}

	public static void main(String[] args) throws IOException, JSONException {
		// Parse command-line arguments.
		CommandLine cmd = parseCommandLine(args);
		//
		if (cmd.hasOption("dir")) {
			Path dir = Path.of(cmd.getOptionValue("dir"));
			ArrayList<String> filenames = new ArrayList<>();
			//
			Files.walk(dir).forEach(f -> {
				String fs = dir.relativize(f).toString();
				if (fs.endsWith(".json")) {
					filenames.add(fs);
				}
			});
			//
			for (int i = 0; i != filenames.size(); ++i) {
				String f = filenames.get(i);
				System.out.print(YELLOW + "\r(" + i + "/" + filenames.size() + ") ");
				System.out.print(RESET + f);
				try {
					run(cmd, dir, f);
					System.out.println();
				} catch (Exception e) {
					System.out.println(RED + " [" + e.getMessage() + "]");
				}
			}
		} else {
			// Continue processing remaining arguments.
			args = cmd.getArgs();
			//
			for (String st : args) {
				run(cmd, Path.of("."), st);
			}
		}
	}

	public static void run(CommandLine cmd, Path dir, String filename) throws IOException, JSONException {
		OutFile out = determineOutFile(cmd, filename);

		Main m = new Main(dir.resolve(filename), out);
		if (cmd.hasOption("prettify")) {
			m = m.prettify(true);
		}
		if (cmd.hasOption("abbreviate")) {
			m = m.abbreviate(Boolean.parseBoolean(cmd.getOptionValue("abbreviate")));
		}
		if (cmd.hasOption("fork")) {
			String fork = cmd.getOptionValue("fork");
			m.filter((f, i) -> f.equals(fork));
		}
		m.run();
		out.close();
	}

	public static OutFile determineOutFile(CommandLine cmd, String filename) {
		if (cmd.hasOption("out")) {
			Path outdir = Path.of(cmd.getOptionValue("out"));
			Path outfile = outdir.resolve(filename);
			// Make enclosing directories as necessary
			outfile.toFile().getParentFile().mkdirs();
			// Create relevant output streams.
			return new OutFile.LazyOutFile(outfile.toFile());
		} else {
			// Default is to write output to console
			return new OutFile.PrintOutFile(System.out);
		}
	}
}
