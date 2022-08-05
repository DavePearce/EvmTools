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
package evmtools;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

import evmtools.util.Hex;

/**
 * Various tests for the abbreviated hex string format, since the algorithms for
 * this were tricky to get right.
 *
 * @author David J. Pearce
 *
 */
public class HexTests {

	@Test
	public void test_abbrev_01() {
		byte[] bytes = toBytes(0,0,0,0);
		checkAbbreviated(16,"0x00000000",bytes);
	}

	@Test
	public void test_abbrev_02() {
		byte[] bytes = toBytes(0,1,2,3);
		checkAbbreviated(16,"0x00010203",bytes);
	}

	@Test
	public void test_abbrev_03() {
		byte[] bytes = toBytes(0,1,2,3,4,5,6,7,8,9,10,11,12,13,14,15);
		checkAbbreviated(16,"0x000102030405060708090a0b0c0d0e0f",bytes);
	}

	@Test
	public void test_abbrev_04() {
		byte[] bytes = toBytes(0,0,0,0,0, 0,0,0,0,0, 0,0,0,0,0);
		checkAbbreviated(16,"0x000000000000000000000000000000",bytes);
	}

	@Test
	public void test_abbrev_05() {
		byte[] bytes = toBytes(0, 0,0,0,0,0, 0,0,0,0,0, 0,0,0,0,0);
		checkAbbreviated(16,"0x00~0f~",bytes);
	}

	@Test
	public void test_abbrev_06() {
		byte[] bytes = toBytes(1,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0);
		checkAbbreviated(16,"0x0100~10~",bytes);
	}

	@Test
	public void test_abbrev_07() {
		byte[] bytes = toBytes(1,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0xfe);
		checkAbbreviated(16,"0x0100~11~fe",bytes);
	}

	@Test
	public void test_abbrev_08() {
		byte[] bytes = toBytes(1,0,0,0xfe,0xb,0xb,0xb);
		checkAbbreviated(2,"0x0100~01~fe0b~02~",bytes);
	}

	@Test
	public void test_abbrev_09() {
		byte[] bytes = toBytes(1,0,0,0xfe,0xb,0xb,0xb,0xfd);
		checkAbbreviated(2,"0x0100~01~fe0b~02~fd",bytes);
	}

	@Test
	public void test_abbrev_10() {
		byte[] bytes = toBytes(1,0,0,0xfe,0xb,0xb,0xb,0xb,0xb,0xfd,0xf0);
		checkAbbreviated(2,"0x0100~01~fe0b~04~fdf0",bytes);
	}

	private void checkAbbreviated(int n, String expected, byte[] bytes) {
		String hex = Hex.toAbbreviatedHexString(n, bytes);
		assertEquals(expected, hex);
		assertArrayEquals(bytes, Hex.toBytesFromAbbreviated(hex));
	}

	/**
	 * Convert an array of Java ints into an array of bytes. This assumes that every
	 * int is within bounds for a byte.
	 *
	 * @param words
	 * @return
	 */
	private byte[] toBytes(int... words) {
		byte[] bytes = new byte[words.length];
		for(int i=0;i!=words.length;++i) {
			int ith = words[i];
			assertTrue(ith >= 0);
			assertTrue(ith <= 255);
			bytes[i] = (byte) ith;
		}
		return bytes;
	}
}
