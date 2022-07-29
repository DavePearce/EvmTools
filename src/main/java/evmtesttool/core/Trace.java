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
package evmtesttool.core;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import evmtesttool.util.Hex;

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
	 * @return
	 */
	public JSONArray toJSON() throws JSONException {
		JSONArray arr = new JSONArray();
		for(int i=0;i!=elements.size();++i) {
			arr.put(i, elements.get(i).toJSON());
		}
		return arr;
	}

	public static Trace fromJSON(JSONArray json) throws JSONException {
		ArrayList<Element> elements = new ArrayList<>();
		for (int i = 0; i != json.length(); ++i) {
			elements.add(Element.fromJSON(json.getJSONObject(i)));
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
		 * @return
		 */
		public JSONObject toJSON() throws JSONException;

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
			if (json.has("error") && json.has("output")) {
				// Abnormal return (e.g. REVERT or exception)
				if (json.getString("error").equals("execution reverted")) {
					byte[] data = Hex.toBytes(json.getString("output"));
					return new Trace.Reverts(data);
				} else {
					// FIXME: confirm error code.
					return new Trace.Exception();
				}
			} else if (json.has("output")) {
				// Normal return (e.g. STOP or RETURNS)
				byte[] data = Hex.toBytes(json.getString("output"));
				return new Trace.Returns(data);
			} else {
				int pc = json.getInt("pc");
				// Memory is not usually reported until it is actually assigned something.
				byte[] memory = Hex.toBytes(json.optString("memory", "0x"));
				BigInteger[] stack = parseStackArray(json.getJSONArray("stack"));
				//
				return new Trace.Step(pc, stack, memory);
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
		public final BigInteger[] stack;
		public final byte[] memory;
		// FIXME: support storage!

		public Step(int pc, BigInteger[] stack, byte[] memory) {
			this.pc = pc;
			this.stack = stack;
			this.memory = memory;
		}

		@Override
		public boolean equals(Object o) {
			if(o instanceof Step) {
				Step s = (Step) o;
				return pc == s.pc && Arrays.equals(stack, s.stack) && Arrays.equals(memory, s.memory);
			}
			return false;
		}

		@Override
		public int hashCode() {
			return pc ^ Arrays.hashCode(stack) ^ Arrays.hashCode(memory);
		}

		@Override
		public String toString() {
			String s = Arrays.toString(stack);
			if(memory.length > 0) {
				String m = Hex.toHexString(memory);
				return String.format("{pc=%d, stack=%s, memory=%s}\n", pc, s, m);
			} else {
				return String.format("{pc=%d, stack=%s}\n", pc, s);
			}
		}

		@Override
		public JSONObject toJSON() throws JSONException {
			JSONObject json = new JSONObject();
			json.put("pc", pc);
			json.put("stack", toStackArray(stack));
			if(memory.length != 0) {
				// Only include if something to show.
				json.put("memory", Hex.toHexString(memory));
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
			return String.format("return(%s)",o);
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
		public JSONObject toJSON() throws JSONException {
			JSONObject json = new JSONObject();
			json.put("output",Hex.toHexString(data));
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
			return String.format("revert(%s)",o);
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
		public JSONObject toJSON() throws JSONException {
			JSONObject json = new JSONObject();
			json.put("error","execution reverted");
			json.put("output",Hex.toHexString(data));
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

		@Override
		public String toString() {
			return "error()";
		}

		@Override
		public boolean equals(Object o) {
			return o instanceof Exception;
		}

		@Override
		public int hashCode() {
			return 0;
		}

		@Override
		public JSONObject toJSON() throws JSONException {
			JSONObject json = new JSONObject();
			json.put("error","");
			json.put("output","");
			return json;
		}
	}

	private static BigInteger[] parseStackArray(JSONArray arr) throws JSONException {
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
}
