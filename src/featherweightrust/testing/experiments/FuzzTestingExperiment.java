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
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Scanner;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import featherweightrust.core.BorrowChecker;
import featherweightrust.core.OperationalSemantics;
import featherweightrust.core.ProgramSpace;
import featherweightrust.core.BorrowChecker.Cell;
import featherweightrust.core.BorrowChecker.Environment;
import featherweightrust.core.Syntax.Expr;
import featherweightrust.core.Syntax.Lifetime;
import featherweightrust.core.Syntax.Stmt;
import featherweightrust.core.Syntax.Type;
import featherweightrust.io.Lexer;
import featherweightrust.io.Parser;
import featherweightrust.util.AbstractSemantics;
import featherweightrust.util.OptArg;
import featherweightrust.util.Pair;
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
	 * Signal whether the rustc supports Non-Lexical Lifetimes or not. And, if so,
	 * attempt to work around them.
	 */
	private static boolean NLL;

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
	private static final int BATCHSIZE = 10;

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
			new OptArg("nll","specify non-lexical lifetimes in play"),
			new OptArg("edition", "e", OptArg.STRING, "set rust edition to use", "2018"),
			new OptArg("expected","n",OptArg.LONG,"set expected domain size",-1L),
			new OptArg("pspace", "p", OptArg.LONGARRAY(4, 4), "set program space"),
			new OptArg("constrained", "c", OptArg.INT, "set maximum block count and constrain use-defs", -1),
			new OptArg("batch", "b", OptArg.LONGARRAY(2, 2), "set batch index and batch count", null),
	};
	//
	public static void main(String[] _args) throws Exception {
		System.out.println("NUM THREADS: " + NTHREADS);
		System.out.println("BETA5");

		List<String> args = new ArrayList<>(Arrays.asList(_args));
		Map<String, Object> options = OptArg.parseOptions(args, OPTIONS);
		long expected = (Long) options.get("expected");
		long[] batch = (long[]) options.get("batch");
		//
		VERBOSE = options.containsKey("verbose");
		QUIET = options.containsKey("quiet");
		NIGHTLY = options.containsKey("nightly");
		EDITION = (String) options.get("edition");
		NLL = options.containsKey("nll");
		// Extract version string
		RUST_VERSION = new RustCompiler(RUSTC, 5000, NIGHTLY, EDITION).version().replace("\n", "").replace("\t", "");
		//
		Iterator<Stmt.Block> iterator;
		String label;
		//
		if (options.containsKey("pspace")) {
			long[] ivdw = (long[]) options.get("pspace");
			int c = (Integer) options.get("constrained");
			ProgramSpace space = new ProgramSpace((int) ivdw[0], (int) ivdw[1], (int) ivdw[2], (int) ivdw[3]);
			// Create iterator
			if (c >= 0) {
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
		} else {
			// Read from stdin line by line
			List<Stmt.Block> inputs = readAll(System.in);
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
			SyntaxError FR_err = calculusCheckProgram(nb);
			boolean FR_status = (FR_err == null);
			// Construct rust program
			String program = toRustProgram(b);
			// Determine hash of program for naming
			String hash = getHash(program);
			// Determine filename based on hash
			String prefix = File.separator + hash;
			// Create temporary file
			String srcFilename = createTemporaryFile(prefix, ".rs", program);
			String binFilename = srcFilename.replace(".rs", "");
			// Run the rust compile
			RustCompiler rustc = new RustCompiler(RUSTC, 5000, NIGHTLY, EDITION);
			Triple<Boolean, String, String> p = rustc.compile(srcFilename, binFilename);
			boolean rustc_status = p.first();
			String rustc_err = p.third();
			// Delete source and binary files
			new File(srcFilename).delete();
			new File(binFilename).delete();
			// Analysis output
			if (FR_status != rustc_status) {
				stats.record(rustc_err);
				// Attempt to execute program
				reportFailure(nb, program, FR_err, rustc_err);
				if (rustc_status) {
					// Rust says yes, FR says no
					if(hasUnsoundness(nb)) {
						stats.inconsistentUnsound++;
					} else if(hasKnownLimitation(nb)) {
						stats.inconsistentKnownLimitations++;
					} else {
						stats.inconsistentInvalid++;
					}
				} else {
					// Rust says no, FR says yes.
					if(hasSelfAssignment(nb)) {
						stats.inconsistentPossibleBug++;
					} else {
						stats.inconsistentValid++;
					}
				}
			} else if (FR_status) {
				stats.valid++;
			} else {
				stats.invalid++;
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
	public static void reportFailure(Stmt.Block b, String program, SyntaxError FR_err, String rustc_err) throws IOException, NoSuchAlgorithmException {
		if(VERBOSE) {
			// Determine hash of program for naming
			System.out.println("********* FAILURE");
			System.out.println("BLOCK: " + b.toString());
			System.out.println("PROGRAM: " + program);
			System.out.println("HASH: " + getHash(program));
			System.out.println("RUSTC: " + (FR_err != null));
			System.out.println("HAS UNSOUNDNESS: " + hasUnsoundness(b));
			System.out.println("POSSIBLE BUG: " + hasSelfAssignment
					(b));
			if(rustc_err != null) {
				System.out.println(rustc_err);
			}
			if(FR_err != null) {
				FR_err.outputSourceError(System.out);
			}
		}
	}

	/**
	 * Borrow check the program using the borrow checker which is part of this
	 * calculs.
	 *
	 * @param b
	 * @param l
	 * @return
	 */
	public static SyntaxError calculusCheckProgram(Stmt.Block b) {
		BorrowChecker checker = new BorrowChecker(b.toString());
		try {
			checker.apply(BorrowChecker.EMPTY_ENVIRONMENT, ProgramSpace.ROOT, b);
			return null;
		} catch (SyntaxError e) {
			return e;
		}
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
				return new Stmt.Block(block.lifetime(), nstmts, stmt.attributes());
			}
		} else if(stmt instanceof Stmt.Let) {
			Stmt.Let s = (Stmt.Let) stmt;
			Expr e = s.initialiser();
			Expr ne = inferVariableClones(e,copyables);
			if(e == ne) {
				return stmt;
			} else {
				return new Stmt.Let(s.variable(), ne, stmt.attributes());
			}
		} else if(stmt instanceof Stmt.Assignment) {
			Stmt.Assignment s = (Stmt.Assignment) stmt;
			Expr e = s.rightOperand();
			Expr ne = inferVariableClones(e,copyables);
			if(e == ne) {
				return stmt;
			} else {
				return new Stmt.Assignment(s.leftOperand(), ne, stmt.attributes());
			}
		} else {
			Stmt.IndirectAssignment s = (Stmt.IndirectAssignment) stmt;
			Expr e = s.rightOperand();
			Expr ne = inferVariableClones(e,copyables);
			if(e == ne) {
				return stmt;
			} else {
				return new Stmt.IndirectAssignment(s.leftOperand(), ne, stmt.attributes());
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
				return new Expr.Copy(v, expr.attributes());
			}
		} else if(expr instanceof Expr.Box) {
			Expr.Box b = (Expr.Box) expr;
			Expr e = b.operand();
			Expr ne = inferVariableClones(e,copyables);
			if(e != ne) {
				return new Expr.Box(ne, expr.attributes());
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
			if(NLL) {
				// Attempt to work around non-lexical lifetimes
				for (int i=declared.size()-1;i>=0;--i) {
					String var = declared.get(i);
					if (live.contains(var)) {
						// declared live variable
						contents = contents + var + "; ";
						live.remove(var);
					}
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
	 * Check for statements of the form <code>y = y</code>. These are incorrectly
	 * rejected by the original borrow checker (e.g. Rust 1.35.0 edition 2015).
	 * However, they are accepted (for the most part) by the new borrow checker.
	 *
	 * @param stmt
	 * @return
	 */
	private static boolean hasSelfAssignment(Stmt stmt) {
		if(stmt instanceof Stmt.Block) {
			Stmt.Block b = (Stmt.Block) stmt;
			for (int i = 0; i != b.size(); ++i) {
				if(hasSelfAssignment(b.get(i))) {
					return true;
				}
			}
			return false;
		} else if(stmt instanceof Stmt.Assignment) {
			Stmt.Assignment s = (Stmt.Assignment) stmt;
			if(s.rhs instanceof Expr.Variable) {
				Expr.Variable rhs = (Expr.Variable) s.rhs;
				return s.lhs.name().equals(rhs.name());
			}
		}
		return false;
	}

	/**
	 * Look for programs which contain statements of the following form:
	 *
	 * <pre>
	 * y = &y
	 * y = &mut y
	 * y = box &y
	 * y = box &mut y
	 * *y = &y
	 * *y = &mut y
	 * *y = box &y
	 * *y = box &mut y
	 * </pre>
	 *
	 * Statements such as this are unsound, but were accepted by the original borrow
	 * checker (e.g. Rust v1.35.0 edition 2015). The new borrow checker (e.g. Rust
	 * v1.36.0 edition 2015) reports a warning for such programs and notes that this
	 * is for backwards compatibility reasons and will be upgraded in the future to
	 * an error.
	 *
	 * @param b
	 * @return
	 */
	private static boolean hasUnsoundness(Stmt stmt) {
		if (stmt instanceof Stmt.Block) {
			Stmt.Block b = (Stmt.Block) stmt;
			for (int i = 0; i != b.size(); ++i) {
				if (hasUnsoundness(b.get(i))) {
					return true;
				}
			}
			return false;
		} else if (stmt instanceof Stmt.Assignment) {
			Stmt.Assignment s = (Stmt.Assignment) stmt;
			return hasUnsoundBorrow(s.lhs.name(), s.rhs);
		} else if (stmt instanceof Stmt.IndirectAssignment) {
			Stmt.IndirectAssignment s = (Stmt.IndirectAssignment) stmt;
			return hasUnsoundBorrow(s.leftOperand().name(), s.rightOperand());
		} else {
			return false;
		}
	}

	/**
	 * Check whether an expression attempts to borrow a given variable which, in the
	 * context this function is used, indicates the enclosing statement is unsound.
	 *
	 * @param var
	 * @param e
	 * @return
	 */
	private static boolean hasUnsoundBorrow(String var, Expr e) {
		if (e instanceof Expr.Box) {
			Expr.Box b = (Expr.Box) e;
			return hasUnsoundBorrow(var, b.operand());
		} else if (e instanceof Expr.Borrow) {
			Expr.Borrow b = (Expr.Borrow) e;
			return b.operand().name().equals(var);
		} else {
			return false;
		}
	}

	/**
	 * <p>
	 * This determines whether this block exposes a known limitation in the current
	 * BorrowChecker for FR. Specifically, it does not currently support moving
	 * through a dereference. For example, the following program is incorrectly
	 * rejected by FR:
	 * </p>
	 *
	 * <pre>
	 * { let mut x = box 0; x = x; { let mut y = box x; y = box *y; *y = box 0; } }
	 * </pre>
	 *
	 * <p>
	 * The current borrow checker for FR reports incorrectly that, in the statement
	 * <code>y = box *y</code>, the expression <code>*y</code> cannot be copied.
	 * This is because the current rule T-BoxDeref requires the type in question be
	 * copyable. In fact, it's possible to destructively update the variable being
	 * dereferenced as we know it owns the location in question.
	 * </p>
	 * <p>
	 * This rule simply employs an updated borrow checker to determine whether or
	 * not the program would borrow check if the more flexible rule was supported.
	 * </p>
	 *
	 * @param b
	 * @return
	 */
	private static boolean hasKnownLimitation(Stmt.Block b) {
		// Extended borrow checker with a more flexible interpretation of T-BoxDeref.
		BorrowChecker checker = new BorrowChecker(b.toString()) {
			@Override
			public Pair<Environment, Type> apply(Environment R1, Lifetime l, Expr.Dereference e) {
				String x = e.operand().name();
				Cell Cx = R1.get(x);
				// Check variable is declared
				check(Cx != null, UNDECLARED_VARIABLE, e);
				// Check variable not moved
				check(!Cx.moved(), VARIABLE_MOVED, e);
				// Locate operand type
				Cell C1 = R1.get(x);
				// Check operand has reference type
				if (C1.type() instanceof Type.Box) {
					// T-BoxDeref
					Type T = ((Type.Box) C1.type()).element();
					//
					if (!copyable(T) && !borrowed(R1, x)) {
						// Implement destructive update
						Environment R2 = R1.move(x);
						//
						return new Pair<>(R2, T);
					}
				}
				// default back to original
				return super.apply(R1, l, e);
			}
		};
		try {
			checker.apply(BorrowChecker.EMPTY_ENVIRONMENT, ProgramSpace.ROOT, b);
			return true;
		} catch (SyntaxError e) {
			return false;
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

	private static List<Stmt.Block> readAll(InputStream in) throws IOException {
		ArrayList<Stmt.Block> inputs = new ArrayList<>();
		Scanner stdin = new Scanner(in);
		while (stdin.hasNext()) {
		    String input = stdin.nextLine();
		    // Tokenize input program
		    List<Lexer.Token> tokens = new Lexer(new StringReader(input)).scan();
			// Parse block
			Stmt.Block stmt = new Parser(input,tokens).parseStatementBlock(new Parser.Context(), ProgramSpace.ROOT);
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
		public long hasCopy = 0;
		public long invalidPrefix = 0;
		public long inconsistentValid = 0;
		public long inconsistentInvalid = 0;
		public long inconsistentUnsound = 0;
		public long inconsistentPossibleBug = 0;
		public long inconsistentKnownLimitations = 0;
		private final HashMap<String,Integer> errors;
		private final HashMap<String,Integer> warnings;

		public Stats(String label) {
			this.label=label;
			this.errors = new HashMap<>();
			this.warnings = new HashMap<>();
		}

		public long total() {
			return valid + invalid + inconsistentValid + inconsistentInvalid + inconsistentUnsound
					+ inconsistentPossibleBug + inconsistentKnownLimitations + notCanonical + hasCopy + invalidPrefix;
		}

		public void join(Stats stats) {
			this.valid += stats.valid;
			this.invalid += stats.invalid;
			this.notCanonical += stats.notCanonical;
			this.hasCopy += stats.hasCopy;
			this.invalidPrefix += stats.invalidPrefix;
			this.inconsistentValid += stats.inconsistentValid;
			this.inconsistentInvalid += stats.inconsistentInvalid;
			this.inconsistentUnsound += stats.inconsistentUnsound;
			this.inconsistentPossibleBug += stats.inconsistentPossibleBug;
			this.inconsistentKnownLimitations += stats.inconsistentKnownLimitations;
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
			System.out.println("\tVERSION: \"" + RUST_VERSION + "\"");
			System.out.println("\tEDITION: " + EDITION);
			System.out.println("\tNIGHTLY: " + NIGHTLY);
			System.out.println("\tTIME: " + (time/1000) + "s");
			System.out.println("\tTOTAL: " + total());
			System.out.println("\tVALID: " + valid);
			System.out.println("\tINVALID: " + invalid);
			System.out.println("\tIGNORED (NOT CANONICAL): " + notCanonical);
			System.out.println("\tIGNORED (HAS COPY): " + hasCopy);
			System.out.println("\tIGNORED (INVALID PREFIX): " + invalidPrefix);
			System.out.println("\tINCONSISTENT (VALID): " + inconsistentValid);
			System.out.println("\tINCONSISTENT (INVALID): " + inconsistentInvalid);
			System.out.println("\tINCONSISTENT (ACTUAL BUG): " + inconsistentUnsound);
			System.out.println("\tINCONSISTENT (POSSIBLE BUG): " + inconsistentPossibleBug);
			System.out.println("\tINCONSISTENT (KNOWN LIMITATIONS): " + inconsistentKnownLimitations);
			for(Map.Entry<String, Integer> e : errors.entrySet()) {
				System.out.println("\tINCONSISTENT (" + e.getKey() + "): " + e.getValue());
			}
			for(Map.Entry<String, Integer> e : warnings.entrySet()) {
				System.out.println("\tWARNING (" + e.getKey().replace("E", "W") + "): " + e.getValue());
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
