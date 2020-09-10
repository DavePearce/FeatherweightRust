// This file is part of the FeatherweightRust Compiler (frc).
//
// The FeatherweightRust Compiler is free software; you can redistribute
// it and/or modify it under the terms of the GNU General Public
// License as published by the Free Software Foundation; either
// version 3 of the License, or (at your option) any later version.
//
// The WhileLang Compiler is distributed in the hope that it
// will be useful, but WITHOUT ANY WARRANTY; without even the
// implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR
// PURPOSE. See the GNU General Public License for more details.
//
// You should have received a copy of the GNU General Public
// License along with the WhileLang Compiler. If not, see
// <http://www.gnu.org/licenses/>
//
// Copyright 2018, David James Pearce.
package featherweightrust.extensions;

import java.util.Set;

import featherweightrust.core.BorrowChecker;
import featherweightrust.core.BorrowChecker.Slot;
import featherweightrust.core.BorrowChecker.Environment;
import featherweightrust.core.OperationalSemantics;
import featherweightrust.core.Syntax.LVal;
import featherweightrust.core.Syntax.Lifetime;
import featherweightrust.core.Syntax.Path;
import featherweightrust.core.Syntax.Term;
import featherweightrust.core.Syntax.Term.AbstractTerm;
import featherweightrust.extensions.Tuples.Typing;
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
	public final static int TERM_ifelse = 20;
	public final static int TERM_if = 21;
	public final static int TERM_while = 22;
	public final static int TERM_dowhile = 23;
	/**
	 * Indicates we're attempt to join two environments with differing key sets.
	 */
	private final static String INVALID_ENVIRONMENT_KEYS = "invalid environment keys";
	/**
	 * Indicates we're attempt to join two environments with incompatible cells
	 */
	private final static String INVALID_ENVIRONMENT_CELLS = "invalid environment cells (lifetime)";

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
		public static class IfElse extends AbstractTerm implements Term.Compound {
			private final boolean eq;
			private final Term lhs;
			private final Term rhs;
			private final Term.Block trueBlock;
			private final Term.Block falseBlock;

			public IfElse(boolean eq, Term lhs, Term rhs, Term.Block trueBlock, Term.Block falseBlock, Attribute... attributes) {
				super(TERM_ifelse, attributes);
				this.eq = eq;
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
			 * Determine whether condition is equality (<code>==</code>) or inequality
			 * (<code>!=</code>).
			 *
			 * @return
			 */
			public boolean condition() {
				return eq;
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

	public static class Semantics extends OperationalSemantics {

		@Override
		public Pair<State, Term> apply(State S, Lifetime l, Term term) {
			if(term instanceof Syntax.IfElse) {
				return apply(S,l,((Syntax.IfElse)term));
			} else {
				return super.apply(S, l, term);
			}
		}

		@Override
		final protected Pair<State, Term> apply(State S, Lifetime l, Term.Access e) {
			if(e.copy() || e.temporary()) {
				return reduceCopy(S, l, e.operand());
			} else {
				return reduceMove(S, l, e.operand());
			}
		}

		private Pair<State, Term> apply(State S1, Lifetime l, Syntax.IfElse t1) {
			final Term lhs = t1.leftHandSide();
			final Term rhs = t1.rightHandSide();
			//
			if (lhs instanceof Value && rhs instanceof Value) {
				boolean eq = lhs.equals(rhs);
				// Both lhs and rhs fully reduced
				if (t1.condition() == eq) {
					return new Pair<>(S1, t1.trueBlock());
				} else {
					return new Pair<>(S1, t1.falseBlock());
				}
			} else if (lhs instanceof Value) {
				// Statement not ready to be reduced yet
				Pair<State, Term> p = apply(S1, l, rhs);
				State S2 = p.first();
				Term _rhs = p.second();
				// Construct term reduced by one step
				Term t2 = new Syntax.IfElse(t1.eq, lhs, _rhs, t1.trueBlock(), t1.falseBlock(), t1.attributes());
				// Done
				return new Pair<>(S2, t2);
			} else {
				// Statement not ready to be reduced yet
				Pair<State, Term> p = apply(S1, l, lhs);
				State S2 = p.first();
				Term _lhs = p.second();
				// Construct term reduced by one step
				Term t2 = new Syntax.IfElse(t1.eq, _lhs, rhs, t1.trueBlock(), t1.falseBlock(), t1.attributes());
				// Done
				return new Pair<>(S2, t2);
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
			final String v = BorrowChecker.fresh();
			// Type left-hand side
			Pair<Environment,Type> p1 = self.apply(R1, l, t.leftHandSide());
			Environment R2 = p1.first();
			Type Tx = p1.second();
			// Type right-hand side
			Pair<Environment,Type> p2 = self.apply(R2.put(v, Tx, l), l, t.rightHandSide());
			Environment R3 = p2.first();
			Type Ty = p2.second();
			// Remove temporary
			Environment R4 = R3.remove(v);
			// Check operands are compatible
			self.check(self.compatible(R4, Tx, Ty), BorrowChecker.INCOMPATIBLE_TYPE, t);
			self.check(Tx.copyable(), BorrowChecker.LVAL_NOT_COPY, t.leftHandSide());
			self.check(Ty.copyable(), BorrowChecker.LVAL_NOT_COPY, t.rightHandSide());
			// Type true and false blocks
			Pair<Environment, Type> pTrue = self.apply(R4, l, t.trueBlock());
			Pair<Environment, Type> pFalse = self.apply(R4, l, t.falseBlock());
			// Destructure pairs
			Environment R5 = pTrue.first();
			Environment R6 = pFalse.first();
			Type T2 = pTrue.second();
			Type T3 = pFalse.second();
			//
			Environment R7 = join(R5, R6, t);
			// Check return types are compatible
			self.check(self.compatible(R7, T2, T3), BorrowChecker.INCOMPATIBLE_TYPE, t);
			// Join environment and types from both branches
			return new Pair<>(R7, T2.union(T3));
		}

		private Environment join(Environment lhs, Environment rhs, SyntacticElement e) {
			Set<String> lhsBindings = lhs.bindings();
			Set<String> rhsBindings = rhs.bindings();
			// When joining environments, should always have same number of keys.
			self.check(lhsBindings.equals(rhsBindings), INVALID_ENVIRONMENT_KEYS, e);
			// Join all keys
			for(String key : lhsBindings) {
				Slot Cl = lhs.get(key);
				Slot Cr = rhs.get(key);
				// Sanity check lifetimes match
				self.check(Cl.lifetime().equals(Cr.lifetime()), INVALID_ENVIRONMENT_CELLS, e);
				// Determine joined type
				Type type = Cl.type().union(Cr.type());
				// Done
				lhs = lhs.put(key, type, Cl.lifetime());
			}
			return lhs;
		}
	}

	public static class Checker extends BorrowChecker {

		public Checker(boolean copyInference, String sourcefile) {
			super(copyInference, sourcefile, TYPING);
		}

		@Override
		public Pair<Environment, Type> apply(Environment R, Lifetime l, Term t) {
			// NOTE: this is a bit sneaky as we need to intercept temporary dereference
			// operations only.
			if (t instanceof Term.Access) {
				Term.Access d = (Term.Access) t;
				if (d.temporary()) {
					return readTemporary(R,d.operand());
				}
			}
			return super.apply(R, l, t);
		}

		/**
		 * Read the value of a given lval from the environment, leading to a potentially
		 * updated environment. For example, if we are reading a type which is not copy,
		 * then it is moved and this must be reflected in the updated environment.
		 *
		 * @param R  The environment in which the read is occuring.
		 * @param lv The lval being read.
		 * @return
		 */
		public Pair<Environment, Type> readTemporary(Environment R, LVal lv) {
			final String x = lv.name();
			// Extract target cell
			Slot Cx = R.get(x);
			check(Cx != null, BorrowChecker.UNDECLARED_VARIABLE, lv);
			// Determine type being read
			Pair<Type,Lifetime> p = lv.typeOf(R);
			// Sanity check type
			check(p != null, LVAL_INVALID, lv);
			// Extract type
			Type T2 = p.first();
			// Sanity check type is moveable
			check(T2.defined(), LVAL_MOVED, lv);
			// Check variable readable (e.g. not mutably borrowed)
			check(!readProhibited(R, lv), LVAL_READ_PROHIBITED, lv);
			// Done
			return new Pair<>(R,T2);
		}
	}

	public static final BorrowChecker.Extension TYPING = new Typing();
	public static final OperationalSemantics SEMANTICS = new Semantics();
}
