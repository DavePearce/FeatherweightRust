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
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
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
import featherweightrust.util.OptArg;
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
	private static final String tempDir = "tmp";

	/**
	 * Flag whether to report failures to the console or not.
	 */
	private static boolean VERBOSE = true;

	/**
	 * Indicate whether Rust nightly is being used. This offers better performance
	 * as we can use the option "-Zno-codegen" to prevent the generation of object
	 * code.
	 */
	private static boolean NIGHTLY = false;

	/**
	 * The command to use for executing the rust compiler.
	 */
	private static final RustCompiler RUSTC = new RustCompiler("rustc", 5000, NIGHTLY, "2018");
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

	static {
		// Force creation of temporary directory
		new File(tempDir).mkdirs();
	}
	/**
	 * Command-line options
	 */
	private static final OptArg[] OPTIONS = {
			new OptArg("verbose","v","set expected domain size"),
			new OptArg("nightly","specify rust nightly available"),
			new OptArg("expected","e",OptArg.LONG,"set expected domain size",-1L),
			new OptArg("ints","i",OptArg.INT,"set maximum number of integers",1),
			new OptArg("vars","n",OptArg.INT,"set maximum number of variables",1),
			new OptArg("depth","d",OptArg.INT,"set maximum block nesting",1),
			new OptArg("width","w",OptArg.INT,"set maximum block width",1),
			new OptArg("constrained", "c", OptArg.INT, "set maximum block count and constrain use-defs", -1),
			new OptArg("first","f",OptArg.LONG,"set domain index to start iterating from",-1L),
			new OptArg("last","l",OptArg.LONG,"set domain index to iterate up to (exclusive)",-1L),
	};
	//
	public static void main(String[] _args) throws Exception {
		System.out.println("NUM THREADS: " + NTHREADS);
		List<String> args = new ArrayList<>(Arrays.asList(_args));
		if(args.size() == 0) {
			System.out.println("usage: java Main <options> target");
			System.out.println();
			OptArg.usage(System.out, OPTIONS);
		} else {
			Map<String, Object> options = OptArg.parseOptions(args, OPTIONS);
			int i = (Integer) options.get("ints");
			int v = (Integer) options.get("vars");
			int d = (Integer) options.get("depth");
			int w = (Integer) options.get("width");
			int c = (Integer) options.get("constrained");
			long expected = (Long) options.get("expected");
			long first = (Long) options.get("first");
			long last = (Long) options.get("last");
			//
			VERBOSE = options.containsKey("verbose");
			NIGHTLY = options.containsKey("nightly");
			System.out.println("VERBOSE: " + VERBOSE);
			//
			ProgramSpace space = new ProgramSpace(i, v, d, w);
			Iterator<Stmt.Block> iterator;
			String label;
			// Create iterator
			if(c >= 0) {
				iterator = space.definedVariableWalker(c).iterator();
				label = space.toString() + "{def," + c + "}";
			} else {
				// Get domain
				Domain.Big<Stmt.Block> domain = space.domain();
				// Determine expected size
				expected = domain.bigSize().longValueExact();
				// Get iterator
				iterator = domain.iterator();
				//
				label = space.toString();
			}
			// Slice iterator (if applicable)
			if(first >= 0 || last >= 0) {
				first = Math.max(0, first);
				last = Math.min(last, expected);
				// Create sliced iterator
				iterator = new SliceIterator(iterator,first,last);
				// Update expected
				expected = last - first;
			}
			// Done
			check(iterator, expected, label);
		}
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
	public static void check(Iterator<Stmt.Block> iterator, long expected, String label)
			throws NoSuchAlgorithmException, IOException, InterruptedException, ExecutionException {
		// Construct temporary memory areas
		Stmt.Block[][] arrays = new Stmt.Block[NTHREADS][BATCHSIZE];
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
		stats.print();

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

	/**
	 * Fuzz test a batch of programs in one go.
	 *
	 * @param batch --- The batch of programs to check which may include
	 *              <code>null</code> programs to skip (i.e. in the last batch).
	 * @return
	 * @throws NoSuchAlgorithmException
	 * @throws IOException
	 * @throws InterruptedException
	 */
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

	/**
	 * Fuzz test a single program whilst updating the stats accordingly.
	 *
	 * @param b
	 * @param stats
	 * @throws NoSuchAlgorithmException
	 * @throws IOException
	 * @throws InterruptedException
	 */
	public static void check(Stmt.Block b, Stats stats) throws NoSuchAlgorithmException, IOException, InterruptedException {
		if(!isCanonical(b)) {
			stats.notCanonical++;
		} else if(hasCopy(b)) {
			stats.hasCopy++;
		} else {
			// Infer copy expressions
			Stmt.Block nb = inferVariableClones(b);
			// Check using calculus
			boolean checked = calculusCheckProgram(nb);
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
				reportFailure(nb, program, status, stderr);
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
			}
			// Always delete binary
			if(!NIGHTLY) {
				new File(binFilename).delete();
			}
		}
	}

	/**
	 * Report an inconsistency between rustc and Featherweight Rust.
	 *
	 * @param b
	 * @param program
	 * @param status
	 * @param stderr
	 * @throws IOException
	 */
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

	/**
	 * The purpose of this method is to perform a "clone analysis". That is, to
	 * determine where we are copying items versus moving them.
	 *
	 * @param stmt
	 * @return
	 */
	public static Stmt.Block inferVariableClones(Stmt.Block stmt) {
		return (Stmt.Block) inferVariableClones(stmt, new HashSet<>());
	}

	/**
	 * Infer variable clones for a given statement using a known set of variables
	 * which have copy semantics (hence, can and should be copied). The key here is
	 * to reduce work by only allocating new objects when things change.
	 *
	 * @param stmt
	 * @param copyables
	 * @return
	 */
	private static Stmt inferVariableClones(Stmt stmt, HashSet<String> copyables) {
		if(stmt instanceof Stmt.Block) {
			copyables = new HashSet<>(copyables);
			Stmt.Block block = (Stmt.Block) stmt;
			final Stmt[] stmts = block.toArray();
			Stmt[] nstmts = stmts;
			for (int i = 0; i != block.size(); ++i) {
				Stmt s = block.get(i);
				Stmt n = inferVariableClones(s, copyables);
				if (s != n) {
					if (stmts == nstmts) {
						nstmts = Arrays.copyOf(stmts, stmts.length);
					}
					nstmts[i] = n;
				}
				// Update copyables
				inferCopyables(s,copyables);
			}
			if (stmts == nstmts) {
				return stmt;
			} else {
				return new Stmt.Block(block.lifetime(), nstmts);
			}
		} else if(stmt instanceof Stmt.Let) {
			Stmt.Let s = (Stmt.Let) stmt;
			Expr e = s.initialiser();
			Expr ne = inferVariableClones(e,copyables);
			if(e == ne) {
				return stmt;
			} else {
				return new Stmt.Let(s.variable(), ne);
			}
		} else if(stmt instanceof Stmt.Assignment) {
			Stmt.Assignment s = (Stmt.Assignment) stmt;
			Expr e = s.rightOperand();
			Expr ne = inferVariableClones(e,copyables);
			if(e == ne) {
				return stmt;
			} else {
				return new Stmt.Assignment(s.leftOperand(), ne);
			}
		} else {
			Stmt.IndirectAssignment s = (Stmt.IndirectAssignment) stmt;
			Expr e = s.rightOperand();
			Expr ne = inferVariableClones(e,copyables);
			if(e == ne) {
				return stmt;
			} else {
				return new Stmt.IndirectAssignment(s.leftOperand(), ne);
			}
		}
	}

	/**
	 * Infer variable copies where necessary using a given set of variables which
	 * are known to have copy semantics at this point.
	 *
	 * @param expr
	 * @param copyables
	 * @return
	 */
	public static Expr inferVariableClones(Expr expr, HashSet<String> copyables) {
		if(expr instanceof Expr.Variable) {
			Expr.Variable v = (Expr.Variable) expr;
			if(copyables.contains(v.name())) {
				return new Expr.Copy(v);
			}
		} else if(expr instanceof Expr.Box) {
			Expr.Box b = (Expr.Box) expr;
			Expr e = b.operand();
			Expr ne = inferVariableClones(e,copyables);
			if(e != ne) {
				return new Expr.Box(ne);
			}
		}
		return expr;
	}

	/**
	 * Determine variables which have copy semantics at this point.
	 *
	 * @param stmt
	 * @param copyables
	 */
	public static void inferCopyables(Stmt stmt, HashSet<String> copyables) {
		if(stmt instanceof Stmt.Let) {
			Stmt.Let s = (Stmt.Let) stmt;
			if(isCopyable(s.initialiser(),copyables)) {
				copyables.add(s.variable().name());
			}
		}
	}

	/**
	 * Check whether the result of a given expression should have copy semantics of
	 * not. For example, expressions which return integers should exhibit copy
	 * semantics.
	 *
	 * @param expr
	 * @param copyables
	 * @return
	 */
	public static boolean isCopyable(Expr expr, HashSet<String> copyables) {
		if (expr instanceof Expr.Box) {
			return false;
		} else if (expr instanceof Expr.Borrow) {
			Expr.Borrow b = (Expr.Borrow) expr;
			return !b.isMutable();
		} else if (expr instanceof Expr.Variable) {
			Expr.Variable v = (Expr.Variable) expr;
			return copyables.contains(v.name());
		}
		return true;
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
		return "fn main() " + toRustString(b, new HashSet<>());
	}

	private static String toRustString(Stmt stmt, HashSet<String> live) {
		if (stmt instanceof Stmt.Block) {
			Stmt.Block block = (Stmt.Block) stmt;
			String contents = "";
			ArrayList<String> declared = new ArrayList<>();
			for (int i = 0; i != block.size(); ++i) {
				Stmt s = block.get(i);
				contents += toRustString(s, live) + " ";
				if (s instanceof Stmt.Let) {
					Stmt.Let l = (Stmt.Let) s;
					declared.add(l.variable().name());
				}
			}
			// Remove declared variables
			for (int i=declared.size()-1;i>=0;--i) {
				String var = declared.get(i);
				if (live.contains(var)) {
					// declared live variable
					contents = contents + var + "; ";
					live.remove(var);
				}
			}
			//
			return "{ " + contents + "}";
		} else if(stmt instanceof Stmt.Let) {
			Stmt.Let s = (Stmt.Let) stmt;
			String init = toRustString(s.initialiser());
			updateLiveness(s.initialiser(),live);
			// By definition variable is live after assignment
			live.add(s.variable().name());
			return "let mut " + s.variable().name() + " = " + init + ";";
		} else if(stmt instanceof Stmt.Assignment) {
			Stmt.Assignment s = (Stmt.Assignment) stmt;
			updateLiveness(s.rightOperand(),live);
			// By definition variable is live after assignment
			live.add(s.leftOperand().name());
			return s.leftOperand().name() + " = " + toRustString(s.rightOperand()) + ";";
		} else {
			Stmt.IndirectAssignment s = (Stmt.IndirectAssignment) stmt;
			updateLiveness(s.rightOperand(),live);
			return "*" + s.leftOperand().name() + " = " + toRustString(s.rightOperand()) + ";";
		}
	}

	/**
	 * Convert an expression into a Rust-equivalent string.
	 *
	 * @param expr
	 * @return
	 */
	private static String toRustString(Expr expr) {
		if (expr instanceof Expr.Copy) {
			Expr.Copy v = (Expr.Copy) expr;
			return v.operand().name();
		} else if (expr instanceof Expr.Box) {
			Expr.Box b = (Expr.Box) expr;
			return "Box::new(" + toRustString(b.operand()) + ")";
		} else {
			return expr.toString();
		}
	}

	/**
	 * Update the set of live variables by removing any which are moved.
	 *
	 * @param expr
	 * @param liveness
	 */
	private static void updateLiveness(Expr expr, HashSet<String> liveness) {
		if (expr instanceof Expr.Variable) {
			// Variable move
			Expr.Variable b = (Expr.Variable) expr;
			liveness.remove(b.name());
		} else if (expr instanceof Expr.Box) {
			Expr.Box b = (Expr.Box) expr;
			updateLiveness(b.operand(), liveness);
		}
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

	private final static class SliceIterator<T> implements Iterator<T> {
		private final Iterator<T> iter;
		private long count;

		public SliceIterator(Iterator<T> iter, long start, long end) {
			this.count = (end - start);
			// Skip forward
			while (start > 0) {
				iter.next();
				start = start - 1;
			}
			this.iter = iter;
		}

		@Override
		public boolean hasNext() {
			return count > 0 && iter.hasNext();
		}

		@Override
		public T next() {
			count = count - 1;
			return iter.next();
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
