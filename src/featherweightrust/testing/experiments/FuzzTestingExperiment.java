package featherweightrust.testing.experiments;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import featherweightrust.core.BorrowChecker;
import featherweightrust.core.ProgramSpace;
import featherweightrust.core.Syntax.Stmt;
import featherweightrust.util.SyntaxError;

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
	 * Timeout in milliseconds for Rust Compiler.
	 */
	private static final long TIMEOUT = 5000;

	/**
	 * Maximum number of blocks to permit in constraint program spaces.
	 */
	private static final int MAX_BLOCKS = 2;

	/**
	 * The command to use for executing the rust compiler.
	 */
	private static final String RUST_CMD = "rustc";

	static {
		// Force creation of temporary directory
		new File(tempDir).mkdirs();
	}
	//
	public static void main(String[] args) throws NoSuchAlgorithmException, IOException, InterruptedException {
		// The set of program spaces to be considered.
		ProgramSpace[] spaces = {
//				new ProgramSpace(1, 1, 1, 1),
//				new ProgramSpace(1, 1, 1, 2),
				new ProgramSpace(1, 1, 2, 2),
//				new ProgramSpace(1, 2, 2, 2),
//				new ProgramSpace(1, 2, 2, 3),
//				new ProgramSpace(1, 3, 2, 3),
//				new ProgramSpace(1, 3, 3, 2),
		};
		long count = 0;
		for(ProgramSpace space : spaces) {
			Stats stats1 = runSampleAllExperiment(space);
			//Stats stats2 = runSampleDefinedExperiment(space);
			//
			stats1.print(space);
			//stats2.print(space);
			// Make some indication of progress
			if((count % 100) == 0) {
				System.out.print(".");
				System.out.flush();
			}
			count=count+1;
		}
	}

	public static Stats runSampleAllExperiment(ProgramSpace space) throws NoSuchAlgorithmException, IOException, InterruptedException {
		Stats stats = new Stats();
		//
		for(Stmt.Block b : space.domain()) {
			checkProgram(b, stats);
		}
		//
		return stats;
	}

	/**
	 * Sample from the space of programs where variables are always defined before
	 * being used. This dramatically increases the chance of exploring an
	 * interesting program
	 */
	public static Stats runSampleDefinedExperiment(ProgramSpace space) throws NoSuchAlgorithmException, IOException, InterruptedException {
		Stats stats = new Stats();
		//
		for(Stmt.Block b : space.definedVariableWalker(MAX_BLOCKS)) {
			checkProgram(b, stats);
		}
		//
		return stats;
	}

	public static void checkProgram(Stmt.Block b, Stats stats) throws NoSuchAlgorithmException, IOException, InterruptedException {
		if (isCanonical(b)) {
			// Check using calculus
			boolean checked = calculusCheckProgram(b);
			// Construct rust program
			String program = toRustProgram(b);
			// Execute rust compiler
			String rustc = rustCheckProgram(program);
			boolean rustc_f = rustc == null;
			//
			if (checked != rustc_f) {
				System.out.println("********* FAILURE");
				System.out.println("BLOCK: " + b.toString());
				System.out.println("PROGRAM: " + program);
				System.out.println("HASH: " + getHash(program));
				System.out.println("RUSTC: " + rustc_f);
				if (rustc != null) {
					System.out.println(rustc);
				}
				stats.inconsistent++;
			} else if (checked) {
				stats.valid++;
			} else {
				stats.invalid++;
			}
		} else {
			stats.ignored++;
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

	/**
	 * Check this program using the rust borrow checker
	 *
	 * @param b
	 * @param l
	 * @return
	 * @throws NoSuchAlgorithmException
	 * @throws IOException
	 * @throws InterruptedException
	 */
	public static String rustCheckProgram(String contents) throws NoSuchAlgorithmException, IOException, InterruptedException {
		// Determine hash of program for naming
		String hash = getHash(contents);
		// Construct complete filename for file
		String filename = tempDir + File.separator + hash +".rs";
		// Create new file
		RandomAccessFile writer = new RandomAccessFile(filename, "rw");
		// Write contents to file
		writer.write(contents.getBytes(StandardCharsets.UTF_8));
		// Done creating file
		writer.close();
		// Attempt to compile it.
		return new RustCompiler(RUST_CMD,TIMEOUT).compile(filename, tempDir);
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


	public static class Stats {
		private final long start = System.currentTimeMillis();

		public long valid = 0;
		public long invalid = 0;
		public long inconsistent = 0;
		public long ignored = 0;

		public void print(ProgramSpace space) {
			long time = System.currentTimeMillis() - start;
			System.out.println("{");
			System.out.println("\tSPACE: " + space);
			System.out.println("\tTIME: " + time + "ms");
			System.out.println("\tTOTAL: " + (valid + invalid));
			System.out.println("\tVALID: " + valid);
			System.out.println("\tINVALID: " + invalid);
			System.out.println("\tIGNORED: " + ignored);
			System.out.println("\tINCONSISTENT: " + inconsistent);
			System.out.println("}");
		}
	}
}
