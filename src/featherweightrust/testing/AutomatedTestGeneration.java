package featherweightrust.testing;

import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.extension.ExtensionContext.Store;

import featherweightrust.core.OperationalSemantics;
import featherweightrust.core.BorrowChecker;
import featherweightrust.core.Syntax.Expr;
import featherweightrust.core.Syntax.Expr.Dereference;
import featherweightrust.core.Syntax.Expr.Variable;
import featherweightrust.core.Syntax.Lifetime;
import featherweightrust.core.Syntax.Stmt;
import featherweightrust.core.Syntax.Stmt.Block;
import featherweightrust.core.Syntax.Stmt.Let;
import featherweightrust.io.Lexer;
import featherweightrust.io.Parser;
import featherweightrust.util.AbstractSemantics;
import featherweightrust.util.SyntaxError;
import modelgen.core.Domain;
import modelgen.util.AbstractDomain;
import featherweightrust.core.Syntax.Value;
import featherweightrust.core.Syntax.Value.Location;

public class AutomatedTestGeneration {

	/**
	 * Given one or more statements, extend each one by generating all valid "one
	 * place" extensions.
	 *
	 * @param stmts
	 * @param gen
	 * @return
	 */
//	public static <T extends Stmt> List<T> extendAll(List<T> stmts, int declared) {
//		ArrayList<T> results = new ArrayList<>();
//		for (int i = 0; i != stmts.size(); ++i) {
//			results.addAll(extend(stmts.get(i), declared, 0));
//		}
//		return results;
//	}

	/**
	 * Given a statement, attempt to generate all valid "one place" extensions.
	 *
	 * @param stmt
	 * @param gen
	 * @return
	 */
//	public static <T extends Stmt> List<T> extend(T stmt, int declared, int depth) {
//		// FIXME: what does depth do?
//		if (depth < 2 && stmt instanceof Stmt.Block) {
//			ArrayList<Stmt.Block> extensions = new ArrayList<>();
//			// Can only extend a block in some way
//			Stmt.Block block = (Stmt.Block) stmt;
//			// Replace any existing statements
//			int blocks = 0;
//			for (int i = 0; i != block.size(); ++i) {
//				Stmt ith = block.get(i);
//				if (ith instanceof Stmt.Let) {
//					declared++;
//				} else if(ith instanceof Stmt.Block) {
//					blocks++;
//				}
//				List<Stmt> es = extend(ith, declared, depth+1);
//				for (int j = 0; j != es.size(); ++j) {
//					extensions.add(replace(block, i, es.get(j)));
//				}
//			}
//			// Extends the block itself
//			Domain<Stmt> gen = STMT_GENERATORS[declared];
//			long size = gen.size();
//			for (long i = 0; i != size; ++i) {
//				extensions.add(add(block, gen.generate(i)));
//			}
//			if (blocks < 1) {
//				for (long i = 0; i != size; ++i) {
//					extensions.add(add(block, new Stmt.Block(block.lifetime(), new Stmt[] { gen.generate(i) })));
//				}
//			}
//			// Done
//			return (List<T>) extensions;
//		} else {
//			return Collections.EMPTY_LIST;
//		}
//	}

	/**
	 * Replace ith item in a given statement block.
	 *
	 * @param block
	 * @param i
	 * @param item
	 * @return
	 */
	private static Stmt.Block replace(Stmt.Block block, int i, Stmt item) {
		Stmt[] stmts = Arrays.copyOf(block.toArray(), block.size());
		stmts[i] = item;
		return new Stmt.Block(block.lifetime(), stmts);
	}

	/**
	 * Add item to end of a given statement block.
	 *
	 * @param block
	 * @param i
	 * @param item
	 * @return
	 */
	private static Stmt.Block add(Stmt.Block block, Stmt item) {
		int size = block.size();
		Stmt[] stmts = Arrays.copyOf(block.toArray(), size + 1);
		stmts[size] = item;
		return new Stmt.Block(block.lifetime(), stmts);
	}

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

//	public static void main(String[] args) throws IOException {
//		Lifetime globalLifetime = new Lifetime();
//		//
//		List<Stmt.Block> worklist = new ArrayList<>();
//		worklist.add(new Stmt.Block(globalLifetime.freshWithin(), new Stmt[0]));
//		long total = 0;
//		for (int i = 0; i != 3; ++i) {
//			// Make all one place extensions
//			worklist = extendAll(worklist, 0);
//			//
//			System.out.println("=======================================");
//			System.out.println("ITH: " + i + " SIZE: " + worklist.size());
//			System.out.println("=======================================");
//			for (int j = 0; j != worklist.size(); ++j) {
//				Stmt.Block b = worklist.get(j);
//				System.out.println("GOT: " + worklist.get(j).toString());
//				runAndCheck(b, globalLifetime);
//			}
//			total += worklist.size();
//		}
//		printStats(total);
//	}
//

	public static Domain<Stmt.Block> toDomain(int nesting, int width, int depth, Lifetime context, Domain<Integer>ints,
			Domain<String> names) {
		Domain<Expr> expressions = Expr.toDomain(depth, ints, names);
		Domain<Stmt> statements = Stmt.toDomain(nesting, width, context, expressions, names);
		return Stmt.Block.toDomain(context, width, statements);
	}

	public static void main(String[] args) throws IOException {
		Lifetime context = new Lifetime();
		Domain<Integer> ints = new Domain.Int(0,0);
		Domain<String> names = new Domain.Finite<>("x", "y", "z");
		Domain<Block> blocks = toDomain(1, 1, 0, context, ints, names);
		long size = blocks.size();
		//
		System.out.println("SIZE: " + size);
		for (long i = 0; i != size; ++i) {
			//runAndCheck(blocks.get(i), context);
		}

		printStats(size);
	}

	public static final 	OperationalSemantics semantics = new OperationalSemantics.BigStep();
	public static final BorrowChecker checker = new BorrowChecker("");
	public static long malformed = 0;
	public static long valid = 0;
	public static long invalid = 0;
	public static long falsepos = 0;
	public static long falseneg = 0;

	public static void runAndCheck(Stmt.Block stmt, Lifetime lifetime) {
		boolean ran = false;
		boolean checked = false;
		// See whether or not it borrow checks
		try {
			checker.apply(BorrowChecker.EMPTY_ENVIRONMENT, lifetime, stmt);
			checked = true;
		} catch (SyntaxError e) {
		}
		// See whether or not it executes
		try {
			semantics.apply(AbstractSemantics.EMPTY_STATE, lifetime, stmt).first();
			ran = true;
		} catch (Exception e) {
		}
		// Update statistics
		if (checked && ran) {
			valid++;
		} else if (checked) {
			System.out.println("*** ERROR: " + stmt.toString());
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
