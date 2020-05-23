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

import java.util.List;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Scanner;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import featherweightrust.core.BorrowChecker;
import featherweightrust.core.ProgramSpace;
import featherweightrust.core.Syntax.Term;
import featherweightrust.io.Lexer;
import featherweightrust.io.Parser;
import featherweightrust.util.ArrayUtils;
import featherweightrust.util.OptArg;
import featherweightrust.util.SliceIterator;
import featherweightrust.util.SyntaxError;
import featherweightrust.util.Triple;
import jmodelgen.core.Domain;

/**
 * The purpose of this experiment is to fuzz test the Rust compiler using a
 * large number of both valid and invalid programs.
 *
 * @author David J. Pearce
 *
 */
public class FuzzTestingExperiment {
	/**
	 * The command to use for executing the rust compiler.
	 */
	private static String RUSTC = "rustc";

	/**
	 * Extracted version tag from RUSTC;
	 */
	private static String RUST_VERSION;

	/**
	 * Flag whether to report failures to the console or not.
	 */
	private static boolean VERBOSE;

	/**
	 * Flag whether to report progress or not.
	 */
	private static boolean QUIET;

	/**
	 * Indicate whether Rust nightly is being used. This offers better performance
	 * as we can use the option "-Zno-codegen" to prevent the generation of object
	 * code.
	 */
	private static boolean NIGHTLY;

	/**
	 * Signal whether or not to enable copy inference for FR. Generally speaking,
	 * this should be enabled so that FR conforms more closely with Rust.
	 */
	private static boolean COPYINFERENCE;

	/**
	 * Indicate which edition of Rust is being used (e.g. 2015 or 2018).
	 */
	private static String EDITION;

	/**
	 * Configure number of threads to use.
	 */
	private static final int NTHREADS = Runtime.getRuntime().availableProcessors();

	/**
	 * Number of programs each thread to process in one go. This can make a real
	 * difference to the overall performance.
	 */
	private static final int BATCHSIZE = 50;

	/**
	 * Number of programs to pass into RUSTC in one go.
	 */
	private static final int RUSTC_BATCHSIZE = 10;

	/**
	 * Construct a thread pool to use for parallel processing.
	 */
	private static final ExecutorService executor = Executors.newCachedThreadPool();

	/**
	 * Command-line options
	 */
	private static final OptArg[] OPTIONS = {
			new OptArg("verbose","v","set verbose output"),
			new OptArg("quiet","q","disable progress reporting"),
			new OptArg("nightly","specify rust nightly available"),
			new OptArg("edition", "e", OptArg.STRING, "set rust edition to use", "2018"),
			new OptArg("expected","n",OptArg.LONG,"set expected domain size",-1L),
			new OptArg("pspace", "p", OptArg.LONGARRAY(4, 4), "set program space"),
			new OptArg("constrained", "c", OptArg.INT, "set maximum block count and constrain use-defs", -1),
			new OptArg("batch", "b", OptArg.LONGARRAY(2, 2), "set batch index and batch count", null),
			new OptArg("rustc", "r", OptArg.STRING, "specify rustc binary to use", RUSTC),
	};
	//
	public static void main(String[] _args) throws Exception {
		System.out.println("NUM THREADS: " + NTHREADS);
		System.out.println("BETA6");

		List<String> args = new ArrayList<>(Arrays.asList(_args));
		Map<String, Object> options = OptArg.parseOptions(args, OPTIONS);
		long expected = (Long) options.get("expected");
		long[] batch = (long[]) options.get("batch");
		//
		VERBOSE = options.containsKey("verbose");
		QUIET = options.containsKey("quiet");
		NIGHTLY = options.containsKey("nightly");
		EDITION = (String) options.get("edition");
		String rustc = (String) options.get("rustc");
		COPYINFERENCE = true;
		// Extract version string
		RUST_VERSION = new RustCompiler(rustc, 5000, NIGHTLY, EDITION).version().replace("\n", "").replace("\t", "");
		//
		Iterator<Term.Block> iterator;
		String label;
		//
		if (options.containsKey("pspace")) {
			long[] ivdw = (long[]) options.get("pspace");
			int c = (Integer) options.get("constrained");
			ProgramSpace space = new ProgramSpace((int) ivdw[0], (int) ivdw[1], (int) ivdw[2], (int) ivdw[3], COPYINFERENCE);
			// Create iterator
			if (c >= 0) {
				iterator = space.definedVariableWalker(c).iterator();
				label = space.toString() + "{def," + c + "}";
			} else {
				// Get domain
				Domain.Big<Term.Block> domain = space.domain();
				// Determine expected size
				expected = domain.bigSize().longValueExact();
				// Get iterator
				iterator = domain.iterator();
				//
				label = space.toString();
			}
		} else {
			// Read from stdin line by line
			List<Term.Block> inputs = readAll(System.in);
			iterator = inputs.iterator();
			expected = inputs.size();
			label = "STDIN";
		}
		// Slice iterator (if applicable)
		if (batch != null) {
			long[] range = determineIndexRange(batch[0], batch[1], expected);
			label += "[" + range[0] + ".." + range[1] + "]";
			System.out.println("LABEL: " + label);
			// Create sliced iterator
			iterator = new SliceIterator(iterator, range[0], range[1]);
			// Update expected
			expected = range[1] - range[0];
		}
		// Done
		check(iterator, expected, label);
		// Done
		System.exit(1);
	}

	/**
	 * Space a space of statements using n threads with a given batch size.
	 *
	 * @param space
	 * @param nthreads
	 * @param batch
	 * @param expected
	 * @param label
	 * @throws NoSuchAlgorithmException
	 * @throws IOException
	 * @throws InterruptedException
	 * @throws ExecutionException
	 */
	public static void check(Iterator<Term.Block> iterator, long expected, String label)
			throws NoSuchAlgorithmException, IOException, InterruptedException, ExecutionException {
		// Construct temporary memory areas
		Term.Block[][] arrays = new Term.Block[NTHREADS][BATCHSIZE];
		Future<Stats>[] threads = new Future[NTHREADS];
		Stats stats = new Stats(label);
		//
		while(iterator.hasNext()) {
			// Create next batch
			for (int i = 0; i != NTHREADS; ++i) {
				copyToArray(arrays[i], iterator);
			}
			// Submit next batch for process
			for (int i = 0; i != NTHREADS; ++i) {
				final Term.Block[] batch = arrays[i];
				threads[i] = executor.submit(() -> check(batch));
			}
			// Join all back together
			for (int i = 0; i != NTHREADS; ++i) {
				stats.join(threads[i].get());
			}
			// Report
			reportProgress(stats,expected);
		}
		//
		stats.print();
	}

	public static void reportProgress(Stats stats, long expected) {
		if(!QUIET) {
			long count = stats.total();
			long time = System.currentTimeMillis() - stats.start;
			//
			if(expected < 0) {
				System.err.print("\r(" + count + ")");
			} else {
				double rate = ((double) time) / count;
				double remainingMS = (expected - count) * rate;
				long remainingS = ((long)remainingMS/1000) % 60;
				long remainingM = ((long)remainingMS/(60*1000)) % 60;
				long remainingH = ((long)remainingMS/(60*60*1000));
				long percent = (long) (100D * (count) / expected);
				String remaining = remainingH + "h " + remainingM + "m " + remainingS + "s";
				System.err.print("\r(" + percent +  "%, " + String.format("%.0f",(1000/rate)) +  "/s, remaining " + remaining + ")           ");
			}
		}
	}

	/**
	 * Fuzz test a batch of programs in one go.
	 *
	 * @param batch --- The batch of programs to check which may include
	 *              <code>null</code> programs to skip (i.e. in the last batch).
	 * @return
	 * @throws NoSuchAlgorithmException
	 * @throws IOException
	 * @throws InterruptedException
	 * @throws JSONException
	 */
	public static Stats check(Term.Block[] batch) throws NoSuchAlgorithmException, IOException, InterruptedException {
		Stats stats = new Stats(null);
		// First, strip out any which are not canonical
		for(int i=0;i!=batch.length;++i) {
			if(!isCanonical(batch[i])) {
				stats.notCanonical++;
				batch[i] = null;
			}
		}
		batch = ArrayUtils.removeAll(batch, null);
		//
		for (int i = 0; i < batch.length; i += RUSTC_BATCHSIZE) {
			Term.Block[] ps = Arrays.copyOfRange(batch, i, Math.min(batch.length, i + RUSTC_BATCHSIZE));
			try {
				check(stats, ps);
			} catch(Exception e) {
				System.out.println("=================================================================");
				System.out.println("Exception");
				System.out.println("=================================================================");
				for (int j = 0; j != ps.length; ++j) {
					System.out.println("[" + j + "] " + ps[j]);
				}
				e.printStackTrace();
			}
		}
		return stats;
	}

	/**
	 * Fuzz test a single program whilst updating the stats accordingly.
	 *
	 * @param b
	 * @param stats
	 * @throws NoSuchAlgorithmException
	 * @throws IOException
	 * @throws InterruptedException
	 * @throws JSONException
	 */
	public static void check(Stats stats, Term.Block[] programs) throws NoSuchAlgorithmException, IOException, InterruptedException {
		// Check using calculus
		SyntaxError[] FR_errs = calculusCheckPrograms(programs);
		// Construct Rust version of programs
		String[] rustPrograms = new String[programs.length];
		for(int i=0;i!=programs.length;++i) {
			rustPrograms[i] = Util.toRustProgram(programs[i],"f_" + i);
		}
		// Iterate batch until no errors returned. This is necessary to extract all
		// errors from the batch because some errors hide others.
		String rustc_err;
		List<String>[] rustcErrors = new List[programs.length];
		List<String>[] rustcWarnings = new List[programs.length];
		while((rustc_err = runRustc(rustPrograms)) != null) {
			// Determine which lines gave errors
			analyseRustErrors(rustc_err, rustcErrors, rustcWarnings, rustPrograms);
			// Remove any program for which an error has been produced. This is necessary to
			// prevent it from being recompiled again.
			for(int i=0;i!=rustcErrors.length;++i) {
				if(rustcErrors[i] != null) {
					rustPrograms[i] = null;
				}
			}
		}
		// Updated statistic accordingly
		for(int i=0;i!=programs.length;++i) {
			Term.Block b = programs[i];
			updateStats(stats, b,  FR_errs[i], rustcErrors[i], rustcWarnings[i]);
		}
	}

	public static String runRustc(String[] rustPrograms) throws NoSuchAlgorithmException, IOException, InterruptedException {
		String rustSourceCode = toRustSourceCode(rustPrograms);
		// Determine hash of program for naming
		String hash = getHash(rustSourceCode);
		// Determine filename based on hash
		String prefix = File.separator + hash;
		// Create temporary file
		String srcFilename = createTemporaryFile(prefix, ".rs", rustSourceCode);
		String binFilename = srcFilename.replace(".rs", "");
		// Run the rust compile
		RustCompiler rustc = new RustCompiler(RUSTC, 5000, NIGHTLY, EDITION);
		Triple<Boolean, String, String> p = rustc.compile(srcFilename, binFilename);
		// Delete source and binary files
		new File(srcFilename).delete();
		new File(binFilename).delete();
		// Done
		return p.first() ? null : p.third();
	}

	public static String toRustSourceCode(String[] rustPrograms) {
		// Compact all rust programs into a source code string
		StringBuilder builder = new StringBuilder();
		for(int i=0;i!=rustPrograms.length;++i) {
			String ith = rustPrograms[i];
			if(ith != null) {
				builder.append(ith);
			}
			builder.append('\n');
		}
		builder.append("fn main() {}");
		// Done
		return builder.toString();
	}

	public static void updateStats(Stats stats, Term.Block program, SyntaxError FR_err,
			List<String> rustcErrors, List<String> rustcWarnings) throws IOException, NoSuchAlgorithmException {
		boolean FR_status = (FR_err == null);
		boolean rustc_status = (rustcErrors == null);
		// Analysis output
		if (FR_status != rustc_status) {
			// FIXME: might want to put that back
			stats.record(rustcErrors);
			stats.record(rustcWarnings);
			if (rustc_status) {
				// Rust says yes, FR says no
				if (Util.requiresDerefCoercions(program)) {
					stats.inconsistentDerefCoercion++;
				} else {
					reportFailure(program, FR_err, rustcErrors);
					stats.inconsistentInvalid++;
				}
			} else {
				// Rust says no, FR says yes.
				reportFailure(program, FR_err, rustcErrors);
				stats.inconsistentPossibleBug++;
			}
		} else if (FR_status) {
			stats.valid++;
		} else {
			stats.invalid++;
		}
	}

	/**
	 * Parse the output from the rust compiler (which is in short form) and
	 * associate each error message with the line on which it was generated. The
	 * line number corresponds, of course, directly to the program in question.
	 *
	 * @param err
	 * @param n
	 * @return
	 * @throws JSONException
	 */
	public static void analyseRustErrors(String err, List<String>[] errors, List<String>[] warnings, String[] rustPrograms) {
		String[] lines = err.split("\n");
		for(int i=0;i!=lines.length;++i) {
			String ith = lines[i];
			if(ith.length() > 0) {
				String[] cols = ith.split(":");
				if(cols.length == 5) {
					int line = Integer.parseInt(cols[1]);
					String type = cols[3].trim();
					String message = cols[4].trim();
					// Line numbers start from 1, programs start from 0
					line = line - 1;
					//
					if(type.startsWith("warning")) {
						// Register warning
						List<String> e = warnings[line];
						if (e == null) {
							e = new ArrayList<>();
							warnings[line] = e;
						}
						e.add(type + ":" + message);

					} else {
						// Register error
						List<String> e = errors[line];
						if (e == null) {
							e = new ArrayList<>();
							errors[line] = e;
						}
						e.add(type + ":" + message);
					}
				}
			}
		}
	}

	/**
	 * Report an inconsistency between rustc and Featherweight Rust.
	 *
	 * @param b
	 * @param program
	 * @param status
	 * @param rustc_err
	 * @throws IOException
	 * @throws NoSuchAlgorithmException
	 */
	public static void reportFailure(Term.Block b, SyntaxError FR_err, List<String> rustc_errs) throws IOException, NoSuchAlgorithmException {
		if(VERBOSE) {
			// Reconstruct the rust program
			String program = Util.toRustProgram(b,"f_?");
			// Determine hash of program for naming
			System.out.println("********* FAILURE");
			System.out.println("BLOCK: " + b.toString());
			System.out.println("PROGRAM: " + program);
			System.out.println("HASH: " + getHash(program));
			System.out.println("RUSTC: " + (FR_err != null));
			if(rustc_errs != null) {
				for(int i=0;i!=rustc_errs.size();++i) {
					System.out.println("> " + rustc_errs.get(i));
				}
			}
			if(FR_err != null) {
				FR_err.outputSourceError(System.out);
			}
		}
	}

	/**
	 * Borrow check zer or more programs using the borrow checker which is part of this
	 * calculs.
	 *
	 * @param b
	 * @param l
	 * @return
	 */
	public static SyntaxError[] calculusCheckPrograms(Term.Block[] programs) {
		SyntaxError[] errs = new SyntaxError[programs.length];
		for(int i=0;i!=programs.length;++i) {
			Term.Block b = programs[i];
			BorrowChecker checker = new BorrowChecker(b.toString());
			try {
				checker.apply(BorrowChecker.EMPTY_ENVIRONMENT, ProgramSpace.ROOT, b);
			} catch (SyntaxError e) {
				errs[i] = e;
			}
		}
		return errs;
	}

	public static String createTemporaryFile(String prefix, String suffix, String contents) throws IOException, InterruptedException {
		// Create new file
		File f = File.createTempFile(prefix,suffix);
		// Open for writing
		FileWriter writer = new FileWriter(f);
		// Write contents to file
		writer.write(contents.toCharArray());
		// Done creating file
		writer.close();
		//
		return f.getAbsolutePath();
	}

	/**
	 * Determine whether a given program is canonical or not. Canonical programs
	 * have their variables declared in a specific order. The purpose of this is
	 * just to eliminate isomorphs with respect to variable renaming.
	 *
	 * @param stmt
	 * @param numDeclared
	 * @return
	 */
	public static boolean isCanonical(Term stmt) {
		try {
			isCanonical(stmt,0);
			return true;
		} catch(IllegalArgumentException e) {
			return false;
		}
	}

	private static int isCanonical(Term stmt, int declared) {
		if(stmt instanceof Term.Block) {
			Term.Block s = (Term.Block) stmt;
			int declaredWithin = declared;
			for(int i=0;i!=s.size();++i) {
				declaredWithin = isCanonical(s.get(i), declaredWithin);
			}
			return declared;
		} else if(stmt instanceof Term.Let) {
			Term.Let s = (Term.Let) stmt;
			String var = s.variable();
			if(!ProgramSpace.VARIABLE_NAMES[declared].equals(var)) {
				// Program is not canonical
				throw new IllegalArgumentException();
			}
			declared = declared+1;
		}
		return declared;
	}

	private static <T> int copyToArray(T[] array, Iterator<T> b) {
		int i = 0;
		// Read items into array
		while (b.hasNext() && i < array.length) {
			array[i++] = b.next();
		}
		// Reset any trailing items
		for (; i < array.length; ++i) {
			array[i] = null;
		}
		// Done
		return i;
	}

	private static long[] determineIndexRange(long index, long count, long n) {
		if (count > n) {
			return new long[] { 0, n };
		} else {
			long size = (n / count) + 1;
			long start = index * size;
			long end = Math.min(n, (index + 1) * size);
			return new long[] { start, end };
		}
	}

	/**
	 * Get the SHA-256 hash for a given string. This is helpful for generating a
	 * unique ID for each filename
	 *
	 * @param original
	 * @return
	 * @throws NoSuchAlgorithmException
	 */
	private static String getHash(String original) throws NoSuchAlgorithmException {
		// Get SHA-256 hash
		byte[] hash = MessageDigest.getInstance("SHA-256").digest(original.getBytes(StandardCharsets.UTF_8));
		// Convert hash to hex string
		StringBuffer hexString = new StringBuffer();
		for (int i = 0; i < hash.length; i++) {
			hexString.append(String.format("%02X", hash[i]));
		}
		return hexString.toString();
	}

	private static List<Term.Block> readAll(InputStream in) throws IOException {
		ArrayList<Term.Block> inputs = new ArrayList<>();
		Scanner stdin = new Scanner(in);
		while (stdin.hasNext()) {
		    String input = stdin.nextLine();
		    // Tokenize input program
		    List<Lexer.Token> tokens = new Lexer(new StringReader(input)).scan();
			// Parse block
			Term.Block stmt = new Parser(input,tokens).parseStatementBlock(new Parser.Context(), ProgramSpace.ROOT);
			// Record it
		    inputs.add(stmt);
		}
		//
		stdin.close();
		// Done
		return inputs;
	}

	private final static class Stats {
		private final long start = System.currentTimeMillis();
		private final String label;
		public long valid = 0;
		public long invalid = 0;
		public long notCanonical = 0;
		public long invalidPrefix = 0;
		public long inconsistentValid = 0;
		public long inconsistentInvalid = 0;
		public long inconsistentDerefCoercion = 0;
		public long inconsistentPossibleBug = 0;
		private final HashMap<String,Integer> errors;

		public Stats(String label) {
			this.label=label;
			this.errors = new HashMap<>();
		}

		public long total() {
			return valid + invalid + inconsistentValid + inconsistentInvalid + inconsistentDerefCoercion
					+ +inconsistentPossibleBug + notCanonical + invalidPrefix;
		}

		public void join(Stats stats) {
			this.valid += stats.valid;
			this.invalid += stats.invalid;
			this.notCanonical += stats.notCanonical;
			this.invalidPrefix += stats.invalidPrefix;
			this.inconsistentValid += stats.inconsistentValid;
			this.inconsistentInvalid += stats.inconsistentInvalid;
			this.inconsistentDerefCoercion += stats.inconsistentDerefCoercion;
			this.inconsistentPossibleBug += stats.inconsistentPossibleBug;
			// Join error classifications
			join(errors,stats.errors);
		}

		/**
		 * Given the output from the RustCompiler look through to see what errors are
		 * present and attempt to classify them.
		 *
		 * @param stderr
		 * @throws IOException
		 */
		public void record(List<String> errs) throws IOException {
			if(errs != null) {
				for(int i=0;i!=errs.size();++i) {
					String line = errs.get(i);
					String code = line.split(":")[0];
					Integer j = errors.get(code);
					if (j == null) {
						errors.put(code, 1);
					} else {
						errors.put(code, j + 1);
					}
				}
			}
		}

		public void print() {
			long time = System.currentTimeMillis() - start;
			System.out.println("{");
			System.out.println("\tSPACE: " + label);
			System.out.println("\tVERSION: \"" + RUST_VERSION + "\"");
			System.out.println("\tEDITION: " + EDITION);
			System.out.println("\tNIGHTLY: " + NIGHTLY);
			System.out.println("\tTIME: " + (time/1000) + "s");
			System.out.println("\tTOTAL: " + total());
			System.out.println("\tVALID: " + valid);
			System.out.println("\tINVALID: " + invalid);
			System.out.println("\tIGNORED (NOT CANONICAL): " + notCanonical);
			System.out.println("\tIGNORED (INVALID PREFIX): " + invalidPrefix);
			System.out.println("\tINCONSISTENT (VALID): " + inconsistentValid);
			System.out.println("\tINCONSISTENT (INVALID): " + inconsistentInvalid);
			System.out.println("\tINCONSISTENT (DEREF COERCION): " + inconsistentDerefCoercion);
			System.out.println("\tINCONSISTENT (POSSIBLE BUG): " + inconsistentPossibleBug);
			for(Map.Entry<String, Integer> e : errors.entrySet()) {
				System.out.println("\tINCONSISTENT (" + e.getKey() + "): " + e.getValue());
			}
			System.out.println("}");
		}

		public static void join(Map<String,Integer> left, Map<String,Integer> right) {
			for(Map.Entry<String, Integer> e : right.entrySet()) {
				String key = e.getKey();
				Integer i = left.get(key);
				if(i != null) {
					i = i + e.getValue();
				} else {
					i = e.getValue();
				}
				left.put(key, i);
			}
		}
	}
}
