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

	public Trace(List<Element> elements) {
		this.elements = new ArrayList<>(elements);
	}

	public List<Element> getElements() {
		return elements;
	}

	@Override
	public boolean equals(Object o) {
		if(o instanceof Trace) {
			Trace t = (Trace) o;
			return elements.equals(t.elements);
		}
		return false;
	}

	@Override
	public int hashCode() {
		return elements.hashCode();
	}


	@Override
	public String toString() {
		return elements.toString();
	}

	/**
	 * Convert this trace into JSON.
	 *
	 * @param Enable the use of abbreviated hex strings (recommended).
	 *
	 * @return
	 */
	public JSONArray toJSON(boolean abbreviate) throws JSONException {
		JSONArray arr = new JSONArray();
		for(int i=0;i!=elements.size();++i) {
			arr.put(i, elements.get(i).toJSON(abbreviate));
		}
		return arr;
	}

	public static Trace fromJSON(JSONArray json) throws JSONException {
		ArrayList<Element> elements = new ArrayList<>();
		for (int i = 0; i != json.length(); ++i) {
			Trace.Element ith = Element.fromJSON(json.getJSONObject(i));
			if(ith != null) {
				elements.add(ith);
			}
		}
		return new Trace(elements);
	}

	/**
	 * Represents a single element of a trace (e.g. a single step of execution).
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
			if (json.has("revert")) {
				byte[] data = Hex.toBytes(json.getString("revert"));
				return new Trace.Reverts(data);
			} else if (json.has("error")) {
				// Parse error message into the appropriate error type. This is not super
				// pretty.
				String err = json.getString("error");
				return new Trace.Exception(Exception.Error.valueOf(err));
			} else if (json.has("return")) {
				// Normal return (e.g. STOP or RETURNS)
				byte[] data = Hex.toBytes(json.getString("return"));
				return new Trace.Returns(data);
			} else if (json.has("pc")) {
				int pc = json.getInt("pc");
				int op = json.getInt("op");
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
				return new Trace.Step(pc, op, stack, memory, storage);
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
		public final BigInteger[] stack;
		public final byte[] memory;
		public final HashMap<BigInteger,BigInteger> storage;

		public Step(int pc, int op, BigInteger[] stack, byte[] memory, Map<BigInteger,BigInteger> storage) {
			this.pc = pc;
			this.op = op;
			this.stack = stack;
			this.memory = memory;
			this.storage = new HashMap<>(storage);
		}

		@Override
		public boolean equals(Object o) {
			if(o instanceof Step) {
				Step s = (Step) o;
				return pc == s.pc && op == s.op && Arrays.equals(stack, s.stack) && Arrays.equals(memory, s.memory)
						&& storage.equals(s.storage);
			}
			return false;
		}

		@Override
		public int hashCode() {
			return pc ^ op ^ Arrays.hashCode(stack) ^ Arrays.hashCode(memory);
		}

		@Override
		public String toString() {
			String s = Hex.toArrayString(stack);
			String st = storage.toString();
			String m = Hex.toAbbreviatedHexString(memory);
			String os = Bytecodes.toString(op);
			if(memory.length > 0 && storage.size() > 0) {
				return String.format("{%d:%s, stack=%s, memory=%s, storage=%s}\n", pc, os, s, m, st);
			} else if(memory.length > 0) {
				return String.format("{%d:%s, stack=%s, memory=%s}\n", pc, os, s, m);
			} else if(storage.size() > 0) {
				return String.format("{%d:%s, stack=%s, storage=%s}\n", pc, os, s, st);
			} else {
				return String.format("{%d:%s, stack=%s}\n", pc, os, s);
			}
		}

		@Override
		public JSONObject toJSON(boolean abbreviate) throws JSONException {
			JSONObject json = new JSONObject();
			json.put("pc", pc);
			json.put("op", op);
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
	}

	/**
	 * Represents the successfull completion of the outermost (externally owned)
	 * contract call.
	 *
	 * @author David J. Pearce
	 *
	 */
	public static class Returns implements Element {
		public final byte[] data;

		public Returns(byte[] data) {
			this.data = data;
		}

		@Override
		public String toString() {
			String o = Hex.toHexString(data);
			return String.format("return(%s)\n",o);
		}

		@Override
		public boolean equals(Object o) {
			return o instanceof Returns && Arrays.equals(data,((Returns)o).data);
		}

		@Override
		public int hashCode() {
			return Arrays.hashCode(data);
		}

		@Override
		public JSONObject toJSON(boolean abbreviate) throws JSONException {
			JSONObject json = new JSONObject();
			json.put("return",Hex.toHexString(data));
			return json;
		}
	}

	/**
	 * Indicates a revert has occurred during execution.
	 *
	 * @author David J. Pearce
	 *
	 */
	public static class Reverts implements Element {
		public final byte[] data;

		public Reverts(byte[] data) {
			this.data = data;
		}

		@Override
		public String toString() {
			String o = Hex.toHexString(data);
			return String.format("revert(%s)\n",o);
		}

		@Override
		public boolean equals(Object o) {
			return o instanceof Reverts && Arrays.equals(data,((Reverts)o).data);
		}

		@Override
		public int hashCode() {
			return Arrays.hashCode(data);
		}

		@Override
		public JSONObject toJSON(boolean abbreviate) throws JSONException {
			JSONObject json = new JSONObject();
			json.put("revert",Hex.toHexString(data));
			return json;
		}
	}

	/**
	 * Indicates an internal exception has arisen during exection (e.g. stack
	 * underflow, etc).
	 *
	 * @author David J. Pearce
	 *
	 */
	public static class Exception implements Element {

		public enum Error {
			UNKNOWN,
			INSUFFICIENT_GAS,
			INVALID_OPCODE,
			STACK_UNDERFLOW,
			STACK_OVERFLOW,
			MEMORY_OVERFLOW,
			RETURNDATA_OVERFLOW,
			INVALID_JUMPDEST,
			CALLDEPTH_EXCEEDED;
		}

		private final Error code;

		public Exception(Error code) {
			this.code = code;
		}

		@Override
		public String toString() {
			return "exception(" + code.toString() + ")\n";
		}

		@Override
		public boolean equals(Object o) {
			if (o instanceof Exception) {
				Exception e = (Exception) o;
				return code.equals(e.code);
			}
			return false;
		}

		@Override
		public int hashCode() {
			return code.hashCode();
		}

		@Override
		public JSONObject toJSON(boolean abbreviate) throws JSONException {
			JSONObject json = new JSONObject();
			json.put("error",code.toString());
			return json;
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
}
