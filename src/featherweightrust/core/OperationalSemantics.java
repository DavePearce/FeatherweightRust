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
import java.util.Set;

import featherweightrust.core.Syntax.Lifetime;
import featherweightrust.core.Syntax.Path;
import featherweightrust.core.Syntax.Slice;
import featherweightrust.core.Syntax.Term;
import featherweightrust.core.Syntax.Type;
import featherweightrust.core.Syntax.Term.Block;
import featherweightrust.core.Syntax.Value;
import static featherweightrust.core.Syntax.Value.Unit;
import featherweightrust.core.Syntax.Value.Location;
import featherweightrust.util.AbstractMachine;
import featherweightrust.util.AbstractMachine.StackFrame;
import featherweightrust.util.AbstractMachine.State;
import featherweightrust.util.AbstractTransformer;
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

	public OperationalSemantics(Extension... extensions) {
		super(extensions);
		// Bind self in extensions
		for(Extension e : extensions) {
			e.self = this;
		}
	}

	/**
	 * Rule R-Assign.
	 */
	public Pair<State, Term> reduceAssignment(State S1, Lifetime l, Term.Variable x, Value v2) {
		// Extract the location being assigned
		Location lx = S1.locate(x.name());
		// Extract value being overwritten
		Value v1 = S1.read(lx);
		// Drop overwritten value (and any owned boxes)
		State S2 = S1.write(lx, null).drop(v1);
		// Perform the assignment
		State S3 = S2.write(lx, v2);
		// Done
		return new Pair<>(S3, Unit);
	}

	/**
	 * Rule R-Declare.
	 */
	public Pair<State, Term> reduceLet(State S1, Lifetime l, Term.Variable x, Value v) {
		// Allocate new location
		Pair<State, Location> pl = S1.allocate(l, v);
		State S2 = pl.first();
		Location lx = pl.second();
		// Bind variable to location
		State S3 = S2.bind(x, lx);
		// Done
		return new Pair<>(S3, Unit);
	}

	/**
	 * Rule R-IndAssign.
	 */
	public Pair<State, Term> reduceIndirectAssignment(State S1, Lifetime l, Term.Variable x, Value v) {
		// Extract the location of x
		Location lx = S1.locate(x.name());
		// Extract target location being assigned
		Location ly = (Location) S1.read(lx);
		// Extract value being overwritten
		Value v1 = S1.read(ly);
		// Drop any owned locations
		State S2 = S1.write(ly, null).drop(v1);
		// Perform the indirect assignment
		State S3 = S2.write(ly, v);
		// Done
		return new Pair<>(S3, Unit);
	}

	/**
	 * Rule R-Deref.
	 */
	public Pair<State, Term> reduceDereference(State S, Term.Variable x) {
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
	public Pair<State, Term> reduceBorrow(State S, Slice s) {
		// Locate operand
		Location lx = S.locate(s);
		//
		if (lx == null) {
			throw new RuntimeException("invalid path \"" + s + "\"");
		}
		// Done
		return new Pair<>(S, lx);
	}

	/**
	 * Rule R-Box.
	 */
	public Pair<State, Term> reduceBox(State S1, Value v, Lifetime global) {
		// Allocate new location
		Pair<State, Location> pl = S1.allocate(global, v);
		State S2 = pl.first();
		Location ln = pl.second();
		// Done
		return new Pair<>(S2, ln);
	}

	/**
	 * Rule R-Var.
	 */
	public Pair<State, Term> reduceVariable(State S, Term.Variable x) {
		// Determine location bound by variable
		Location lx = S.locate(x.name());
		// Read location from store
		return new Pair<>(S, S.read(lx));
	}

	@Override
	public Pair<State, Term> apply(State S1, Lifetime lifetime, Block b) {
		// Save current bindings so they can be restored
		StackFrame outerFrame = S1.frame();
		Term returnValue = Unit;
		//
		if (b.size() == 1 && b.get(0) instanceof Value) {
			// Return value produced
			returnValue = b.get(0);
		} else if (b.size() > 0) {
			Pair<State, Term> p = apply(S1, b.lifetime(), b.get(0));
			Term s = p.second();
			S1 = p.first();
			if(s == Unit) {
				// Slice off head
				return new Pair<>(S1, new Term.Block(b.lifetime(), slice(b, 1), b.attributes()));
			} else {
				// Statement hasn't completed
				Term[] stmts = Arrays.copyOf(b.toArray(), b.size());
				// Replace with partially reduced statement
				stmts[0] = s;
				// Go around again
				return new Pair<>(S1, new Term.Block(b.lifetime(), stmts, b.attributes()));
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
	final public Pair<State, Term> apply(State S1, Lifetime l, Term.IndirectAssignment s) {
		if (s.rightOperand() instanceof Value) {
			// Statement can be completely reduced
			return reduceIndirectAssignment(S1, l, s.leftOperand(), (Value) s.rightOperand());
		} else {
			// Statement not ready to be reduced yet
			Pair<State, Term> rhs = apply(S1, l, s.rightOperand());
			// Construct reduce statement
			s = new Term.IndirectAssignment(s.leftOperand(), rhs.second(), s.attributes());
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
		return reduceDereference(S, e.operand());
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
	final public Pair<State, Term> apply(State S, Lifetime l, Term.Variable e) {
		return reduceVariable(S, e);
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
	public Pair<State, Term> apply(State S, Lifetime lifetime, Value.Location v) {
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
		for (int i = n; i != b.size(); ++i) {
			stmts[i - n] = b.get(i);
		}
		return stmts;
	}
}
