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

import featherweightrust.core.Syntax;
import featherweightrust.core.Syntax.Lifetime;
import featherweightrust.core.Syntax.Term;
import featherweightrust.core.Syntax.Value;

public abstract class AbstractTransformer<T,S> {

	@SuppressWarnings("unchecked")
	public Pair<T, S> apply(T state, Lifetime lifetime, Term term) {
		switch(term.getOpcode()) {
		case Syntax.TERM_let:
			return (Pair<T, S>) apply(state, lifetime, (Term.Let) term);
		case Syntax.TERM_assignment:
			return (Pair<T, S>) apply(state, lifetime, (Term.Assignment) term);
		case Syntax.TERM_indirectassignment:
			return (Pair<T, S>) apply(state, lifetime, (Term.IndirectAssignment) term);
		case Syntax.TERM_block:
			return (Pair<T, S>) apply(state, lifetime, (Term.Block) term);
		case Syntax.TERM_move:
			return (Pair<T, S>) apply(state, lifetime, (Term.Variable) term);
		case Syntax.TERM_copy:
			return (Pair<T, S>) apply(state, lifetime, (Term.Copy) term);
		case Syntax.TERM_borrow:
			return (Pair<T, S>) apply(state, lifetime, (Term.Borrow) term);
		case Syntax.TERM_dereference:
			return (Pair<T, S>) apply(state, lifetime, (Term.Dereference) term);
		case Syntax.TERM_box:
			return (Pair<T, S>) apply(state, lifetime, (Term.Box) term);
		case Syntax.TERM_integer:
			return (Pair<T, S>) apply(state, (Value.Integer) term);
		case Syntax.TERM_location:
			return (Pair<T, S>) apply(state, (Value.Location) term);
		}
		throw new IllegalArgumentException("Invalid term encountered: " + term);
	}

	public abstract Pair<T, S> apply(T state, Lifetime lifetime, Term.Assignment stmt);

	public abstract Pair<T, S> apply(T state, Lifetime lifetime, Term.Block stmt);

	public abstract Pair<T, S> apply(T state, Lifetime lifetime, Term.IndirectAssignment stmt);

	public abstract Pair<T, S> apply(T state, Lifetime lifetime, Term.Let stmt);

	public abstract Pair<T, S> apply(T state, Value.Integer value);

	public abstract Pair<T, S> apply(T state, Value.Location value);

	public abstract Pair<T, S> apply(T state, Lifetime lifetime, Term.Dereference expr);

	public abstract Pair<T, S> apply(T state, Lifetime lifetime, Term.Borrow expr);

	public abstract Pair<T, S> apply(T state, Lifetime lifetime, Term.Box expr);

	public abstract Pair<T, S> apply(T state, Lifetime lifetime, Term.Variable expr);

	public abstract Pair<T, S> apply(T state, Lifetime lifetime, Term.Copy expr);


}
