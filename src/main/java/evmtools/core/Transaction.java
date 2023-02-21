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
import java.util.Arrays;
import java.util.Map;
import java.util.Objects;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import evmtools.util.Hex;

public class Transaction {
	public enum Expectation {
		/**
		 * Indicates test ran to completion, possibly producing output data.
		 */
		OK,
		/**
		 * Indicates not enough gas to start with!
		 */
		IntrinsicGas,
		/**
		 * Indicates out-of-gas.
		 */
		OutOfGas,
		/**
		 * Transaction type not supported (?).
		 */
		TypeNotSupported,
		/**
		 * Nonce has maximum value and cannot be incremented.
		 */
		NonceHasMaxValue,
		/**
		 * Indicates test ran but caused a revert.
		 */
		REVERT,
		/**
		 * Indicates an outcome was not generated due to some kind of internal issue
		 * (e.g. fork not supported, transaction type not supported, etc).
		 */
		FAILURE
	}

	/**
	 * Address of the sender making this transaction.
	 */
	public final BigInteger sender;
	/**
	 * Secret key of sender (this is needed to sign the transaction later on).
	 */
	public final BigInteger secretKey;
	/**
	 * Address of account being called. If this is <code>null</code>, then
	 * transaction is a contract creation.
	 */
	public final BigInteger to;
	/**
	 * Maximum amount of gas to expend trying to complete the transaction.
	 */
	public final BigInteger gasLimit;
	/**
	 * Price per unit of Gas (in Wei).
	 */
	public final BigInteger gasPrice;

	public final BigInteger nonce;
	/**
	 * Funds being transferred (in Wei)
	 */
	public final BigInteger value;
	/**
	 * Call data provided for the contract call (e.g. which typically follows the
	 * Solidity ABI).
	 */
	public final byte[] data;

	/**
	 * The expected outcome from executing this transaction (e.g. normal execution,
	 * revert, etc).
	 */
	public Transaction(BigInteger sender, BigInteger secretKey, BigInteger to, BigInteger gasLimit, BigInteger gasPrice, BigInteger nonce,
			BigInteger value, byte[] data) {
		this.sender = sender;
		this.secretKey = secretKey;
		this.to = to;
		this.gasLimit = gasLimit;
		this.gasPrice = gasPrice;
		this.nonce = nonce;
		this.value = value;
		this.data = data;
	}

	/**
	 * Get the contract code to execute for this transaction.
	 *
	 * @param worldState
	 * @return
	 */
	public byte[] getCode(WorldState worldState) {
		if (to == null) {
			// NOTE: its not clear to me why this makes sense, but some of the ethereum
			// reference tests are setup like this. Specifically, to allow them to be
			// parameterised over code.
			return data;
		} else {
			return worldState.get(to).code;
		}
	}

	@Override
	public boolean equals(Object o) {
		if(o instanceof Transaction) {
			Transaction t = (Transaction) o;
			return sender.equals(t.sender) && Objects.equals(to,t.to) && gasLimit.equals(t.gasLimit)
					&& gasPrice.equals(t.gasPrice) && nonce.equals(t.nonce) && value.equals(t.value)
					&& Arrays.equals(data, t.data);
		}
		return false;
	}

	@Override
	public int hashCode() {
		return Objects.hash(sender, to, gasLimit, gasPrice, nonce, value) ^ Arrays.hashCode(data);
	}

	/**
	 * Convert transaction information into JSON.
	 *
	 * @return
	 * @throws JSONException
	 */
	public JSONObject toJSON() throws JSONException {
		JSONObject json = new JSONObject();
		json.put("sender",Hex.toHexString(sender));
		json.put("secretKey",Hex.toHexString(secretKey));
		if(to == null) {
			json.put("to","");
		} else {
			json.put("to",Hex.toHexString(to));
		}
		json.put("gasLimit",Hex.toHexString(gasLimit));
		json.put("gasPrice",Hex.toHexString(gasPrice));
		json.put("nonce",Hex.toHexString(nonce));
		json.put("value",Hex.toHexString(value));
		json.put("input",Hex.toHexString(data));
		return json;
	}

	public static Transaction fromJSON(JSONObject json) throws JSONException {
		BigInteger sender = Hex.toBigInt(json.getString("sender"));
		BigInteger secret = Hex.toBigInt(json.getString("secretKey"));
		String _to = json.getString("to");
		BigInteger to = _to.isEmpty() ? null : Hex.toBigInt(_to);
		BigInteger gasLimit = Hex.toBigInt(json.getString("gasLimit"));
		BigInteger gasPrice = Hex.toBigInt(json.getString("gasPrice"));
		BigInteger nonce = Hex.toBigInt(json.getString("nonce"));
		BigInteger value = Hex.toBigInt(json.getString("value"));
		byte[] data = Hex.toBytes(json.getString("input"));
		return new Transaction(sender, secret, to, gasLimit, gasPrice, nonce, value, data);
	}

	/**
	 * A transaction template is a transaction parameterised on three values:
	 * <code>data</code>, <code>gasLimit</code> and <code>value</code>. For each of
	 * these items, a template has a predefined array of values. A transaction can
	 * then be instantiated from a template by providing a <i>index</i> into the
	 * array of each item.
	 *
	 * @author David J. Pearce
	 *
	 */
	public static class Template {
		private final Transaction template;
		private final BigInteger[] gasLimits;
		private final BigInteger[] values;
		private final byte[][] datas;

		public Template(BigInteger sender, BigInteger secretKey,BigInteger to, BigInteger[] gasLimits, BigInteger gasPrice, BigInteger nonce,
				BigInteger[] values, byte[][] datas) {
			// Create the "templated" transaction which has empty slots for the
			// parameterised values.
			this.template = new Transaction(sender,secretKey,to,null,gasPrice,nonce,null,null);
			this.gasLimits = gasLimits;
			this.values = values;
			this.datas = datas;
		}

		/**
		 * Instantiate a transaction with a given set of _indexes_. That is, indices
		 * which refer to the array of available values for each item.
		 *
		 * @param data
		 * @param gas
		 * @param value
		 * @return
		 */
		public Transaction instantiate(Map<String, Integer> indices) {
			int g = indices.get("gas");
			int d = indices.get("data");
			int v = indices.get("value");
			return new Transaction(template.sender, template.secretKey, template.to, gasLimits[g], template.gasPrice, template.nonce,
					values[v], datas[d]);
		}

		/**
		 * Parse transaction template information from a JSON input file, as used by
		 * state tests found in the Ethereum Reference Tests.
		 *
		 * @param tx
		 * @return
		 */
		public static Template fromJSON(JSONObject json) throws JSONException {
			String _to = json.getString("to");
			BigInteger to = _to.isEmpty() ? null : Hex.toBigInt(_to);
			BigInteger sender = Hex.toBigInt(json.getString("sender"));
			BigInteger secretKey = Hex.toBigInt(json.getString("secretKey"));
			// NOTE: for reasons unknown some of the tests don't have a gasPrice field. For
			// now I have just set it to zero if its missing. A better solution might be to
			// use a default.
			BigInteger gasPrice = Hex.toBigInt(json.optString("gasPrice","0x0"));
			BigInteger nonce = Hex.toBigInt(json.getString("nonce"));
			BigInteger[] gasLimits = parseValueArray(json.getJSONArray("gasLimit"));
			BigInteger[] values = parseValueArray(json.getJSONArray("value"));
			byte[][] datas = parseDataArray(json.getJSONArray("data"));
			return new Template(sender, secretKey, to, gasLimits, gasPrice, nonce, values, datas);
		}

	}


	public static byte[][] parseDataArray(JSONArray json) throws JSONException {
		byte[][] bytes = new byte[json.length()][];
		for(int i=0;i!=json.length();++i) {
			bytes[i] = Hex.toBytes(json.getString(i));
		}
		//
		return bytes;
	}

	public static BigInteger[] parseValueArray(JSONArray json) throws JSONException {
		BigInteger[] values = new BigInteger[json.length()];
		for(int i=0;i!=json.length();++i) {
			values[i] = Hex.toBigInt(json.getString(i));
		}
		return values;
	}
}
