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

	public Result t8n(String fork, Environment env, WorldState pre, Transaction tx) throws JSONException, IOException {		
		Path tempDir = null;
		String envFile = null;
		String allocFile = null;
		String txsFile = null;
		try {
			tempDir = createTemporaryDirectory();
			envFile = createEnvFile(tempDir,env,fork);
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

	public class Result {
		public final Transaction.Outcome outcome;
		public final byte[] data;
		public final Trace trace;

		public Result(Transaction.Outcome outcome, byte[] data, Trace trace) {
			this.outcome = outcome;
			this.trace = trace;
			this.data = data;
		}
	}

	// ===============================================================================
	// Parsers for trace output
	// ===============================================================================

	private Result readTraceFile(Path dir) throws IOException {
		List<Result> tr = new ArrayList<>();
		//
		Files.walk(dir, 10).forEach(f -> {
			try {
				if (f.toString().endsWith(".jsonl")) {
					if (tr.size() > 1) {
						throw new IllegalArgumentException("multiple trace files detected");
					}
					TraceIterator iter = new TraceIterator(f);
					if (iter.hasNext() && !iter.peek().has("output")) {
						Trace t = parseTraceAtDepth(iter, 1);
						tr.add(parseTransactionOutcome(iter,t));
					} else if(iter.hasNext()){
						tr.add(parseTransactionOutcome(iter,null));
					} else {
						tr.add(new Result(Transaction.Outcome.UNKNOWN, new byte[0], null));
					}
					iter.close();
				}
			} catch (JSONException e) {
				throw new RuntimeException("Unable to parse JSON trace file",e);
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
		});
		return tr.get(0);
	}

	private Result parseTransactionOutcome(TraceIterator iter, Trace trace) throws JSONException {
		if (iter.hasNext()) {
			JSONObject json = iter.next();
			byte[] data = Hex.toBytes(json.getString("output"));
			Transaction.Outcome outcome = Transaction.Outcome.RETURN;
			if (json.has("error")) {
				outcome = parseError(json.getString("error"));
			}
			return new Result(outcome, data, trace);
		} else {
			throw new IllegalArgumentException("deadcode reached");
		}
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
	private Trace parseTraceAtDepth(TraceIterator iter, int depth) throws JSONException {
		// Parse into JSON. Geth produces one line per trace element.
		ArrayList<Trace.Element> elements = new ArrayList<>();
		JSONObject json = iter.peek();
		// Continue parsing the trace at the given depth, including anything within it.
		while (!json.has("output") && json.getInt("depth") >= depth) {
			int d = json.getInt("depth");
			// Handle nested case
			if (d > depth) {
				// Parse nested trace
				Trace sub = parseTraceAtDepth(iter, depth + 1);
				elements.add(new Trace.SubTrace(sub));
			} else if (json.has("error")) {
				// Indicates this step caused an exception, so we just have to decide whether or
				// not the offending instruction should be included.
				String error = json.getString("error");
				if (!isPostError(parseError(error))) {
					elements.add(parseStep(json, stackSize));
				}
				iter.next();
				return parseTraceError(json, elements);
			} else {
				elements.add(parseStep(json, stackSize));
				iter.next();
			}
			// Peek next step
			json = iter.peek();
		}
		// Sanity check what to do
		return parseTraceReturn(json, elements);
	}

	private Trace parseTraceError(JSONObject json, List<Trace.Element> elements) throws JSONException {
		// Exception terminator
		Transaction.Outcome outcome = parseError(json.getString("error"));
		// Extract return bytes (if any)
		byte[] data = (outcome == Transaction.Outcome.REVERT) ? Hex.toBytes(json.getString("output")) : new byte[0];
		//
		return new Trace(elements, outcome, data);
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
	private Trace parseTraceReturn(JSONObject json, List<Trace.Element> elements) throws JSONException {
		Trace.Element last = elements.get(elements.size() - 1);
		if (last instanceof Trace.Step) {
			// Extract the return data.
			Transaction.Outcome outcome = isReturn((Trace.Step) last) ? Transaction.Outcome.RETURN : Transaction.Outcome.REVERT;;
			byte[] returndata = extractReturnData((Trace.Step) last);
			return new Trace(elements, outcome, returndata);
		} else {
			throw new IllegalArgumentException("deadcode reached");
		}
	}

	/**
	 * Convert a Geth error message into an <code>Transaction.Outcome</code>
	 * object.
	 *
	 * @param err
	 * @return
	 */
	private static Transaction.Outcome parseError(String err) {
		switch(err) {
		case "execution reverted":
			return Transaction.Outcome.REVERT;
		case "gas uint64 overflow":
		case "out of gas":
			return Transaction.Outcome.OUT_OF_GAS;
		case "invalid jump destination":
			return Transaction.Outcome.INVALID_JUMPDEST;
		case "return data out of bounds":
			return Transaction.Outcome.RETURNDATA_OVERFLOW;
		case "returndata overflow":
			return Transaction.Outcome.RETURNDATA_OVERFLOW;
		case "call depth exceeded":
			return Transaction.Outcome.CALLDEPTH_EXCEEDED;
		case "write protection":
			return Transaction.Outcome.WRITE_PROTECTION;
		case "max code size exceeded":
			return Transaction.Outcome.CODESIZE_EXCEEDED;
		case "contract creation code storage out of gas":
			return Transaction.Outcome.CREATION_OUT_OF_GAS;
		case "must not begin with 0xef":
			return Transaction.Outcome.INVALID_EOF;
		default:
			if(err.startsWith("stack underflow")) {
				return Transaction.Outcome.STACK_UNDERFLOW;
			} else if(err.startsWith("stack limit reached")) {
				return Transaction.Outcome.STACK_OVERFLOW;
			} else if(err.startsWith("invalid opcode")) {
				return Transaction.Outcome.INVALID_OPCODE;
			} else {
				throw new IllegalArgumentException(err);
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
	 * Check whether last instruction was a revert or not.
	 *
	 * @param last
	 * @return
	 */
	private boolean isReturn(Trace.Step last) {
		switch (last.op) {
		case Bytecodes.STOP:
		case Bytecodes.SELFDESTRUCT:
		case Bytecodes.RETURN:
			return true;
		case Bytecodes.REVERT:
			return false;
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
	private static boolean isPostError(Transaction.Outcome err) throws JSONException {
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

	private final class TraceIterator {
		private static final boolean debug = false;
		private final Scanner scanner;
		private JSONObject next;

		public TraceIterator(Path f) throws IOException, JSONException {
			this.scanner = new Scanner(f);
		}

		public JSONObject next() throws JSONException {
			JSONObject json = peek();
			next = null;
			return json;
		}

		/**
		 * Peek the next line from the input file. If we have a cached version of it,
		 * then that is returned directly.
		 *
		 * @return
		 * @throws JSONException
		 */
		public JSONObject peek() throws JSONException {
			if (next == null) {
				String line = scanner.nextLine();
				next = new JSONObject(line);
				// NOTE: revert lines signaled by Geth are a bit inconsistent, so I ignore them.
				while (next.has("pc") && next.optString("error","").equals("execution reverted")) {
					if(debug) { System.out.println("DROP: " + line); }
					line = scanner.nextLine();
					next = new JSONObject(line);
				}
				if(debug) { System.out.println("LINE: " + line); }
			}
			return next;
		}

		public boolean hasNext() {
			return next != null || scanner.hasNext();
		}

		public void close() {
			scanner.close();
		}
	}

	private static String createAllocFile(Path dir, WorldState pre)
			throws JSONException, IOException, InterruptedException {
		JSONObject json = pre.toJSON();
		byte[] bytes = json.toString(2).getBytes();
		return createTemporaryFile(dir, "alloc.json", bytes);
	}

	private static String createEnvFile(Path dir, Environment env, String fork)
			throws JSONException, IOException, InterruptedException {
		JSONObject json = env.toJSON();
		if(isPostMerge(fork)) {
			json.remove("currentDifficulty");
		}
		// FIXME: following line is something of a hack :)
		json.put("parentBeaconBlockRoot", "0x0000000000000000000000000000000000000000000000000000000000000000");
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

	public static boolean isPostMerge(String fork) {
		switch(fork.toUpperCase()) {
		case "BERLIN":
		case "LONDON":
			return false;
		case "SHANGHAI":
		case "CANCUN":
			return true;
		default:
			throw new IllegalArgumentException("unknown fork encountered \"" + fork + "\"");
		}
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
