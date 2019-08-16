// This file is part of the FeatherweightRust Compiler (frc).
//
// The FeatherweightRust Compiler is free software; you can redistribute
// it and/or modify it under the terms of the GNU General Public
// License as published by the Free Software Foundation; either
// version 3 of the License, or (at your option) any later version.
//
// The WhileLang Compiler is distributed in the hope that it
// will be useful, but WITHOUT ANY WARRANTY; without even the
// implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR
// PURPOSE. See the GNU General Public License for more details.
//
// You should have received a copy of the GNU General Public
// License along with the WhileLang Compiler. If not, see
// <http://www.gnu.org/licenses/>
//
// Copyright 2018, David James Pearce.
package featherweightrust.testing.experiments;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.concurrent.TimeUnit;

/**
 * A simplistic Java binding to the Rust Compiler.
 *
 * @author David J. Pearce
 *
 */
public final class RustCompiler {
	private final String rust_cmd;
	private final long timeout;

	public RustCompiler(String rust_cmd, long timeout) {
		this.rust_cmd = rust_cmd;
		this.timeout = timeout;
	}

	/**
	 * Attempt to compile a given program. If non-null return indicates an error
	 * occurred (non-zero exit code from rustc).
	 *
	 * @param filename
	 * @return
	 * @throws InterruptedException
	 * @throws IOException
	 */
	public String compile(String filename, String outdir) throws InterruptedException, IOException {
		// ===================================================
		// Construct command
		// ===================================================
		ArrayList<String> command = new ArrayList<>();
		command.add(rust_cmd);
		command.add("-A");
		command.add("unused-variables");
		command.add("-A");
		command.add("unused-mut");
		command.add("--out-dir");
		command.add(outdir);
		command.add("-Zno-codegen");
		command.add(filename);
		// ===================================================
		// Execute Process
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
			if(success && child.exitValue() == 0) {
				return null;
			} else {
				return new String(stderr);
			}
		} finally {
			// make sure child process is destroyed.
			child.destroy();
		}
	}

	private static byte[] readInputStream(InputStream input) throws IOException {
		byte[] buffer = new byte[1024];
		ByteArrayOutputStream output = new ByteArrayOutputStream();
		while (input.available() > 0) {
			int count = input.read(buffer);
			output.write(buffer, 0, count);
		}
		return output.toByteArray();
	}
}