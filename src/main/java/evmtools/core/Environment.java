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

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import evmtools.util.Hex;

public class Environment {
	public final BigInteger currentCoinbase;
	public final BigInteger currentDifficulty;
	public final BigInteger currentGasLimit;
	public final BigInteger currentNumber;
	public final BigInteger currentTimestamp;
	public final BigInteger currentBaseFee;
	/**
	 * Hash of the previous block.
	 */
	public final BigInteger previousHash;
	/**
	 * A map of historical block numbers and their hashes.
	 */
	public final Map<BigInteger,BigInteger> blockHashes;

	public Environment(BigInteger currentCoinbase, BigInteger currentDifficulty, BigInteger currentGasLimit,
			BigInteger currentNumber, BigInteger currentTimestamp, BigInteger currentBaseFee, BigInteger previousHash,
			Map<BigInteger, BigInteger> blockHashes) {
		this.currentCoinbase = currentCoinbase;
		this.currentDifficulty = currentDifficulty;
		this.currentGasLimit = currentGasLimit;
		this.currentNumber = currentNumber;
		this.currentTimestamp = currentTimestamp;
		this.currentBaseFee = currentBaseFee;
		this.previousHash = previousHash;
		this.blockHashes = new HashMap<>(blockHashes);
	}

	public JSONObject toJSON() throws JSONException {
		JSONObject json = new JSONObject();
		json.put("currentCoinbase", Hex.toHexString(currentCoinbase));
		json.put("currentDifficulty", Hex.toHexString(currentDifficulty));
		json.put("currentGasLimit", Hex.toHexString(currentGasLimit));
		json.put("currentNumber", Hex.toHexString(currentNumber));
		json.put("currentTimestamp", Hex.toHexString(currentTimestamp));
		json.put("currentBaseFee", Hex.toHexString(currentBaseFee));
		json.put("previousHash", Hex.toHexString(previousHash));
		if(!blockHashes.isEmpty()) {
			JSONObject st = new JSONObject();
			for (Map.Entry<BigInteger, BigInteger> e : blockHashes.entrySet()) {
				st.put(e.getKey().toString(), Hex.toHexString(e.getValue(),32));
			}
			json.put("blockHashes", st);
		}
		return json;
	}

	public static Environment fromJSON(JSONObject json) throws JSONException {
		BigInteger coinBase = Hex.toBigInt(json.getString("currentCoinbase"));
		BigInteger difficulty = Hex.toBigInt(json.getString("currentDifficulty"));
		BigInteger gasLimit = Hex.toBigInt(json.getString("currentGasLimit"));
		BigInteger number = Hex.toBigInt(json.getString("currentNumber"));
		BigInteger timeStamp = Hex.toBigInt(json.getString("currentTimestamp"));
		BigInteger baseFee = Hex.toBigInt(json.getString("currentBaseFee"));
		BigInteger prevHash = Hex.toBigInt(json.getString("previousHash"));
		// Construct blockHashes
		HashMap<BigInteger,BigInteger> hashes = new HashMap<>();
		if(json.has("blockHashes")) {
			JSONObject map = json.getJSONObject("blockHashes");
			JSONArray names = map.names();
			if (names != null) {
				for (int i = 0; i != names.length(); ++i) {
					String ith = names.getString(i);
					BigInteger addr = Hex.toBigInt(ith);
					BigInteger value = Hex.toBigInt(map.getString(ith));
					hashes.put(addr, value);
				}
			}
		}
		// Done
		return new Environment(coinBase, difficulty, gasLimit, number, timeStamp, baseFee, prevHash, hashes);
	}
}
