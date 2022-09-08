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
import java.util.Arrays;

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
		int n = s.length();
		//
		if (n % 2 != 0) {
			throw new IllegalArgumentException("invalid hex string");
		} else {
			// Skip "0x" if string starts with it.
			int i = 0;
			if (s.startsWith("0x")) {
				i = i + 2;
				n = n - 2;
			}
			// Parse the string.
			byte[] data = new byte[n >> 1];
			copyHexToBytes(s, i, data, 0, data.length);
			return data;
		}
	}

	public static byte[] toBytesFromAbbreviated(String s) {
		int i = s.indexOf('~');
		if (i == -1) {
			// Not abbreviated
			return toBytes(s);
		} else if(s.startsWith("0x")) {
			// FIXME: this is unnecessary.
			s = s.substring(2);
			i = i - 2;
		}
		// Yes, this is an abbreviated string. First allocate enough bytes for it.
		byte[] bytes = allocBytesForAbbreviated(i, s);
		// Now, copy everything over.
		int bIndex = 0;
		int sIndex = 0;
		while (i != -1) {
			// Copy everything upto abbreviation.
			int n = (i - sIndex) >> 1;
			copyHexToBytes(s, sIndex, bytes, bIndex, n);
			bIndex = expandHexAbbreviation(s, i, bytes, bIndex + n);
			// Skip over abbreviation
			sIndex = s.indexOf('~', i + 1) + 1;
			// Determine next abbreviation
			i = s.indexOf('~', sIndex);
		}
		// Copy anything remaining from last abbreviation to the end.
		int n = (s.length() - sIndex) >> 1;
		copyHexToBytes(s,sIndex,bytes,bIndex,n);
		return bytes;
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
	 * Convert a sequence of bytes into a hexadecimal string, allowing abbreviations
	 * of large sequences of the same value.
	 *
	 * @param bytes
	 * @return
	 */
	public static String toAbbreviatedHexString(byte... bytes) {
		return toAbbreviatedHexString(16,bytes);
	}

	/**
	 * Convert a sequence of bytes into a hexadecimal string, allowing abbreviations
	 * of large sequences of the same value. Example abbreviated strings and their
	 * expansions:
	 *
	 * <pre>
	 * 0x00~02~ ==> 0x0000
	 * 0x0100~03~ ==> 0x01000000
	 * 0x0102~03~ ==> 0x01020202
	 * 0x0100~0A~ ==> 0x0100000000000000000000
	 * </pre>
	 *
	 * Observe that an abbreviated hex string is always a multiple of two. Also that
	 * the abbreviation is in hexadecimal.
	 *
	 * @param n     Minimum number of repeating elements before abbreviation.
	 * @param bytes
	 * @return
	 */
	public static String toAbbreviatedHexString(int n, byte... bytes) {
		StringBuilder sb = new StringBuilder();
		sb.append("0x");
		for (int i = 0; i < bytes.length;) {
			int j = next(i, bytes);
			int diff = j - i;
			//
			if (diff >= n) {
				// Yes, abbreviation.
				sb.append(abbreviate(i, j, bytes));
				i = j;
			} else {
				// No abbreviation.
				int b = bytes[i] & 0xff;
				while (i < j) {
					sb.append(String.format("%02x", b));
					i = i + 1;
				}
			}
		}
		return sb.toString();
	}

	/**
	 * Compute an appropriate abbreviation for a given range within a byte array.
	 *
	 * @param i
	 * @param j
	 * @param bytes
	 * @return
	 */
	private static String abbreviate(int i, int j, byte[] bytes) {
		if (j <= i) {
			throw new IllegalArgumentException("invalid abbreviation");
		} else {
			int b = bytes[i] & 0xff;
			int diff = (j - i) - 1;
			String d = String.format("%x", diff);
			if (d.length() % 2 != 0) {
				d = "0" + d;
			}
			return String.format("%02x~%s~", b, d);
		}
	}

	/**
	 * Determine next index which does not match current byte value.
	 *
	 * @param i
	 * @param bytes
	 * @return
	 */
	private static int next(int i, byte[] bytes) {
		byte ith = bytes[i];
		int j = i + 1;
		while (j < bytes.length && bytes[j] == ith) {
			j = j + 1;
		}
		return j;
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
		return "0x" + s;
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
		if (s.length() > (len * 2)) {
			throw new IllegalArgumentException("invalid hex string (too long) --- \"" + s + "\" is " + s.length()
					+ " chars, but expecting " + len);
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

	public static String toArrayString(BigInteger[] items) {
		String[] r = new String[items.length];
		for(int i=0;i!=items.length;++i) {
			r[i] = toHexString(items[i]);
		}
		return Arrays.toString(r);
	}

	/**
	 * Convert a given number of bytes from a string in hexadecimal form, copying
	 * them into a destination array.
	 *
	 * @param src
	 * @param srcIndex
	 * @param dest
	 * @param dstIndex
	 * @param length
	 */
	private static void copyHexToBytes(String src, int srcIndex, byte[] dest, int dstIndex, int length) {
		for (int i = 0; i < length; ++i) {
			int j = srcIndex + (i << 1);
			char ith = src.charAt(j);
			char ithp1 = src.charAt(j + 1);
			int val = (Character.digit(ith, 16) << 4) | Character.digit(ithp1, 16);
			dest[i + dstIndex] = (byte) val;
		}
	}

	/**
	 * Expand a given hex abbreviation which starts at a given point within the
	 * string. It is assumed to expand the preceeding byte in the destination array.
	 *
	 * @param src      Source string.
	 * @param srcIndex Start of abbreviation.
	 * @param dest     Destination array.
	 * @param dstIndex Point at which to expand the abbreviation.
	 */
	private static int expandHexAbbreviation(String src, int srcIndex, byte[] dest, int dstIndex) {
		// Expand abbreviation
		byte b = dest[dstIndex - 1];
		int j = src.indexOf('~', srcIndex + 1);
		int l = Integer.parseInt(src.substring(srcIndex + 1, j), 16);
		Arrays.fill(dest, dstIndex, dstIndex+l, b);
		//
		return dstIndex + l;
	}

	private static byte[] allocBytesForAbbreviated(int i, String s) {
		int len = (i >> 1);
		//
		while (i != -1) {
			// Expand abbreviation
			int j = s.indexOf('~', i + 1);
			int l = Integer.parseInt(s.substring(i + 1, j), 16);
			len += l;
			// Skip to next abbreviation (if any)
			i = s.indexOf('~', j + 1);
			if (i != -1) {
				len += (i - j) >> 1;
			} else {
				len += (s.length() - j) >> 1;
			}
		}
		//
		return new byte[len];
	}
}
