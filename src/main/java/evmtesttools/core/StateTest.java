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
package evmtesttools.core;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import evmtesttools.util.Hex;

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
	 * Maps a given fork to a given set of test instances to run.
	 */
	private final Map<String, List<Instance>> instances;
	/**
	 * Defines the genesis information for the state test.
	 */
	private final Map<BigInteger,Account> pre;
	/**
	 * Provides necessary specifics for the transaction to be executed. Observe this
	 * is a <i>template</i> and not a concrete transaction. Thus, we must
	 * instantiate it with one or more instances.
	 */
	private final Transaction.Template transaction;

	public StateTest(Map<String,List<Instance>> instances, Map<BigInteger,Account> pre, Transaction.Template tx) {
		this.instances = instances;
		this.pre = pre;
		this.transaction = tx;
	}

	/**
	 * Get the genesis information which specifies the blockchain state prior to
	 * executing the test(s).
	 *
	 * @return
	 */
	public Map<BigInteger,Account> getPreState() {
		return pre;
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
	 * Get an iterator over the concrete transactions described by this state test
	 * for a given fork.
	 *
	 * @param fork
	 * @return
	 */
	public Iterator<Transaction> iterate(String fork) {
		return new Iterator<Transaction>() {
			private final Iterator<Instance> iter = instances.get(fork).iterator();
			@Override
			public boolean hasNext() { return iter.hasNext(); }

			@Override
			public Transaction next() {
				Instance i = iter.next();
				return transaction.instantiate(i.expect, i.indexes);
			}
		};
	}

	/**
	 * Read a state test encoded in JSON.
	 *
	 * @param json
	 * @return
	 * @throws JSONException
	 */
	public static StateTest fromJSON(JSONObject json) throws JSONException {
		// Parse transaction template
		Transaction.Template template = Transaction.Template.fromJSON(json.getJSONObject("transaction"));
		// Parse world state
		Map<BigInteger, Account> worldstate = parsePreState(json.getJSONObject("pre"));
		// Parse state test info
		Map<String, List<StateTest.Instance>> instances = parsePostState(json.getJSONObject("post"));
		// Done
		return new StateTest(instances, worldstate, template);
	}

	public static Map<BigInteger, Account> parsePreState(JSONObject json) throws JSONException {
		HashMap<BigInteger, Account> world = new HashMap<>();
		for (String addr : JSONObject.getNames(json)) {
			JSONObject contents = json.getJSONObject(addr);
			BigInteger hexAddr = Hex.toBigInt(addr);
			world.put(hexAddr, Account.fromJSON(contents));
		}
		return world;
	}

	public static Map<String, List<StateTest.Instance>> parsePostState(JSONObject json) throws JSONException {
		HashMap<String, List<StateTest.Instance>> forks = new HashMap<>();
		for (String fork : JSONObject.getNames(json)) {
			JSONArray tests = json.getJSONArray(fork);
			List<StateTest.Instance> sts = new ArrayList<>();
			for (int i = 0; i != tests.length(); ++i) {
				sts.add(StateTest.Instance.fromJSON(tests.getJSONObject(i)));
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

		public final Map<String, Integer> indexes;
		public final Transaction.Expectation expect;

		public Instance(Map<String, Integer> indices, Transaction.Expectation expect) {
			this.indexes = Collections.unmodifiableMap(indices);
			this.expect = expect;
		}

		public static Instance fromJSON(JSONObject json) throws JSONException {
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
				default:
					throw new RuntimeException("unrecognised exception: " + except);
				}
			} else {
				kind = Transaction.Expectation.OK;
			}
			return new Instance(map, kind);
		}
	}
}
