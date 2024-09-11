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

public class LegacyTransaction extends Transaction {
	/**
	 * Price per unit of Gas (in Wei).
	 */
	private final BigInteger gasPrice;	

	public LegacyTransaction(BigInteger sender, BigInteger secretKey, BigInteger to, BigInteger nonce, BigInteger gasLimit, BigInteger value,
			byte[] data, Access[] accessList, BigInteger gasPrice) {
		super(sender, secretKey, to, nonce, gasLimit, value, data, accessList);
		this.gasPrice = gasPrice;
	}

	/**
	 * Copy Constructor
	 * @param tx
	 */
	public LegacyTransaction(LegacyTransaction tx) {
		super(tx);
		this.gasPrice = tx.gasPrice;		
	}

	public BigInteger gasPrice() {
		return gasPrice;
	}

	
	@Override
	public JSONObject toJSON() throws JSONException {
		JSONObject json = super.toJSON();
		json.put("gasPrice",Hex.toHexString(gasPrice));		
		if(this.accessList() != null) {
			json.put("type", "0x1");
		}
		return json;
	}

	@Override
	public int hashCode() {
		return Objects.hash(super.hashCode(),gasPrice);
	}

	@Override
	public boolean equals(Object o) {
		if (o != null && o.getClass() == LegacyTransaction.class) {
			LegacyTransaction t = (LegacyTransaction) o;
			return super.equals(t) && gasPrice.equals(t.gasPrice);
		}
		return false;
	}

	@Override
	public Transaction clone() {
		return new LegacyTransaction(this);
	}
}
