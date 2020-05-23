package featherweightrust.testing.experiments;

import java.util.ArrayList;
import java.util.HashSet;

import featherweightrust.core.BorrowChecker;
import featherweightrust.core.ProgramSpace;
import featherweightrust.core.Syntax.LVal;
import featherweightrust.core.Syntax.Path;
import featherweightrust.core.Syntax.Term;
import featherweightrust.core.Syntax.Type;
import featherweightrust.util.SyntaxError;

public class Util {
	private static final String DEREF_COERCION_REQUIRED = "Deref coercion required";

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
		BorrowChecker checker = new BorrowChecker(b.toString()) {
			@Override
			public Environment write(Environment R1, LVal lv, Type T1, boolean strong) {
				// Determine type of lval
				Type T3 = typeOf(R1, lv);
				// Check compatibility
				if (!compatible(R1, T3, T1, R1) && derefCoercion(R1,T3,T1)) {
					syntaxError(DEREF_COERCION_REQUIRED,lv);
				}
				return super.write(R1, lv, T1, strong);
			}

			private boolean derefCoercion(Environment R, Type Tl, Type Tr) {
				if (compatible(R, Tl, Tr, R)) {
					return true;
				} else if (Tr instanceof Type.Box) {
					Type.Box b = (Type.Box) Tr;
					return derefCoercion(R, Tl, b.element());
				} else if (Tr instanceof Type.Borrow) {
					Type.Borrow b = (Type.Borrow) Tr;
					boolean before = derefBefore(R, Tl, b.lvals());
					boolean after = derefAfter(R, Tl, b.lvals(), b.isMutable());
					return before || after;
				} else {
					return false;
				}
			}

			private boolean derefBefore(Environment R, Type Tl, LVal[] lvals) {
				for (int i = 0; i != lvals.length; ++i) {
					LVal ith = lvals[i];
					if (!derefCoercion(R, Tl, typeOf(R, ith))) {
						return false;
					}
				}
				return true;
			}

			private boolean derefAfter(Environment R, Type Tl, LVal[] lvals, boolean mut) {
				for (int i = 0; i != lvals.length; ++i) {
					LVal ith = lvals[i];
					LVal lv = new LVal(ith.name(),Path.DEREF.append(ith.path()));
					Type T = typeOf(R, ith);
					if (isDerefable(T) && !derefCoercion(R, Tl, new Type.Borrow(mut,lv))) {
						return false;
					}
				}
				return true;
			}

			private boolean isDerefable(Type T) {
				return T instanceof Type.Borrow || T instanceof Type.Box;
			}
		};

		try {
			checker.apply(BorrowChecker.EMPTY_ENVIRONMENT, ProgramSpace.ROOT, b);
			throw new IllegalArgumentException("Program type checks!");
		} catch (SyntaxError e) {
			return e.msg() == DEREF_COERCION_REQUIRED;
		}
	}


	/**
	 * Given a statement block in the calculus, generate a syntactically correct
	 * Rust program.
	 *
	 * @param b
	 * @return
	 */
	public static String toRustProgram(Term.Block b) {
		return "fn main() " + toRustString(b, new HashSet<>());
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
		} else if(expr instanceof Term.Dereference) {
			Term.Dereference d = (Term.Dereference) expr;
			String r = d.operand().toString();
			if(d.copy()) {
				return "*&" + r;
			} else {
				return r;
			}
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
		if (expr instanceof Term.Dereference) {
			// Variable move
			Term.Dereference b = (Term.Dereference) expr;
			if(!b.copy()) {
				liveness.remove(b.operand().name());
			}
		} else if (expr instanceof Term.Box) {
			Term.Box b = (Term.Box) expr;
			updateLiveness(b.operand(), liveness);
		}
	}
}
