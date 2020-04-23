package featherweightrust.extensions;

import featherweightrust.core.BorrowChecker;
import featherweightrust.core.BorrowChecker.Cell;
import featherweightrust.core.BorrowChecker.Environment;
import featherweightrust.core.OperationalSemantics;
import featherweightrust.core.Syntax.Lifetime;
import featherweightrust.core.Syntax.Term;
import featherweightrust.core.Syntax.Term.AbstractTerm;
import featherweightrust.core.Syntax.Type;
import featherweightrust.core.Syntax.Value;
import featherweightrust.util.AbstractMachine.State;
import featherweightrust.util.Pair;
import featherweightrust.util.SyntacticElement;

/**
 * Extensions to the core calculus for simple control-flow constructs (e.g. if-else, while, etc).
 *
 * @author David J. Pearce
 *
 */
public class ControlFlow {
	public final static int TERM_ifelse = 11;
	public final static int TERM_if = 12;
	public final static int TERM_while = 13;
	public final static int TERM_dowhile = 14;

	/**
	 * Extensions to the core syntax of the language.
	 *
	 * @author David J. Pearce
	 *
	 */
	public static class Syntax {
		/**
		 * Represents an if-else statement with some restrictions. Specifically, since
		 * there are no boolean values or comparators in FR, the condition is always an
		 * equality test between two integer constants. We also require that there is
		 * always an else block.
		 *
		 * @author David J. Pearce
		 *
		 */
		public static class IfElse extends AbstractTerm {
			private final Term lhs;
			private final Term rhs;
			private final Term.Block trueBlock;
			private final Term.Block falseBlock;

			public IfElse(Term lhs, Term rhs, Term.Block trueBlock, Term.Block falseBlock, Attribute... attributes) {
				super(TERM_ifelse, attributes);
				this.lhs = lhs;
				this.rhs = rhs;
				this.trueBlock = trueBlock;
				this.falseBlock = falseBlock;
			}

			/**
			 * Get the left-hand side of the equality test representing the condition.
			 *
			 * @return
			 */
			public Term getLeftHandSide() {
				return lhs;
			}

			/**
			 * Get the right-hand side of the equality test representing the condition.
			 *
			 * @return
			 */
			public Term getRightHandSide() {
				return rhs;
			}

			/**
			 * Get the true block for this statement.
			 *
			 * @return
			 */
			public Term.Block getTrueBlock() {
				return trueBlock;
			}

			/**
			 * Get the false block for this statement.
			 *
			 * @return
			 */
			public Term.Block getFalseBlock() {
				return falseBlock;
			}

			@Override
			public String toString() {
				return "if " + lhs + "==" + rhs + " " + trueBlock + " else " + falseBlock;
			}
		}
	}

	public static class Semantics extends OperationalSemantics.Extension {

		@Override
		public Pair<State, Term> apply(State S, Lifetime l, Term term) {
			if(term instanceof Syntax.IfElse) {
				return apply(S,l,((Syntax.IfElse)term));
			} else {
				return null;
			}
		}

		private Pair<State, Term> apply(State S, Lifetime l, Syntax.IfElse t1) {
			Term lhs = t1.getLeftHandSide();
			Term rhs = t1.getRightHandSide();
			//
			if (lhs instanceof Value && rhs instanceof Value) {
				// Both lhs and rhs fully reduced
				if (lhs.equals(rhs)) {
					return new Pair<>(S, t1.getTrueBlock());
				} else {
					return new Pair<>(S, t1.getFalseBlock());
				}
			} else if (lhs instanceof Value) {
				// lhs is fully reduced
				Pair<State, Term> p = self.apply(S, l, rhs);
				State S2 = p.first();
				Term t2 = new Syntax.IfElse(lhs, p.second(), t1.getTrueBlock(), t1.getFalseBlock(), t1.attributes());
				//
				return new Pair<State, Term>(S2, t2);
			} else {
				// lhs not fully reduced
				Pair<State, Term> p = self.apply(S, l, lhs);
				State S2 = p.first();
				Term t2 = new Syntax.IfElse(p.second(), rhs, t1.getTrueBlock(), t1.getFalseBlock(), t1.attributes());
				//
				return new Pair<State, Term>(S2, t2);
			}
		}
	}

	public static class Typing extends BorrowChecker.Extension {

		@Override
		public Pair<Environment, Type> apply(Environment state, Lifetime lifetime, Term term) {
			if (term instanceof Syntax.IfElse) {
				return apply(state, lifetime, (Syntax.IfElse) term);
			} else {
				return null;
			}
		}

		private Pair<Environment, Type> apply(Environment R1, Lifetime l, Syntax.IfElse t) {
			// Type left-hand side
			Pair<Environment, Type> pLhs = self.apply(R1, l, t.getLeftHandSide());
			Environment R2 = pLhs.first();
			Type Tx = pLhs.second();
			// Type right-hand side
			Pair<Environment, Type> pRhs = self.apply(R2, l, t.getRightHandSide());
			Environment R3 = pRhs.first();
			Type Ty = pRhs.second();
			// Check operands are compatible
			self.check(self.compatible(R2, Tx, Ty), BorrowChecker.INCOMPATIBLE_TYPE, t);
			// Type true and false blocks
			Pair<Environment, Type> pTrue = self.apply(R3, l, t.getTrueBlock());
			Pair<Environment, Type> pFalse = self.apply(R3, l, t.getFalseBlock());
			// Join results
			return join(pTrue, pFalse, t);
		}

		private Pair<Environment,Type> join(Pair<Environment, Type> lhs, Pair<Environment, Type> rhs, SyntacticElement e) {
			// FIXME: this is just a hack for now.
			self.check(lhs.equals(rhs),"environments cannot be joing",e);
			// Done
			return lhs;
		}
	}
}
