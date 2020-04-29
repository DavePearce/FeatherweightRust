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

public abstract class AbstractTransformer<T, S, E extends AbstractTransformer.Extension<T, S>> {
	/**
	 * The set of available extensions for this transformer.
	 */
	private final E[] extensions;

	@SafeVarargs
	public AbstractTransformer(E... extensions) {
		this.extensions = extensions;
	}

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
		case Syntax.TERM_unit:
			return (Pair<T, S>) apply(state, lifetime, (Value.Unit) term);
		case Syntax.TERM_integer:
			return (Pair<T, S>) apply(state, lifetime, (Value.Integer) term);
		case Syntax.TERM_location:
			return (Pair<T, S>) apply(state, lifetime, (Value.Location) term);
		}
		// Attempt to run extensions
		for(int i=0;i!=extensions.length;++i) {
			Pair<T,S> r = extensions[i].apply(state, lifetime, term);
			if(r != null) {
				return r;
			}
		}
		// Give up
		throw new IllegalArgumentException("Invalid term encountered: " + term);
	}

	/**
	 * Apply this transformer to a given assignment statement.
	 *
	 * @param state    The current state (e.g. typing or runtime store)
	 * @param lifetime The enclosing lifetime of this term
	 * @param stmt     The term being transformed.
	 * @return
	 */
	public abstract Pair<T, S> apply(T state, Lifetime lifetime, Term.Assignment term);

	/**
	 * Apply this transformer to a given block statement.
	 *
	 * @param state    The current state (e.g. typing or runtime store)
	 * @param lifetime The enclosing lifetime of this term
	 * @param stmt     The term being transformed.
	 * @return
	 */
	public abstract Pair<T, S> apply(T state, Lifetime lifetime, Term.Block term);

	/**
	 * Apply this transformer to a given indirect assignment statement.
	 *
	 * @param state    The current state (e.g. typing or runtime store)
	 * @param lifetime The enclosing lifetime of this term
	 * @param stmt     The term being transformed.
	 * @return
	 */
	public abstract Pair<T, S> apply(T state, Lifetime lifetime, Term.IndirectAssignment term);

	/**
	 * Apply this transformer to a given let statement.
	 *
	 * @param state    The current state (e.g. typing or runtime store)
	 * @param lifetime The enclosing lifetime of this term
	 * @param stmt     The term being transformed.
	 * @return
	 */
	public abstract Pair<T, S> apply(T state, Lifetime lifetime, Term.Let term);

	/**
	 * Apply this transformer to a given dereference expression.
	 *
	 * @param state    The current state (e.g. typing or runtime store)
	 * @param lifetime The enclosing lifetime of this term
	 * @param stmt     The term being transformed.
	 * @return
	 */
	public abstract Pair<T, S> apply(T state, Lifetime lifetime, Term.Dereference term);

	/**
	 * Apply this transformer to a given borrow expression.
	 *
	 * @param state    The current state (e.g. typing or runtime store)
	 * @param lifetime The enclosing lifetime of this term
	 * @param stmt     The term being transformed.
	 * @return
	 */
	public abstract Pair<T, S> apply(T state, Lifetime lifetime, Term.Borrow term);

	/**
	 * Apply this transformer to a given box expression.
	 *
	 * @param state    The current state (e.g. typing or runtime store)
	 * @param lifetime The enclosing lifetime of this term
	 * @param stmt     The term being transformed.
	 * @return
	 */
	public abstract Pair<T, S> apply(T state, Lifetime lifetime, Term.Box term);

	/**
	 * Apply this transformer to a given variable move expression.
	 *
	 * @param state    The current state (e.g. typing or runtime store)
	 * @param lifetime The enclosing lifetime of this term
	 * @param stmt     The term being transformed.
	 * @return
	 */
	public abstract Pair<T, S> apply(T state, Lifetime lifetime, Term.Variable term);

	/**
	 * Apply this transformer to a given variable copy expression.
	 *
	 * @param state    The current state (e.g. typing or runtime store)
	 * @param lifetime The enclosing lifetime of this term
	 * @param stmt     The term being transformed.
	 * @return
	 */
	public abstract Pair<T, S> apply(T state, Lifetime lifetime, Term.Copy term);


	/**
	 * Apply this transformer to the unit constant.
	 *
	 * @param state    The current state (e.g. typing or runtime store)
	 * @param lifetime The enclosing lifetime of this term
	 * @param stmt     The term being transformed.
	 * @return
	 */
	public abstract Pair<T, S> apply(T state, Lifetime lifetime, Value.Unit value);

	/**
	 * Apply this transformer to a given integer constant.
	 *
	 * @param state    The current state (e.g. typing or runtime store)
	 * @param lifetime The enclosing lifetime of this term
	 * @param stmt     The term being transformed.
	 * @return
	 */
	public abstract Pair<T, S> apply(T state, Lifetime lifetime, Value.Integer value);

	/**
	 * Apply this transformer to a given assignment statement.
	 *
	 * @param state    The current state (e.g. typing or runtime store)
	 * @param lifetime The enclosing lifetime of this term
	 * @param stmt     The term being transformed.
	 * @return
	 */
	public abstract Pair<T, S> apply(T state, Lifetime lifetim, Value.Location value);

	/**
	 * Provides a mechanism by which a transformer can be extended.
	 *
	 * @author David J. Pearce
	 *
	 */
	public interface Extension<T,S> {
		abstract Pair<T, S> apply(T state, Lifetime lifetime, Term term);
	}
}
