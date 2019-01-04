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
import featherweightrust.core.Syntax.Value;
import featherweightrust.core.Syntax.Value.Location;

public class AutomatedTestGeneration {

	public interface Generator<T> {
		/**
		 * Determine how many objects this generator can produce
		 *
		 * @return
		 */
		public long size();

		/**
		 * Generate an instance of the given kind
		 *
		 * @param kind
		 * @return
		 */
		public T generate(long index);
	}

	public static class BoolGenerator implements Generator<Boolean> {

		@Override
		public long size() {
			return 2;
		}

		@Override
		public Boolean generate(long index) {
			return index == 0;
		}
	}

	public static class IntGenerator implements Generator<Integer> {
		private final int lower;
		private final int upper;

		public IntGenerator(int lower, int upper) {
			this.lower = lower;
			this.upper = upper;
		}

		@Override
		public long size() {
			return upper - lower + 1;
		}

		@Override
		public Integer generate(long index) {
			return lower + (int) index;
		}
	}

	public static class FiniteGenerator<T> implements Generator<T> {
		private final T[] items;

		public FiniteGenerator(T[] items) {
			this.items = items;
		}

		@Override
		public long size() {
			return items.length;
		}

		@Override
		public T generate(long index) {
			return items[(int) index];
		}
	}

	public static abstract class AbstractUnaryGenerator<T, S> implements Generator<T> {
		protected final Generator<S> generator;

		public AbstractUnaryGenerator(Generator<S> generator) {
			this.generator = generator;
		}

		@Override
		public long size() {
			return generator.size();
		}

		@Override
		public T generate(long index) {
			S s = generator.generate(index);
			return generate(s);
		}

		public abstract T generate(S s);
	}

	public static abstract class AbstractBinaryGenerator<T, L, R> implements Generator<T> {
		private final Generator<L> left;
		private final Generator<R> right;

		public AbstractBinaryGenerator(Generator<L> left, Generator<R> right) {
			this.left = left;
			this.right = right;
		}

		@Override
		public long size() {
			return left.size() * right.size();
		}

		@Override
		public T generate(long index) {
			long left_size = left.size();
			L l = left.generate(index % left_size);
			R r = right.generate(index / left_size);
			return generate(l, r);
		}

		public abstract T generate(L left, R right);
	}

	public static abstract class AbstractNaryGenerator<T, S> implements Generator<T> {
		private final int max;
		private final Generator<S> generator;

		public AbstractNaryGenerator(int max, Generator<S> generator) {
			this.max = max;
			this.generator = generator;
		}

		@Override
		public long size() {
			long generator_size = generator.size();
			long size = 0;
			for (int i = 0; i <= max; ++i) {
				long delta = delta(generator_size, i);
				size = size + delta;
			}
			return size;
		}

		@Override
		public T generate(long index) {
			ArrayList<S> items = new ArrayList<>();
			final long generator_size = generator.size();
			// Yes, this one is a tad complex
			for (int i = 0; i <= max; ++i) {
				long delta = delta(generator_size, i);
				if (index < delta) {
					for (int j = 0; j < i; ++j) {
						items.add(generator.generate(index % generator_size));
						index = index / generator_size;
					}
					break;
				}
				index = index - delta;
			}
			return generate(items);
		}

		private static long delta(long base, int power) {
			if (power == 0) {
				// special case as only one empty list
				return 1;
			} else {
				long r = base;
				for (int i = 1; i < power; ++i) {
					r = r * base;
				}
				return r;
			}
		}

		public abstract T generate(List<S> items);
	}

	public static class SubclassGenerator<T> implements Generator<T> {
		private final Generator<? extends T>[] generators;

		public SubclassGenerator(Generator<? extends T>... generators) {
			this.generators = generators;
		}

		@Override
		public long size() {
			long sum = 0;
			for (int i = 0; i != generators.length; ++i) {
				sum = sum + generators[i].size();
			}
			return sum;
		}

		@Override
		public T generate(long index) {
			long sum = 0;
			for (int i = 0; i != generators.length; ++i) {
				Generator<? extends T> ith = generators[i];
				long size = ith.size();
				if (index < (sum + size)) {
					return ith.generate(index - sum);
				}
				sum = sum + size;
			}
			throw new IllegalArgumentException("invalid index");
		}
	}

	public static class ValueGenerator extends AbstractUnaryGenerator<Value, Integer> {

		public ValueGenerator(Generator<Integer> gen) {
			super(gen);
		}

		@Override
		public Value generate(Integer val) {
			return new Value.Integer(val);
		}
	}

	public static class VariableGenerator implements Generator<Expr.Variable> {
		private final String[] names;

		public VariableGenerator(String... names) {
			this.names = names;
		}

		@Override
		public long size() {
			return names.length;
		}

		@Override
		public Variable generate(long index) {
			return new Expr.Variable(names[(int) index]);
		}
	}

	public static class DerefGenerator extends AbstractUnaryGenerator<Expr.Dereference, Expr> {

		public DerefGenerator(Generator<Expr> gen) {
			super(gen);
		}

		@Override
		public Dereference generate(Expr expr) {
			return new Expr.Dereference(expr);
		}
	}

	public static class BorrowGenerator extends AbstractBinaryGenerator<Expr.Borrow, Boolean, String> {

		public BorrowGenerator(String... names) {
			super(new BoolGenerator(), new FiniteGenerator<>(names));
		}

		@Override
		public Expr.Borrow generate(Boolean mut, String name) {
			return new Expr.Borrow(new Expr.Variable(name), mut);
		}
	}

	public static class BoxGenerator extends AbstractUnaryGenerator<Expr.Box, Expr> {

		public BoxGenerator(Generator<Expr> gen) {
			super(gen);
		}

		@Override
		public Expr.Box generate(Expr expr) {
			return new Expr.Box(expr);
		}
	}

	public static class LetGenerator extends AbstractBinaryGenerator<Stmt.Let, String, Expr> {

		public LetGenerator(Generator<Expr> generator, String... names) {
			super(new FiniteGenerator<>(names), generator);
		}

		@Override
		public Stmt.Let generate(String name, Expr initialiser) {
			return new Stmt.Let(name, initialiser);
		}
	}

	public static class AssignmentGenerator extends AbstractBinaryGenerator<Stmt.Assignment, String, Expr> {

		public AssignmentGenerator(Generator<Expr> generator, String... names) {
			super(new FiniteGenerator<>(names), generator);
		}

		@Override
		public Stmt.Assignment generate(String name, Expr initialiser) {
			return new Stmt.Assignment(new Expr.Variable(name), initialiser);
		}
	}

	public static class IndirectAssignmentGenerator
			extends AbstractBinaryGenerator<Stmt.IndirectAssignment, String, Expr> {

		public IndirectAssignmentGenerator(Generator<Expr> generator, String... names) {
			super(new FiniteGenerator<>(names), generator);
		}

		@Override
		public Stmt.IndirectAssignment generate(String name, Expr initialiser) {
			return new Stmt.IndirectAssignment(new Expr.Variable(name), initialiser);
		}
	}

	public static class BlockGenerator extends AbstractNaryGenerator<Stmt.Block, Stmt> {
		private Lifetime lifetime;
		//
		public BlockGenerator(Lifetime lifetime, int maxWidth, Generator<Stmt> gen) {
			super(maxWidth, gen);
			this.lifetime = lifetime;
		}

		@Override
		public Block generate(List<Stmt> s) {
			return new Stmt.Block(lifetime, s.toArray(new Stmt[s.size()]));
		}
	}

	private static int EXPR_DEPTH = 0;
	private static int STMT_DEPTH = 1;
	private static int STMT_WIDTH = 1;

	private static final int INTEGER_MIN = 0;
	private static final int INTEGER_MAX = 0;
	private static final String[][] VARIABLES = {
			{ },              // 0 declared
			{ "x" },          // 1 declared
			{ "x", "y" },     // 2 declared
			{ "x", "y", "z" } // 3 declared
	};
	private static final String[][] LET_VARIABLES = {
			{ "x" },              // 0 declared
			{ "y" },          // 1 declared
			{ "z" },     // 2 declared
			{ } // 3 declared
	};
	public static Generator<Expr> buildExprGenerator(int depth, int declared) {
		IntGenerator intGenerator = new IntGenerator(INTEGER_MIN, INTEGER_MAX);
		ValueGenerator valueGenerator = new ValueGenerator(intGenerator);
		VariableGenerator variableGenerator = new VariableGenerator(VARIABLES[declared]);
		BorrowGenerator borrowGenerator = new BorrowGenerator(VARIABLES[declared]);
		//
		if (depth == 0) {
			// Terminal cases only
			return new SubclassGenerator<>(valueGenerator, variableGenerator, borrowGenerator);
		} else {
			Generator<Expr> exprGenerator = buildExprGenerator(depth - 1, declared);
			DerefGenerator derefGenerator = new DerefGenerator(exprGenerator);
			BoxGenerator boxGenerator = new BoxGenerator(exprGenerator);
			return new SubclassGenerator<>(valueGenerator, variableGenerator, derefGenerator, boxGenerator);
		}
	}


	public static Generator<Stmt> buildStmtGenerator(int depth, int width, Lifetime lifetime,
			Generator<Expr> exprGenerator, int declared) {
		LetGenerator letGenerator = new LetGenerator(exprGenerator, LET_VARIABLES[declared]);
		AssignmentGenerator assignGenerator = new AssignmentGenerator(exprGenerator, VARIABLES[declared]);
		IndirectAssignmentGenerator indAssignGenerator = new IndirectAssignmentGenerator(exprGenerator,
				VARIABLES[declared]);
		if (depth == 0) {
			return new SubclassGenerator<>(letGenerator, assignGenerator, indAssignGenerator);
		} else {
			Generator<Stmt> stmtGenerator = buildStmtGenerator(depth - 1, width, lifetime, exprGenerator, declared);
			BlockGenerator blockGenerator = new BlockGenerator(lifetime.freshWithin(), width, stmtGenerator);
			return new SubclassGenerator<>(letGenerator, assignGenerator, indAssignGenerator, blockGenerator);
		}
	}

	private static Generator<Expr>[] EXPR_GENERATORS = new Generator[]{
		buildExprGenerator(EXPR_DEPTH,0),
		buildExprGenerator(EXPR_DEPTH,1),
		buildExprGenerator(EXPR_DEPTH,2),
		buildExprGenerator(EXPR_DEPTH,3),
	};

	private static Generator<Stmt>[] STMT_GENERATORS = new Generator[]{
			// Stmt depth always at zero since extensions handle separately
			buildStmtGenerator(0, STMT_WIDTH, new Lifetime(), EXPR_GENERATORS[0], 0),
			buildStmtGenerator(0, STMT_WIDTH, new Lifetime(), EXPR_GENERATORS[1], 1),
			buildStmtGenerator(0, STMT_WIDTH, new Lifetime(), EXPR_GENERATORS[2], 2),
			buildStmtGenerator(0, STMT_WIDTH, new Lifetime(), EXPR_GENERATORS[3], 3)
	};

	/**
	 * Given one or more statements, extend each one by generating all valid "one
	 * place" extensions.
	 *
	 * @param stmts
	 * @param gen
	 * @return
	 */
	public static <T extends Stmt> List<T> extendAll(List<T> stmts, int declared) {
		ArrayList<T> results = new ArrayList<>();
		for (int i = 0; i != stmts.size(); ++i) {
			results.addAll(extend(stmts.get(i), declared, 0));
		}
		return results;
	}

	/**
	 * Given a statement, attempt to generate all valid "one place" extensions.
	 *
	 * @param stmt
	 * @param gen
	 * @return
	 */
	public static <T extends Stmt> List<T> extend(T stmt, int declared, int depth) {
		if (depth < 2 && stmt instanceof Stmt.Block) {
			ArrayList<Stmt.Block> extensions = new ArrayList<>();
			// Can only extend a block in some way
			Stmt.Block block = (Stmt.Block) stmt;
			// Replace any existing statements
			int blocks = 0;
			for (int i = 0; i != block.size(); ++i) {
				Stmt ith = block.get(i);
				if (ith instanceof Stmt.Let) {
					declared++;
				} else if(ith instanceof Stmt.Block) {
					blocks++;
				}
				List<Stmt> es = extend(ith, declared, depth+1);
				for (int j = 0; j != es.size(); ++j) {
					extensions.add(replace(block, i, es.get(j)));
				}
			}
			// Extends the block itself
			Generator<Stmt> gen = STMT_GENERATORS[declared];
			long size = gen.size();
			for (long i = 0; i != size; ++i) {
				extensions.add(add(block, gen.generate(i)));
			}
			if (blocks < 1) {
				for (long i = 0; i != size; ++i) {
					extensions.add(add(block, new Stmt.Block(block.lifetime(), new Stmt[] { gen.generate(i) })));
				}
			}
			// Done
			return (List<T>) extensions;
		} else {
			return Collections.EMPTY_LIST;
		}
	}

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

	public static void main(String[] args) throws IOException {
		Lifetime globalLifetime = new Lifetime();
		//
		List<Stmt.Block> worklist = new ArrayList<>();
		worklist.add(new Stmt.Block(globalLifetime.freshWithin(), new Stmt[0]));
		long total = 0;
		for (int i = 0; i != 4; ++i) {
			// Make all one place extensions
			worklist = extendAll(worklist, 0);
			//
			System.out.println("=======================================");
			System.out.println("ITH: " + i + " SIZE: " + worklist.size());
			System.out.println("=======================================");
			for (int j = 0; j != worklist.size(); ++j) {
				Stmt.Block b = worklist.get(j);
				System.out.println("GOT: " + worklist.get(j).toString());
				runAndCheck(b, globalLifetime);
			}
			total += worklist.size();
		}
		printStats(total);
	}
//
//	public static void main2(String[] args) throws IOException {
//		Generator<Expr> exprGenerator = buildExprGenerator(0, VARIABLES);
//		Generator<Stmt> stmtGenerator = buildStmtGenerator(1, 2, exprGenerator, VARIABLES);
//		BlockGenerator blockGenerator = new BlockGenerator(3, stmtGenerator);
//
//		long size = blockGenerator.size();
//		//
//		for (long i = 0; i != size; ++i) {
//			runAndCheck(blockGenerator.generate(i));
//			//
//			if (i % 100000 == 0) {
//				double percent = (i * 100) / size;
//				System.out.println("COMPLETED: " + i + " / " + size + " (" + percent + "%)");
//			}
//		}
//
//		printStats(size);
//	}

	public static final 	OperationalSemantics semantics = new OperationalSemantics();
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
