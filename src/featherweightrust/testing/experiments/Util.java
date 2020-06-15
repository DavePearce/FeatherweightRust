package featherweightrust.testing.experiments;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.Stack;

import featherweightrust.core.BorrowChecker;
import featherweightrust.core.ProgramSpace;
import featherweightrust.core.BorrowChecker.Environment;
import featherweightrust.core.Syntax.LVal;
import featherweightrust.core.Syntax.Lifetime;
import featherweightrust.core.Syntax.Path;
import featherweightrust.core.Syntax.Term;
import featherweightrust.core.Syntax.Type;
import featherweightrust.io.Lexer;
import featherweightrust.io.Parser;
import featherweightrust.util.OptArg;
import featherweightrust.util.Pair;
import featherweightrust.util.SliceIterator;
import featherweightrust.util.SyntaxError;
import featherweightrust.util.Triple;
import jmodelgen.core.Domain;
import jmodelgen.core.Walker;

public class Util {
	private static final String DEREF_COERCION_REQUIRED = "Deref coercion required";

	protected static Triple<Iterator<Term.Block>,Long,String> parseDefaultConfiguration(Map<String, Object> options)
			throws IOException {
		Iterator<Term.Block> iterator;
		String label;
		long expected = (Long) options.get("expected");
		long[] batch = (long[]) options.get("batch");
		if (options.containsKey("pspace")) {
			long[] ivdw = (long[]) options.get("pspace");
			int c = (Integer) options.get("constrained");
			ProgramSpace space = new ProgramSpace((int) ivdw[0], (int) ivdw[1], (int) ivdw[2], (int) ivdw[3]);
			// Create iterator
			if (c >= 0) {
				Walker<Term.Block> walker = space.definedVariableWalker(c);
				label = space.toString() + "{def," + c + "}";
				// Slice iterator (if applicable)
				if (batch != null) {
					long[] range = determineIndexRange(batch[0], batch[1], expected);
					label += "[" + range[0] + ".." + range[1] + "]";
					// Create sliced iterator
					iterator = new SliceIterator(walker, range[0], range[1]);
					// Update expected
					expected = range[1] - range[0];
					return new Triple<>(iterator, expected, label);
				} else {
					iterator = walker.iterator();
				}
			} else {
				// Get domain
				Domain.Big<Term.Block> domain = space.domain();
				// Determine expected size
				expected = domain.bigSize().longValueExact();
				// Get iterator
				iterator = domain.iterator();
				//
				label = space.toString();
			}
		} else {
			// Read from stdin line by line
			List<Term.Block> inputs = readAll(System.in);
			iterator = inputs.iterator();
			expected = inputs.size();
			label = "STDIN";
		}
		// Slice iterator (if applicable)
		if (batch != null) {
			long[] range = determineIndexRange(batch[0], batch[1], expected);
			label += "[" + range[0] + ".." + range[1] + "]";
			// Create sliced iterator
			iterator = new SliceIterator(iterator, range[0], range[1]);
			// Update expected
			expected = range[1] - range[0];
		}
		return new Triple<>(iterator, expected, label);
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

	private static List<Term.Block> readAll(InputStream in) throws IOException {
		ArrayList<Term.Block> inputs = new ArrayList<>();
		Scanner stdin = new Scanner(in);
		while (stdin.hasNext()) {
		    String input = stdin.nextLine();
		    // Tokenize input program
		    List<Lexer.Token> tokens = new Lexer(new StringReader(input)).scan();
			// Parse block
			Term.Block stmt = new Parser(input,tokens).parseStatementBlock(new Parser.Context(), ProgramSpace.ROOT);
			// Record it
		    inputs.add(stmt);
		}
		//
		stdin.close();
		// Done
		return inputs;
	}

	/**
	 * Check whether a give term fails type checking because it requires a "deref
	 * coercion".
	 *
	 * @param R
	 * @param l
	 * @param b
	 * @param sourceFile
	 * @return
	 */
	public static boolean requiresDerefCoercions(Term.Block b) {
		// NOTE: copy inference enabled when fuzzing
		BorrowChecker checker = new CoercionChecker(true,b.toString());
		try {
			checker.apply(BorrowChecker.EMPTY_ENVIRONMENT, ProgramSpace.ROOT, b);
			return true;
		} catch (SyntaxError e) {
			return false;
		}
	}

	/**
	 * Extends the standard checker with support for implicit coercions as found in
	 * Rust.
	 *
	 * @author David J. Pearce
	 *
	 */
	private static class CoercionChecker extends BorrowChecker {
		private Type target = null;

		public CoercionChecker(boolean copyInference, String sourcefile, Extension... extensions) {
			super(copyInference, sourcefile, extensions);
		}

		@Override
		protected Pair<Environment, Type> apply(Environment R1, Lifetime l, Term.Assignment t) {
			LVal lv = t.leftOperand();
			// NOTE: only works because don't generate expression blocks when fuzzing.
			this.target = super.typeOf(R1, lv).concretize();
			// Declaration check
			check(R1.get(lv.name()) != null, UNDECLARED_VARIABLE, lv);
			// First, look for nooperation
			if (isNoOperation(R1, l, t)) {
				this.target = null;
				return new Pair<>(R1,Type.Unit);
			}
			Pair<Environment, Type> r = super.apply(R1, l, t);
			this.target = null;
			return r;
		}

		@Override
		protected Pair<Environment, Type> apply(Environment R, Lifetime l, Term.Access t) {
			Pair<Environment,Type> p = super.apply(R, l, t);
			Type T1 = p.second();
			Type T2 = coerce(p.first(),target,T1);
			if(T1 == T2) {
				return p;
			} else {
				return new Pair<>(p.first(),T2);
			}
		}

		@Override
		protected Pair<Environment, Type> apply(Environment R, Lifetime l, Term.Borrow t) {
			if(target != null) {
				t = coerce(target,t);
			}
			Pair<Environment,Type> p = super.apply(R, l, t);
			Type T1 = p.second();
			Type T2 = coerce(p.first(),target,T1);
			if(T1 == T2) {
				return p;
			} else {
				return new Pair<>(p.first(),T2);
			}
		}

		@Override
		protected Pair<Environment, Type> apply(Environment R1, Lifetime l, Term.Box t) {
			// Update target
			if(target instanceof Type.Box) {
				target = ((Type.Box)target).element();
			} else {
				target = null;
			}
			//
			return super.apply(R1, l, t);
		}

		/**
		 * Attempt to apply a deref coercion.
		 *
		 * @param target
		 * @param actual
		 * @return
		 */
		private Type coerce(Environment R, Type target, Type actual) {
			if (target == null) {
				return actual;
			} else {
				Stack<Type> guesses = new Stack<>();
				guesses.push(actual);
				while(guesses.size() > 0) {
					// Extract guess
					Type guess = guesses.pop();
					if (compatible(R, target, guess)) {
						return guess;
					} else if (guess instanceof Type.Box) {
						guesses.push(((Type.Box) guess).element());
					} else if (guess instanceof Type.Borrow) {
						Type.Borrow b = (Type.Borrow) guess;
						// Try deref coercion after
						guesses.push(innerDeref(b));
						// Try deref coercion before
						guesses.push(typeOf(R, b.lvals()[0]));
					}
				}
				// Give up --- can't figure it out.
				return actual;
			}
		}

		private static Type.Borrow innerDeref(Type.Borrow b) {
			LVal[] lvals =b.lvals();
			LVal[] nlvals = new LVal[lvals.length];
			for(int i=0;i!=lvals.length;++i) {
				LVal ith = lvals[i];
				Path p = Path.DEREF.append(ith.path());
				nlvals[i] = new LVal(ith.name(), p);
			}
			return new Type.Borrow(b.isMutable(),nlvals);
		}

		private static Term.Borrow coerce(Type target, Term.Borrow b) {
			if(b.isMutable() && target instanceof Type.Borrow) {
				Type.Borrow t = (Type.Borrow) target;
				if(!t.isMutable()) {
					// Coerce immutable borrow into mutable borrow.
					return new Term.Borrow(b.operand(), false);
				}
			}
			return b;
		}

		private boolean isNoOperation(Environment R1, Lifetime l, Term.Assignment t) {
			Type _target = target;
			LVal lv = t.leftOperand();
			try {
				Type T1 = typeOf(R1, lv);
				// Update type of lhs to be undefined
				Environment R2 = write(R1,lv,T1.undefine(),true);
				// Attempt to type check without lhs
				Pair<Environment,Type> p =apply(R2,l,t.rightOperand());
				// If we get here, then no problem
				Type T2 = p.second();
				return T1.equals(T2);
			} catch(Exception e) {

			}
			target = _target;
			return false;
		}

	}

	public static boolean containsCyclicAssignment(Term t) {
		if(t instanceof Term.Block) {
			Term.Block b = (Term.Block) t;
			for(int i=0;i!=b.size();++i) {
				if(containsCyclicAssignment(b.get(i))) {
					return true;
				}
			}
		} else if(t instanceof Term.Assignment) {
			Term.Assignment a = (Term.Assignment) t;
			LVal lv = a.leftOperand();
			Term rhs = a.rightOperand();
			// Something else is up
			if(rhs instanceof Term.Access && lv.equals(((Term.Access)rhs).operand())) {
				// Don't count statements of the form x = x, *x = *x. etc.
				return false;
			} else if(lv.path().size() == 0 && cycle(lv.name(),a.rightOperand())) {
				// NOTE: only consider assignments to variables (rather than arbitrary lvals)
				// because only these perform strong updates.
				return true;
			}
		}
		return false;
	}

	public static boolean cycle(String name, Term e) {
		if(e instanceof Term.Box) {
			return cycle(name,((Term.Box)e).operand());
		} else if(e instanceof Term.Access) {
			Term.Access a = (Term.Access) e;
			return a.operand().name().equals(name);
		} else if(e instanceof Term.Borrow) {
			Term.Borrow a = (Term.Borrow) e;
			return a.operand().name().equals(name);
		} else {
			return false;
		}
	}
	/**
	 * Given a statement block in the calculus, generate a syntactically correct
	 * Rust program.
	 *
	 * @param b
	 * @return
	 */
	public static String toRustProgram(Term.Block b, String name) {
		return "fn " + name + "() " + toRustString(b, new HashSet<>());
	}

	private static String toRustString(Term stmt, HashSet<String> live) {
		if (stmt instanceof Term.Block) {
			Term.Block block = (Term.Block) stmt;
			String contents = "";
			ArrayList<String> declared = new ArrayList<>();
			for (int i = 0; i != block.size(); ++i) {
				Term s = block.get(i);
				contents += toRustString(s, live) + " ";
				if (s instanceof Term.Let) {
					Term.Let l = (Term.Let) s;
					declared.add(l.variable());
				}
			}
			// Attempt to work around non-lexical lifetimes
			for (int i=declared.size()-1;i>=0;--i) {
				String var = declared.get(i);
				if (live.contains(var)) {
					// declared live variable
					contents = contents + var + "; ";
					live.remove(var);
				}
			}
			//
			return "{ " + contents + "}";
		} else if(stmt instanceof Term.Let) {
			Term.Let s = (Term.Let) stmt;
			String init = toRustString(s.initialiser());
			updateLiveness(s.initialiser(),live);
			// By definition variable is live after assignment
			live.add(s.variable());
			return "let mut " + s.variable() + " = " + init + ";";
		} else {
			Term.Assignment s = (Term.Assignment) stmt;
			updateLiveness(s.rightOperand(),live);
			// By definition variable is live after assignment
			live.add(s.leftOperand().name());
			return s.leftOperand() + " = " + toRustString(s.rightOperand()) + ";";
		}
	}

	/**
	 * Convert an expression into a Rust-equivalent string.
	 *
	 * @param expr
	 * @return
	 */
	private static String toRustString(Term expr) {
		if (expr instanceof Term.Box) {
			Term.Box b = (Term.Box) expr;
			return "Box::new(" + toRustString(b.operand()) + ")";
		} else if(expr instanceof Term.Access) {
			Term.Access d = (Term.Access) expr;
			String r = d.operand().toString();
//			if(d.copy()) {
//				return "*&" + r;
//			} else {
//				return r;
//			}
			return r;
		}else {
			return expr.toString();
		}
	}

	/**
	 * Update the set of live variables by removing any which are moved.
	 *
	 * @param expr
	 * @param liveness
	 */
	private static void updateLiveness(Term expr, HashSet<String> liveness) {
		if (expr instanceof Term.Access) {
			// Variable move
			Term.Access b = (Term.Access) expr;
			if(!b.copy()) {
				liveness.remove(b.operand().name());
			}
		} else if (expr instanceof Term.Box) {
			Term.Box b = (Term.Box) expr;
			updateLiveness(b.operand(), liveness);
		}
	}
	private static Term.Block parse(String input) throws IOException {
		// Allocate the global lifetime. This is the lifetime where all heap allocated
				// data will reside.
		Lifetime globalLifetime = new Lifetime();
		List<Lexer.Token> tokens = new Lexer(new StringReader(input)).scan();
		// Parse block
		return new Parser(input, tokens).parseStatementBlock(new Parser.Context(), globalLifetime);
	}

	public static void main(String[] args) throws IOException {
//		String input = "{ let mut x = box 0 ; { let mut y = box &*x ; *y = &x } }";
//		String input = "{ let mut x = box 0 ; { let mut y = box &*x ; y = box &*y } }";
		String input = "{ let mut x = box 0 ; { let mut y = &*x ; y = &*y } }";
		Term.Block program = parse(input);
		System.out.println("DEREF COERCION: " + requiresDerefCoercions(program));
		new BorrowChecker(true,input).apply(BorrowChecker.EMPTY_ENVIRONMENT, new Lifetime(), program);
	}
}
