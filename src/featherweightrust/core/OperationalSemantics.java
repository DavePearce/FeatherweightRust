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
 * Encodes the operational semantics of Featherweight Rust.
 *
 * @author djp
 *
 */
public class OperationalSemantics extends AbstractSemantics {
	/**
	 * Rule R-Assign.
	 */
	@Override
	public Pair<State, Stmt> apply2(State S1, Lifetime lifetime, Stmt.Assignment<Value> stmt) {
		// Extract variable being assigned
		Expr.Variable lhs = stmt.leftOperand();
		// Extract the location being assigned
		Location l = S1.locate(lhs.name());
		// Extract value being overwritten
		Value v1 = S1.read(l);
		// Extract value being assigned
		Value v2 = stmt.rightOperand();
		// Drop overwritten value
		State S2 = S1.drop(v1);
		// Perform the assignment
		State S3 = S2.write(l, v2);
		// Done
		return new Pair<>(S3, null);
	}

	/**
	 * Rule R-Block.
	 */
	@Override
	public Pair<State, Stmt> apply(State S1, Lifetime lifetime, Stmt.Block stmt) {
		// Save current bindings so they can be restored
		StackFrame outerFrame = S1.frame();
		//
		for (int i = 0; i != stmt.size(); ++i) {
			Pair<State, Stmt> p = apply(S1, stmt.lifetime(), stmt.get(i));
			Stmt s = p.second();
			S1 = p.first();
			//
			if(s != null || s instanceof Value) {
				// Either we're stuck, or we produced a return value.

				// FIXME: drop allocated locations!!

				return new Pair<>(S1,s);
			}
		}
		// drop all bindings created within block
		S1 = new State(outerFrame, S1.store());
		// drop all allocated locations
		S1 = S1.drop(stmt.lifetime());
		//
		return new Pair<>(S1, null);
	}

	/**
	 * Rule R-Declare.
	 */
	@Override
	public Pair<State, Stmt> apply2(State S1, Lifetime lifetime, Stmt.Let<Value> stmt) {
		// Extract initializer value
		Value v = stmt.initialiser();
		// Allocate new location
		Pair<State, Location> pl = S1.allocate(lifetime, v);
		State S2 = pl.first();
		Location l = pl.second();
		// Bind variable to location
		State S3 = S2.bind(stmt.name(), l);
		// Done
		return new Pair<>(S3, null);
	}

	/**
	 * Rule R-IndAssign.
	 */
	@Override
	public Pair<State, Stmt> apply2(State S1, Lifetime lifetime, Stmt.IndirectAssignment<Value> stmt) {
		// Extract variable being indirectly assigned
		Expr.Variable lhs = stmt.leftOperand();
		// Extract the location being assigned
		Location l = (Location) S1.read(S1.locate(lhs.name()));
		// Extract value being overwritten
		Value v1 = S1.read(l);
		// Extract value being assigned
		Value v2 = stmt.rightOperand();
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
	@Override
	public Pair<State, Expr> apply2(State S1, Expr.Dereference<Value> e) {
		// Extract location, or throw exception otherwise
		Location l = (Location) e.operand();
		// Read contents of cell at given location
		return new Pair<>(S1, S1.read(l));
	}

	/**
	 * Rule R-Borrow.
	 */
	@Override
	public Pair<State, Expr> apply(State state, Lifetime lifetime, Expr.Borrow expr) {
		String name = expr.operand().name();
		// Locate operand
		Location loc = state.locate(expr.operand().name());
		//
		if(loc == null) {
			throw new RuntimeException("invalid variable \"" + name + "\"");
		}
		// Strip ownership flag since is a borrow
		loc = new Location(loc.getAddress(),false);
		// Done
		return new Pair<>(state, loc);
	}

	/**
	 * Rule R-Box.
	 */
	@Override
	public Pair<State, Expr> apply2(State S1, Lifetime lifetime, Expr.Box<Value> e) {
		Lifetime globalLifetime = lifetime.getRoot();
		// Extract operand
		Value v = e.operand();
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
	@Override
	public Pair<State, Expr> apply(State state, Lifetime lifetime, Expr.Variable expr) {
		// Determine location bound by variable
		Location loc = state.locate(expr.name());
		// Read location from store
		return new Pair<>(state, state.read(loc));
	}
}
