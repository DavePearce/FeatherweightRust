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
import featherweightrust.core.Syntax.LVal;
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
	 * Rule R-Assign.
	 */
	@Override
	public Pair<State, Stmt> apply(State state, String lifetime, Stmt.Assignment stmt) {
		// Evaluate left hand side operand
		Pair<State, LVal> lhs = apply(state, stmt.leftOperand());
		// Evaluate right hand side operand
		Pair<State, Expr> rhs = apply(lhs.first(), stmt.rightOperand());
		// Extract the location being assigned
		Location loc = (Location) lhs.second();
		// Perform the assignment
		state = rhs.first().write(loc, (Value) rhs.second());
		// Done
		return new Pair<>(state, null);
	}

	/**
	 * Case for Rule R-Assign
	 */
	@Override
	public Pair<State, LVal> apply(State state, LVal.Dereference lval) {
		// Evaluate operand
		Pair<State, Expr> p = apply(state, lval.operand());
		// Done
		return new Pair<>(p.first(), (Location) p.second());
	}

	/**
	 * Case for Rule R-Assign
	 */
	@Override
	public Pair<State, LVal> apply(State state, LVal.Variable lval) {
		Location loc = state.locate(lval.name());
		return new Pair<>(state, loc);
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
			state = p.first();
		}
		// drop all bindings created within block
		state = new State(originalBindings, state.store());
		// drop all allocated locations
		state = state.drop(stmt.lifetime());
		//
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
		// Evaluate operand
		Pair<State, LVal> pe = apply(state, expr.operand());
		// Done
		return new Pair<>(pe.first(), (Expr) pe.second());
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
