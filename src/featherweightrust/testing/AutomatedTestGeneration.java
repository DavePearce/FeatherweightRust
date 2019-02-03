package featherweightrust.testing;

import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.BiFunction;

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
import jmodelgen.core.Domain;
import jmodelgen.core.Mutable;
import jmodelgen.core.Transformer;
import jmodelgen.util.AbstractDomain;
import jmodelgen.util.IterativeGenerator;
import featherweightrust.core.Syntax.Value;
import featherweightrust.core.Syntax.Value.Location;

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

	/**
	 * The def use statement domain is a specialised domain which records
	 * information about which variables are declared and uses this to limit the way
	 * that statements are created. For example, it won't allow a redeclaration of
	 * the same variable. Likewise, it won't allow a variable to be used which has
	 * not already been declared.
	 *
	 * @author David J. Pearce
	 *
	 */
	public static class DefUseDomain extends AbstractDomain<Stmt> implements Domain<Stmt> {
		/**
		 * The root lifetime to use
		 */
		private final Lifetime root;
		/**
		 * The domain of integers to use
		 */
		private final Domain<Integer> ints;
		/**
		 * The domain of all names
		 */
		private final Domain<String> names;
		/**
		 * The number of variables which have been declared
		 */
		private int declared;
		/**
		 * The constructed subdomain reflecting the above
		 */
		private Domain<Stmt> subdomain;

		public DefUseDomain(Lifetime root, Domain<Integer> ints, Domain<String> names, int declared) {
			this.root = root;
			this.ints = ints;
			this.names = names;
			this.declared = declared;
			subdomain = construct(names,declared);
		}

		@Override
		public long size() {
			return subdomain.size();
		}

		@Override
		public Stmt get(long index) {
			return subdomain.get(index);
		}

		/**
		 * Construct an updated domain where one more variable has been declared.
		 *
		 * @return
		 */
		public DefUseDomain declare() {
			if(declared == names.size()) {
				throw new IllegalArgumentException("cannot declare any more variables!");
			}
			return new DefUseDomain(root, ints, names, declared + 1);
		}

		/**
		 * Construct the domain assuming that the first n variables have been declared.
		 *
		 * @param names
		 *            The domain of variable names.
		 * @param n
		 *            The first n variables in the domain have already been declared.
		 *            Hence, these variables are available to be used, but not declared.
		 * @return
		 */
		private Domain<Stmt> construct(Domain<String> names, int n) {
			// Create the domain of declared variables from domain of all variables.
			Domain<String> declared = names.slice(0,n);
			// Create the domain of undeclared variables from a single variable. This
			// ensures that we will attempt to declare at most one additional variable.
			Domain<String> undeclared = names.slice(n, Math.min(names.size(), n + 1));
			// Construct domain of expressions over *declared* variables
			Domain<Expr> expressions = Expr.toDomain(1, ints, declared);
			// Construct domain of statements over declared and undeclared variables
			return Stmt.toDomain(1, 0, root, expressions, declared, undeclared);
		}
	}

	/**
	 * The transformer is responsible for narrowing a given domain based upon
	 * statements which are already defined. For example, initially there are no
	 * variables declared and, hence, we cannot construct any statements other than
	 * a declaration.
	 *
	 * @author David J. Pearce
	 *
	 */
	public static class DefUseTransformer implements BiFunction<Stmt,DefUseDomain,DefUseDomain> {

		@Override
		public DefUseDomain apply(Stmt stmt, DefUseDomain domain) {
			if(stmt instanceof Stmt.Let) {
				return domain.declare();
			} else {
				return domain;
			}
		}

	}

	public static void main(String[] args) throws IOException {
		Lifetime root = new Lifetime();
		// The domain of all integers
		Domain<Integer> ints = new Domain.Int(0,0);
		// The domain of all variable names
		Domain<String> names = new Domain.Finite<>("x");
		// The specialised domain for creating statements
		DefUseDomain statements = new DefUseDomain(root, ints, names, 0);
		// Construct a suitable mutator
		Mutable.LeftMutator<Stmt, DefUseDomain> extender = new Mutable.LeftMutator<>(statements, new DefUseTransformer());
		//
		IterativeGenerator<Stmt> generator = new IterativeGenerator<>(new Stmt.Block(root, new Stmt[0]), 2,
				extender);
		//
		int i = 0;
		for(Stmt s : generator) {
			System.out.println((Stmt.Block) s);
			runAndCheck((Stmt.Block) s, root);
			++i;
		}
		//
		System.out.println("GENERATED: " + i);
		printStats(i);
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
