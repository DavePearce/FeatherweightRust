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

import java.util.Arrays;
import java.util.BitSet;
import java.util.Set;

import featherweightrust.core.Syntax.Lifetime;
import featherweightrust.core.Syntax.LVal;
import featherweightrust.core.Syntax.Term;
import featherweightrust.core.Syntax.Term.Block;
import featherweightrust.core.Syntax.Value;
import static featherweightrust.core.Syntax.Value.Unit;
import featherweightrust.core.Syntax.Value.Reference;
import featherweightrust.util.AbstractMachine;
import featherweightrust.util.AbstractMachine.StackFrame;
import featherweightrust.util.AbstractMachine.State;
import featherweightrust.util.AbstractTransformer;
import featherweightrust.util.ArrayUtils;
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
public class OperationalSemantics extends AbstractTransformer<AbstractMachine.State, Term, OperationalSemantics.Extension> {
	private static final boolean DEBUG = false;

	public OperationalSemantics(Extension... extensions) {
		super(extensions);
		// Bind self in extensions
		for(Extension e : extensions) {
			e.self = this;
		}
	}

	@Override
	public final Pair<State, Term> apply(State S, Lifetime l, Term t) {
		Pair<State,Term> p = super.apply(S,l,t);
		if(DEBUG) {
			String sl = ArrayUtils.leftPad(60,"(" + S + ", " + t + ")");
			String sr = ArrayUtils.rightPad(60,p.toString());
			System.err.println(sl + " ===> " + sr);
		}
		return p;
	}

	/**
	 * Rule R-Declare.
	 */
	public Pair<State, Term> reduceLet(State S1, Lifetime l, String x, Value v) {
		// Allocate new location
		Pair<State, Reference> pl = S1.allocate(l, v);
		State S2 = pl.first();
		Reference lx = pl.second();
		// Bind variable to location
		State S3 = S2.bind(x, lx);
		// Done
		return new Pair<>(S3, Unit);
	}

	/**
	 * Rule R-IndAssign.
	 */
	public Pair<State, Term> reduceAssignment(State S1, Lifetime l, LVal lv, Value v) {
		// Extract location, or throw exception otherwise
		Reference lx = lv.locate(S1);
		// Extract value being overwritten
		Value v1 = S1.read(lx);
		// Perform the assignment
		State S2 = S1.write(lx, v);
		// Drop overwritten value (and any owned boxes)
		State S3 = S2.drop(v1);
		// Done
		return new Pair<>(S3, Unit);
	}

	/**
	 * Rule R-Deref.
	 */
	public Pair<State, Term> reduceDereference(State S, LVal lv, boolean copy) {
		// Extract location, or throw exception otherwise
		Reference lx = lv.locate(S);
		// Read contents of cell at given location
		Value v = S.read(lx);
		// Check whether move required
		if(!copy) {
			// Apply destructive update
			S = S.write(lx, null);
		}
		// Done
		return new Pair<>(S, v);
	}

	/**
	 * Rule R-Borrow.
	 */
	public Pair<State, Term> reduceBorrow(State S, LVal s) {
		// Locate operand
		Reference lx = s.locate(S);
		//
		if (lx == null) {
			throw new RuntimeException("invalid path \"" + s + "\"");
		}
		// NOTE: since this is a borrow, must obtain a reference to the location (i.e.
		// something which is not also an owner of that location). Otherwise, we risk
		// this borrow resulting in the location to which it refers being dropped.
		return new Pair<>(S, lx.reference());
	}

	/**
	 * Rule R-Box.
	 */
	public Pair<State, Term> reduceBox(State S1, Value v, Lifetime global) {
		// Allocate new location
		Pair<State, Reference> pl = S1.allocate(global, v);
		State S2 = pl.first();
		Reference ln = pl.second();
		// Done
		return new Pair<>(S2, ln);
	}

	@Override
	public Pair<State, Term> apply(State S1, Lifetime lifetime, Block b) {
		final int n = b.size();
		// Save current bindings so they can be restored
		StackFrame outerFrame = S1.frame();
		Term returnValue = Unit;
		//
		if (n > 0) {
			Pair<State, Term> p = apply(S1, b.lifetime(), b.get(0));
			Term s = p.second();
			S1 = p.first();
			if(s instanceof Value) {
				if(n == 1) {
					returnValue = s;
				} else {
					// Slice off head
					return new Pair<>(S1, new Term.Block(b.lifetime(), slice(b, 1), b.attributes()));
				}
			} else {
				// Statement hasn't completed
				Term[] stmts = Arrays.copyOf(b.toArray(), n);
				// Replace with partially reduced statement
				stmts[0] = s;
				// Go around again
				return new Pair<>(S1, new Term.Block(b.lifetime(), stmts, b.attributes()));
			}
		}
		// drop all bindings created within block
		State S2 = new State(outerFrame, S1.store());
		// Identify locations allocated in this lifetime
		BitSet phi = S2.findAll(b.lifetime());
		// drop all matching locations
		State S3 = S2.drop(phi);
		//
		return new Pair<>(S3, returnValue);
	}

	@Override
	final public Pair<State, Term> apply(State S1, Lifetime l, Term.Let s) {
		if (s.initialiser() instanceof Value) {
			// Statement can be completely reduced
			Value v = (Value) s.initialiser();
			return reduceLet(S1, l, s.variable(), v);
		} else {
			// Statement not ready to be reduced yet
			Pair<State, Term> rhs = apply(S1, l, s.initialiser());
			// Construct reduce statement
			s = new Term.Let(s.variable(), rhs.second(), s.attributes());
			// Done
			return new Pair<>(rhs.first(), s);
		}
	}

	@Override
	final public Pair<State, Term> apply(State S1, Lifetime l, Term.Assignment s) {
		if (s.rightOperand() instanceof Value) {
			// Statement can be completely reduced
			return reduceAssignment(S1, l, s.leftOperand(), (Value) s.rightOperand());
		} else {
			// Statement not ready to be reduced yet
			Pair<State, Term> rhs = apply(S1, l, s.rightOperand());
			// Construct reduce statement
			s = new Term.Assignment(s.leftOperand(), rhs.second(), s.attributes());
			// Done
			return new Pair<>(rhs.first(), s);
		}
	}

	@Override
	final public Pair<State, Term> apply(State S, Lifetime l, Term.Borrow e) {
		return reduceBorrow(S, e.operand());
	}

	@Override
	final public Pair<State, Term> apply(State S, Lifetime l, Term.Dereference e) {
		return reduceDereference(S, e.operand(), e.copy());
	}

	@Override
	final public Pair<State, Term> apply(State S1, Lifetime l, Term.Box e) {
		if (e.operand() instanceof Value) {
			// Statement can be completely reduced
			return reduceBox(S1, (Value) e.operand(), l.getRoot());
		} else {
			// Statement not ready to be reduced yet
			Pair<State, Term> rhs = apply(S1, l, e.operand());
			// Construct reduce statement
			e = new Term.Box(rhs.second(), e.attributes());
			// Done
			return new Pair<>(rhs.first(), e);
		}
	}

	@Override
	public Pair<State, Term> apply(State S, Lifetime lifetime, Value.Unit v) {
		return new Pair<>(S, v);
	}

	@Override
	public Pair<State, Term> apply(State S, Lifetime lifetime, Value.Integer v) {
		return new Pair<>(S, v);
	}

	@Override
	public Pair<State, Term> apply(State S, Lifetime lifetime, Value.Reference v) {
		return new Pair<>(S, v);
	}

	/**
	 * Provides a specific extension mechanism for the semantics.
	 *
	 * @author David J. Pearce
	 *
	 */
	public abstract static class Extension implements AbstractTransformer.Extension<AbstractMachine.State, Term> {
		protected OperationalSemantics self;
	}

	/**
	 * Return a new block with only a given number of its statements.
	 *
	 * @param b
	 * @param n
	 * @return
	 */
	private static Term[] slice(Term.Block b, int n) {
		Term[] stmts = new Term[b.size() - n];
		for (int i = n; i < b.size(); ++i) {
			stmts[i - n] = b.get(i);
		}
		return stmts;
	}
}
