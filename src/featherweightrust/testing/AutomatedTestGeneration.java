package featherweightrust.testing;

import java.io.IOException;
import java.io.StringReader;
import java.math.BigInteger;
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
import jmodelgen.util.AbstractBigDomain;
import jmodelgen.util.AbstractDomain;
import jmodelgen.core.Domains;
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
	public static class DefUseDomain extends AbstractBigDomain<Stmt> implements Domain.Big<Stmt> {
		private final DefUseDomain parent;
		/**
		 * The root lifetime to use
		 */
		private final Lifetime root;
		/**
		 * The domain of integers to use
		 */
		private final Domain.Small<Integer> ints;
		/**
		 * The domain of all names
		 */
		private final Domain.Small<String> names;
		/**
		 * Maximum number of blocks to allow
		 */
		private final int maxBlocks;
		/**
		 * The number of variables which have been declared
		 */
		private int declared;
		/**
		 * The block count
		 */
		private int blocks;
		/**
		 * The constructed subdomain reflecting the above
		 */
		private Domain.Big<Stmt> subdomain;

		public DefUseDomain(Lifetime root, Domain.Small<Integer> ints, Domain.Small<String> names, int maxBlocks) {
			this(root, ints, names, maxBlocks, null, 0, 0);
		}

		public DefUseDomain(Lifetime root, Domain.Small<Integer> ints, Domain.Small<String> names, int maxBlocks, DefUseDomain parent, int declared, int depth) {
			this.parent = parent;
			this.root = root;
			this.ints = ints;
			this.names = names;
			this.maxBlocks = maxBlocks;
			this.declared = declared;
			this.blocks = depth;
			subdomain = construct(names,declared);
		}

		@Override
		public BigInteger bigSize() {
			return subdomain.bigSize();
		}

		@Override
		public Stmt get(BigInteger index) {
			return subdomain.get(index);
		}

		/**
		 * Construct an updated domain where one more variable has been declared.
		 *
		 * @return
		 */
		public DefUseDomain declare() {
			if(names.bigSize().compareTo(BigInteger.valueOf(declared)) == 0) {
				throw new IllegalArgumentException("cannot declare any more variables!");
			}
			return new DefUseDomain(root, ints, names, maxBlocks, parent, declared + 1, blocks);
		}

		public DefUseDomain enter() {
			return new DefUseDomain(root, ints, names, maxBlocks, this, declared, blocks + 1);
		}

		public DefUseDomain leave() {
			// When leaving we revert to the parent with the update block information
			return new DefUseDomain(root, ints, names, maxBlocks, parent.parent, parent.declared, blocks);
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
		private Domain.Big<Stmt> construct(Domain.Big<String> names, int _n) {
			BigInteger n = BigInteger.valueOf(_n);
			// Create the domain of declared variables from domain of all variables.
			Domain.Small<String> declared = names.slice(BigInteger.ZERO,n);
			// Create the domain of undeclared variables from a single variable. This
			// ensures that we will attempt to declare at most one additional variable.
			Domain.Small<String> undeclared = names.slice(n, names.bigSize().min(n.add(BigInteger.ONE)));
			// Construct domain of expressions over *declared* variables
			Domain.Big<Expr> expressions = Expr.toBigDomain(1, ints, declared);
			// Calculate depth argument
			int d = blocks < maxBlocks ? 1 : 0;
			// Construct domain of statements over declared and undeclared variables
			return Stmt.toBigDomain(d, 0, root, expressions, declared, undeclared);
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
	public static class DefUseTransfer implements Transfer<Stmt,DefUseDomain> {

		@Override
		public DefUseDomain enter(Stmt stmt, DefUseDomain context) {
			if(stmt instanceof Stmt.Block) {
				return context.enter();
			} else {
				return context;
			}
		}

		@Override
		public DefUseDomain leave(Stmt stmt, DefUseDomain domain) {
			if(stmt instanceof Stmt.Let) {
				return domain.declare();
			} else if(stmt instanceof Stmt.Block){
				return domain.leave();
			} else {
				return domain;
			}
		}

	}

	public static void main(String[] args) throws IOException {
		Lifetime root = new Lifetime();
		// The domain of all integers
		Domain.Big<Integer> ints = BigDomains.Int(0,0);
		// The domain of all variable names
		Domain.Big<String> names = BigDomains.Finite("x","y","z");
		// The specialised domain for creating statements
		DefUseDomain statements = new DefUseDomain(root, ints, names, 2);
		// Construct a suitable mutator (restricting to width 3)
		Mutable.LeftMutator<Stmt, DefUseDomain> extender = new Mutable.LeftMutator<>(statements, new DefUseTransfer(), 3);
		// Construct empty block as seed (which cannot have the root lifetime)
		Stmt seed = new Stmt.Block(root.freshWithin(), new Stmt[0]);
		// Construct Iterative Generator from seed
		IterativeGenerator<Stmt> generator = new IterativeGenerator<>(seed, 5, extender);
		//
		int i = 0;
		for(Stmt s : generator) {
//			if(s.toString().equals("{ let mut x = 0; let mut y = &x; { let mut z = 0; y = &z; } }")) {
			if(s.toString().equals("{ let mut x = 0; let mut y = &mut x; { let mut z = &mut y; *z = z; } }")) {
				System.out.println(s);
			}
			runAndCheck((Stmt.Block) s, root);
			++i;
		}
		//
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
