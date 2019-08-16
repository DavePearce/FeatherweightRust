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

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import featherweightrust.core.BorrowChecker;
import featherweightrust.core.ProgramSpace;
import featherweightrust.core.Syntax.Expr;
import featherweightrust.core.Syntax.Stmt;
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
	 *
	 * NOTE: mounting this as tmpfs (i.e. in memory file system) makes a big
	 * difference.
	 */
	private static final String tempDir = "tmp";

	/**
	 * The command to use for executing the rust compiler.
	 */
	private static final RustCompiler RUSTC = new RustCompiler("rustc",5000);

	private static final boolean VERBOSE = false;

	/**
	 * Configure number of threads to use.
	 */
	private static final int NTHREADS = Runtime.getRuntime().availableProcessors();

	/**
	 * Number of programs each thread to process in one go.
	 */
	private static final int BATCHSIZE = 100;

	/**
	 * Construct a thread pool to use for parallel processing.
	 */
	private static final ExecutorService executor = Executors
			.newFixedThreadPool(NTHREADS);

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
//		check(new ProgramSpace(1, 2, 2, 2), 2, 4208);
		check(new ProgramSpace(2, 2, 2, 2), 2, 11280);
//		check(new ProgramSpace(1, 2, 2, 3), 2, 34038368);
		executor.awaitTermination(1000, TimeUnit.MILLISECONDS);
		System.out.println("DONE");
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
			double remaining = ((expected - count) * rate)/1000;
			long percent = (long) (100D * (count) / expected);
			System.out.print("\r(" + percent + "%, remaining " + String.format("%.2f", remaining) + "s)");
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
		} else if(hasDeadCopy(b)) {
			stats.hasDeadCopy++;
		} else {
			// Construct prefix
			String prefix = toPrefixString(b);
			//System.out.println("PREFIX: " + prefix + " FROM: " + b);
			// Check prefix not already seen
//			if(INVALID_PREFIXES.containsKey(prefix)) {
//				stats.invalidPrefix++;
//			} else {
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
				String rustc =  RUSTC.compile(srcFilename, tempDir);
				boolean rustc_f = rustc == null;
				//
				if (checked != rustc_f) {
					reportFailure(b,program,rustc);
					if (rustc != null) {
						// Rust says no, FR says yes.
						stats.inconsistentValid++;
					} else {
						stats.inconsistentInvalid++;
					}
				} else if (checked) {
					new File(srcFilename).delete();
					stats.valid++;
				} else {
					new File(srcFilename).delete();
					stats.invalid++;
					// register invalid prefix
//					INVALID_PREFIXES.put(prefix, true);
				}
				// Always delete binary
				new File(binFilename).delete();
			//}
		}
	}

	public static void reportFailure(Stmt.Block b, String program, String rustc) {
		if(VERBOSE) {
			System.out.println("********* FAILURE");
			System.out.println("BLOCK: " + b.toString());
			System.out.println("PROGRAM: " + program);
			System.out.println("HASH: " + program.toString());
			System.out.println("RUSTC: " + (rustc == null));
			if(rustc != null) {
				System.out.println(rustc);
			}
		}
	}

	public static final BorrowChecker checker = new BorrowChecker("");

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

//	private static final ConcurrentHashMap<String,Boolean> INVALID_PREFIXES = new ConcurrentHashMap();

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
	public static boolean hasDeadCopy(Stmt stmt) {
		try{
			hasDeadCopy(stmt,new HashSet<>());
			return false;
		} catch(IllegalArgumentException e) {
			return true;
		}
	}

	private static  Set<String> hasDeadCopy(Stmt stmt, Set<String> live) {
		if(stmt instanceof Stmt.Block) {
			Stmt.Block b = (Stmt.Block) stmt;
			// Go backwards through the block for obvious reasons.
			for (int i = b.size() - 1; i >= 0; --i) {
				live = hasDeadCopy(b.get(i), live);
			}
			return live;
		} else if(stmt instanceof Stmt.Let) {
			Stmt.Let s = (Stmt.Let) stmt;
			live.remove(s.variable().name());
			hasDeadCopy(s.initialiser(),live);
			addVariableUses(s.initialiser(),live);
		} else if(stmt instanceof Stmt.Assignment) {
			Stmt.Assignment s = (Stmt.Assignment) stmt;
			live.remove(s.lhs.name());
			hasDeadCopy(s.rightOperand(),live);
			addVariableUses(s.rightOperand(),live);
		} else {
			Stmt.IndirectAssignment s = (Stmt.IndirectAssignment) stmt;
			hasDeadCopy(s.leftOperand(),live);
			hasDeadCopy(s.rightOperand(),live);
			addVariableUses(s.leftOperand(),live);
			addVariableUses(s.rightOperand(),live);
		}
		return live;
	}

	private static void hasDeadCopy(Expr expr, Set<String> live) {
		if(expr instanceof Expr.Copy) {
			Expr.Copy e = (Expr.Copy) expr;
			if(!live.contains(e.operand().name())) {
				// dead copy detected
				throw new IllegalArgumentException("dead copy detected");
			}
			hasDeadCopy(e.operand(),live);
		} else if(expr instanceof Expr.Borrow) {
			Expr.Borrow e = (Expr.Borrow) expr;
			hasDeadCopy(e.operand(),live);
		} else if(expr instanceof Expr.Box) {
			Expr.Box e = (Expr.Box) expr;
			hasDeadCopy(e.operand(),live);
		} else if(expr instanceof Expr.Dereference) {
			Expr.Dereference e = (Expr.Dereference) expr;
			hasDeadCopy(e.operand(),live);
		}
	}

	private static void addVariableUses(Expr expr, Set<String> uses) {
		if(expr instanceof Expr.Variable) {
			Expr.Variable e = (Expr.Variable) expr;
			uses.add(e.name());
		} else if(expr instanceof Expr.Copy) {
			Expr.Copy e = (Expr.Copy) expr;
			addVariableUses(e.operand(),uses);
		} else if(expr instanceof Expr.Borrow) {
			Expr.Borrow e = (Expr.Borrow) expr;
			addVariableUses(e.operand(),uses);
		} else if(expr instanceof Expr.Box) {
			Expr.Box e = (Expr.Box) expr;
			addVariableUses(e.operand(),uses);
		} else if(expr instanceof Expr.Dereference) {
			Expr.Dereference e = (Expr.Dereference) expr;
			addVariableUses(e.operand(),uses);
		}
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

	private final static class Stats {
		private final long start = System.currentTimeMillis();
		private final String label;
		public long valid = 0;
		public long invalid = 0;
		public long notCanonical = 0;
		public long hasDeadCopy = 0;
		public long invalidPrefix = 0;
		public long inconsistentValid = 0;
		public long inconsistentInvalid = 0;

		public Stats(String label) {
			this.label=label;
		}

		public long total() {
			return valid + invalid + inconsistentValid + inconsistentInvalid + notCanonical + hasDeadCopy + invalidPrefix;
		}

		public void join(Stats stats) {
			this.valid += stats.valid;
			this.invalid += stats.invalid;
			this.notCanonical += stats.notCanonical;
			this.hasDeadCopy += stats.hasDeadCopy;
			this.invalidPrefix += stats.invalidPrefix;
			this.inconsistentValid += stats.inconsistentValid;
			this.inconsistentInvalid += stats.inconsistentInvalid;
		}

		public void print() {
			long time = System.currentTimeMillis() - start;
			System.out.println("{");
			System.out.println("\tSPACE: " + label);
			System.out.println("\tTIME: " + (time/1000) + "s");
			System.out.println("\tTOTAL: " + total());
			System.out.println("\tVALID: " + valid);
			System.out.println("\tINVALID: " + invalid);
			System.out.println("\tIGNORED(NOT CANONICAL): " + notCanonical);
			System.out.println("\tIGNORED(HAS DEAD COPY): " + hasDeadCopy);
			System.out.println("\tIGNORED(INVALID PREFIX): " + invalidPrefix);
			System.out.println("\tINCONSISTENT (VALID): " + inconsistentValid);
			System.out.println("\tINCONSISTENT (INVALID): " + inconsistentInvalid);
			System.out.println("}");
		}
	}
}
