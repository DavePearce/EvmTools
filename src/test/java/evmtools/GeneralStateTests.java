/*
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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiPredicate;
import java.util.stream.Stream;

import org.json.JSONException;
import org.json.JSONObject;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import evmtools.core.*;
import evmtools.evms.Geth;

/**
 * A test runner for executing the <code>"GeneralStateTests</code> provided as
 * part of the Ethereum Reference tests (see
 * <a href="https://github.com/ethereum/tests/">here</a>). The test runner works
 * by combining two pieces of information for each tests:
 *
 * <ul>
 * <li><b>Test Fixture</b>. This is the (filled) tests provided by the Ethereum
 * reference tests, and accessible from this repository within the
 * <code>fixtures/</code> directory (which is a submodule).</li>
 * <li><b>Internal State</b>. This internal state information generated from
 * running the corresponding fixture using an existing tool, such as Geth's
 * `evm` command-line tool. This internal state supplements the test fixture
 * which information about the EVM internals during execution of the test (e.g.
 * the value of the stack or memory after executing each bytecode). This data is
 * stored within the <code>tests/</code> directory, where the layout follows
 * that of the <code>fixtures</code> directory.</li>
 * </ul>
 *
 * This test runner is "driven" by the test files stored within the
 * <code>tests/</code>. That means a test is only run when there is a
 * corresponding entry in this file.
 *
 * @author David J. Pearce
 *
 */
public class GeneralStateTests {
	/**
	 * Fork which (for now) I'm assuming we are running on. All others are ignored.
	 */
	public final static String FORK = "Berlin";
	/**
	 * Configure timeout for each test.
	 */
	public final static int TIMEOUT = 60; // seconds
	/**
	 * The directory containing the reference test test files.
	 */
	public final static Path FIXTURES_DIR = Path.of("fixtures/GeneralStateTests");

	public final static String[] INCLUDES = {
			//"**/*.json", // everything.
			"stEIP1559/typeTwoBerlin.json"
//			"stExample/*.json",",
//			"stStaticCall/*.json",",
//			"stReturnDataTest/*.json",",
//			"stWalletTest/walletConstructionOOG.json",",
//			"stRevertTest/*.json",",
//			"stMemoryTest/*.json",",
//			"stSLoadTest/*.json",",
//			"stSStoreTest/*.json",",
//			"stCreateTest/*.json",",
//			"VMTests/vmArithmeticTest/*.json",",
//			"VMTests/vmBitwiseLogicOperation/*.json",",
//			"VMTests/vmIOAndFlowOperations/*.json","
	};

	public final static String[] EXCLUDES = {
			"stStaticCall/*50000*.json",
			"VMTests/vmPerformance/*.json", // some performance issues here
	};

	@ParameterizedTest
	@MethodSource("allTestFiles")
	public void tests(StateTest.Instance instance) throws IOException, JSONException {
		runTest(instance.getName(), instance.getEnvironment(), instance.getWorldState(), instance.instantiate(), instance.outcome());
	}

	// Here we enumerate all available test cases.
	private static Stream<StateTest.Instance> allTestFiles() throws IOException {
		return readTestFiles(FIXTURES_DIR, (f,i) -> f.equals(FORK));
	}

	/**
	 * Run the given state test through one (or more) EVM implementations, thereby
	 * generating the juicy trace data we are ultimately after.
	 *
	 * @param i
	 */
	private static void runTest(String name, Environment env, WorldState state, Transaction tx, Transaction.Outcome outcome) throws JSONException, IOException {
		Geth geth = new Geth().setTimeout(TIMEOUT * 1000);
		Trace trace = geth.t8n(FORK, env, state, tx).trace;
		// Test can convert transaction to JSON, and then back again.
		assertEquals(state, WorldState.fromJSON(state.toJSON()));
		//assertEquals(tx, Transaction.fromJSON(tx.toJSON()));
		//JSONArray t = trace.toJSON();
		System.out.println(trace);
//		System.out.println("OUTCOME: " + outcome);
		//assertEquals(trace, Trace.fromJSON(trace.toJSON()));
	}

	// ======================================================================
	// Data sources
	// ======================================================================

	/**
	 * Read all JSON files reachable (recursively) from a given directory. Each JSON
	 * file is loaded into memory, parsed into a sequence of one or more state tests
	 * and these are then instantiated into concrete test instances.
	 *
	 * @param dir
	 * @param filter
	 * @return
	 * @throws IOException
	 */
	public static Stream<StateTest.Instance> readTestFiles(Path dir, BiPredicate<String,StateTest.Instance> filter) throws IOException {
		PathMatcher includes = createPathMatcher(INCLUDES);
		PathMatcher excludes = createPathMatcher(EXCLUDES);

		ArrayList<StateTest> testcases = new ArrayList<>();
		//
		Files.walk(dir,10).forEach(f -> {
			Path rf = dir.relativize(f);
			if (f.toString().endsWith(".json") && includes.matches(rf) && !excludes.matches(rf)) {
				try {
					// Read contents of fixture file
					String contents = Files.readString(f);
					// Convert fixture into JSON
					JSONObject json = new JSONObject(contents);
					// Parse into one or more tests
					List<StateTest> tests = StateTest.fromJSON(json);
					// Add them all
					testcases.addAll(tests);
				} catch(JSONException e) {
					System.err.println("Problem parsing file into JSON " + f + " (" + e.getMessage() + ")");
				} catch(IOException e) {
					System.err.println("Problem reading file " + f + " (" + e.getMessage() + ")");
				} catch(Exception e) {
					System.err.println("Problem reading file " + f + " (" + e.getMessage() + ")");
				}
			}
		});
		// Instantiate each state test into one or more
		return testcases.stream().map(st -> st.selectInstances(filter)).flatMap(l -> l.stream());
	}

	public static PathMatcher createPathMatcher(String[] lines) throws IOException {
		FileSystem fs = FileSystems.getDefault();
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
