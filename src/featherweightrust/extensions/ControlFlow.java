package featherweightrust.extensions;

import featherweightrust.core.Syntax.Lifetime;
import featherweightrust.core.Syntax.Term;
import featherweightrust.core.Syntax.Term.AbstractTerm;
import featherweightrust.core.Syntax.Value;
import featherweightrust.util.AbstractMachine.State;
import featherweightrust.util.Pair;

/**
 * Extensions to the core calculus for simple control-flow constructs (e.g. if-else, while, etc).
 *
 * @author David J. Pearce
 *
 */
public class ControlFlow {
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

			public IfElse(Term lhs, Term rhs, Term.Block trueBlock, Term.Block falseBlock,
					Attribute... attributes) {
				super(attributes);
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
				return "if(" + lhs + "==" + rhs + ")" + trueBlock + "else" + falseBlock;
			}
		}
	}

	public static class Semantics {
		public Pair<State, Term> apply(State S, Lifetime l, Syntax.IfElse s) {
			Term lhs = s.getLeftHandSide();
			Term rhs = s.getRightHandSide();
			//
			if (lhs instanceof Value && rhs instanceof Value) {
				// Both lhs and rhs fully reduced
				if(lhs.equals(rhs)) {
					return new Pair<>(S,s.getTrueBlock());
				} else {
					return new Pair<>(S,s.getFalseBlock());
				}
			} else if(lhs instanceof Value) {
				// lhs is fully reduced
				Pair<State, Term> r = apply(S, l, rhs);
				State S2 = r.first();
				Term s2 = new Syntax.IfElse(lhs,r.second(),s.getTrueBlock(),s.getFalseBlock(),s.attributes());
				//
				return new Pair<State,Term>(S2,s);
			} else {
				// lhs not fully reduced
				Pair<State, Term> l = apply(S, l, lhs);
				State S2 = l.first();
				Term s2 = new Syntax.IfElse(l.second(),lhs,s.getTrueBlock(),s.getFalseBlock(),s.attributes());
				//
				return new Pair<State,Term>(S2,s);
			}
		}
	}

	public static class Typing {

	}
}
