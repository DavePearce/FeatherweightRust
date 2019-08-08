package featherweightrust.testing;

import java.io.IOException;
import java.io.StringReader;
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

public class AutomatedTestGeneration {

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
		ProgramSpace space = new ProgramSpace(1,2,2,2);
		//
		int i = 0;
		for(Stmt s : space.constrainedWalker(2)) {
//			if(s.toString().equals("{ let mut x = 0; let mut y = &x; { let mut z = 0; y = &z; } }")) {
			if(s.toString().equals("{ let mut x = 0; let mut y = &mut x; { let mut z = &mut y; *z = z; } }")) {
				System.out.println(s);
			}
			runAndCheck((Stmt.Block) s, ProgramSpace.ROOT);
			++i;
		}
		//
		printStats(i);
	}

	public static final OperationalSemantics semantics = new OperationalSemantics.BigStep();
	public static final BorrowChecker checker = new BorrowChecker("");
	public static long malformed = 0;
	public static long valid = 0;
	public static long invalid = 0;
	public static long falsepos = 0;
	public static long falseneg = 0;

	public static void runAndCheck(Stmt.Block stmt, Lifetime lifetime) {
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
			valid++;
		} else if (checked) {
			error.printStackTrace(System.out);
			System.out.println("*** ERROR(" + error.getMessage() + "): " + stmt.toString());
			falseneg++;
		} else if (ran) {
			falsepos++;
		} else {
			invalid++;
		}
	}

	public static void printStats(long size) {
		System.out.println("================================");
		System.out.println("TOTAL: " + size);
		System.out.println("MALFORMED: " + malformed);
		System.out.println("VALID: " + valid);
		System.out.println("INVALID: " + invalid);
		System.out.println("FALSEPOS: " + falsepos);
		System.out.println("FALSENEG: " + falseneg);
	}
}
