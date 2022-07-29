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
package evmtools.evms;

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

import evmtools.core.Account;
import evmtools.core.StateTest;
import evmtools.core.Trace;
import evmtools.core.Transaction;
import evmtools.core.WorldState;
import evmtools.util.Hex;
import evmtools.util.StreamGrabber;

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
	public Trace execute(WorldState pre, Transaction tx) throws JSONException {
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
		StringBuffer syserr = new StringBuffer();
		StringBuffer sysout = new StringBuffer();
		Process child = builder.start();
		try {
			// NOTE: the stream grabbers are required to prevent internal buffers from
			// getting full. Since some of the trace output for state tests is very large,
			// this is a real issue we encounter. That is, if we just wait for the process
			// to exit then read everything from its inputstream ... well, this won't work.
			new StreamGrabber(child.getErrorStream(), syserr);
			new StreamGrabber(child.getInputStream(), sysout);
			// second, read the result whilst checking for a timeout
			boolean success = child.waitFor(timeout, TimeUnit.MILLISECONDS);
			if (success && child.exitValue() == 0) {
				// NOTE: should we do anything with syserr here?
				return sysout.toString();
			}
		} finally {
			// make sure child process is destroyed.
			child.destroy();
		}
		// Failure
		return null;
	}
}
