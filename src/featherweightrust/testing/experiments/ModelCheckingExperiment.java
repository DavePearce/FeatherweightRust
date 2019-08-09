package featherweightrust.testing.experiments;

import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;

import featherweightrust.core.OperationalSemantics;
import featherweightrust.core.ProgramSpace;
import featherweightrust.core.BorrowChecker;
import featherweightrust.core.Syntax.Lifetime;
import featherweightrust.core.Syntax.Stmt;
import featherweightrust.io.Lexer;
import featherweightrust.io.Parser;
import featherweightrust.util.AbstractSemantics;
import featherweightrust.util.SyntaxError;

public class ModelCheckingExperiment {

	/**
	 * Rebuild the given input in such a way that it contains line information. This
	 * is useful for reporting proper errors at the source level (i.e. for
	 * debugging).
	 *
	 * @param input
	 * @return
	 * @throws IOException
	 */
	public static Stmt.Block rebuildWithInfo(Stmt.Block block) throws IOException {
		// Turn input into string
		String input = block.toString();
		//
		try {
			// Scan input
			List<Lexer.Token> tokens = new Lexer(new StringReader(input)).scan();
			// Parse block
			return new Parser(input, tokens).parseStatementBlock(Parser.ROOT_CONTEXT, block.lifetime());
		} catch (SyntaxError e) {
			return null;
		}
	}

	public static void main(String[] args) throws IOException {
		ProgramSpace[] spaces = {
				new ProgramSpace(1, 1, 1, 1, 2),
				new ProgramSpace(1, 1, 1, 2, 2),
				new ProgramSpace(1, 1, 2, 2, 2),
				new ProgramSpace(1, 2, 2, 2, 2),
				new ProgramSpace(1, 2, 2, 3, 2),
				new ProgramSpace(1, 3, 2, 3, 2),
				new ProgramSpace(1, 3, 3, 2, 2),
			};
		//
		for(ProgramSpace space : spaces) {
			Stats stats = new Stats();
			int size = 0;
			for(Stmt s : space.constrainedWalker()) {
				//			if(s.toString().equals("{ let mut x = 0; let mut y = &x; { let mut z = 0; y = &z; } }")) {
				if(s.toString().equals("{ let mut x = 0; let mut y = &mut x; { let mut z = &mut y; *z = z; } }")) {
					System.out.println(s);
				}
				runAndCheck((Stmt.Block) s, ProgramSpace.ROOT, stats);
				++size;
			}
			//
			stats.print(space,size);

		}
	}

	public static final OperationalSemantics semantics = new OperationalSemantics.BigStep();
	public static final BorrowChecker checker = new BorrowChecker("");

	public static void runAndCheck(Stmt.Block stmt, Lifetime lifetime, Stats stats) {
		boolean ran = false;
		boolean checked = false;
		Exception error = null;
		// See whether or not it borrow checks
		try {
			checker.apply(BorrowChecker.EMPTY_ENVIRONMENT, lifetime, stmt);
			checked = true;
		} catch (SyntaxError e) {
			error = e;
		}
		// See whether or not it executes
		try {
			semantics.apply(AbstractSemantics.EMPTY_STATE, lifetime, stmt).first();
			ran = true;
		} catch (Exception e) {
			error = e;
		}
		// Update statistics
		if (checked && ran) {
			stats.valid++;
		} else if (checked) {
			error.printStackTrace(System.out);
			System.out.println("*** ERROR(" + error.getMessage() + "): " + stmt.toString());
			stats.falseneg++;
		} else if (ran) {
			stats.falsepos++;
		} else {
			stats.invalid++;
		}
	}

	public static class Stats {
		private final long start = System.currentTimeMillis();

		public long malformed = 0;
		public long valid = 0;
		public long invalid = 0;
		public long falsepos = 0;
		public long falseneg = 0;

		public void print(ProgramSpace space, int size) {
			long time = System.currentTimeMillis() - start;
			System.out.println("================================");
			System.out.println("TIME: " + time + "ms");
			System.out.println("SPACE: " + space);
			System.out.println("TOTAL: " + size);
			System.out.println("MALFORMED: " + malformed);
			System.out.println("VALID: " + valid);
			System.out.println("INVALID: " + invalid);
			System.out.println("FALSEPOS: " + falsepos);
			System.out.println("FALSENEG: " + falseneg);
		}
	}
}
