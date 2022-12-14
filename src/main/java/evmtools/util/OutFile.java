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
import java.util.zip.GZIPOutputStream;

public interface OutFile {
	public void print(String text) throws IOException;

	public void println(String text) throws IOException;

	public void close() throws IOException;

	public boolean exists();

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

		@Override
		public boolean exists() {
			return false;
		}
	}

	public static class LazyOutFile implements OutFile {
		private final File file;
		private final boolean gzip;
		private FileOutputStream fout;
		private PrintStream pout;

		public LazyOutFile(File file, boolean gzip) {
			this.file = file;
			this.gzip = gzip;
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


		@Override
		public boolean exists() {
			return file.exists();
		}

		private void init() throws IOException {
			if(pout == null) {
				fout = new FileOutputStream(file);
				if(gzip) {
					pout = new PrintStream(new GZIPOutputStream(fout));
				} else {
					pout = new PrintStream(fout);
				}
			}
		}
	}
}
