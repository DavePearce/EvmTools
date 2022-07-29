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
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import org.json.JSONException;
import org.json.JSONObject;

import evmtesttools.util.Hex;

public class ProfiledStateTest {
	/**
	 * Every individual test has a name.
	 */
	private final String name;
	/**
	 * Defines the genesis information for the state test.
	 */
	private final WorldState state;
	/**
	 * Defines the transaction to be executed.
	 */
	private final Transaction transaction;
	/**
	 * Defines the expected trace when executing the transaction.
	 */
	private final Trace trace;

	public ProfiledStateTest(String name, WorldState state, Transaction transaction, Trace trace) {
		this.name = name;
		this.state = state;
		this.transaction = transaction;
		this.trace = trace;
	}

	@Override
	public String toString() {
		try {
			return toJSON().toString();
		} catch (JSONException e) {
			return null;
		}
	}

	/**
	 * Convert this test into a JSON file.
	 *
	 * @return
	 */
	public JSONObject toJSON() throws JSONException {
		 JSONObject json = new JSONObject();
		json.put("name", name);
		json.put("state", state.toJSON());
		json.put("transaction",transaction.toJSON());
		json.put("trace",trace.toJSON());
		// Done
		return json;
	}

	@Override
	public boolean equals(Object o) {
		if (o instanceof ProfiledStateTest) {
			ProfiledStateTest p = (ProfiledStateTest) o;
			return name.equals(p.name) &&
					state.equals(p.state) &&
					transaction.equals(p.transaction)
					&& trace.equals(p.trace);
		}
		return false;
	}

	@Override
	public int hashCode() {
		return Objects.hash(name,state,transaction,trace);
	}

	/**
	 * Parse a serialised form a transaction test.
	 *
	 * @param json
	 * @return
	 */
	public static ProfiledStateTest fromJSON(JSONObject json) throws JSONException {
		String name = json.getString("name");
		WorldState ws = WorldState.fromJSON(json.getJSONObject("state"));
		Transaction tx = Transaction.fromJSON(json.getJSONObject("transaction"));
		Trace trace = Trace.fromJSON(json.getJSONArray("trace"));
		return new ProfiledStateTest(name, ws, tx, trace);
	}
}
