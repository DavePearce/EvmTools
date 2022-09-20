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
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiPredicate;
import java.util.stream.Collectors;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import evmtools.util.Hex;

/**
 * A state test represents the execution of a single contract call (i.e.
 * transaction) on an EVM blockchain. The state test may include genesis
 * information as necessary to preinitialise the blockchain for the test. A
 * state test is not, in fact, a single test but rather a <i>family</i> of
 * related tests. For example, we can instantiate a state test with a predefined
 * set of parameter values (<code>data</code>, <code>gas</code> and/or
 * <code>value</code>) to produce on or more concrete tests. Furthermore, each
 * concrete test is associated with a specific EVM fork (e.g. <i>Berlin</i>,
 * <i>London</i>, etc).
 *
 * @author David J. Pearce
 *
 */
public class StateTest {
	/**
	 * Every individual test has a name.
	 */
	private final String name;
	/**
	 * Maps a given fork to a given set of test instances to run.
	 */
	private final Map<String, List<Instance>> instances;
	/**
	 * Defines the genesis information for the state test.
	 */
	private final WorldState pre;
	/**
	 * Various environmental parameters (e.g. difficulty).
	 */
	private final Environment env;
	/**
	 * Provides necessary specifics for the transaction to be executed. Observe this
	 * is a <i>template</i> and not a concrete transaction. Thus, we must
	 * instantiate it with one or more instances.
	 */
	private final Transaction.Template transaction;

	public StateTest(String name, Map<String,List<Instance>> instances, WorldState pre, Environment env, Transaction.Template tx) {
		this.name = name;
		this.instances = instances;
		this.pre = pre;
		this.env = env;
		this.transaction = tx;
		// Associate each instance with this test
		for(List<Instance> is : instances.values()) {
			for(Instance i : is) {
				i.parent = this;
			}
		}
	}

	/**
	 * Get the name of this test.
	 *
	 * @return
	 */
	public String getName() {
		return name;
	}

	/**
	 * Get the genesis information which specifies the blockchain state prior to
	 * executing the test(s).
	 *
	 * @return
	 */
	public WorldState getPreState() {
		return pre;
	}

	/**
	 * Get the current environment which defines various parameters (e.g.
	 * difficulty).
	 *
	 * @return
	 */
	public Environment getEnvironment() {
		return env;
	}

	/**
	 * Get the set of forks supported by this test.
	 *
	 * @return
	 */
	public Set<String> getForks() {
		return instances.keySet();
	}

	/**
	 * Get a list of all instances.
	 *
	 * @return
	 */
	public List<Instance> getInstances() {
		ArrayList<Instance> res = new ArrayList<>();
		for(List<Instance> i : instances.values()) {
			res.addAll(i);
		}
		return res;
	}

	/**
	 * Get a list of all instances.
	 *
	 * @return
	 */
	public List<Instance> getInstances(String fork) {
		return instances.get(fork);
	}

	/**
	 * Select all instances meeting a certain criteria (e.g. fork).
	 *
	 * @param p Predicate to test with, where first parameter is the fork and second
	 *          provides the instance details.
	 * @return
	 */
	public List<Instance> selectInstances(BiPredicate<String, Instance> p) {
		ArrayList<Instance> res = new ArrayList<>();
		for (Map.Entry<String, List<Instance>> is : instances.entrySet()) {
			for (Instance i : is.getValue()) {
				if (p.test(is.getKey(), i)) {
					res.add(i);
				}
			}
		}
		return res;
	}

	/**
	 * Read a state test encoded in JSON producing a list of state test.
	 *
	 * @param json
	 * @return
	 * @throws JSONException
	 */
	public static List<StateTest> fromJSON(JSONObject json) throws JSONException {
		ArrayList<StateTest> tests = new ArrayList<>();
		//
		for (String testname : JSONObject.getNames(json)) {
			JSONObject ith = json.getJSONObject(testname);
			// Parse transaction template
			Transaction.Template template = Transaction.Template.fromJSON(ith.getJSONObject("transaction"));
			// Parse world state
			WorldState worldstate = WorldState.fromJSON(ith.getJSONObject("pre"));
			// Parse environment
			Environment env = Environment.fromJSON(ith.getJSONObject("env"));
			// Parse state test info
			Map<String, List<StateTest.Instance>> instances = parsePostState(ith.getJSONObject("post"));
			// Done
			tests.add(new StateTest(testname, instances, worldstate, env, template));
		}
		// Done
		return tests;
	}

	public static Map<String, List<StateTest.Instance>> parsePostState(JSONObject json) throws JSONException {
		HashMap<String, List<StateTest.Instance>> forks = new HashMap<>();
		for (String fork : JSONObject.getNames(json)) {
			JSONArray tests = json.getJSONArray(fork);
			List<StateTest.Instance> sts = new ArrayList<>();
			for (int i = 0; i != tests.length(); ++i) {
				sts.add(StateTest.Instance.fromJSON(fork, tests.getJSONObject(i)));
			}
			forks.put(fork, sts);
		}
		return forks;
	}

	/**
	 * Represents a concrete instance of a given state test which will correspond to
	 * a single run of the state test on a given fork with specific values for the
	 * parameters <code>data</code>, <code>gas</code> and <code>value</code>.
	 *
	 * @author David J. Pearce
	 *
	 */
	public static class Instance {
		private StateTest parent;
		public final String fork;
		public final Map<String, Integer> indexes;
		public final Transaction.Expectation expect;

		public Instance(String fork, Map<String, Integer> indices, Transaction.Expectation expect) {
			this.fork = fork;
			this.indexes = Collections.unmodifiableMap(indices);
			this.expect = expect;
		}

		public String getID() {
			int g = indexes.get("gas");
			int d = indexes.get("data");
			int v = indexes.get("value");
			return String.format("%s_%d_%d_%d",fork,g,d,v);
		}

		public String getName() {
			int g = indexes.get("gas");
			int d = indexes.get("data");
			int v = indexes.get("value");
			return String.format("%s_%s_%d_%d_%d",parent.getName(),fork,g,d,v);
		}

		/**
		 * Get the world state that should hold before this instance executes.
		 *
		 * @return
		 */
		public WorldState getWorldState() {
			return parent.pre;
		}

		/**
		 * Get the current environment which defines various parameters (e.g.
		 * difficulty).
		 *
		 * @return
		 */
		public Environment getEnvironment() {
			return parent.env;
		}

		/**
		 * Instantiate this instance with a given transaction.
		 *
		 * @return
		 */
		public Transaction instantiate() {
			return parent.transaction.instantiate(indexes);
		}

		@Override
		public String toString() {
			return getName();
		}

		public static Instance fromJSON(String fork, JSONObject json) throws JSONException {
			JSONObject is = json.getJSONObject("indexes");
			HashMap<String, Integer> map = new HashMap<>();
			map.put("data", is.getInt("data"));
			map.put("gas", is.getInt("gas"));
			map.put("value", is.getInt("value"));
			Transaction.Expectation kind;
			if (json.has("expectException")) {
				String except = json.getString("expectException");
				switch (except) {
				case "TR_IntrinsicGas": {
					kind = Transaction.Expectation.IntrinsicGas;
					break;
				}
				case "TR_GasLimitReached": {
					kind = Transaction.Expectation.OutOfGas;
					break;
				}
				case "TR_TypeNotSupported": {
					kind = Transaction.Expectation.TypeNotSupported;
					break;
				}
				case "TR_NonceHasMaxValue": {
					kind = Transaction.Expectation.NonceHasMaxValue;
					break;
				}
				default:
					throw new RuntimeException("unrecognised exception: " + except);
				}
			} else {
				kind = Transaction.Expectation.OK;
			}
			return new Instance(fork, map, kind);
		}
	}
}
