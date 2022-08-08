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

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import java.util.Scanner;
import java.util.concurrent.TimeUnit;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import evmtools.core.Environment;
import evmtools.core.Trace;
import evmtools.core.Transaction;
import evmtools.core.WorldState;
import evmtools.util.StreamGrabber;

/**
 * An interface into Geth's <code>evm t8n</code> tool. At this stage, there
 * remains an outstanding problem to resolve --- namely, how to determine the
 * <code>v</code>, <code>r</code> and <code>s</code> values.
 *
 * @author David J. Pearce
 *
 */
public class GethT8n {
	/**
	 * Command to use to execture Geth.
	 */
	private final String cmd = "evm";

	private boolean debug = true;

	/**
	 * Timeout (in millisecond) to wait for the process to complete.
	 */
	private int timeout = 10000;

	public GethT8n setTimeout(int timeout) {
		this.timeout = timeout;
		return this;
	}

	/**
	 * Execute a given transaction using the EVM.
	 *
	 * @param tx
	 * @return
	 */
	public Trace execute(String fork, Environment env, WorldState pre, Transaction tx) throws JSONException, IOException {
		Path tempDir = null;
		String envFile = null;
		String allocFile = null;
		String txsFile = null;
		try {
			tempDir = createTemporaryDirectory();
			System.out.println("GOT: " + pre.toJSON().toString());
			envFile = createEnvFile(tempDir,env);
			allocFile = createAllocFile(tempDir,pre);
			txsFile = createTransactionsFile(tempDir,tx);
			// Build up the command
			ArrayList<String> command = new ArrayList<>();
			command.add(cmd);
			command.add("t8n");
			command.add("--state.fork");
			command.add(fork);
			// Trace info
			command.add("--trace");
			//command.add("--trace.memory");
//			command.add("--trace.nostorage");
//			command.add("false");
			// Input
			command.add("--input.env");
			command.add(envFile);
			command.add("--input.alloc");
			command.add(allocFile);
			command.add("--input.txs");
			command.add(txsFile);
			command.add("--output.basedir");
			command.add(tempDir.toString());
//			command.add("--output.result");
//			command.add("stdout");
			command.add("--output.alloc");
			command.add("alloc-out.json");
			//
			System.out.println("COMMAND: " + command);
			String out = exec(command);
			System.out.println("SYSOUT: " + out);
			//
			if(out != null) {
				// Parse into JSON. Geth produces one line per trace element.
				return readTraceFile(tempDir);
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
			if(debug) {
				System.out.println("Working directory: " + tempDir);
			} else {
				forceDelete(tempDir);
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

	private static String createAllocFile(Path dir, WorldState pre)
			throws JSONException, IOException, InterruptedException {
		JSONObject json = pre.toJSON();
		byte[] bytes = json.toString(2).getBytes();
		return createTemporaryFile(dir, "alloc.json", bytes);
	}

	private static String createEnvFile(Path dir, Environment env)
			throws JSONException, IOException, InterruptedException {
		JSONObject json = env.toJSON();
		byte[] bytes = json.toString(2).getBytes();
		return createTemporaryFile(dir, "env.json", bytes);
	}

	private static String createTransactionsFile(Path dir, Transaction tx)
			throws JSONException, IOException, InterruptedException {
		JSONObject obj = tx.toJSON();
		obj.put("v","");
		obj.put("r","");
		obj.put("s","");
		JSONArray json = new JSONArray();
		json.put(0,obj);
		byte[] bytes = json.toString(2).getBytes();
		return createTemporaryFile(dir, "txs.json", bytes);
	}

	private static Path createTemporaryDirectory() throws IOException {
		return Files.createTempDirectory("geth");
	}

	private static Trace readTraceFile(Path dir) throws IOException {
		ArrayList<Trace.Element> elements = new ArrayList<>();
		Files.walk(dir, 10).forEach(f -> {
			try {
				if (f.toString().endsWith(".jsonl")) {
					if (elements.size() > 0) {
						throw new IllegalArgumentException("multiple trace files detected");
					}
					Scanner scanner = new Scanner(f);
					while (scanner.hasNextLine()) {
						String line = scanner.nextLine();
						JSONObject element = new JSONObject(line);
						elements.add(Trace.Element.fromJSON(element));
					}
					scanner.close();
				}
			} catch (JSONException e) {
				throw new RuntimeException("Unable to parse JSON trace file");
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
		});
		return new Trace(elements);
	}

	/**
     * Write a given string into a temporary file which can then be checked by boogie.
     *
     * @param contents
     * @return
     */
    private static String createTemporaryFile(Path dir, String name, byte[] contents)
            throws IOException, InterruptedException {
    	File f = dir.resolve(name).toFile();
        // Open for writing
        FileOutputStream fout = new FileOutputStream(f);
        // Write contents to file
        fout.write(contents);
        // Done creating file
        fout.close();
        //
        return f.getAbsolutePath();
    }

    /**
	 * Force a directory and all its contents to be deleted.
	 *
	 * @param path
	 * @throws IOException
	 */
	public void forceDelete(Path path) throws IOException {
		if (Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) {
			try (DirectoryStream<Path> entries = Files.newDirectoryStream(path)) {
				for (Path entry : entries) {
					forceDelete(entry);
				}
			}
		}
		if (path.toFile().exists()) {
			Files.delete(path);
		}
	}
}
