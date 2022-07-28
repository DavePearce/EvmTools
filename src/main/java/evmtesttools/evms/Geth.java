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
package evmtesttools.evms;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.concurrent.TimeUnit;

import org.json.JSONException;
import org.json.JSONObject;

import evmtesttools.core.Account;
import evmtesttools.core.StateTest;
import evmtesttools.core.Trace;
import evmtesttools.core.Transaction;
import evmtesttools.util.Hex;

/**
 * An interface to Geth's command-line <code>evm</code> tool. This allows us to
 * execute a state test and get back the trace data. Essentially, this class
 * manages two tasks: (1) executing the tool as a (sub) process; (2) marshing
 * the state test data into and out of the tool.
 *
 * @author David J. Pearce
 *
 */
public class Geth {
	/**
	 * Command to use to execture Geth.
	 */
	private final String cmd = "evm";

	/**
	 * Timeout (in millisecond) to wait for the process to complete.
	 */
	private int timeout = 10000;

	public Geth setTimeout(int timeout) {
		this.timeout = timeout;
		return this;
	}

	/**
	 * Execute a given transaction using the EVM.
	 *
	 * @param tx
	 * @return
	 */
	public Trace execute(Map<BigInteger, Account> pre, Transaction tx) throws JSONException {
		try {
			// Build up the command
			ArrayList<String> command = new ArrayList<>();
			command.add(cmd);
			command.add("--json");
			command.add("--code");
			command.add(Hex.toHexString(tx.getCode(pre)));
			command.add("run");
			//
			String out = exec(command);
			// Parse into JSON. Geth produces one line per trace element.
			ArrayList<Trace.Element> elements = new ArrayList<>();
			Scanner scanner = new Scanner(out);
			while (scanner.hasNextLine()) {
				String line = scanner.nextLine();
				JSONObject element = new JSONObject(line);
				elements.add(Trace.Element.fromJSON(element));
			}
			scanner.close();
			return new Trace(elements);
		} catch (IOException e) {
			return null;
		} catch (InterruptedException e) {
			return null;
		}
	}

	public String exec(List<String> command) throws IOException, InterruptedException {
		// ===================================================
		// Construct the process
		// ===================================================
		ProcessBuilder builder = new ProcessBuilder(command);
		Process child = builder.start();
		try {
			// second, read the result whilst checking for a timeout
			InputStream input = child.getInputStream();
			InputStream error = child.getErrorStream();
			boolean success = child.waitFor(timeout, TimeUnit.MILLISECONDS);
			byte[] stdout = readInputStream(input);
			byte[] stderr = readInputStream(error);
			if (success && child.exitValue() == 0) {
				return new String(stdout);
			}
		} finally {
			// make sure child process is destroyed.
			child.destroy();
		}
		// Failure
		return null;
	}

	/**
     * Read an input stream entirely into a byte array.
     *
     * @param input
     * @return
     * @throws IOException
     */
    private static byte[] readInputStream(InputStream input) throws IOException {
        byte[] buffer = new byte[32768];
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        while (input.available() > 0) {
            int count = input.read(buffer);
            output.write(buffer, 0, count);
        }
        return output.toByteArray();
    }
}
