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
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Arrays;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import evmtools.util.Hex;

public class Account {
	public final BigInteger balance;
	public final BigInteger nonce;
	public final HashMap<BigInteger,BigInteger> storage;
	public final byte[] code;

	public Account(BigInteger balance, BigInteger nonce, Map<BigInteger,BigInteger> storage, byte[] code) {
		this.balance = balance;
		this.nonce = nonce;
		this.storage = new HashMap<>(storage);
		this.code = code;
	}

	@Override
	public boolean equals(Object o) {
		if(o instanceof Account) {
			Account a = (Account) o;
			return balance.equals(a.balance) && nonce.equals(a.nonce) && storage.equals(a.storage)
					&& Arrays.equals(code, a.code);
		}
		return false;
	}

	@Override
	public int hashCode() {
		return Objects.hash(balance,nonce,storage) ^ Arrays.hashCode(code);
	}

	@Override
	public String toString() {
		return "<" + Hex.toHexString(balance) + ":" + Hex.toHexString(nonce) + ":" + storage + ":" + Hex.toHexString(code) + ">";
	}

	/**
	 * Convert account information into JSON.
	 *
	 * @return
	 * @throws JSONException
	 */
	public JSONObject toJSON() throws JSONException {
		JSONObject json = new JSONObject();
		json.put("balance", Hex.toHexString(balance));
		json.put("nonce", Hex.toHexString(nonce));
		JSONObject st = new JSONObject();
		for (Map.Entry<BigInteger, BigInteger> e : storage.entrySet()) {
			st.put(Hex.toHexString(e.getKey(),32), Hex.toHexString(e.getValue(),32));
		}
		json.put("storage", st);
		json.put("code", Hex.toHexString(code));
		return json;
	}

	/**
	 * Parse account information from a JSON input file, as used by state tests
	 * found in the Ethereum Reference Tests.
	 *
	 * @param json
	 * @return
	 */
	public static Account fromJSON(JSONObject json) throws JSONException {
		BigInteger balance = Hex.toBigInt(json.getString("balance"));
		BigInteger nonce = Hex.toBigInt(json.getString("nonce"));
		byte[] code = Hex.toBytes(json.getString("code"));
		// Parse the storage map
		JSONObject map = json.getJSONObject("storage");
		Map<BigInteger,BigInteger> storage = new HashMap<>();
		JSONArray names = map.names();
		// NOTE: for reasons I don't understand, an empty object at this point returns
		// null.
		if (names != null) {
			for (int i = 0; i != names.length(); ++i) {
				String ith = names.getString(i);
				BigInteger addr = Hex.toBigInt(ith);
				BigInteger value = Hex.toBigInt(map.getString(ith));
				storage.put(addr, value);
			}
		}
		// Done
		return new Account(balance,nonce,storage,code);
	}
}
