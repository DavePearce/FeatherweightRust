package featherweightrust.testing;

import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import featherweightrust.core.BigStepSemantics;
import featherweightrust.core.BorrowChecker;
import featherweightrust.core.Syntax.Expr;
import featherweightrust.core.Syntax.Expr.Dereference;
import featherweightrust.core.Syntax.Expr.Variable;
import featherweightrust.core.Syntax.Stmt;
import featherweightrust.core.Syntax.Stmt.Block;
import featherweightrust.core.Syntax.Stmt.Let;
import featherweightrust.io.Lexer;
import featherweightrust.io.Parser;
import featherweightrust.util.AbstractSemantics;
import featherweightrust.util.SyntaxError;
import featherweightrust.core.Syntax.Value;

public class TestInputGenerator {


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

	public static abstract class AbstractUnaryGenerator<T,S> implements Generator<T> {
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

	public static abstract class AbstractBinaryGenerator<T,L,R> implements Generator<T> {
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
			return generate(l,r);
		}

		public abstract T generate(L left, R right);
	}

	public static abstract class AbstractNaryGenerator<T,S> implements Generator<T> {
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
				long delta = delta(generator_size,i);
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
				long delta = delta(generator_size,i);
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
					return ith.generate(index-sum);
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

	public static class BorrowGenerator extends AbstractBinaryGenerator<Expr.Borrow,Boolean,String> {

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
			super(new FiniteGenerator<>(names),generator);
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


	public static class IndirectAssignmentGenerator extends AbstractBinaryGenerator<Stmt.IndirectAssignment, String, Expr> {

		public IndirectAssignmentGenerator(Generator<Expr> generator, String... names) {
			super(new FiniteGenerator<>(names), generator);
		}

		@Override
		public Stmt.IndirectAssignment generate(String name, Expr initialiser) {
			return new Stmt.IndirectAssignment(new Expr.Variable(name), initialiser);
		}
	}

	public static class BlockGenerator extends AbstractNaryGenerator<Stmt.Block, Stmt> {
		public BlockGenerator(int maxWidth, Generator<Stmt> gen) {
			super(maxWidth, gen);
		}

		@Override
		public Block generate(List<Stmt> s) {
			return new Stmt.Block("?", s.toArray(new Stmt[s.size()]));
		}
	}

	public static Generator<Expr> buildExprGenerator(int depth, String... names) {
		IntGenerator intGenerator = new IntGenerator(0, 2);
		ValueGenerator valueGenerator = new ValueGenerator(intGenerator);
		VariableGenerator variableGenerator = new VariableGenerator(names);
		BorrowGenerator borrowGenerator = new BorrowGenerator(names);
		//
		if (depth == 0) {
			// Terminal cases only
			return new SubclassGenerator<>(valueGenerator, variableGenerator, borrowGenerator);
		} else {
			Generator<Expr> exprGenerator = buildExprGenerator(depth - 1, names);
			DerefGenerator derefGenerator = new DerefGenerator(exprGenerator);
			BoxGenerator boxGenerator = new BoxGenerator(exprGenerator);
			return new SubclassGenerator<>(valueGenerator, variableGenerator, derefGenerator, boxGenerator);
		}
	}

	public static Generator<Stmt> buildStmtGenerator(int depth, int width, Generator<Expr> exprGenerator, String... names) {
		LetGenerator letGenerator = new LetGenerator(exprGenerator,names);
		AssignmentGenerator assignGenerator = new AssignmentGenerator(exprGenerator,names);
		IndirectAssignmentGenerator indAssignGenerator = new IndirectAssignmentGenerator(exprGenerator,names);
		if(depth == 0) {
			return new SubclassGenerator<>(exprGenerator, letGenerator, assignGenerator, indAssignGenerator);
		} else {
			Generator<Stmt> stmtGenerator = buildStmtGenerator(depth - 1, width, exprGenerator, names);
			BlockGenerator blockGenerator = new BlockGenerator(width,stmtGenerator);
			return new SubclassGenerator<>(exprGenerator, letGenerator, assignGenerator, indAssignGenerator, blockGenerator);
		}
	}

	public static void main(String[] args) throws IOException {
		Generator<Expr> exprGenerator = buildExprGenerator(0, "x", "y", "z");
		Generator<Stmt> stmtGenerator = buildStmtGenerator(1, 2, exprGenerator, "x", "y", "z");
		BlockGenerator blockGenerator = new BlockGenerator(3,stmtGenerator);
		BigStepSemantics semantics = new BigStepSemantics();
		long valid = 0;
		long invalid = 0;
		long falsepos = 0;
		long falseneg = 0;
		long size = blockGenerator.size();
		//
		for (long i = 0; i != size; ++i) {
			String input = blockGenerator.generate(i).toString();
			// Scan block
			Stmt.Block stmt;
			try {
				List<Lexer.Token> tokens = new Lexer(new StringReader(input)).scan();
				// Parse block
				stmt = new Parser(input, tokens).parseStatementBlock(new Parser.Context());
			} catch (SyntaxError e) {
				System.out.println("GENERATED: " + input + " ... [MALFORMED]");
				continue;
			}
			//
			boolean ran = false;
			boolean checked = false;
			// See whether or not it borrow checks
			try {
				new BorrowChecker(input).apply(new BorrowChecker.Environment(), "*", stmt);
				checked = true;
			} catch(SyntaxError e) { }
			// See whether or not it executes
			try {
				semantics.apply(new AbstractSemantics.State(), "*", stmt);
				ran = true;
			} catch(Exception e) { }
			//
			double percent = (i * 100) / size;
			System.out.print("[" + i + " (" + percent + "%)]: " + input + " ... ");
			if(checked && ran) {
				System.out.println("[VALID]");
				valid++;
			} else if(checked) {
				System.out.println("[FALSE NEGATIVE]");
				falseneg++;
			} else if(ran) {
				System.out.println("[FALSE POSITIVE]");
				falsepos++;
			} else {
				invalid++;
				System.out.println("[INVALID]");
			}
		}
		System.out.println("================================");
		System.out.println("TOTAL: " + size);
		System.out.println("VALID: " + valid);
		System.out.println("INVALID: " + invalid);
		System.out.println("FALSEPOS: " + falsepos);
		System.out.println("FALSENEG: " + falseneg);
	}
}
