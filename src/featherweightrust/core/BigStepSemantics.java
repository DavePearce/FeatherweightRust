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
import featherweightrust.core.Syntax.Stmt;
import featherweightrust.core.Syntax.Value;
import featherweightrust.core.Syntax.Value.Location;
import featherweightrust.util.AbstractSemantics;
import featherweightrust.util.Pair;

/**
 * Encodes the operational semantics of Rust using a recursive decomposition of
 * evaluation functions.
 *
 * @author djp
 *
 */
public class BigStepSemantics extends AbstractSemantics {


	/**
	 * Rule R-Assign.
	 */
	@Override
	public Pair<State, Stmt> apply(State state, String lifetime, Stmt.Assignment stmt) {
		// Extract variable being assigned
		Expr.Variable lhs = stmt.leftOperand();
		// Evaluate right hand side operand
		Pair<State, Expr> rhs = apply(state, stmt.rightOperand());
		// Extract the location being assigned
		Location loc = state.locate(lhs.name());
		// Perform the assignment
		state = rhs.first().write(loc, (Value) rhs.second());
		// Done
		return new Pair<>(state, null);
	}

	/**
	 * Rule R-Block.
	 */
	@Override
	public Pair<State, Stmt> apply(State state, String lifetime, Stmt.Block stmt) {
		// Save current bindings so they can be restored
		StackFrame originalBindings = state.frame();
		//
		for (int i = 0; i != stmt.size(); ++i) {
			Pair<State, Stmt> p = apply(state, stmt.lifetime(), stmt.get(i));
			Stmt s = p.second();
			state = p.first();
			//
			if(s != null || s instanceof Value) {
				// Either we're stuck, or we produced a return value.
				return new Pair<>(state,s);
			}
		}
		// drop all bindings created within block
		state = new State(originalBindings, state.store());
		// drop all allocated locations
		state = state.drop(stmt.lifetime());
		//
		return new Pair<>(state, null);
	}

	/**
	 * Rule R-Declare.
	 */
	@Override
	public Pair<State, Stmt> apply(State state, String lifetime, Stmt.Let stmt) {
		// Evaluate initialiser
		Pair<State, Expr> pe = apply(state, stmt.initialiser());
		// Allocate new location
		Pair<State, Location> pl = pe.first().allocate(lifetime, (Value) pe.second());
		// Bind variable to location
		state = pl.first().bind(stmt.name(), pl.second());
		// Done
		return new Pair<>(state, null);
	}

	/**
	 * Rule R-IndAssign.
	 */
	@Override
	public Pair<State, Stmt> apply(State state, String lifetime, Stmt.IndirectAssignment stmt) {
		// Extract variable being indirectly assigned
		Expr.Variable lhs = stmt.leftOperand();
		// Evaluate right hand side operand
		Pair<State, Expr> rhs = apply(state, stmt.rightOperand());
		// Update state
		state = rhs.first();
		// Extract the location being assigned
		Location loc = (Location) state.read(state.locate(lhs.name()));
		// Perform the indirect assignment
		state = state.write(loc, (Value) rhs.second());
		// Done
		return new Pair<>(state, null);
	}

	/**
	 * Rule R-Deref.
	 */
	@Override
	public Pair<State, Expr> apply(State state, Expr.Dereference expr) {
		// Evaluate operand
		Pair<State, Expr> p = apply(state, expr.operand());
		// Extract updated state
		state = p.first();
		// Extract location, or throw exception otherwise
		Location loc = (Location) p.second();
		// Read contents of cell at given location
		return new Pair<>(state, state.read(loc));
	}

	/**
	 * Rule R-Borrow.
	 */
	@Override
	public Pair<State, Expr> apply(State state, Expr.Borrow expr) {
		// Locate operand
		Location loc = state.locate(expr.operand().name());
		// Done
		return new Pair<>(state, loc);
	}

	/**
	 * Rule R-Box.
	 */
	@Override
	public Pair<State, Expr> apply(State state, Expr.Box expr) {
		// Evaluate operand
		Pair<State, Expr> pe = apply(state, expr.operand());
		// Allocate new location
		Pair<State, Location> pl = pe.first().allocate("*", (Value) pe.second());
		// Done
		return new Pair<>(pl.first(), pl.second());
	}

	/**
	 * Rule R-Var.
	 */
	@Override
	public Pair<State, Expr> apply(State state, Expr.Variable expr) {
		// Determine location bound by variable
		Location loc = state.locate(expr.name());
		// Read location from store
		return new Pair<>(state, state.read(loc));
	}
}
