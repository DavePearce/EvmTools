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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import evmtools.core.Transaction.Outcome;
import evmtools.util.Hex;

public class TraceTest {
	/**
	 * Every individual test has a name.
	 */
	private final String name;
	/**
	 * Defines the genesis information for the state test.
	 */
	private final WorldState state;
	/**
	 * Various environmental parameters (e.g. difficulty).
	 */
	private final Environment env;
	/**
	 * Specific instances of this trace test (arranged by fork).
	 */
	private final Map<String,List<Instance>> forks;

	public TraceTest(String name, WorldState state, Environment env, Map<String, List<Instance>> instances) {
		this.name = name;
		this.state = state;
		this.env = env;
		this.forks = instances;
		// Associate each instance with this test
		for(List<Instance> is : instances.values()) {
			for(Instance i : is) {
				i.parent = this;
			}
		}
	}

	public WorldState getWorldState() {
		return state;
	}

	public Environment getEnvironment() {
		return env;
	}

	@Override
	public String toString() {
		try {
			return toJSON(true).toString();
		} catch (JSONException e) {
			return null;
		}
	}

	public Set<String> getForks() {
		return forks.keySet();
	}

	public boolean hasInstances(String fork) {
		return forks.containsKey(fork);
	}

	public List<Instance> getInstances(String fork) {
		return forks.get(fork);
	}

	/**
	 * Convert this test into a JSON file.
	 *
	 * @param abbreviate Enable the use of abbreviated hex strings (recommended).
	 *
	 * @return
	 */
	public JSONObject toJSON(boolean abbreviate) throws JSONException {
		JSONObject json = new JSONObject();
		json.put("pre", state.toJSON());
		json.put("env", env.toJSON());
		JSONObject tests = new JSONObject();
		for(Map.Entry<String,List<Instance>> e : forks.entrySet()) {
			String fork = e.getKey();
			List<Instance> instances = e.getValue();
			JSONArray is = new JSONArray();
			for (int i = 0; i != instances.size(); ++i) {
				is.put(i, instances.get(i).toJSON(abbreviate));
			}
			tests.put(fork, is);
		}
		json.put("tests", tests);
		// Done
		return json;
	}

	/**
	 * Parse a serialised form a transaction test.
	 *
	 * @param json
	 * @return
	 */
	public static TraceTest fromJSON(String name, JSONObject json) throws JSONException {
		WorldState state = WorldState.fromJSON(json.getJSONObject("pre"));
		Environment env = Environment.fromJSON(json.getJSONObject("env"));
		JSONObject tests = json.getJSONObject("tests");
		Map<String,List<Instance>> forks = new HashMap<>();
		String[] names = JSONObject.getNames(tests);
		if(names != null) {
			// NOTE: names can be null when the test map is empty (which itself can arise if
			// there were no forks of interest in the original state test).
			for(String fork : names) {
				JSONArray is = tests.getJSONArray(fork);
				ArrayList<Instance> instances = new ArrayList<>();
				for(int i=0;i!=is.length();++i) {
					instances.add(Instance.fromJSON(is.getJSONObject(i)));
				}
				forks.put(fork,instances);
			}
		}
		return new TraceTest(name, state, env, forks);
	}

	public static class Instance {
		private TraceTest parent;
		private final String id;
		private final Tx transaction;

		public Instance(String id, Tx transaction) {
			this.id = id;
			this.transaction = transaction;
		}

		public Environment getEnvironment() {
			return parent.getEnvironment();
		}

		public WorldState getWorldState() {
			return parent.getWorldState();
		}

		public Tx getTransaction() {
			return transaction;
		}

		@Override
		public String toString() {
			return String.format("%s_%s",parent.name,id);
		}

		/**
		 * Convert this trace test into JSON.
		 *
		 * @param abbreviate Enable the use of abbreviated hex strings (recommended).
		 *
		 * @return
		 */
		public JSONObject toJSON(boolean abbreviate) throws JSONException {
			JSONObject json = new JSONObject();
			json.put("id", id);
			json.put("tx", transaction.toJSON(abbreviate));
			return json;
		}

		public static Instance fromJSON(JSONObject json) throws JSONException {
			String id = json.getString("id");
			Tx tx = Tx.fromJSON(json.getJSONObject("tx"));
			return new Instance(id, tx);
		}
	}

	/**
	 * A transaction augmented with trace information about how it should be
	 * executed.
	 *
	 * @author David J. Pearce
	 *
	 */
	public static class Tx {
		/**
		 * Details of the transaction used to drive the EVM.
		 */
		private final Transaction transaction;
		/**
		 * Expected outcome from attempting to execute the transaction.
		 */
		private final Transaction.Outcome outcome;
		/**
		 * Expected data (if any) from transaction.
		 */
		private final byte[] data;
		/**
		 * Execution trace for the transaction (which may be empty if the transaction
		 * failed immediately e.g. because insufficient funds).
		 */
		private final Trace trace;

		public Tx(Transaction transaction, Transaction.Outcome outcome, byte[] data, Trace trace) {
			this.transaction = transaction;
			this.outcome = outcome;
			this.data = data;
			this.trace = trace;
		}

		public Transaction getTransaction() {
			return transaction;
		}

		public Transaction.Outcome getOutcome() {
			return outcome;
		}

		public byte[] getData() {
			return data;
		}

		public Trace getTrace() {
			return trace;
		}

		public JSONObject toJSON(boolean abbreviate) throws JSONException {
			JSONObject json = new JSONObject();
			json.put("transaction",transaction.toJSON());
			json.put("outcome",outcome.toString());
			json.put("data",Hex.toAbbreviatedHexString(data));
			if(trace != null) {
				json.put("trace",trace.toJSON(abbreviate));
			}
			return json;
		}

		public static Tx fromJSON(JSONObject json) throws JSONException {
			Transaction tx = Transaction.fromJSON(json.getJSONObject("transaction"));
			Transaction.Outcome outcome = Transaction.Outcome.valueOf(json.getString("outcome"));
			byte[] data = Hex.toBytesFromAbbreviated(json.getString("data"));
			Trace trace = null;
			if(json.has("trace")) {
				trace = Trace.fromJSON(json.getJSONObject("trace"));
			}
			return new Tx(tx,outcome,data,trace);
		}
	}
}
