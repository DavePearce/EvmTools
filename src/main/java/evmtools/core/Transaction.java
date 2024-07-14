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
		/**
		 * Indicates a transaction which exceeded the block gas limit.
		 */
		GAS_LIMIT_REACHED,
	}

	/**
	 * Address of the sender making this transaction.
	 */
	private BigInteger chainID;
	
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
	 * Optional access list.
	 */
	private Access[] accessList;

	/**
	 * The expected outcome from executing this transaction (e.g. normal execution,
	 * revert, etc).
	 */
	public Transaction(BigInteger sender, BigInteger secretKey, BigInteger to, BigInteger nonce,
			BigInteger gasLimit, BigInteger value, byte[] data, Access[] accessList) {
		this.chainID = BigInteger.ONE;
		this.sender = sender;
		this.secretKey = secretKey;
		this.to = to;
		this.nonce = nonce;
		this.gasLimit = gasLimit;
		this.value = value;
		this.data = data;
		this.accessList = accessList;
	}

	/**
	 * Copy Constructor
	 * @param tx
	 */
	public Transaction(Transaction tx) {
		this.chainID = tx.chainID;
		this.sender = tx.sender;
		this.secretKey = tx.secretKey;
		this.to = tx.to;
		this.nonce = tx.nonce;
		this.gasLimit = tx.gasLimit;
		this.value = tx.value;
		this.data = tx.data;
		this.accessList = tx.accessList;
	}

	public BigInteger chainID() {
		return chainID;
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


	public Access[] accessList() {
		return accessList;
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
	 * Set the access list for this transaction.
	 *
	 * @param data
	 * @return
	 */
	public Transaction setAccessList(Access[] accessList) {
		this.accessList = accessList;
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
			return chainID.equals(t.chainID) && sender.equals(t.sender) && Objects.equals(to, t.to)
					&& nonce.equals(t.nonce) && value.equals(t.value) && Arrays.equals(data, t.data)
					&& gasLimit.equals(t.gasLimit);
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
		json.put("chainId", Hex.toHexString(chainID));		
		json.put("sender",Hex.toHexString(sender,40));
		json.put("secretKey",Hex.toHexString(secretKey,64));
		if(to != null) {
			json.put("to",Hex.toHexString(to,40));
		}
		json.put("gasLimit",Hex.toHexString(gasLimit));
		json.put("nonce",Hex.toHexString(nonce));
		json.put("value",Hex.toHexString(value));
		json.put("input",Hex.toHexString(data));
		if(accessList != null) {
			json.put("accessList", accessListToJSON(this.accessList));
		}
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
		Access[] accessList = null;
		if(json.has("accessList")) {
			accessList = LegacyTransaction.accessListFromJSON(json.getJSONArray("accessList"));
		}
		// Decide what type of transaction we have
		if(json.has("gasPrice")) {
			BigInteger gasPrice = Hex.toBigInt(json.getString("gasPrice"));
			return new LegacyTransaction(sender, secret, to, nonce, gasLimit, value, data, accessList, gasPrice);
		} else if (json.has("maxPriorityFeePerGas") && json.has("maxFeePerGas")) {
			BigInteger maxPriorityFeePerGas = Hex.toBigInt(json.getString("maxPriorityFeePerGas"));
			BigInteger maxFeePerGas = Hex.toBigInt(json.getString("maxFeePerGas"));
			return new Eip1559Transaction(sender, secret, to, nonce, gasLimit, value, data, accessList, maxPriorityFeePerGas, maxFeePerGas);
		} else {
			throw new IllegalArgumentException("unsupported transaction type (" + json + ")");
		}
	}


	public static JSONArray accessListToJSON(Access[] accessList) throws JSONException {
		JSONArray arr = new JSONArray();
		for(int i=0;i!=accessList.length;++i) {
			arr.put(i, accessList[i].toJSON());
		}
		
		return arr;

	}
	/**
	 * Parse an access list array from a given JSON array.
	 * @param arr
	 * @return
	 */
	public static Access[] accessListFromJSON(JSONArray arr) throws JSONException {
		var accessList = new ArrayList<>();
		// Filter out any which are null
		for(int i=0;i!=arr.length();++i) {
			accessList.add(Access.fromJSON(arr.getJSONObject(i)));
		}
		// Done
		return accessList.toArray(new Access[accessList.size()]);
	}
	
	/**
	 * Describes an item in the access list.
	 */
	public static class Access {
		/**
		 * Determines the address of the contract being accessed.
		 */
		public final BigInteger address;
		
		/**
		 * Storage keys which will be accessed.
		 */
		public final BigInteger[] storageKeys;
		
		public Access(BigInteger address, BigInteger... storageKeys) {
			this.address = address;
			this.storageKeys = storageKeys;
		}
		
		public static Access fromJSON(JSONObject json) throws JSONException {
			JSONArray keys = json.getJSONArray("storageKeys");
			BigInteger address = Hex.toBigInt(json.getString("address"));
			BigInteger[] storageKeys = new BigInteger[keys.length()];
			for(int i=0;i!=storageKeys.length;++i) {
				storageKeys[i] = Hex.toBigInt(keys.getString(i));
			}
			return new Access(address, storageKeys);
		}
		
		public JSONObject toJSON() throws JSONException {
			JSONObject obj = new JSONObject();
			obj.put("address", Hex.toHexString(address, 40));
			// storage keys
			JSONArray arr = new JSONArray();
			for(int i=0;i!=storageKeys.length;++i) {
				arr.put(i,Hex.toHexString(storageKeys[i], 64));
			}
			obj.put("storageKeys", arr);
			return obj;
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
		private final Access[][] accessLists;

		public Template(Transaction template, BigInteger[] gasLimits, BigInteger[] values, byte[][] datas, Access[][] accessLists) {
			// Create the "templated" transaction which has empty slots for the
			// parameterised values.
			this.template = template;
			this.gasLimits = gasLimits;
			this.values = values;
			this.datas = datas;
			this.accessLists = accessLists;
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
			Access[] al = accessLists[indices.get("data")];
			return template.clone().setGasLimit(g).setValue(v).setData(d).setAccessList(al);
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
			Access[][] accessLists = null;
			// Check for optional access list
			if(json.has("accessLists")) {
				accessLists = parseAccessListsArray(json.getJSONArray("accessLists"));
			}
			//
			if(json.has("gasPrice")) {
				// EIP2930 transaction
				BigInteger gasPrice = Hex.toBigInt(json.getString("gasPrice"));				
				LegacyTransaction tx = new LegacyTransaction(sender, secretKey, to, nonce, null, null, null, null, gasPrice);
				return new Template(tx, gasLimits, values, datas, accessLists);
			} else if(json.has("maxPriorityFeePerGas") && json.has("maxFeePerGas")) {
				BigInteger maxPriorityFeePerGas = Hex.toBigInt(json.getString("maxPriorityFeePerGas"));
				BigInteger maxFeePerGas = Hex.toBigInt(json.getString("maxFeePerGas"));
				Eip1559Transaction tx = new Eip1559Transaction(sender, secretKey, to, nonce, null, null, null, null, maxPriorityFeePerGas, maxFeePerGas);
				return new Template(tx, gasLimits, values, datas, accessLists);
			} else {
				throw new IllegalArgumentException("unsupported transaction template (" + json +")");
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
	
	public static Access[][] parseAccessListsArray(JSONArray json) throws JSONException  {
		Access[][] accessLists = new Access[json.length()][];
		for(int i=0;i!=json.length();++i) {
			JSONArray ith = json.optJSONArray(i);
			if(ith != null) {
				accessLists[i] = LegacyTransaction.accessListFromJSON(ith);
			} else {
				accessLists[i] = new Access[0];
			}
		}
		return accessLists;
	}
}
