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

import java.util.Arrays;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import evmtools.core.StateTest.Instance;
import evmtools.core.Transaction.Expectation;

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
	 * Specific instances of this trace test (arranged by fork).
	 */
	private final Map<String,List<Instance>> forks;

	public TraceTest(String name, WorldState state, Map<String, List<Instance>> instances) {
		this.name = name;
		this.state = state;
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

	@Override
	public String toString() {
		try {
			return toJSON().toString();
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
	 * @return
	 */
	public JSONObject toJSON() throws JSONException {
		JSONObject json = new JSONObject();
		json.put("pre", state.toJSON());
		JSONObject tests = new JSONObject();
		for(Map.Entry<String,List<Instance>> e : forks.entrySet()) {
			String fork = e.getKey();
			List<Instance> instances = e.getValue();
			JSONArray is = new JSONArray();
			for (int i = 0; i != instances.size(); ++i) {
				is.put(i, instances.get(i).toJSON());
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
		return new TraceTest(name, state, forks);
	}

	public static class Instance {
		private TraceTest parent;
		private final Transaction transaction;
		private final Trace trace;
		private final Transaction.Expectation expectation;

		public Instance(Transaction transaction, Trace trace, Transaction.Expectation expectation) {
			this.transaction = transaction;
			this.trace = trace;
			this.expectation = expectation;
		}

		public WorldState getWorldState() {
			return parent.getWorldState();
		}

		public Transaction getTransaction() {
			return transaction;
		}

		public Trace getTrace() {
			return trace;
		}

		@Override
		public String toString() {
			int hash = transaction.hashCode();
			return String.format("%s_%x",parent.name,hash);
		}

		public JSONObject toJSON() throws JSONException {
			JSONObject json = new JSONObject();
			json.put("transaction", transaction.toJSON());
			json.put("trace", trace.toJSON());
			json.put("expect", expectation.toString());
			return json;
		}

		public static Instance fromJSON(JSONObject json) throws JSONException {
			Transaction tx = Transaction.fromJSON(json.getJSONObject("transaction"));
			Trace trace = Trace.fromJSON(json.getJSONArray("trace"));
			return new Instance(tx, trace, Expectation.valueOf(json.getString("expect")));
		}
	}
}
