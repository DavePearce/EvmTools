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
package evmtools.util;

import java.math.BigInteger;

public class Hex {

	/**
	 * Decode a hexidecimal string (possibly starting with "0x") into a BigInteger.
	 *
	 * @param hex
	 * @return
	 */
	public static BigInteger toBigInt(String hex) {
		// Skip "0x" if string starts with it.
		if (hex.startsWith("0x")) { hex = hex.substring(2); }
		return new BigInteger(hex, 16);
	}

	/**
	 * Parse a string of hex digits (e.g. <code>0F606B</code>) into a byte array.
	 * Note that, the length of the string must be even.
	 *
	 * @param s
	 * @return
	 */
	public static byte[] toBytes(String s) {
		if (s.length() % 2 != 0) {
			throw new IllegalArgumentException("invalid hex string");
		} else {
			// Skip "0x" if string starts with it.
			if(s.startsWith("0x")) {
				s = s.substring(2);
			}
			// Parse the string.
			final int n = s.length();
			byte[] data = new byte[n >> 1];
			for (int i = 0; i < n; i = i + 2) {
				char ith = s.charAt(i);
				char ithp1 = s.charAt(i+1);
				int val = (Character.digit(ith, 16) << 4) | Character.digit(ithp1, 16);
				data[i / 2] = (byte) val;
			}
			return data;
		}
	}

	/**
	 * Convert a sequence of bytes into a hexadecimal string.
	 *
	 * @param bytes
	 * @return
	 */
	public static String toHexString(byte... bytes) {
		StringBuilder sb = new StringBuilder();
		sb.append("0x");
		for(int i=0;i!=bytes.length;++i) {
			int b = bytes[i] & 0xff;
			sb.append(String.format("%02x",b));
		}
		return sb.toString();
	}

	/**
	 * Convert a biginteger into a hexadecimal string.
	 *
	 * @param bytes
	 * @return
	 */
	public static String toHexString(BigInteger i) {
		String s = i.toString(16);
		// NOTE: this just ensures the length of the string is even, as Geth appears to
		// require this.
		if((s.length() % 2) != 0) {
			return "0x0" + s;
		} else {
			return "0x" + s;
		}
	}

	/**
	 * Convert a biginteger into a hexadecimal string which is of a certain length.
	 *
	 * @param bytes
	 * @return
	 */
	public static String toHexString(BigInteger i, int len) {
		String s = i.toString(16);
		//
		if (s.length() > len) {
			throw new IllegalArgumentException("invalid hex string (too long)");
		} else {
			StringBuilder sb = new StringBuilder();
			sb.append("0x");
			len = len - s.length() + 2;
			while (sb.length() < len) {
				sb.append('0');
			}
			sb.append(s);
			return sb.toString();
		}
	}
}
