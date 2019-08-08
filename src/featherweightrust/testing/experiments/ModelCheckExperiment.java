package featherweightrust.testing.experiments;

import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.function.BiFunction;


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
import jmodelgen.core.Mutable.Transfer;
import jmodelgen.core.Transformer;
import jmodelgen.util.AbstractDomain;
import jmodelgen.util.Domains;
import jmodelgen.util.IterativeGenerator;
import featherweightrust.core.Syntax.Value;
import featherweightrust.core.Syntax.Value.Location;

public class ModelCheckExperiment {

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
	public static class DefUseDomain extends AbstractDomain<Stmt> implements Domain<Stmt> {
		private final DefUseDomain parent;
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
		private Domain<Stmt> subdomain;

		public DefUseDomain(Lifetime root, Domain<Integer> ints, Domain<String> names, int maxBlocks) {
			this(root, ints, names, maxBlocks, null, 0, 0);
		}

		public DefUseDomain(Lifetime root, Domain<Integer> ints, Domain<String> names, int maxBlocks, DefUseDomain parent, int declared, int depth) {
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
		private Domain<Stmt> construct(Domain<String> names, int n) {
			// Create the domain of declared variables from domain of all variables.
			Domain<String> declared = names.slice(0,n);
			// Create the domain of undeclared variables from a single variable. This
			// ensures that we will attempt to declare at most one additional variable.
			Domain<String> undeclared = names.slice(n, Math.min(names.size(), n + 1));
			// Construct domain of expressions over *declared* variables
			Domain<Expr> expressions = Expr.toDomain(1, ints, declared);
			// Calculate depth argument
			int d = blocks < maxBlocks ? 1 : 0;
			// Construct domain of statements over declared and undeclared variables
			return Stmt.toDomain(d, 1, root, expressions, declared, undeclared);
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

	private static final String[] VARIABLE_NAMES = { "x", "y", "z", "a", "b", "c", "d", "e", "f", "g", "h", "i", "j",
			"k", "l", "m", "n", "o", "p", "q", "r", "s", "t", "u", "v", "w" };

	/**
	 * Run an experiment checking every program in a particular program space.
	 * Programs are limited to those where every varable is defined before being
	 * used and, also, that isomorphic programs (with respect to variable names) are
	 * ignored.
	 *
	 * @param i The number of integers to consider.
	 * @param v The number of variables to consier.
	 * @param d The maximum nesting of blocks to permit.
	 * @param w The maximum width of blocks to permit.
	 * @param b The maximum number of blocks to permit.
	 */
	public static Stats run(int i, int v, int d, int w, int b) {
		Stats stats = new Stats(i,v,d,w,b);
		//
		Lifetime root = new Lifetime();
		// The domain of all integers
		Domain<Integer> ints = Domains.Int(0,i-1);
		// The domain of all variable names
		Domain<String> names = Domains.Finite(Arrays.copyOf(VARIABLE_NAMES, v));
		// The specialised domain for creating statements
		DefUseDomain statements = new DefUseDomain(root, ints, names, b);
		// Construct a suitable mutator (restricting to width 3)
		Mutable.LeftMutator<Stmt, DefUseDomain> extender = new Mutable.LeftMutator<>(statements, new DefUseTransfer(), w);
		// Construct empty block as seed (which cannot have the root lifetime)
		Stmt seed = new Stmt.Block(root.freshWithin(), new Stmt[0]);
		// Construct Iterative Generator from seed
		IterativeGenerator<Stmt> generator = new IterativeGenerator<>(seed, 4, extender);
		//
		ArrayList<String> items = new ArrayList<>();
		for(Stmt s : generator) {
			if(s.toString().equals("{ let mut x = 0; let mut y = &x; { let mut z = 0; y = &z; } }")) {
//			if(s.toString().equals("{ let mut x = 0; let mut y = &mut x; { let mut z = &mut y; *z = z; } }")) {
				System.out.println(s);
			}
			items.add(s.toString());
			runAndCheck((Stmt.Block) s, root, stats);
			stats.total++;
		}
		//
		stats.unique = new HashSet<>(items).size();
		//
		return stats;
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
		private final int i;
		private final int v;
		private final int d;
		private final int w;
		private final int b;
		public long total = 0;
		public long unique = 0;
		public long malformed = 0;
		public long valid = 0;
		public long invalid = 0;
		public long falsepos = 0;
		public long falseneg = 0;

		public Stats(int i, int v, int d, int w, int b) {
			this.i = i;
			this.v = v;
			this.d = d;
			this.w = w;
			this.b = b;
		}

		public void print() {
			System.out.println("================================");
			System.out.println("SPACE: " + i + "," + v + "," + d + "," + w + "," + b);
			System.out.println("TOTAL: " + total);
			System.out.println("UNIQUE: " + unique);
			System.out.println("MALFORMED: " + malformed);
			System.out.println("VALID: " + valid);
			System.out.println("INVALID: " + invalid);
			System.out.println("FALSEPOS: " + falsepos);
			System.out.println("FALSENEG: " + falseneg);
		}
	}

	public static void main(String[] args) throws IOException {
		int[][] spaces = {
				{1, 1, 1, 1, 2},
				{1, 1, 1, 2, 2},
				{1, 1, 2, 2, 2},
				{1, 2, 2, 2, 2},
				{1, 2, 2, 3, 2},
//				{1, 3, 2, 3, 2},
			};
		//
		for(int[] space : spaces) {
			Stats stats = run(space[0],space[1],space[2],space[3],space[4]);
			stats.print();
		}
	}

}
