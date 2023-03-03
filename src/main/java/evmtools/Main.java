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
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiPredicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
	private int stackSize = 10;
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

	private Main stackSize(int stackSize) {
		this.stackSize = stackSize;
		return this;
	}

	public int run() throws IOException, JSONException {
		// Read contents of fixture file
		String contents = Files.readString(inFile);
		JSONObject json = convertState2TraceTest(new JSONObject(contents));
		// Following line does cause a crash in some cases.
		String jsonStr = prettify ? json.toString(2) : json.toString();
		out.print(jsonStr);
		return jsonStr.length();
	}

	public JSONObject convertState2TraceTest(JSONObject stfile) throws JSONException, IOException {
		Geth geth = new Geth().setTimeout(timeout * 1000).setStackSize(stackSize);
		JSONObject json = new JSONObject();
		// Convert
		for (StateTest st : StateTest.fromJSON(stfile)) {
			WorldState state = st.getPreState();
			Map<String, List<TraceTest.Instance>> forks = new HashMap<>();
			for (String fork : st.getForks()) {
				ArrayList<TraceTest.Instance> instances = new ArrayList<>();
				for (StateTest.Instance inst : st.getInstances(fork)) {
					if (filter.test(fork, inst)) {
						String id = inst.getID();
						Transaction transaction = inst.instantiate();
						Geth.Result r = geth.t8n(fork, inst.getEnvironment(), state, transaction);
						// NOTE: we ignore the instance outcome here, since it is often wrong.
						TraceTest.Tx tx = new TraceTest.Tx(inst.instantiate(), r.outcome, r.data, r.trace);
						// Test can convert transaction to JSON, and then back again.
						instances.add(new TraceTest.Instance(id, tx));
					}
					if (instances.size() != 0) {
						forks.put(fork, instances);
					}
				}
			}
			TraceTest tt = new TraceTest(st.getName(), state, st.getEnvironment(), forks);
			json.put(st.getName(),tt.toJSON(abbreviate));
		}
		return json;
	}

	// =====================================================================================
	// Command-Line Interface
	// =====================================================================================

	private static final String RED = "\u001b[31m";
	private static final String GREEN = "\u001b[32m";
	private static final String WHITE = "\u001b[37m";
	private static final String YELLOW = "\u001b[33m";
	private static final String RESET = "\u001b[0m";
	private static final PathMatcher DEFAULT_INCLUDES = FileSystems.getDefault().getPathMatcher("glob:**/*.json");
	private static final PathMatcher DEFAULT_EXCLUDES = (p) -> false;
	private static final int ONE_MB = (1024 * 1024);

	private static final Option[] OPTIONS = new Option[] {
			// What options do we need?
			new Option("dir", true, "Read all state tests from given dir"),
			new Option("out", true, "Directory to write generate files"),
			new Option("gzip", false, "Compress generated files using gzip"),
			new Option("fork", true, "Restrict to a particular fork (e.g. 'Berlin')."),
			new Option("prettify", false, "Output \"pretty\" json"),
			new Option("abbreviate", true, "Enable/Disable hex string abbreviation (default is enabled)"),
			new Option("excludes", true, "Don't include tests matching globs in this file."),
			new Option("includes", true, "Only include tests matching globs in this file."),
			new Option("incremental", false, "Prohibits regeneration trace data when it already exists."),
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
		// Sanity check whether Geth is installed
		String gethVersion = new Geth().version();
		if(gethVersion == null) {
			System.err.println("*** geth not installed");
			System.exit(1);
		} else {
			System.out.println("Geth version: " + gethVersion);
		}
		//
		if (cmd.hasOption("dir")) {
			Path dir = Path.of(cmd.getOptionValue("dir"));
			ArrayList<Path> filenames = new ArrayList<>();
			PathMatcher includes = parseGlobFile(cmd.getOptionValue("includes"), DEFAULT_INCLUDES);
			PathMatcher excludes = parseGlobFile(cmd.getOptionValue("excludes"), DEFAULT_EXCLUDES);
			//
			Files.walk(dir).forEach(f -> {
				Path p = dir.relativize(f);
				if(includes.matches(p) && !excludes.matches(p)) {
					filenames.add(p);
				}
			});
			//
			for (int i = 0; i != filenames.size(); ++i) {
				Path f = filenames.get(i);
				// Check on whitelist
				System.out.print(YELLOW + "\r(" + i + "/" + filenames.size() + ") ");
				System.out.print(RESET + f);
				try {
					int len = run(cmd, dir, f);
					if(len >= ONE_MB) {
						System.out.println(GREEN + " [" + len/ONE_MB + "mb]");
					} else if(len < 0) {
						System.out.println(YELLOW + " [skipped]");
					} else {
						System.out.println();
					}
				} catch (Exception e) {
					System.out.println(RED + " [" + e.getMessage() + "]");
				}
			}
		} else {
			// Continue processing remaining arguments.
			args = cmd.getArgs();
			//
			for (String st : args) {
				run(cmd, Path.of("."), Path.of(st));
			}
		}
	}

	public static int run(CommandLine cmd, Path dir, Path filename) throws IOException, JSONException {
		OutFile out = determineOutFile(cmd, filename, cmd.hasOption("gzip"));
		if(cmd.hasOption("incremental") && out.exists()) {
			return -1;
		} else {
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
			int len = m.run();
			out.close();
			return len;
		}
	}

	public static OutFile determineOutFile(CommandLine cmd, Path filename, boolean gzip) {
		if (cmd.hasOption("out")) {
			Path outname = gzip ? filename.resolve(".gz") : filename;
			Path outdir = Path.of(cmd.getOptionValue("out"));
			Path outfile = outdir.resolve(outname);
			// Make enclosing directories as necessary
			outfile.toFile().getParentFile().mkdirs();
			// Create relevant output streams.
			return new OutFile.LazyOutFile(outfile.toFile(),gzip);
		} else {
			// Default is to write output to console
			return new OutFile.PrintOutFile(System.out);
		}
	}

	public static PathMatcher parseGlobFile(String filename, PathMatcher def) throws IOException {
		if (filename == null) {
			return def;
		} else {
			FileSystem fs = FileSystems.getDefault();
			List<String> lines = Files.readAllLines(Path.of(filename));
			final List<PathMatcher> matchers = new ArrayList<>();
			for (String line : lines) {
				matchers.add(fs.getPathMatcher("glob:" + line));
			}
			return (p) -> {
				for (PathMatcher m : matchers) {
					if (m.matches(p)) {
						return true;
					}
				}
				return false;
			};
		}
	}
}
