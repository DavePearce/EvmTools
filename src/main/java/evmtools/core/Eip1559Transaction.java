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
import java.util.Objects;

import org.json.JSONException;
import org.json.JSONObject;

import evmtools.util.Hex;

public class Eip1559Transaction extends Transaction {
	private final BigInteger maxPriorityFeePerGas;	
	private final BigInteger maxFeePerGas;

	public Eip1559Transaction(BigInteger sender, BigInteger secretKey, BigInteger to, BigInteger nonce, BigInteger gasLimit, BigInteger value,
			byte[] data, Access[] accessList, BigInteger maxPriorityFeePerGas, BigInteger maxFeePerGas) {
		super(sender, secretKey, to, nonce, gasLimit, value, data, accessList);
		this.maxPriorityFeePerGas = maxPriorityFeePerGas;
		this.maxFeePerGas = maxFeePerGas;
	}

	/**
	 * Copy Constructor
	 * @param tx
	 */
	public Eip1559Transaction(Eip1559Transaction tx) {
		super(tx);
		this.maxPriorityFeePerGas = tx.maxPriorityFeePerGas;
		this.maxFeePerGas = tx.maxFeePerGas;	
	}

	public BigInteger maxPriorityFeePerGas() {
		return this.maxPriorityFeePerGas;
	}

	public BigInteger maxFeePerGas() {
		return this.maxFeePerGas;
	}

	@Override
	public JSONObject toJSON() throws JSONException {
		JSONObject json = super.toJSON();
		json.put("maxPriorityFeePerGas",Hex.toHexString(maxPriorityFeePerGas));		
		json.put("maxFeePerGas",Hex.toHexString(maxFeePerGas));
		json.put("type", "0x2");
		return json;
	}

	@Override
	public int hashCode() {
		return Objects.hash(super.hashCode(),maxPriorityFeePerGas,maxFeePerGas);
	}

	@Override
	public boolean equals(Object o) {
		if (o != null && o.getClass() == Eip1559Transaction.class) {
			Eip1559Transaction t = (Eip1559Transaction) o;
			return super.equals(t) && maxPriorityFeePerGas.equals(t.maxPriorityFeePerGas) && maxFeePerGas.equals(t.maxFeePerGas);
		}
		return false;
	}

	@Override
	public Transaction clone() {
		return new Eip1559Transaction(this);
	}
}
