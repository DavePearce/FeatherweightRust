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
import featherweightrust.core.Syntax.Lifetime;
import featherweightrust.core.Syntax.Stmt;
import featherweightrust.core.Syntax.Value;

public abstract class AbstractTransformer<T,S,E extends S> {

	@SuppressWarnings("unchecked")
	public Pair<T, S> apply(T state, Lifetime lifetime, Stmt stmt) {
		if (stmt instanceof Stmt.Assignment) {
			return apply(state, lifetime, (Stmt.Assignment) stmt);
		} else if (stmt instanceof Stmt.Block) {
			return apply(state, lifetime, (Stmt.Block) stmt);
		} else if (stmt instanceof Stmt.IndirectAssignment) {
			return apply(state, lifetime, (Stmt.IndirectAssignment) stmt);
		} else if (stmt instanceof Stmt.Let) {
			return apply(state, lifetime, (Stmt.Let) stmt);
		} else {
			Pair<T,E> p = apply(state, lifetime, (Expr) stmt);
			return new Pair<>(p.first(),p.second());
		}
	}

	public abstract Pair<T,S> apply(T state, Lifetime lifetime, Stmt.Assignment stmt);

	public abstract Pair<T,S> apply(T state, Lifetime lifetime, Stmt.Block stmt);

	public abstract Pair<T,S> apply(T state, Lifetime lifetime, Stmt.IndirectAssignment stmt);

	public abstract Pair<T,S> apply(T state, Lifetime lifetime, Stmt.Let stmt);

	@SuppressWarnings("unchecked")
	public Pair<T,E> apply(T state, Lifetime lifetime, Expr expr) {
		if(expr instanceof Value.Integer) {
			return apply(state, (Value.Integer) expr);
		} else if(expr instanceof Value.Location) {
			return apply(state, (Value.Location) expr);
		} else if (expr instanceof Expr.Dereference) {
			return apply(state, lifetime, (Expr.Dereference) expr);
		} else if (expr instanceof Expr.Borrow) {
			return apply(state, lifetime, (Expr.Borrow) expr);
		} else if (expr instanceof Expr.Box) {
			return apply(state, lifetime, (Expr.Box) expr);
		} else if (expr instanceof Expr.Copy){
			return apply(state, lifetime, (Expr.Copy) expr);
		} else {
			return apply(state, lifetime, (Expr.Variable) expr);
		}
	}


	public abstract Pair<T,E> apply(T state, Value.Integer value);

	public abstract Pair<T,E> apply(T state, Value.Location value);

	public abstract Pair<T,E> apply(T state, Lifetime lifetime, Expr.Dereference expr);

	public abstract Pair<T,E> apply(T state, Lifetime lifetime, Expr.Borrow expr);

	public abstract Pair<T,E> apply(T state, Lifetime lifetime, Expr.Box expr);

	public abstract Pair<T,E> apply(T state, Lifetime lifetime, Expr.Variable expr);

	public abstract Pair<T,E> apply(T state, Lifetime lifetime, Expr.Copy expr);
}
