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
package evmtools.evms;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import evmtools.core.Environment;
import evmtools.core.Trace;
import evmtools.core.Transaction;
import evmtools.core.WorldState;
import evmtools.util.AbstractExecutable;
import evmtools.util.Bytecodes;

import static evmtools.util.Arrays.trimFront;
import evmtools.util.Hex;

/**
 * An interface to Geth's command-line <code>evm</code> tool. This allows us to
 * execute a state test and get back the trace data. Essentially, this class
 * manages two tasks: (1) executing the tool as a (sub) process; (2) marshing
 * the state test data into and out of the tool.
 *
 * @author David J. Pearce
 *
 */
public class Geth extends AbstractExecutable {
	/**
	 * Command to use to execture Geth.
	 */
	private final String cmd = "evm";

	/**
	 * Parameter which controls how much of the stack is actually recorded in the
	 * trace.
	 */
	private int stackSize = 10;

	/**
	 * Set the timeout (in milliseconds)
	 *
	 * @param timeout
	 * @return
	 */
	public Geth setTimeout(int timeout) {
		this.timeout = timeout;
		return this;
	}

	/**
	 * Set the maximum stack size that is stored at every step.
	 *
	 * @param stackSize
	 * @return
	 */
	public Geth setStackSize(int stackSize) {
		this.stackSize = stackSize;
		return this;
	}

	/**
	 * Check whether Geth is installed or not and report version.
	 *
	 * @return
	 */
	public String version() {
		ArrayList<String> command = new ArrayList<>();
		command.add(cmd);
		command.add("--version");
		try {
			return exec(command);
		} catch (IOException e) {
			return null;
		} catch (InterruptedException e) {
			return null;
		}
	}

	public Trace t8n(String fork, Environment env, WorldState pre, Transaction tx) throws JSONException, IOException {
		Path tempDir = null;
		String envFile = null;
		String allocFile = null;
		String txsFile = null;
		try {
			tempDir = createTemporaryDirectory();
			envFile = createEnvFile(tempDir,env);
			allocFile = createAllocFile(tempDir,pre);
			txsFile = createTransactionsFile(tempDir,tx);
			// Build up the command
			ArrayList<String> command = new ArrayList<>();
			command.add(cmd);
			command.add("t8n");
			command.add("--state.fork");
			command.add(fork);
			// Trace info
			command.add("--trace");
			command.add("--trace.memory"); // ensures memory included in trace output
			// Input
			command.add("--input.env");
			command.add(envFile);
			command.add("--input.alloc");
			command.add(allocFile);
			command.add("--input.txs");
			command.add(txsFile);
			command.add("--output.basedir");
			command.add(tempDir.toString());
			// Output
			command.add("--output.alloc");
			command.add("alloc-out.json");
			//
			String out = exec(command);
			//
			if(out != null) {
				// Parse into JSON. Geth produces one line per trace element.
				return readTraceFile(tempDir);
			} else {
				// Geth failed for some reason
				throw new RuntimeException();
			}
		} catch (InterruptedException e) {
			return null;
		} finally {
			forceDelete(tempDir);
		}
	}

	// ===============================================================================
	// Parsers for trace output
	// ===============================================================================

	private Trace readTraceFile(Path dir) throws IOException {
		List<Trace> tr = new ArrayList<>();
		//
		Files.walk(dir, 10).forEach(f -> {
			try {
				if (f.toString().endsWith(".jsonl")) {
					if (tr.size() > 1) {
						throw new IllegalArgumentException("multiple trace files detected");
					}
					TraceIterator iter = new TraceIterator(f);
					tr.add(parseTrace(iter, 0));
				}
			} catch (JSONException e) {
				throw new RuntimeException("Unable to parse JSON trace file",e);
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
		});
		return tr.get(0);
	}

	/**
	 * Parse an execution trace at a given depth. Observe that this may contain
	 * nested traces within which correspond to contract calls.
	 *
	 * @param scanner
	 * @param depth
	 * @return
	 * @throws JSONException
	 */
	private Trace parseTrace(TraceIterator iter, int depth) throws JSONException {
		// Parse into JSON. Geth produces one line per trace element.
		ArrayList<Trace.Element> elements = new ArrayList<>();
		while (iter.hasNext()) {
			JSONObject json = iter.next();
			if (json.has("output")) {
				return parseTraceOutcome(json, elements);
			} else if(json.has("error")) {
				String error = json.getString("error");
				if (!isPostError(parseError(error))) {
					elements.add(parseStep(json, stackSize));
				}
				return parseTraceOutcome(json, elements);
			} else {
				// Extract current depth
				int d = json.getInt("depth") - 1;
				// Check where we are upto
				if (depth == d) {
					elements.add(parseStep(json, stackSize));
				} else if (depth < d) {
					// Backtrack
					iter.previous();
					// Parse the trace for a nested call.
					Trace sub = parseTrace(iter, depth + 1);
					elements.add(new Trace.SubTrace(sub));
				} else {
					// Backtrack
					iter.previous();
					Trace.Element last = elements.get(elements.size()-1);
					if(last instanceof Trace.Step) {
						// Attempt to figure out return data, since Geth doesn't make this available to
						// us.
						byte[] returndata = extractReturnData((Trace.Step) last);
						return new Trace(elements, Trace.Outcome.RETURN, returndata);
					} else {
						throw new IllegalArgumentException("deadcode reached");
					}
				}
			}
		}
		if(elements.size() > 0) {
			throw new IllegalArgumentException("dead code reached");
		}
		return null;
	}
	/**
	 * Parse the outcome of an execution trace and create a completed
	 * <code>Trace</code> using the steps seen thus far.
	 *
	 * @param json
	 * @param elements
	 * @return
	 * @throws JSONException
	 */
	private Trace parseTraceOutcome(JSONObject json, List<Trace.Element> elements) throws JSONException {
		String error = json.optString("error", null);
		// Case analysis
//		if (error != null && error.equals("execution reverted")) {
//			byte[] data;
//			if(json.has("output")) {
//				data = Hex.toBytes(json.getString("output"));
//			} else {
//				data = extractReturnData(parseStep(json,stackSize));
//			}
//			return new Trace(elements, Trace.Outcome.REVERT, data);
//		} else
		if (error != null) {
			// Parse the error
			Trace.Outcome outcome = parseError(error);
			// FIXME: handle posterrors
			return new Trace(elements, outcome, null);
		} else {
			// Normal return (e.g. STOP or RETURN)
			byte[] data = Hex.toBytes(json.getString("output"));
			return new Trace(elements, Trace.Outcome.RETURN, data);
		}
	}

	/**
	 * Convert a Geth error message into an <code>Trace.Exception.Error</code>
	 * object.
	 *
	 * @param err
	 * @return
	 */
	private static Trace.Outcome parseError(String err) {
		switch(err) {
		case "gas uint64 overflow":
		case "out of gas":
			return Trace.Outcome.INSUFFICIENT_GAS;
		case "invalid jump destination":
			return Trace.Outcome.INVALID_JUMPDEST;
		case "return data out of bounds":
			return Trace.Outcome.RETURNDATA_OVERFLOW;
		case "returndata overflow":
			return Trace.Outcome.RETURNDATA_OVERFLOW;
		case "call depth exceeded":
			return Trace.Outcome.CALLDEPTH_EXCEEDED;
		case "write protection":
			return Trace.Outcome.WRITE_PROTECTION;
		case "max code size exceeded":
			return Trace.Outcome.CODESIZE_EXCEEDED;
		case "unknown":
			return Trace.Outcome.ERROR_UNKNOWN;
		default:
			if(err.startsWith("stack underflow")) {
				return Trace.Outcome.STACK_UNDERFLOW;
			} else if(err.startsWith("stack limit reached")) {
				return Trace.Outcome.STACK_OVERFLOW;
			} else if(err.startsWith("invalid opcode")) {
				return Trace.Outcome.INVALID_OPCODE;
			} else {
				return Trace.Outcome.ERROR_UNKNOWN;
			}
		}
	}


	/**
	 * Attempt to determine the return data by looking at the last instruction
	 * executed.
	 *
	 * @param last
	 * @return
	 */
	private byte[] extractReturnData(Trace.Step last) {
		switch (last.op) {
		case Bytecodes.STOP:
		case Bytecodes.SELFDESTRUCT:
			return new byte[0];
		case Bytecodes.RETURN:
		case Bytecodes.REVERT:
			// Extract return data from memory.
			int n = last.stack.length-1;
			BigInteger u0 = last.stack[n];
			BigInteger u1 = last.stack[n-1];
			return readFromMemory(last.memory,u0,u1);
		default:
			throw new IllegalArgumentException("unexpected end-of-trace (0x" + Integer.toHexString(last.op) + ")");
		}
	}

	/**
	 * Read from memory whilst padding anything read above what has been expanded.
	 *
	 * @param bytes
	 * @param _start
	 * @param _length
	 * @return
	 */
	private byte[] readFromMemory(byte[] bytes, BigInteger _start, BigInteger _length) {
		try {
			int start = _start.intValueExact();
			int len = _length.intValueExact();
			byte[] data = new byte[len];
			if(start < bytes.length) {
				int max = Math.min(bytes.length - start, len);
				System.arraycopy(bytes, start, data, 0, max);
			}
			return data;
		} catch(ArithmeticException e) {
			// This indicates we couldn't fit one of the bigint values into an int.
			return new byte[0];
		}
	}


	/**
	 * Parse an atomic execution step from a given JSON object.
	 *
	 * @param json
	 * @return
	 * @throws JSONException
	 */
	private static Trace.Step parseStep(JSONObject json, int stackSize) throws JSONException {
		int pc = json.getInt("pc");
		int op = json.getInt("op");
		// NOTE: Geth reports depth starting at 1, when in fact it should start at 0.
		// Therefore, we normalise at the point of generation here.
		int depth = json.getInt("depth") - 1;
		long gas = Hex.toBigInt(json.getString("gas")).longValueExact();
		// Memory is not usually reported until it is actually assigned something.
		byte[] memory = Hex.toBytesFromAbbreviated(json.optString("memory", "0x"));
		BigInteger[] stack = Trace.parseStackArray(json.getJSONArray("stack"));
		Map<BigInteger, BigInteger> storage;
		if (json.has("storage")) {
			storage = Trace.parseStorageMap(json.getJSONObject("storage"));
		} else {
			storage = new HashMap<>();
		}
		return new Trace.Step(pc, op, depth, gas, stack.length, trimFront(stackSize,stack), memory, storage);
	}


	/**
	 * A post error is something which is not an immediate pre-condition violation.
	 * In essence, we have to decide whether or not to include the instruction which
	 * generated the exception in the trace.
	 *
	 * @param err
	 * @return
	 * @throws JSONException
	 */
	private static boolean isPostError(Trace.Outcome err) throws JSONException {
		switch(err) {
		case RETURNDATA_OVERFLOW:
		case INVALID_JUMPDEST:
		case WRITE_PROTECTION:
		case INVALID_OPCODE:
			return true;
		default:
			return false;
		}
	}

//	public void parseElement(JSONObject json, List<Trace.Element> elements) throws JSONException {
//		if (json.has("error") && json.has("output")) {
//			String err = json.getString("error");
//			// Abnormal return (e.g. REVERT or exception)
//			if (err.equals("execution reverted")) {
//				byte[] data = Hex.toBytes(json.getString("output"));
//				elements.add(new Trace.Reverts(data));
//			} else if(err.equals("max code size exceeded")) {
//				Exception.Error e = parseError(err);
//				elements.add(new Trace.Exception(e));
//			} else {
//				throw new IllegalArgumentException("Unexpected error: \"" + err + "\"");
//			}
//		} else if (json.has("output")) {
//			// Normal return (e.g. STOP or RETURNS)
//			byte[] data = Hex.toBytes(json.getString("output"));
//			elements.add(new Trace.Returns(data));
//		} else if (json.has("pc")) {
//			Trace.Step step = parseStep(json, stackSize);
//			if(!json.has("error")) {
//				// Easy case: no error.
//				elements.add(step);
//			} else {
//				String err = json.getString("error");
//				if(err.equals("execution reverted")) {
//					// NOTE: whilst we could try to manage this by inserting the expected instance
//					// of <code>Trace.Reverts</code>, we would still have to figure out the right
//					// data to supply..
//					// elements.add(new Trace.Reverts(new byte[0]));
//				} else {
//					// Have an error, therefore need to decide whether the step is included or not.
//					Exception.Error e = parseError(err);
//					if(!isPostError(e)) {
//						elements.add(step);
//					}
//					elements.add(new Trace.Exception(e));
//				}
//			}
//		} else {
//			throw new IllegalArgumentException("unknown trace record: " + json.toString());
//		}
//	}

	private class TraceIterator {
		private ArrayList<String> lines;
		private int index;

		public TraceIterator(Path f) throws IOException {
			lines = new ArrayList<>();
			index = 0;
			Scanner scanner = new Scanner(f);
			while (scanner.hasNextLine()) {
				String line = scanner.nextLine();
				// NOTE: revert lines signaled by Geth are a bit inconsistent, so I ignore them.
				if(!line.contains("execution reverted")) {
					lines.add(line);
				}
			}
			scanner.close();
		}

		public boolean hasNext() {
			return index < lines.size();
		}

		public JSONObject next() throws JSONException {
			JSONObject json = peek();
			index = index + 1;
			return json;
		}

		public JSONObject peek() throws JSONException {
			return new JSONObject(lines.get(index));
		}

		public void previous() {
			index = index - 1;
		}
	}

	private static String createAllocFile(Path dir, WorldState pre)
			throws JSONException, IOException, InterruptedException {
		JSONObject json = pre.toJSON();
		byte[] bytes = json.toString(2).getBytes();
		return createTemporaryFile(dir, "alloc.json", bytes);
	}

	private static String createEnvFile(Path dir, Environment env)
			throws JSONException, IOException, InterruptedException {
		JSONObject json = env.toJSON();
		byte[] bytes = json.toString(2).getBytes();
		return createTemporaryFile(dir, "env.json", bytes);
	}

	private static String createTransactionsFile(Path dir, Transaction tx)
			throws JSONException, IOException, InterruptedException {
		JSONObject obj = tx.toJSON();
		// NOTE: for reasons unknown, Geth calls it "gas" not "gasLimit". Perhaps this
		// is historical.
		obj.put("gas",obj.get("gasLimit"));
		obj.remove("gasLimit");
		obj.remove("sender"); // unused, as recoverable from signing info.
		obj.put("v","");
		obj.put("r","");
		obj.put("s","");
		JSONArray json = new JSONArray();
		json.put(0,obj);
		//
		byte[] bytes = json.toString(2).getBytes();
		return createTemporaryFile(dir, "txs.json", bytes);
	}

	private static Path createTemporaryDirectory() throws IOException {
		return Files.createTempDirectory("geth");
	}

	/**
     * Write a given string into a temporary file which can then be checked by boogie.
     *
     * @param contents
     * @return
     */
    private static String createTemporaryFile(Path dir, String name, byte[] contents)
            throws IOException, InterruptedException {
    	File f = dir.resolve(name).toFile();
        // Open for writing
        FileOutputStream fout = new FileOutputStream(f);
        // Write contents to file
        fout.write(contents);
        // Done creating file
        fout.close();
        //
        return f.getAbsolutePath();
    }

    /**
	 * Force a directory and all its contents to be deleted.
	 *
	 * @param path
	 * @throws IOException
	 */
	public void forceDelete(Path path) throws IOException {
		if (Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) {
			try (DirectoryStream<Path> entries = Files.newDirectoryStream(path)) {
				for (Path entry : entries) {
					forceDelete(entry);
				}
			}
		}
		if (path.toFile().exists()) {
			Files.delete(path);
		}
	}
}
