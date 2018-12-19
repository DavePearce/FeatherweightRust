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
package featherweightrust.util;

import featherweightrust.core.Syntax.Expr;
import featherweightrust.core.Syntax.LVal;
import featherweightrust.core.Syntax.Stmt;
import featherweightrust.core.Syntax.Value;

public abstract class AbstractTransformer<T,S,E,L> {

	public Pair<T,S> apply(T state, String lifetime, Stmt stmt) {
		if (stmt instanceof Stmt.Let) {
			return apply(state, lifetime, (Stmt.Let) stmt);
		} else if (stmt instanceof Stmt.Assignment) {
			return apply(state, lifetime, (Stmt.Assignment) stmt);
		} else {
			return apply(state, lifetime, (Stmt.Block) stmt);
		}
	}

	public abstract Pair<T,S> apply(T state, String lifetime, Stmt.Let stmt);

	public abstract Pair<T,S> apply(T state, String lifetime, Stmt.Assignment stmt);

	public abstract Pair<T,S> apply(T state, String lifetime, Stmt.Block stmt);

	public Pair<T,L> apply(T state, LVal lval) {
		if (lval instanceof LVal.Dereference) {
			return apply(state, (LVal.Dereference) lval);
		} else {
			return apply(state, (LVal.Variable) lval);
		}
	}

	public abstract Pair<T,L> apply(T state, LVal.Dereference expr);

	public abstract Pair<T,L> apply(T state, LVal.Variable expr);

	public Pair<T,E> apply(T state, Expr expr) {
		if(expr instanceof Value.Integer) {
			return apply(state, (Value.Integer) expr);
		} else if(expr instanceof Value.Location) {
			return apply(state, (Value.Location) expr);
		} else if (expr instanceof Expr.Dereference) {
			return apply(state, (Expr.Dereference) expr);
		} else if (expr instanceof Expr.Borrow) {
			return apply(state, (Expr.Borrow) expr);
		} else if (expr instanceof Expr.Box) {
			return apply(state, (Expr.Box) expr);
		} else {
			return apply(state, (Expr.Variable) expr);
		}
	}


	public abstract Pair<T,E> apply(T state, Value.Integer value);

	public abstract Pair<T,E> apply(T state, Value.Location value);

	public abstract Pair<T,E> apply(T state, Expr.Dereference expr);

	public abstract Pair<T,E> apply(T state, Expr.Borrow expr);

	public abstract Pair<T,E> apply(T state, Expr.Box expr);

	public abstract Pair<T,E> apply(T state, Expr.Variable expr);
}
