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
import java.io.FileOutputStream;
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
import evmtools.core.Environment;
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
	public Trace execute(Environment env, WorldState pre, Transaction tx) throws JSONException {
		String preStateFile = null;
		try {
			preStateFile = createPreStateFile(env,pre,tx);
			// Build up the command
			ArrayList<String> command = new ArrayList<>();
			command.add(cmd);
			command.add("--json");
			command.add("--input");
			command.add(Hex.toHexString(tx.data));
			command.add("--nomemory=false");
			command.add("--nostorage=false");
			command.add("--prestate");
			command.add(preStateFile);
			command.add("--receiver");
			command.add(Hex.toHexString(tx.to));
			command.add("--code");
			command.add(Hex.toHexString(tx.getCode(pre)));
			command.add("run");
			//
			String out = exec(command);
			//
			if(out != null) {
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
			} else {
				// Geth failed for some reason, so dump the input to help debugging.
				//System.out.println(pre.toJSON().toString(2));
				return null;
			}
		} catch (IOException e) {
			return null;
		} catch (InterruptedException e) {
			return null;
		} finally {
			if (preStateFile != null) {
				// delete the temporary file
				new File(preStateFile).delete();
			}
		}
	}

	public String exec(List<String> command) throws IOException, InterruptedException {
		// ===================================================
		// Construct the process
		// ===================================================
		ProcessBuilder builder = new ProcessBuilder(command);
		Process child = builder.start();
		try {
			// NOTE: the stream grabbers are required to prevent internal buffers from
			// getting full. Since some of the trace output for state tests is very large,
			// this is a real issue we encounter. That is, if we just wait for the process
			// to exit then read everything from its inputstream ... well, this won't work.
			StreamGrabber syserr = new StreamGrabber(child.getErrorStream());
			StreamGrabber sysout = new StreamGrabber(child.getInputStream());
			// second, read the result whilst checking for a timeout
			boolean success = child.waitFor(timeout, TimeUnit.MILLISECONDS);
			String out = sysout.get();
			String err = syserr.get();
			if(!err.equals("")) {
				System.err.println(syserr.get());
			}
			if (success && child.exitValue() == 0) {
				// NOTE: should we do anything with syserr here?
				return out;
			} else if (success) {
				System.err.println(err);
			} else {
				throw new RuntimeException("timeout");
			}
		} finally {
			// make sure child process is destroyed.
			child.destroy();
		}
		// Failure
		return null;
	}

	private static String createPreStateFile(Environment env, WorldState pre, Transaction tx)
			throws JSONException, IOException, InterruptedException {
		JSONObject json = tx.toJSON();
		//
		json.put("alloc",pre.toJSON());
		json.put("difficulty", Hex.toHexString(env.currentDifficulty));
		//json.put("pre",pre.toJSON());
		byte[] bytes = json.toString(2).getBytes();
		return createTemporaryFile("geth_prestate", ".json", bytes);
	}

	/**
     * Write a given string into a temporary file which can then be checked by boogie.
     *
     * @param contents
     * @return
     */
    private static String createTemporaryFile(String prefix, String suffix, byte[] contents)
            throws IOException, InterruptedException {
        // Create new file
        File f = File.createTempFile(prefix, suffix);
        // Open for writing
        FileOutputStream fout = new FileOutputStream(f);
        // Write contents to file
        fout.write(contents);
        // Done creating file
        fout.close();
        //
        return f.getAbsolutePath();
    }
}
