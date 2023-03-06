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
package evmtools.core;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.List;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import evmtools.util.Bytecodes;
import evmtools.util.Hex;

/**
 * Represents an internal trace of the Ethereum Virtual Machine whilst executing
 * a given sequence of bytecodes. Observe that this is a trace, meaning it can
 * span across multiple contract calls, etc.
 *
 * @author David J. Pearce
 *
 */
public class Trace {
	private final List<Element> elements;
	/**
	 * The outcome from the execution.
	 */
	private final Transaction.Outcome outcome;
	/**
	 * Data returned from the execution in the case of <code>RETURNS</code> or
	 * <code>REVERTS</code>. Otherwise, it is null.
	 */
	private final byte[] data;

	public Trace(List<Element> elements, Transaction.Outcome outcome, byte[] data) {
		this.elements = new ArrayList<>(elements);
		this.outcome = outcome;
		this.data = data;
	}

	public List<Element> getElements() {
		return elements;
	}

	public Transaction.Outcome getOutcome() {
		return outcome;
	}

	public byte[] getData() {
		if(outcome == Transaction.Outcome.RETURN || outcome == Transaction.Outcome.REVERT) {
			return data;
		} else {
			throw new IllegalArgumentException("data only for RETURNS or REVERTS");
		}
	}

	@Override
	public boolean equals(Object o) {
		if(o instanceof Trace) {
			Trace t = (Trace) o;
			return elements.equals(t.elements) && outcome.equals(t.outcome) && Arrays.equals(data,t.data);
		}
		return false;
	}

	@Override
	public int hashCode() {
		return elements.hashCode() ^ outcome.hashCode() ^ Arrays.hashCode(data);
	}

	@Override
	public String toString() {
		return toString(0);
	}

	public String toString(int depth) {
		StringBuilder bdr = new StringBuilder();
		for(Trace.Element e : elements) {
			bdr.append(e.toString(depth));
			bdr.append("\n");
		}
		bdr.append(indent(depth));
		bdr.append(outcome.toString());
		if(data.length > 0) {
			bdr.append("(");
			bdr.append(Hex.toAbbreviatedHexString(data));
			bdr.append(")");
		}
		return bdr.toString();
	}

	/**
	 * Convert this trace into JSON. By default, use abbreviated hex strings.
	 *
	 * @return
	 */
	public JSONObject toJSON() throws JSONException {
		return toJSON(true);
	}

	/**
	 * Convert this trace into JSON.
	 *
	 * @param Enable the use of abbreviated hex strings (recommended).
	 *
	 * @return
	 */
	public JSONObject toJSON(boolean abbreviate) throws JSONException {
		JSONObject json = new JSONObject();
		JSONArray steps = new JSONArray();
		for(int i=0;i!=elements.size();++i) {
			steps.put(i, elements.get(i).toJSON(abbreviate));
		}
		json.put("steps",steps);
		json.put("outcome",outcome.toString());
		json.put("data",Hex.toAbbreviatedHexString(data));
		return json;
	}

	public static Trace fromJSON(JSONObject json) throws JSONException {
		JSONArray steps = json.getJSONArray("steps");
		ArrayList<Element> elements = new ArrayList<>();
		for (int i = 0; i != steps.length(); ++i) {
			Trace.Element ith = Element.fromJSON(steps.getJSONObject(i));
			if(ith != null) {
				elements.add(ith);
			}
		}
		Transaction.Outcome outcome = Transaction.Outcome.valueOf(json.getString("outcome"));
		byte[] data = Hex.toBytesFromAbbreviated(json.getString("data"));
		return new Trace(elements, outcome, data);
	}

	/**
	 * Represents a single element of a trace (e.g. a single step of execution or a
	 * nested execution trace for a contract call).
	 *
	 * @author David J. Pearce
	 *
	 */
	public static interface Element {
		/**
		 * Convert a trace element into JSON.
		 *
		 * @param Enable the use of abbreviated hex strings.
		 * @return
		 */
		public JSONObject toJSON(boolean abbreviate) throws JSONException;

		/**
		 * Convert a trace element to a string at a given depth. This allows for some
		 * indentation.
		 *
		 * @param depth
		 * @return
		 */
		public String toString(int depth);

		/**
		 * Convert a <code>JSON</code> object into a <code>Trace</code> object. An
		 * example corresponding to a <code>Trace.Step</code> is the following:
		 *
		 * <pre>
		 * {"pc":0,"op":96,"gas":"0x5c878","gasCost":"0x3","memSize":0,"stack":[]}
		 * </pre>
		 *
		 * See EIP-3155 for more information on the format used <a href=
		 * "https://github.com/ethereum/EIPs/blob/master/EIPS/eip-3155.md">here</here>.
		 *
		 * @param json
		 * @return
		 * @throws JSONException
		 */
		public static Trace.Element fromJSON(JSONObject json) throws JSONException {
			if (json.has("pc")) {
				int pc = json.getInt("pc");
				int op = json.getInt("op");
				int depth = json.getInt("depth");
				int stackSize = json.getInt("stackSize");
				long gas = json.getLong("gas");
				// Memory is not usually reported until it is actually assigned something.
				byte[] memory = Hex.toBytesFromAbbreviated(json.optString("memory", "0x"));
				BigInteger[] stack = parseStackArray(json.getJSONArray("stack"));
				Map<BigInteger, BigInteger> storage;
				if (json.has("storage")) {
					storage = parseStorageMap(json.getJSONObject("storage"));
				} else {
					storage = new HashMap<>();
				}
				//
				return new Trace.Step(pc, op, depth, gas, stackSize, stack, memory, storage);
			} else if(json.has("steps")) {
				return new SubTrace(Trace.fromJSON(json));
			} else {
				throw new IllegalArgumentException("unknown trace record: " + json.toString());
			}
		}
	}

	/**
	 * Captures a single (non-terminating) execution step by the EVM.
	 *
	 * @author David J. Pearce
	 *
	 */
	public static class Step implements Element {
		public final int pc;
		public final int op;
		public final int depth;
		public final long gas;
		public final int stackSize;
		public final BigInteger[] stack;
		public final byte[] memory;
		public final HashMap<BigInteger,BigInteger> storage;

		public Step(int pc, int op, int depth, long gas, int stackSize, BigInteger[] stack, byte[] memory, Map<BigInteger,BigInteger> storage) {
			this.pc = pc;
			this.op = op;
			this.depth = depth;
			this.gas = gas;
			this.stackSize = stackSize;
			this.stack = stack;
			this.memory = memory;
			this.storage = new HashMap<>(storage);
		}

		@Override
		public boolean equals(Object o) {
			if(o instanceof Step) {
				Step s = (Step) o;
				return pc == s.pc && op == s.op && depth == s.depth && gas == s.gas && stackSize == s.stackSize
						&& Arrays.equals(stack, s.stack) && Arrays.equals(memory, s.memory)
						&& storage.equals(s.storage);
			}
			return false;
		}

		@Override
		public int hashCode() {
			return pc ^ op ^ stackSize ^ Arrays.hashCode(stack) ^ Arrays.hashCode(memory);
		}

		@Override
		public String toString(int depth) {
			String s = toStackString(stackSize,stack);
			String st = storage.toString();
			String m = Hex.toAbbreviatedHexString(memory);
			String os = Bytecodes.toString(op);
			String g = Hex.toHexString(BigInteger.valueOf(gas));
			String indent = indent(depth);
			if (memory.length > 0 && storage.size() > 0) {
				return String.format("%s%d:%s, gas=%s, stack=%s, memory=%s, storage=%s", indent, pc, os, g, s, m, st);
			} else if (memory.length > 0) {
				return String.format("%s%d:%s, gas=%s, stack=%s, memory=%s", indent, pc, os, g, s, m);
			} else if (storage.size() > 0) {
				return String.format("%s%d:%s, gas=%s, stack=%s, storage=%s", indent, pc, os, g, s, st);
			} else {
				return String.format("%s%d:%s, gas=%s, stack=%s", indent, pc, os, g, s);
			}
		}

		@Override
		public JSONObject toJSON(boolean abbreviate) throws JSONException {
			JSONObject json = new JSONObject();
			json.put("pc", pc);
			json.put("op", op);
			json.put("depth",depth);
			json.put("gas", gas);
			json.put("stackSize", stackSize);
			json.put("stack", toStackArray(stack));
			if(memory.length != 0) {
				// Only include if something to show.
				String hex = abbreviate ? Hex.toAbbreviatedHexString(memory) : Hex.toHexString(memory);
				json.put("memory", hex);
			}
			if(storage.size() != 0) {
				// Only include if something to show.
				JSONObject st = new JSONObject();
				for (Map.Entry<BigInteger, BigInteger> e : storage.entrySet()) {
					st.put(Hex.toHexString(e.getKey()), Hex.toHexString(e.getValue()));
				}
				json.put("storage", st);
			}
			// FIXME: include storage
			return json;
		}

		private static String toStackString(int size, BigInteger[] items) {
			final int n = items.length;
			//
			if (n >= size) {
				return Hex.toArrayString(items);
			} else {
				StringBuffer buf = new StringBuffer();
				buf.append("[ (");
				buf.append(Integer.toString(size - n));
				buf.append(" items) ... ");
				for (int i = 0; i != n; ++i) {
					if (i != 0) {
						buf.append(", ");
					}
					buf.append(Hex.toHexString(items[i]));
				}
				buf.append("]");
				return buf.toString();
			}
		}
	}

	/**
	 * This is just a wrapper around Trace.
	 *
	 * @author David J. Pearce
	 *
	 */
	public static class SubTrace implements Element {
		private final Trace trace;

		public SubTrace(Trace trace) {
			this.trace = trace;
		}

		public Trace getTrace() {
			return trace;
		}

		@Override
		public boolean equals(Object o) {
			if(o instanceof SubTrace) {
				SubTrace st = (SubTrace) o;
				return trace.equals(st.trace);
			}
			return false;
		}

		@Override
		public int hashCode() {
			return trace.hashCode();
		}

		@Override
		public JSONObject toJSON(boolean abbreviate) throws JSONException {
			return trace.toJSON(abbreviate);
		}

		@Override
		public String toString(int depth) {
			return trace.toString(depth+1);
		}
	}

	public static BigInteger[] parseStackArray(JSONArray arr) throws JSONException {
		BigInteger[] is = new BigInteger[arr.length()];
		for (int i = 0; i != is.length; ++i) {
			is[i] = Hex.toBigInt(arr.getString(i));
		}
		return is;
	}

	private static JSONArray toStackArray(BigInteger[] stack) throws JSONException {
		JSONArray arr = new JSONArray();
		for (int i = 0; i != stack.length; ++i) {
			arr.put(i,Hex.toHexString(stack[i]));
		}
		return arr;
	}

	public static Map<BigInteger, BigInteger> parseStorageMap(JSONObject json) throws JSONException {
		if (json == null) {
			return new HashMap<>();
		} else {
			HashMap<BigInteger, BigInteger> r = new HashMap<>();
			for (String addr : JSONObject.getNames(json)) {
				BigInteger value = Hex.toBigInt(json.getString(addr));
				r.put(Hex.toBigInt(addr), value);
			}
			return r;
		}
	}

	public static String indent(int depth) {
		StringBuilder bdr = new StringBuilder();
		for (int i = 0; i < Math.min(10, depth); ++i) {
			bdr.append(". ");
		}
		if(depth >= 10) {
			bdr.append(String.format("(0x%02x) ",depth));
		}
		return bdr.toString();
	}
}
