package featherweightrust.extensions;

import java.util.Set;

import featherweightrust.core.BorrowChecker;
import featherweightrust.core.BorrowChecker.Cell;
import featherweightrust.core.BorrowChecker.Environment;
import featherweightrust.core.OperationalSemantics;
import featherweightrust.core.Syntax.Lifetime;
import featherweightrust.core.Syntax.Term;
import featherweightrust.core.Syntax.Term.AbstractTerm;
import featherweightrust.core.Syntax.Value.Location;
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
	 * Indicates we're attempt to join two environments with differing key sets.
	 */
	private final static String INVALID_ENVIRONMENT_KEYS = "invalid environment keys";
	/**
	 * Indicates we're attempt to join two environments with incompatible cells
	 */
	private final static String INVALID_ENVIRONMENT_CELLS = "invalid environment cells (lifetime)";
	/**
	 * Indicates we're attempt to join two environments with incompatible cells
	 */
	private final static String INVALID_ENVIRONMENT_TYPES = "invalid environment cells (type)";

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
			public Term leftHandSide() {
				return lhs;
			}

			/**
			 * Get the right-hand side of the equality test representing the condition.
			 *
			 * @return
			 */
			public Term rightHandSide() {
				return rhs;
			}

			/**
			 * Get the true block for this statement.
			 *
			 * @return
			 */
			public Term.Block trueBlock() {
				return trueBlock;
			}

			/**
			 * Get the false block for this statement.
			 *
			 * @return
			 */
			public Term.Block falseBlock() {
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
			Term lhs = t1.leftHandSide();
			Term rhs = t1.rightHandSide();
			//
			if (lhs instanceof Value && rhs instanceof Value) {
				// Both lhs and rhs fully reduced
				if (lhs.equals(rhs)) {
					return new Pair<>(S, t1.trueBlock());
				} else {
					return new Pair<>(S, t1.falseBlock());
				}
			} else if (lhs instanceof Value) {
				// lhs fully reduced but rhs not, so reduce it
				Term.Variable rv = (Term.Variable) rhs;
				// Read lhs from store
				Value v = S.read(S.locate(rv.name()));
				// Read location from store
				Term t2 = new Syntax.IfElse(lhs, v, t1.trueBlock(), t1.falseBlock(), t1.attributes());
				// Done
				return new Pair<State, Term>(S, t2);
			} else {
				// lhs not fully reduced, so reduce it
				Term.Variable lv = (Term.Variable) lhs;
				// Read lhs from store
				Value v = S.read(S.locate(lv.name()));
				// Construct reduced term
				Term t2 = new Syntax.IfElse(v, rhs, t1.trueBlock(), t1.falseBlock(), t1.attributes());
				// Done
				return new Pair<State, Term>(S, t2);
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
			Type Tx = self.apply(R1, l, t.leftHandSide()).second();
			// Type right-hand side
			Type Ty = self.apply(R1, l, t.rightHandSide()).second();
			// Check operands are compatible
			self.check(self.compatible(R1, Tx, R1, Ty), BorrowChecker.INCOMPATIBLE_TYPE, t);
			// Type true and false blocks
			Pair<Environment, Type> pTrue = self.apply(R1, l, t.trueBlock());
			Pair<Environment, Type> pFalse = self.apply(R1, l, t.falseBlock());
			// Join results
			return join(pTrue, pFalse, t);
		}

		private Pair<Environment,Type> join(Pair<Environment, Type> lhs, Pair<Environment, Type> rhs, SyntacticElement e) {
			// Join environments
			Environment env = join(lhs.first(),rhs.first(),e);
			// Join result types
			Type type = join(env, lhs.second(), env, rhs.second(), e);
			// Done
			return new Pair<>(env,type);
		}

		private Environment join(Environment lhs, Environment rhs, SyntacticElement e) {
			Set<String> lhsBindings = lhs.bindings();
			Set<String> rhsBindings = rhs.bindings();
			// When joining environments, should always have same number of keys.
			self.check(lhsBindings.equals(rhsBindings), INVALID_ENVIRONMENT_KEYS, e);
			// Join all keys
			for(String key : lhsBindings) {
				Cell Cl = lhs.get(key);
				Cell Cr = rhs.get(key);
				// Sanity check lifetimes match
				self.check(Cl.lifetime().equals(Cr.lifetime()), INVALID_ENVIRONMENT_CELLS, e);
				// Check types are compatible
				self.check(self.compatible(lhs, Cl.type(), rhs, Cr.type()), BorrowChecker.INCOMPATIBLE_TYPE, e);
				// Determine joined type
				Type type = join(lhs, Cl.type(), rhs, Cr.type(), e);
				// Determine joined effect
				boolean moved = join(Cl.moved(),Cr.moved());
				// Done
				lhs = lhs.put(key, type, Cl.lifetime());
				//
				if(moved) {
					// FIXME: this is ugly
					lhs = lhs.move(key);
				}
			}
			return lhs;
		}

		private Type join(Environment R1, Type lhs, Environment R2, Type rhs, SyntacticElement e) {
			if (lhs instanceof Type.Int && rhs instanceof Type.Int) {
				return lhs;
			} else if (lhs instanceof Type.Borrow && rhs instanceof Type.Borrow) {
				Type.Borrow b1 = (Type.Borrow) lhs;
				Type.Borrow b2 = (Type.Borrow) rhs;
				Cell c1 = R1.get(b1.name());
				Cell c2 = R2.get(b2.name());
				Type t = join(R1, c1.type(), R2, c2.type(), e);
				boolean mut = b1.isMutable() || b2.isMutable();
				self.check(b1.name().equals(b2.name()), "cannot join names!", e);
				return new Type.Borrow(mut, b1.name());
			} else if (lhs instanceof Type.Box && rhs instanceof Type.Box) {
				Type.Box b1 = (Type.Box) lhs;
				Type.Box b2 = (Type.Box) rhs;
				return join(R1, b1.element(), R2, b2.element(), e);
			} else {
				throw new IllegalArgumentException("types are not compatible (" + lhs + " : " + rhs);
			}
		}

		private boolean join(boolean lhs, boolean rhs) {
			return lhs || rhs;
		}
	}
}
