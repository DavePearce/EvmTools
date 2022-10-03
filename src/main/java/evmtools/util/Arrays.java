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

public class Arrays {
	/**
	 * Trim a given array to a given size by removing elements from the front. Note,
	 * if the parameter is not larger than the <code>size</code>, then iis returned
	 * untouched.
	 *
	 * @param size
	 * @param items
	 * @return
	 */
	public static <T> T[] trimFront(int size, T[] items) {
		int n = items.length;
		//
		if (n <= size) {
			return items;
		} else {
			return java.util.Arrays.copyOfRange(items, n - size, n);
		}
	}
}
