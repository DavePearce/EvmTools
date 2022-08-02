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

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;

public interface OutFile {
	public void print(String text) throws IOException;

	public void println(String text) throws IOException;

	public void close() throws IOException;

	//
	public static class PrintOutFile implements OutFile {
		private final PrintStream pout;

		public PrintOutFile(PrintStream pout) {
			this.pout = pout;
		}

		@Override
		public void print(String text) {
			pout.print(text);
		}

		@Override
		public void println(String text) {
			pout.println(text);
		}

		@Override
		public void close() {
			pout.close();
		}
	}

	public static class LazyOutFile implements OutFile {
		private final File file;
		private FileOutputStream fout;
		private PrintStream pout;

		public LazyOutFile(File file) {
			this.file = file;
		}

		@Override
		public void print(String text) throws IOException {
			init();
			pout.print(text);
		}
		@Override
		public void println(String text) throws IOException {
			init();
			pout.println(text);
		}
		@Override
		public void close() throws IOException {
			pout.flush();
			fout.flush();
			pout.close();
			fout.close();
		}

		private void init() throws IOException {
			if(pout == null) {
				fout = new FileOutputStream(file);
				pout = new PrintStream(fout);
			}
		}
	}
}
