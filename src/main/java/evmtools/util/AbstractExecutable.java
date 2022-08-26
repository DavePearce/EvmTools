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

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class AbstractExecutable {

	/**
	 * Timeout (in millisecond) to wait for the process to complete.
	 */
	protected int timeout = 10000;

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


	/**
	 * Grab everything produced by a given input stream until the End-Of-File (EOF)
	 * is reached. This is implemented as a separate thread to ensure that reading
	 * from other streams can happen concurrently. For example, we can read
	 * concurrently from <code>stdin</code> and <code>stderr</code> for some process
	 * without blocking that process.
	 *
	 * @author David J. Pearce
	 *
	 */
	public static class StreamGrabber extends Thread {
		private InputStream input;
		private StringBuffer buffer;
		private AtomicBoolean finished = new AtomicBoolean(false);

		public StreamGrabber(InputStream input) {
			this.input = input;
			this.buffer = new StringBuffer();
			start();
		}

		@Override
		public void run() {
			try {
				int nextChar;
				// keep reading!!
				while ((nextChar = input.read()) != -1) {
					buffer.append((char) nextChar);
				}
			} catch (IOException ioe) {
			}
			this.finished.set(true);
		}

		public String get() {
			while(!finished.get()) {
				try {
					Thread.sleep(200);
				} catch (InterruptedException e) {
					// Ignore.
				}
			}
			return buffer.toString();
		}
	}
}
