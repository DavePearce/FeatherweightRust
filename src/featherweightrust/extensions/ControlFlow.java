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
		public static class IfElse extends AbstractTerm {
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

	public static class Semantics extends OperationalSemantics.Extension {

		@Override
		public Pair<State, Term> apply(State S, Lifetime l, Term term) {
			if(term instanceof Syntax.IfElse) {
				return apply(S,l,((Syntax.IfElse)term));
			} else {
				return null;
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
				Pair<State, Term> p = self.apply(S1, l, rhs);
				State S2 = p.first();
				Term _rhs = p.second();
				// Construct term reduced by one step
				Term t2 = new Syntax.IfElse(t1.eq, lhs, _rhs, t1.trueBlock(), t1.falseBlock(), t1.attributes());
				// Done
				return new Pair<State, Term>(S2, t2);
			} else {
				// Statement not ready to be reduced yet
				Pair<State, Term> p = self.apply(S1, l, lhs);
				State S2 = p.first();
				Term _lhs = p.second();
				// Construct term reduced by one step
				Term t2 = new Syntax.IfElse(t1.eq, _lhs, rhs, t1.trueBlock(), t1.falseBlock(), t1.attributes());
				// Done
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
			Type Tx = self.apply(R1, l, t.leftHandSide()).second();
			// Type right-hand side
			Type Ty = self.apply(R1, l, t.rightHandSide()).second();
			// Check operands are compatible
			self.check(self.compatible(R1, Tx, Ty, R1), BorrowChecker.INCOMPATIBLE_TYPE, t);
			// Type true and false blocks
			Pair<Environment, Type> pTrue = self.apply(R1, l, t.trueBlock());
			Pair<Environment, Type> pFalse = self.apply(R1, l, t.falseBlock());
			// Destructure pairs
			Environment R2 = pTrue.first();
			Environment R3 = pFalse.first();
			Type T2 = pTrue.second();
			Type T3 = pFalse.second();
			// Check return types are compatible
			self.check(self.compatible(R2, T2, T3, R3), BorrowChecker.INCOMPATIBLE_TYPE, t);
			// Join environment and types from both branches
			return new Pair<>(join(R2, R3, t), T2.union(T3));
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
				self.check(self.compatible(lhs, Cl.type(), Cr.type(), rhs), BorrowChecker.INCOMPATIBLE_TYPE, e);
				// Determine joined type
				Type type = Cl.type().union(Cr.type());
				// Done
				lhs = lhs.put(key, type, Cl.lifetime());
			}
			return lhs;
		}
	}
}
