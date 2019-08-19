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
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import featherweightrust.core.BorrowChecker;
import featherweightrust.core.ProgramSpace;
import featherweightrust.core.Syntax.Expr;
import featherweightrust.core.Syntax.Lifetime;
import featherweightrust.core.Syntax.Stmt;
import featherweightrust.core.Syntax.Type;
import featherweightrust.util.Pair;
import featherweightrust.util.SyntaxError;
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
	 * Temporary directory into which rust programs are placed.
	 */
	private static final String tempDir = "/local/scratch/tmp";

	/**
	 * Flag whether to report failures to the console or not.
	 */
	private static final boolean VERBOSE = true;

	/**
	 * Indicate whether Rust nightly is being used. This offers better performance
	 * as we can use the option "-Zno-codegen" to prevent the generation of object
	 * code.
	 */
	private static final boolean NIGHTLY = true;

	/**
	 * The command to use for executing the rust compiler.
	 */
	private static final RustCompiler RUSTC = new RustCompiler("/local/scratch/cargo/bin/rustc", 5000, NIGHTLY);
	/**
	 * Configure number of threads to use.
	 */
	private static final int NTHREADS = Runtime.getRuntime().availableProcessors();

	/**
	 * Number of programs each thread to process in one go. This can make a real
	 * difference to the overall performance.
	 */
	private static final int BATCHSIZE = 10;

	/**
	 * Construct a thread pool to use for parallel processing.
	 */
	private static final ExecutorService executor = Executors.newCachedThreadPool();

	private static final ConcurrentHashMap<String,Boolean> INVALID_PREFIXES = new ConcurrentHashMap<>();

	static {
		// Force creation of temporary directory
		new File(tempDir).mkdirs();
	}
	//
	public static void main(String[] args) throws Exception {
		System.out.println("NUM THREADS: " + NTHREADS);
		// Complete domains
//		check(new ProgramSpace(1, 1, 1, 1));
//		check(new ProgramSpace(1, 1, 1, 2));
//		check(new ProgramSpace(1, 1, 2, 2));
		// Constrained domains
//		check(new ProgramSpace(1, 1, 2, 2), 2, 1400);
		check(new ProgramSpace(1, 2, 2, 2), 2, 4208);
		check(new ProgramSpace(2, 2, 2, 2), 2, 11280);
//		check(new ProgramSpace(1, 2, 2, 3), 2, 34038368);
//		check(new ProgramSpace(1, 3, 2, 3), 2, 76524416);
		System.exit(1);
	}

	public static void check(ProgramSpace space) throws Exception  {
		Domain.Big<Stmt.Block> domain = space.domain();
		check(domain,domain.bigSize().longValueExact(),space.toString());
	}

	public static void check(ProgramSpace space, int maxBlocks, long expected) throws Exception {
		check(space.definedVariableWalker(maxBlocks), expected, space.toString() + "{def," + maxBlocks + "}");
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
	public static void check(Iterable<Stmt.Block> space, long expected, String label)
			throws NoSuchAlgorithmException, IOException, InterruptedException, ExecutionException {
		// Construct temporary memory areas
		Stmt.Block[][] arrays = new Stmt.Block[NTHREADS][BATCHSIZE];
		Future<Stats>[] threads = new Future[NTHREADS];
		//
		Iterator<Stmt.Block> iterator = space.iterator();
		Stats stats = new Stats(label);
		//
		while(iterator.hasNext()) {
			// Create next batch
			for (int i = 0; i != NTHREADS; ++i) {
				copyToArray(arrays[i], iterator);
			}
			// Submit next batch for process
			for (int i = 0; i != NTHREADS; ++i) {
				final Stmt.Block[] batch = arrays[i];
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
		long count = stats.total();
		long time = System.currentTimeMillis() - stats.start;
		//
		if(expected < 0) {
			System.out.print("\r(" + count + ")");
		} else {
			double rate = ((double) time) / count;
			double remainingMS = (expected - count) * rate;
			long remainingS = ((long)remainingMS/1000) % 60;
			long remainingM = ((long)remainingMS/(60*1000)) % 60;
			long remainingH = ((long)remainingMS/(60*60*1000));
			long percent = (long) (100D * (count) / expected);
			String remaining = remainingH + "h " + remainingM + "m " + remainingS + "s";
			System.out.print("\r(" + percent +  "%, " + String.format("%.0f",(1000/rate)) +  "/s, remaining " + remaining + ")           ");
		}
	}

	public static Stats check(Stmt.Block[] batch) throws NoSuchAlgorithmException, IOException, InterruptedException {
		Stats stats = new Stats(null);
		for(int i=0;i!=batch.length;++i) {
			Stmt.Block block = batch[i];
			if(block != null) {
				check(block,stats);
			}
		}
		return stats;
	}

	public static void check(Stmt.Block b, Stats stats) throws NoSuchAlgorithmException, IOException, InterruptedException {
		if(!isCanonical(b)) {
			stats.notCanonical++;
		} else if(hasCopy(b)) {
			stats.hasCopy++;
		} else {
			String prefix = toPrefixString(b);
			//
			if(INVALID_PREFIXES.containsKey(prefix)) {
				stats.invalidPrefix++;
			} else {
				// Check using calculus
				boolean checked = calculusCheckProgram(b);
				// Construct rust program
				String program = toRustProgram(b);
				// Determine hash of program for naming
				long hash = program.hashCode() & 0x00000000ffffffffL;
				// Determine filename based on hash
				String binFilename = tempDir + File.separator + hash;
				String srcFilename = binFilename + ".rs";
				// Create temporary file
				createTemporaryFile(srcFilename, program);
				// Run the rust compile
				Pair<Boolean,String> p =  RUSTC.compile(srcFilename, tempDir);
				boolean status = p.first();
				String stderr = p.second();
				//
				if (checked != status) {
					reportFailure(b,program,status,stderr);
					stats.record(stderr);
					if (status) {
						stats.inconsistentInvalid++;
					} else {
						// Rust says no, FR says yes.
						stats.inconsistentValid++;
					}
					if(!VERBOSE) {
						// Delete erronous rust source file
						new File(srcFilename).delete();
					}
				} else if (checked) {
					new File(srcFilename).delete();
					stats.valid++;
				} else {
					new File(srcFilename).delete();
					stats.invalid++;
					// Record invalid prefix
					INVALID_PREFIXES.put(prefix, true);
				}
				// Always delete binary
				if(!NIGHTLY) {
					new File(binFilename).delete();
				}
			}
		}
	}

	public static void reportFailure(Stmt.Block b, String program, boolean status, String stderr) throws IOException {
		if(VERBOSE) {
			// Determine hash of program for naming
			long hash = program.hashCode() & 0x00000000ffffffffL;
			System.err.println("********* FAILURE");
			System.err.println("BLOCK: " + b.toString());
			System.err.println("PROGRAM: " + program);
			System.err.println("HASH: " + hash);
			System.err.println("RUSTC: " + status);
			if(stderr != null) {
				System.err.println(stderr);
			}
		}
	}

	public static final BorrowChecker checker = new ExtendedBorrowChecker("");

	/**
	 * Borrow check the program using the borrow checker which is part of this
	 * calculs.
	 *
	 * @param b
	 * @param l
	 * @return
	 */
	public static boolean calculusCheckProgram(Stmt.Block b) {
		try {
			checker.apply(BorrowChecker.EMPTY_ENVIRONMENT, ProgramSpace.ROOT, b);
			return true;
		} catch (SyntaxError e) {
			return false;
		}
	}

	public static void createTemporaryFile(String filename, String contents) throws IOException, InterruptedException {
		// Create new file
		RandomAccessFile writer = new RandomAccessFile(filename, "rw");
		// Write contents to file
		writer.write(contents.getBytes(StandardCharsets.UTF_8));
		// Done creating file
		writer.close();
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
	public static boolean isCanonical(Stmt stmt) {
		try {
			isCanonical(stmt,0);
			return true;
		} catch(IllegalArgumentException e) {
			return false;
		}
	}

	private static int isCanonical(Stmt stmt, int declared) {
		if(stmt instanceof Stmt.Block) {
			Stmt.Block s = (Stmt.Block) stmt;
			int declaredWithin = declared;
			for(int i=0;i!=s.size();++i) {
				declaredWithin = isCanonical(s.get(i), declaredWithin);
			}
			return declared;
		} else if(stmt instanceof Stmt.Let) {
			Stmt.Let s = (Stmt.Let) stmt;
			String var = s.variable().name();
			if(!ProgramSpace.VARIABLE_NAMES[declared].equals(var)) {
				// Program is not canonical
				throw new IllegalArgumentException();
			}
			declared = declared+1;
		}
		return declared;
	}

	/**
	 * Check whether the given program attempts to copy a variable that is no longer
	 * live. Such programs are ignored because such expressions will be treated
	 * differently by the rust compiler (i.e. treated as moves).
	 *
	 * @param stmt
	 * @return
	 */
	private static boolean hasCopy(Stmt stmt) {
		if(stmt instanceof Stmt.Block) {
			Stmt.Block b = (Stmt.Block) stmt;
			// Go backwards through the block for obvious reasons.
			for (int i = 0; i != b.size(); ++i) {
				if (hasCopy(b.get(i))) {
					return true;
				}
			}
			return false;
		} else if(stmt instanceof Stmt.Let) {
			Stmt.Let s = (Stmt.Let) stmt;
			return hasCopy(s.initialiser());
		} else if(stmt instanceof Stmt.Assignment) {
			Stmt.Assignment s = (Stmt.Assignment) stmt;
			return hasCopy(s.rightOperand());
		} else {
			Stmt.IndirectAssignment s = (Stmt.IndirectAssignment) stmt;
			return hasCopy(s.leftOperand()) || hasCopy(s.rightOperand());
		}
	}

	private static boolean hasCopy(Expr expr) {
		if(expr instanceof Expr.Copy) {
			return true;
		} else if(expr instanceof Expr.Box) {
			Expr.Box e = (Expr.Box) expr;
			return hasCopy(e.operand());
		}
		return false;
	}

	/**
	 *
	 */

	/**
	 * Given a statement block in the calculus, generate a syntactically correct
	 * Rust program.
	 *
	 * @param b
	 * @return
	 */
	private static String toRustProgram(Stmt.Block b) {
		return "fn main() " + b.toRustString();
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

	/**
	 * Compute the "prefix string" for a block. That is, the string of the block
	 * without trailing curly braces. For example, "{ let mut x = 0; }" becomes "{
	 * let mut x = 0;".
	 *
	 * @param b
	 * @return
	 */
	private static String toPrefixString(Stmt.Block b) {
		String str = b.toRustString();
		int end = str.length();
		while (end > 0) {
			end = end - 1;
			char c = str.charAt(end);
			if (c != ' ' && c != '}') {
				break;
			}
		}
		return str.substring(0, end + 1);
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

	/**
	 * This provides a slightly alternative implementation of the borrow checker
	 * which infers whether or not a copy or move is being made.
	 *
	 * @author David J. Pearce
	 *
	 */
	private final static class ExtendedBorrowChecker extends BorrowChecker {

		public ExtendedBorrowChecker(String sourcefile) {
			super(sourcefile);
		}
		/**
		 * T-MoveVar
		 */
		@Override
		public Pair<Environment, Type> apply(Environment R1, Lifetime l, Expr.Variable e) {
			String x = e.name();
			//
			check(R1.get(x) != null, UNDECLARED_VARIABLE, e);
			// Extract type from current environment
			Type T = R1.get(x).type();
			//
			Environment R2;
			// Implement destructive update (if applicable)
			if (copyable(T)) {
				// Check variable not mutably borrowed
				check(!mutBorrowed(R1,x), VARIABLE_BORROWED, e);
				//
				R2 = R1;
			} else {
				// Check variable not borrowed to prevent "moving out"
				check(!borrowed(R1, x), VARIABLE_BORROWED, e);
				// Implement destructive update (if applicable)
				R2 = R1.remove(x);
			}
			//
			return new Pair<>(R2,T);
		}

		/**
		 * T-CopyVar
		 */
		@Override
		public Pair<Environment, Type> apply(Environment R, Lifetime l, Expr.Copy e) {
			throw new IllegalArgumentException("copies ignored and, instead, inferred");
		}
	}

	private final static class Stats {
		private final long start = System.currentTimeMillis();
		private final String label;
		public long valid = 0;
		public long invalid = 0;
		public long notCanonical = 0;
		public long hasCopy = 0;
		public long invalidPrefix = 0;
		public long inconsistentValid = 0;
		public long inconsistentInvalid = 0;
		private final HashMap<String,Integer> errors;
		private final HashMap<String,Integer> warnings;

		public Stats(String label) {
			this.label=label;
			this.errors = new HashMap<>();
			this.warnings = new HashMap<>();
		}

		public long total() {
			return valid + invalid + inconsistentValid + inconsistentInvalid + notCanonical + hasCopy
					+ invalidPrefix;
		}

		public void join(Stats stats) {
			this.valid += stats.valid;
			this.invalid += stats.invalid;
			this.notCanonical += stats.notCanonical;
			this.hasCopy += stats.hasCopy;
			this.invalidPrefix += stats.invalidPrefix;
			this.inconsistentValid += stats.inconsistentValid;
			this.inconsistentInvalid += stats.inconsistentInvalid;
			// Join error classifications
			join(errors,stats.errors);
			join(warnings,stats.warnings);
		}

		/**
		 * Given the output from the RustCompiler look through to see what errors are
		 * present and attempt to classify them.
		 *
		 * @param stderr
		 * @throws IOException
		 */
		public void record(String stderr) throws IOException {
			BufferedReader reader = new BufferedReader(new StringReader(stderr));
			String line;
			while ((line = reader.readLine()) != null) {
				if (line.startsWith("error[E")) {
					String errcode = line.substring(6, 11);
					Integer i = errors.get(errcode);
					if (i == null) {
						errors.put(errcode, 1);
					} else {
						errors.put(errcode, i + 1);
					}
				} else if (line.startsWith("warning[E")) {
					String errcode = line.substring(8, 13);
					Integer i = warnings.get(errcode);
					if (i == null) {
						warnings.put(errcode, 1);
					} else {
						warnings.put(errcode, i + 1);
					}
				}
			}
		}

		public void print() {
			long time = System.currentTimeMillis() - start;
			System.out.println("{");
			System.out.println("\tSPACE: " + label);
			System.out.println("\tTIME: " + (time/1000) + "s");
			System.out.println("\tTOTAL: " + total());
			System.out.println("\tVALID: " + valid);
			System.out.println("\tINVALID: " + invalid);
			System.out.println("\tIGNORED (NOT CANONICAL): " + notCanonical);
			System.out.println("\tIGNORED (HAS COPY): " + hasCopy);
			System.out.println("\tIGNORED (INVALID PREFIX): " + invalidPrefix);
			System.out.println("\tINCONSISTENT (VALID): " + inconsistentValid);
			System.out.println("\tINCONSISTENT (INVALID): " + inconsistentInvalid);
			for(Map.Entry<String, Integer> e : errors.entrySet()) {
				System.out.println("\tINCONSISTENT (" + e.getKey() + ")=" + e.getValue());
			}
			for(Map.Entry<String, Integer> e : warnings.entrySet()) {
				System.out.println("\tWARNING (" + e.getKey() + ")=" + e.getValue());
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
