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
package featherweightrust.core;

import featherweightrust.core.Syntax.Expr;
import featherweightrust.core.Syntax.Lifetime;
import featherweightrust.core.Syntax.Stmt;
import featherweightrust.core.Syntax.Value;
import featherweightrust.core.Syntax.Value.Location;
import featherweightrust.util.AbstractSemantics;
import featherweightrust.util.Pair;

/**
 * Encodes the core operational semantics of Featherweight Rust. That is, the
 * individual reduction rules without committing to small-step or big-step
 * semantics. Each of these rules corresponds to a rule in the paper, and an
 * implementation for both big-step and small-step semantics is provided.
 *
 * @author David J. Pearce
 *
 */
public abstract class OperationalSemantics extends AbstractSemantics {
	/**
	 * Rule R-Assign.
	 */
	public Pair<State, Stmt> reduceAssignment(State S1, Lifetime lifetime, Expr.Variable x, Value v2) {
		// Extract the location being assigned
		Location l = S1.locate(x.name());
		// Extract value being overwritten
		Value v1 = S1.read(l);
		// Drop overwritten value
		State S2 = S1.drop(v1);
		// Perform the assignment
		State S3 = S2.write(l, v2);
		// Done
		return new Pair<>(S3, null);
	}

	/**
	 * Rule R-Declare.
	 */
	public Pair<State, Stmt> reduceLet(State S1, Lifetime lifetime, Expr.Variable x, Value v) {
		// Allocate new location
		Pair<State, Location> pl = S1.allocate(lifetime, v);
		State S2 = pl.first();
		Location l = pl.second();
		// Bind variable to location
		State S3 = S2.bind(x.name(), l);
		// Done
		return new Pair<>(S3, null);
	}

	/**
	 * Rule R-IndAssign.
	 */
	public Pair<State, Stmt> reduceIndirectAssignment(State S1, Lifetime lifetime, Expr.Variable x, Value v2) {
		// Extract the location being assigned
		Location l = (Location) S1.read(S1.locate(x.name()));
		// Extract value being overwritten
		Value v1 = S1.read(l);
		// Drop owned locations
		State S2 = S1.drop(v1);
		// Perform the indirect assignment
		State S3 = S2.write(l, v2);
		// Done
		return new Pair<>(S3, null);
	}

	/**
	 * Rule R-Deref.
	 */
	public Pair<State, Expr> reduceDereference(State S1, Lifetime lifetime, Value v) {
		// Extract location, or throw exception otherwise
		Location l = (Location) v;
		// Read contents of cell at given location
		return new Pair<>(S1, S1.read(l));
	}

	/**
	 * Rule R-Borrow.
	 */
	public Pair<State, Expr> reduceBorrow(State state, Lifetime lifetime, Expr.Variable x) {
		String name = x.name();
		// Locate operand
		Location loc = state.locate(x.name());
		//
		if (loc == null) {
			throw new RuntimeException("invalid variable \"" + name + "\"");
		}
		// Strip ownership flag since is a borrow
		loc = new Location(loc.getAddress());
		// Done
		return new Pair<>(state, loc);
	}

	/**
	 * Rule R-Box.
	 */
	public Pair<State, Expr> reduceBox(State S1, Lifetime lifetime, Value v) {
		Lifetime globalLifetime = lifetime.getRoot();
		// Allocate new location
		Pair<State, Location> pl = S1.allocate(globalLifetime, v);
		State S2 = pl.first();
		Location l = pl.second();
		// Done
		return new Pair<>(S2, l);
	}

	/**
	 * Rule R-Var.
	 */
	public Pair<State, Expr> reduceVariable(State state, Lifetime lifetime, Expr.Variable x) {
		// Determine location bound by variable
		Location loc = state.locate(x.name());
		// Read location from store
		return new Pair<>(state, state.read(loc));
	}

	/**
	 * Implementation of big step semantics for Featherweight Rust. This recursively
	 * reduces expressions and statements immediately. Thus, each step can consist
	 * of multiple applications of the operational semantic rules above (hence why
	 * it's called "big step").
	 *
	 * @author David J. Pearce
	 *
	 */
	public static class BigStep extends OperationalSemantics {

		@Override
		final public Pair<State, Stmt> apply(State S1, Lifetime lifetime, Stmt.Assignment stmt) {
			// Evaluate right hand side operand
			Pair<State, Expr> rhs = apply(S1, lifetime, stmt.rightOperand());
			State S2 = rhs.first();
			Value v = (Value) rhs.second();
			// Reduce right hand side
			return reduceAssignment(S2, lifetime, stmt.leftOperand(), v);
		}

		/**
		 * Rule R-Block.
		 */
		@Override
		public Pair<State, Stmt> apply(State S1, Lifetime lifetime, Stmt.Block stmt) {

			// FIXME: need to figure out how to split this out into R-Seq and R-Block.
			// Currently, cannot do small step because need to chain bindings properly

			// Save current bindings so they can be restored
			StackFrame outerFrame = S1.frame();
			Stmt returnValue = null;
			//
			for (int i = 0; i != stmt.size(); ++i) {
				Pair<State, Stmt> p = apply(S1, stmt.lifetime(), stmt.get(i));
				Stmt s = p.second();
				S1 = p.first();
				//
				if(s != null || s instanceof Value) {
					// Either we're stuck, or we produced a return value.
					returnValue = s;
					break;
				}
			}
			// drop all bindings created within block
			S1 = new State(outerFrame, S1.store());
			// drop all allocated locations
			S1 = S1.drop(stmt.lifetime());
			//
			return new Pair<>(S1, returnValue);
		}

		@Override
		final public Pair<State, Stmt> apply(State S1, Lifetime lifetime, Stmt.Let stmt) {
			// Evaluate right hand side operand
			Pair<State, Expr> rhs = apply(S1, lifetime, stmt.initialiser());
			State S2 = rhs.first();
			Value v = (Value) rhs.second();
			// Reduce right hand side
			return reduceLet(S2, lifetime, stmt.variable(), v);
		}

		@Override
		final public Pair<State, Stmt> apply(State S1, Lifetime lifetime, Stmt.IndirectAssignment stmt) {
			// Evaluate right hand side operand
			Pair<State, Expr> rhs = apply(S1, lifetime, stmt.rightOperand());
			State S2 = rhs.first();
			Value v = (Value) rhs.second();
			// Reduce right hand side
			return reduceIndirectAssignment(S2, lifetime, stmt.leftOperand(), v);
		}

		@Override
		final public Pair<State, Expr> apply(State state, Lifetime lifetime, Expr.Borrow expr) {
			return reduceBorrow(state, lifetime, expr.operand());
		}

		@Override
		final public Pair<State, Expr> apply(State S1, Lifetime lifetime, Expr.Dereference e) {
			// Evaluate right hand side operand
			Pair<State, Expr> rhs = apply(S1, lifetime, e.operand());
			State S2 = rhs.first();
			Value v = (Value) rhs.second();
			// Reduce indirect assignment
			return reduceDereference(S2, lifetime, v);
		}

		@Override
		final public Pair<State, Expr> apply(State S1, Lifetime lifetime, Expr.Box e) {
			// Evaluate right hand side operand
			Pair<State, Expr> rhs = apply(S1, lifetime, e.operand());
			State S2 = rhs.first();
			Value v = (Value) rhs.second();
			// Reduce indirect assignment
			return reduceBox(S2, lifetime, v);
		}

		@Override
		final public Pair<State, Expr> apply(State S1, Lifetime lifetime, Expr.Variable e) {
			return reduceVariable(S1, lifetime, e);
		}

		@Override
		public Pair<State, Expr> apply(State state, Value.Integer value) {
			return new Pair<>(state, value);
		}

		@Override
		public Pair<State, Expr> apply(State state, Value.Location value) {
			return new Pair<>(state, value);
		}
	}


	/**
	 * Implementation of small step semantics for Featherweight Rust. This reduces
	 * expressions and statements step-by-step. Thus, each step consists of exactly
	 * one application of the operational semantic rules above (hence why it's
	 * called "small step").
	 *
	 * @author David J. Pearce
	 *
	 */
	public static class SmallStep extends OperationalSemantics {

		@Override
		final public Pair<State, Stmt> apply(State S1, Lifetime lifetime, Stmt.Assignment stmt) {
			if (stmt.rightOperand() instanceof Value) {
				// Statement can be completely reduced
				return reduceAssignment(S1, lifetime, stmt.leftOperand(), (Value) stmt.rightOperand());
			} else {
				// Statement not ready to be reduced yet
				Pair<State, Expr> rhs = apply(S1, lifetime, stmt.rightOperand());
				// Construct reduce statement
				stmt = new Stmt.Assignment(stmt.leftOperand(), rhs.second(), stmt.attributes());
				// Done
				return new Pair<>(rhs.first(),stmt);
			}
		}

		@Override
		final public Pair<State, Stmt> apply(State S1, Lifetime lifetime, Stmt.Let stmt) {
			if (stmt.initialiser() instanceof Value) {
				// Statement can be completely reduced
				Value v = (Value) stmt.initialiser();
				return reduceLet(S1, lifetime, stmt.variable(), v);
			} else {
				// Statement not ready to be reduced yet
				Pair<State, Expr> rhs = apply(S1, lifetime, stmt.initialiser());
				// Construct reduce statement
				stmt = new Stmt.Let(stmt.variable(), rhs.second(), stmt.attributes());
				// Done
				return new Pair<>(rhs.first(), stmt);
			}
		}

		@Override
		final public Pair<State, Stmt> apply(State S1, Lifetime lifetime, Stmt.IndirectAssignment stmt) {
			if (stmt.rightOperand() instanceof Value) {
				// Statement can be completely reduced
				return reduceIndirectAssignment(S1, lifetime, stmt.leftOperand(), (Value) stmt.rightOperand());
			} else {
				// Statement not ready to be reduced yet
				Pair<State, Expr> rhs = apply(S1, lifetime, stmt.rightOperand());
				// Construct reduce statement
				stmt = new Stmt.IndirectAssignment(stmt.leftOperand(), rhs.second(), stmt.attributes());
				// Done
				return new Pair<>(rhs.first(),stmt);
			}
		}

		@Override
		final public Pair<State, Expr> apply(State state, Lifetime lifetime, Expr.Borrow expr) {
			return reduceBorrow(state, lifetime, expr.operand());
		}

		@Override
		final public Pair<State, Expr> apply(State S1, Lifetime lifetime, Expr.Dereference e) {
			if (e.operand() instanceof Value) {
				// Statement can be completely reduced
				return reduceDereference(S1, lifetime, (Value) e.operand());
			} else {
				// Statement not ready to be reduced yet
				Pair<State, Expr> rhs = apply(S1, lifetime, e.operand());
				// Construct reduce statement
				e = new Expr.Dereference(rhs.second(), e.attributes());
				// Done
				return new Pair<>(rhs.first(), e);
			}
		}

		@Override
		final public Pair<State, Expr> apply(State S1, Lifetime lifetime, Expr.Box e) {
			if (e.operand() instanceof Value) {
				// Statement can be completely reduced
				return reduceBox(S1, lifetime, (Value) e.operand());
			} else {
				// Statement not ready to be reduced yet
				Pair<State, Expr> rhs = apply(S1, lifetime, e.operand());
				// Construct reduce statement
				e = new Expr.Box(rhs.second(), e.attributes());
				// Done
				return new Pair<>(rhs.first(), e);
			}
		}

		@Override
		final public Pair<State, Expr> apply(State S1, Lifetime lifetime, Expr.Variable e) {
			return reduceVariable(S1, lifetime, e);
		}

		@Override
		public Pair<State, Expr> apply(State state, Value.Integer value) {
			return new Pair<>(state, value);
		}

		@Override
		public Pair<State, Expr> apply(State state, Value.Location value) {
			return new Pair<>(state, value);
		}
	}
}
