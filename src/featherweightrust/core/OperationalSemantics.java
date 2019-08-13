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

import java.util.Set;

import featherweightrust.core.Syntax.Expr;
import featherweightrust.core.Syntax.Lifetime;
import featherweightrust.core.Syntax.Stmt;
import featherweightrust.core.Syntax.Stmt.Block;
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
	public Pair<State, Stmt> reduceAssignment(State S1, Lifetime l, Expr.Variable x, Value v2) {
		// Extract the location being assigned
		Location lx = S1.locate(x.name());
		// Extract value being overwritten
		Value v1 = S1.read(lx);
		// Drop overwritten value (and any owned boxes)
		State S2 = S1.write(lx,null).drop(v1);
		// Perform the assignment
		State S3 = S2.write(lx, v2);
		// Done
		return new Pair<>(S3, null);
	}

	/**
	 * Rule R-Declare.
	 */
	public Pair<State, Stmt> reduceLet(State S1, Lifetime l, Expr.Variable x, Value v) {
		// Allocate new location
		Pair<State, Location> pl = S1.allocate(l, v);
		State S2 = pl.first();
		Location lx = pl.second();
		// Bind variable to location
		State S3 = S2.bind(x.name(), lx);
		// Done
		return new Pair<>(S3, null);
	}

	/**
	 * Rule R-IndAssign.
	 */
	public Pair<State, Stmt> reduceIndirectAssignment(State S1, Lifetime l, Expr.Variable x, Value v) {
		// Extract the location of x
		Location lx = S1.locate(x.name());
		// Extract target location being assigned
		Location ly = (Location) S1.read(lx);
		// Extract value being overwritten
		Value v1 = S1.read(ly);
		// Drop any owned locations
		State S2 = S1.write(ly,null).drop(v1);
		// Perform the indirect assignment
		State S3 = S2.write(ly, v);
		// Done
		return new Pair<>(S3, null);
	}

	/**
	 * Rule R-Deref.
	 */
	public Pair<State, Expr> reduceDereference(State S, Lifetime l, Expr.Variable x) {
		// Extract location, or throw exception otherwise
		Location lx = S.locate(x.name());
		// Read contents of x (which should be location)
		Location ly = (Location) S.read(lx);
		// Read contents of cell at given location
		Value v = S.read(ly);
		//
		return new Pair<>(S, v);
	}

	/**
	 * Rule R-Borrow.
	 */
	public Pair<State, Expr> reduceBorrow(State S, Lifetime l, Expr.Variable x) {
		String name = x.name();
		// Locate operand
		Location lx = S.locate(x.name());
		//
		if (lx == null) {
			throw new RuntimeException("invalid variable \"" + name + "\"");
		}
		// Done
		return new Pair<>(S, lx);
	}

	/**
	 * Rule R-Box.
	 */
	public Pair<State, Expr> reduceBox(State S1, Lifetime l, Value v) {
		Lifetime globalLifetime = l.getRoot();
		// Allocate new location
		Pair<State, Location> pl = S1.allocate(globalLifetime, v);
		State S2 = pl.first();
		Location ln = pl.second();
		// Done
		return new Pair<>(S2, ln);
	}

	/**
	 * Rule R-CopyVar.
	 */
	public Pair<State, Expr> reduceCopy(State S, Lifetime l, Expr.Variable x) {
		// Determine location bound by variable
		Location lx = S.locate(x.name());
		// Read location from store
		return new Pair<>(S, S.read(lx));
	}

	/**
	 * Rule R-MoveVar.
	 */
	public Pair<State, Expr> reduceVariable(State S1, Lifetime l, Expr.Variable x) {
		// Determine location bound by variable
		Location lx = S1.locate(x.name());
		// Read value held by x
		Value v = S1.read(lx);
		// Render location unusable
		State S2 = S1.write(lx, null);
		// Read location from store
		return new Pair<>(S2, v);
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
		final public Pair<State, Stmt> apply(State S1, Lifetime l, Stmt.Assignment s) {
			// Evaluate right hand side operand
			Pair<State, Expr> rhs = apply(S1, l, s.rightOperand());
			State S2 = rhs.first();
			Value v = (Value) rhs.second();
			// Reduce right hand side
			return reduceAssignment(S2, l, s.leftOperand(), v);
		}

		/**
		 * Rule R-Block.
		 */
		@Override
		public Pair<State, Stmt> apply(State S1, Lifetime l, Stmt.Block b) {

			// FIXME: need to figure out how to split this out into R-Seq and R-Block.
			// Currently, cannot do small step because need to chain bindings properly

			// Save current bindings so they can be restored
			StackFrame outerFrame = S1.frame();
			Stmt returnValue = null;
			//
			for (int i = 0; i != b.size(); ++i) {
				Pair<State, Stmt> p = apply(S1, b.lifetime(), b.get(i));
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
			State S2 = new State(outerFrame, S1.store());
			// Identify locations allocated in this lifetime
			Set<Location> phi = S2.findAll(b.lifetime());
			// drop all matching locations
			State S3 = S2.drop(phi);
			//
			return new Pair<>(S3, returnValue);
		}

		@Override
		final public Pair<State, Stmt> apply(State S1, Lifetime l, Stmt.Let s) {
			// Evaluate right hand side operand
			Pair<State, Expr> rhs = apply(S1, l, s.initialiser());
			State S2 = rhs.first();
			Value v = (Value) rhs.second();
			// Reduce right hand side
			return reduceLet(S2, l, s.variable(), v);
		}

		@Override
		final public Pair<State, Stmt> apply(State S1, Lifetime l, Stmt.IndirectAssignment s) {
			// Evaluate right hand side operand
			Pair<State, Expr> rhs = apply(S1, l, s.rightOperand());
			State S2 = rhs.first();
			Value v = (Value) rhs.second();
			// Reduce right hand side
			return reduceIndirectAssignment(S2, l, s.leftOperand(), v);
		}

		@Override
		final public Pair<State, Expr> apply(State S, Lifetime l, Expr.Borrow e) {
			return reduceBorrow(S, l, e.operand());
		}

		@Override
		final public Pair<State, Expr> apply(State S, Lifetime l, Expr.Dereference e) {
			return reduceDereference(S, l, e.operand());
		}

		@Override
		final public Pair<State, Expr> apply(State S1, Lifetime l, Expr.Box e) {
			// Evaluate right hand side operand
			Pair<State, Expr> rhs = apply(S1, l, e.operand());
			State S2 = rhs.first();
			Value v = (Value) rhs.second();
			// Reduce indirect assignment
			return reduceBox(S2, l, v);
		}

		@Override
		final public Pair<State, Expr> apply(State S, Lifetime l, Expr.Variable e) {
			return reduceVariable(S, l, e);
		}

		@Override
		final public Pair<State, Expr> apply(State S, Lifetime l, Expr.Copy e) {
			return reduceCopy(S, l, e.operand());
		}

		@Override
		public Pair<State, Expr> apply(State S, Value.Integer v) {
			return new Pair<>(S, v);
		}

		@Override
		public Pair<State, Expr> apply(State S, Value.Location v) {
			return new Pair<>(S, v);
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
		public Pair<State, Stmt> apply(State state, Lifetime lifetime, Block stmt) {
			throw new IllegalArgumentException("Implement me!");
		}

		@Override
		final public Pair<State, Stmt> apply(State S1, Lifetime l, Stmt.Assignment s) {
			if (s.rightOperand() instanceof Value) {
				// Statement can be completely reduced
				return reduceAssignment(S1, l, s.leftOperand(), (Value) s.rightOperand());
			} else {
				// Statement not ready to be reduced yet
				Pair<State, Expr> rhs = apply(S1, l, s.rightOperand());
				// Construct reduce statement
				s = new Stmt.Assignment(s.leftOperand(), rhs.second(), s.attributes());
				// Done
				return new Pair<>(rhs.first(),s);
			}
		}

		@Override
		final public Pair<State, Stmt> apply(State S1, Lifetime l, Stmt.Let s) {
			if (s.initialiser() instanceof Value) {
				// Statement can be completely reduced
				Value v = (Value) s.initialiser();
				return reduceLet(S1, l, s.variable(), v);
			} else {
				// Statement not ready to be reduced yet
				Pair<State, Expr> rhs = apply(S1, l, s.initialiser());
				// Construct reduce statement
				s = new Stmt.Let(s.variable(), rhs.second(), s.attributes());
				// Done
				return new Pair<>(rhs.first(), s);
			}
		}

		@Override
		final public Pair<State, Stmt> apply(State S1, Lifetime l, Stmt.IndirectAssignment s) {
			if (s.rightOperand() instanceof Value) {
				// Statement can be completely reduced
				return reduceIndirectAssignment(S1, l, s.leftOperand(), (Value) s.rightOperand());
			} else {
				// Statement not ready to be reduced yet
				Pair<State, Expr> rhs = apply(S1, l, s.rightOperand());
				// Construct reduce statement
				s = new Stmt.IndirectAssignment(s.leftOperand(), rhs.second(), s.attributes());
				// Done
				return new Pair<>(rhs.first(),s);
			}
		}

		@Override
		final public Pair<State, Expr> apply(State S, Lifetime l, Expr.Borrow e) {
			return reduceBorrow(S, l, e.operand());
		}

		@Override
		final public Pair<State, Expr> apply(State S, Lifetime l, Expr.Dereference e) {
			return reduceDereference(S, l, e.operand());
		}

		@Override
		final public Pair<State, Expr> apply(State S1, Lifetime l, Expr.Box e) {
			if (e.operand() instanceof Value) {
				// Statement can be completely reduced
				return reduceBox(S1, l, (Value) e.operand());
			} else {
				// Statement not ready to be reduced yet
				Pair<State, Expr> rhs = apply(S1, l, e.operand());
				// Construct reduce statement
				e = new Expr.Box(rhs.second(), e.attributes());
				// Done
				return new Pair<>(rhs.first(), e);
			}
		}

		@Override
		final public Pair<State, Expr> apply(State S, Lifetime l, Expr.Variable e) {
			return reduceVariable(S, l, e);
		}

		@Override
		final public Pair<State, Expr> apply(State S, Lifetime lifetime, Expr.Copy e) {
			return reduceCopy(S, lifetime, e.operand());
		}

		@Override
		public Pair<State, Expr> apply(State S, Value.Integer v) {
			return new Pair<>(S, v);
		}

		@Override
		public Pair<State, Expr> apply(State S, Value.Location v) {
			return new Pair<>(S, v);
		}
	}
}
