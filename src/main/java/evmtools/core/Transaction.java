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

public abstract class Transaction {
	/**
	 * Represents the outcome of a transaction. At a high-level, there two possible
	 * scenarios: (1) the transaction was executed and either suceeded or reverted
	 * (e.g. because the EVM raised an exception or reached a REVERT instruction);
	 * or (2) transaction execution did not even begin (e.g. sender has insufficient
	 * funds, or provided an insufficient gas limit, etc).
	 *
	 * @author David J. Pearce
	 *
	 */
	public enum Outcome {
		/**
		 * Transaction was executed.
		 */
		RETURN,
		/**
		 * Transaction reverted.
		 */
		REVERT,
		/**
		 * Unknown error
		 */
		UNKNOWN,
		/**
		 * Indicates not enough gas to start with!
		 */
		INTRINSIC_GAS,
		/**
		 * Indicates out-of-gas
		 */
		OUT_OF_GAS,
		/**
		 * Insufficient gas provided to initialise the contract after the initcode returned.
		 */
		CREATION_OUT_OF_GAS,
		/**
		 * Transaction type not supported.
		 */
		TYPE_NOT_SUPPORTED,
		/**
		 * Nonce has maximum value and cannot be incremented.
		 */
		NONCE_MAX_VALUE,
		/**
		 * Sender of transaction is not an End User Account.
		 */
		SENDER_NOT_EOA,
		/**
		 * ?
		 */
		FEECAP_LESS_BLOCKS,
		/**
		 * Insufficient funds for transaction.
		 */
		INSUFFICIENT_FUNDS,
		/**
		 * Max code size exceeded
		 */
		CODESIZE_EXCEEDED,
		/**
		 * Attempt to execute invalid opcode
		 */
		INVALID_OPCODE,
		/**
		 * Attempt to create contract with EOF marker.
		 */
		INVALID_EOF,
		/**
		 * Attempt to pop operand from an empty stack.
		 */
		STACK_UNDERFLOW,
		/**
		 * Attempt to push operand onto stack with 1024 items.
		 */
		STACK_OVERFLOW,
		MEMORY_OVERFLOW,
		/**
		 * Attempt to access returndata out-of-bounds.
		 */
		RETURNDATA_OVERFLOW,
		/**
		 * Attempt to branch to instruction which is not a <code>JUMPDEST</code>.
		 */
		INVALID_JUMPDEST,
		/**
		 * Call depth exceeded 1024.
		 */
		CALLDEPTH_EXCEEDED,
		/**
		 * Attempt to create account which already exists.
		 */
		ACCOUNT_COLLISION,
		/**
		 * Attempt to modify state from a static call.
		 */
		WRITE_PROTECTION,
	}

	/**
	 * Address of the sender making this transaction.
	 */
	private BigInteger sender;
	/**
	 * Secret key of sender (this is needed to sign the transaction later on).
	 */
	private BigInteger secretKey;
	/**
	 * Address of account being called. If this is <code>null</code>, then
	 * transaction is a contract creation.
	 */
	private BigInteger to;


	private BigInteger nonce;
	/**
	 * Maximum amount of gas to expend trying to complete the transaction.
	 */
	private BigInteger gasLimit;
	/**
	 * Funds being transferred (in Wei)
	 */
	private BigInteger value;
	/**
	 * Call data provided for the contract call (e.g. which typically follows the
	 * Solidity ABI).
	 */
	private byte[] data;

	/**
	 * The expected outcome from executing this transaction (e.g. normal execution,
	 * revert, etc).
	 */
	public Transaction(BigInteger sender, BigInteger secretKey, BigInteger to, BigInteger nonce,
			BigInteger gasLimit, BigInteger value, byte[] data) {
		this.sender = sender;
		this.secretKey = secretKey;
		this.to = to;
		this.nonce = nonce;
		this.gasLimit = gasLimit;
		this.value = value;
		this.data = data;
	}

	/**
	 * Copy Constructor
	 * @param tx
	 */
	public Transaction(Transaction tx) {
		this.sender = tx.sender;
		this.secretKey = tx.secretKey;
		this.to = tx.to;
		this.nonce = tx.nonce;
		this.gasLimit = tx.gasLimit;
		this.value = tx.value;
		this.data = tx.data;
	}

	public BigInteger sender() {
		return sender;
	}

	public BigInteger to() {
		return to;
	}

	public BigInteger gasLimit() {
		return gasLimit;
	}

	public BigInteger value() {
		return value;
	}

	public byte[] data() {
		return data;
	}

	public Transaction setSender(BigInteger sender) {
		this.sender = sender;
		return this;
	}

	/**
	 * Set a specific gas limit for this transaction.
	 *
	 * @param gasLimit
	 * @return
	 */
	public Transaction setGasLimit(long gasLimit) {
		return setGasLimit(BigInteger.valueOf(gasLimit));
	}

	/**
	 * Set a specific gas limit for this transaction.
	 *
	 * @param gasLimit
	 * @return
	 */
	public Transaction setGasLimit(BigInteger gasLimit) {
		this.gasLimit = gasLimit;
		return this;
	}

	/**
	 * Set a specific value for this transaction.
	 *
	 * @param value
	 * @return
	 */
	public Transaction setValue(BigInteger value) {
		this.value = value;
		return this;
	}

	/**
	 * Set specific call data for this transaction.
	 *
	 * @param data
	 * @return
	 */
	public Transaction setData(byte[] data) {
		this.data = data;
		return this;
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
	public abstract Transaction clone();

	@Override
	public int hashCode() {
		return Objects.hash(sender, to, gasLimit, nonce, value) ^ Arrays.hashCode(data);
	}

	@Override
	public boolean equals(Object o) {
		if (o instanceof Transaction) {
			Transaction t = (Transaction) o;
			return sender.equals(t.sender) && Objects.equals(to, t.to) && nonce.equals(t.nonce) && value.equals(t.value)
					&& Arrays.equals(data, t.data) && gasLimit.equals(t.gasLimit);
		}
		return false;
	}

	/**
	 * Convert transaction information into JSON.
	 *
	 * @return
	 * @throws JSONException
	 */
	public JSONObject toJSON() throws JSONException {
		JSONObject json = new JSONObject();
		json.put("sender",Hex.toHexString(sender,40));
		json.put("secretKey",Hex.toHexString(secretKey,64));
		if(to != null) {
			json.put("to",Hex.toHexString(to,40));
		}
		json.put("gasLimit",Hex.toHexString(gasLimit));
		json.put("nonce",Hex.toHexString(nonce));
		json.put("value",Hex.toHexString(value));
		json.put("input",Hex.toHexString(data));
		// Done
		return json;
	}


	public static Transaction fromJSON(JSONObject json) throws JSONException {
		BigInteger sender = Hex.toBigInt(json.getString("sender"));
		BigInteger secret = Hex.toBigInt(json.getString("secretKey"));
		String _to = json.optString("to","");
		BigInteger to = _to.isEmpty() ? null : Hex.toBigInt(_to);
		BigInteger gasLimit = Hex.toBigInt(json.getString("gasLimit"));
		BigInteger nonce = Hex.toBigInt(json.getString("nonce"));
		BigInteger value = Hex.toBigInt(json.getString("value"));
		byte[] data = Hex.toBytes(json.getString("input"));
		// Decide what type of transaction we have
		if(json.length() <= 8 && json.has("gasPrice")) {
			BigInteger gasPrice = Hex.toBigInt(json.getString("gasPrice"));
			return new LegacyTransaction(sender, secret, to, nonce, gasLimit, value, data, gasPrice);
		} else {
			throw new IllegalArgumentException("unsupported transaction type (" + json + ")");
		}
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

		public Template(Transaction template, BigInteger[] gasLimits, BigInteger[] values, byte[][] datas) {
			// Create the "templated" transaction which has empty slots for the
			// parameterised values.
			this.template = template;
			this.gasLimits = gasLimits;
			this.values = values;
			this.datas = datas;
		}

		/**
		 * Instantiate a transaction with a given set of _indexes_. That is, indices
		 * which refer to the array of available values for each item.
		 *
		 * @param indices
		 * @return
		 */
		public Transaction instantiate(Map<String, Integer> indices) {
			BigInteger g = gasLimits[indices.get("gas")];
			BigInteger v = values[indices.get("value")];
			byte[] d = datas[indices.get("data")];
			return template.clone().setGasLimit(g).setValue(v).setData(d);
		}

		/**
		 * Parse transaction template information from a JSON input file, as used by
		 * state tests found in the Ethereum Reference Tests.
		 *
		 * @param json
		 * @return
		 */
		public static Template fromJSON(JSONObject json) throws JSONException {
			String _to = json.optString("to","");
			BigInteger to = _to.isEmpty() ? null : Hex.toBigInt(_to);
			BigInteger sender = Hex.toBigInt(json.getString("sender"));
			BigInteger secretKey = Hex.toBigInt(json.getString("secretKey"));
			BigInteger nonce = Hex.toBigInt(json.getString("nonce"));
			BigInteger[] gasLimits = parseValueArray(json.getJSONArray("gasLimit"));
			BigInteger[] values = parseValueArray(json.getJSONArray("value"));
			byte[][] datas = parseDataArray(json.getJSONArray("data"));			
			//
			if (json.length() <= 8 && json.has("gasPrice")) {
				BigInteger gasPrice = Hex.toBigInt(json.optString("gasPrice", "0x0"));
				LegacyTransaction tx = new LegacyTransaction(sender, secretKey, to, nonce, null, null, null, gasPrice);
				return new Template(tx, gasLimits, values, datas);
			} else {
				throw new IllegalArgumentException("unsupported transaction template");
			}
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
