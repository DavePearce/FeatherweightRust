package featherweightrust.testing.experiments;

import featherweightrust.core.BorrowChecker;
import featherweightrust.core.ProgramSpace;
import featherweightrust.core.Syntax.LVal;
import featherweightrust.core.Syntax.Term;
import featherweightrust.core.Syntax.Type;
import featherweightrust.util.SyntaxError;

public class Algorithms {
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
	public static boolean failsWithoutDerefCoercion(Term.Block b) {
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
					LVal[] lvals = b.lvals();
					for (int i = 0; i != lvals.length; ++i) {
						LVal ith = lvals[i];
						if (!derefCoercion(R, Tl, typeOf(R, ith))) {
							return false;
						}
					}
					return true;
				} else {
					return false;
				}
			}
		};

		try {
			checker.apply(BorrowChecker.EMPTY_ENVIRONMENT, ProgramSpace.ROOT, b);
			throw new IllegalArgumentException("Program type checks!");
		} catch (SyntaxError e) {
			return e.msg() == DEREF_COERCION_REQUIRED;
		}
	}
}
