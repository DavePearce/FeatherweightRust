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

import java.io.IOException;
import java.io.StringReader;
import java.util.Arrays;
import java.util.BitSet;
import java.util.List;

import featherweightrust.core.Syntax.Lifetime;
import featherweightrust.core.Syntax.LVal;
import featherweightrust.core.Syntax.Term;
import featherweightrust.core.Syntax.Term.Block;
import featherweightrust.core.Syntax.Value;
import static featherweightrust.core.Syntax.Value.Unit;
import featherweightrust.core.Syntax.Value.Reference;
import featherweightrust.io.Lexer;
import featherweightrust.io.Parser;
import featherweightrust.testing.CoreTests;
import featherweightrust.util.AbstractMachine;
import featherweightrust.util.AbstractMachine.StackFrame;
import featherweightrust.util.AbstractMachine.State;
import featherweightrust.util.AbstractTransformer;
import featherweightrust.util.ArrayUtils;
import featherweightrust.util.Pair;
import featherweightrust.util.SyntaxError;

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

	public final Term execute(Lifetime l, Term t) {
		// Execute block in outermost lifetime "*")
		Pair<State, Term> state = new Pair<>(AbstractMachine.EMPTY_STATE, t);
		// Execute continually until all reductions complete
		Term result;
		do {
			state = apply(state.first(), l, state.second());
			result = state.second();
		} while (result != null && !(result instanceof Value));
		//
		if(!state.first().isHeapEmpty()) {
			throw new RuntimeException("memory leak detected: " + state.first());
		}
		//
		return result;
	}

	@Override
	public Pair<State, Term> apply(State S, Lifetime l, Term t) {
		Pair<State,Term> p = super.apply(S,l,t);
		if(DEBUG) {
			String sl = ArrayUtils.leftPad(60,"(" + S + ", " + t + ")");
			String sr = ArrayUtils.rightPad(60,p.toString());
			System.err.println(sl + " ===> " + sr);
		}
		return p;
	}


	/**
	 * Rule R-Copy.
	 */
	protected Pair<State, Term> reduceCopy(State S,  Lifetime l, LVal w) {
		// Read contents of cell at given location
		Value v = S.read(w);
		// Done
		return new Pair<>(S, v);
	}

	/**
	 * Rule R-Move.
	 */
	protected Pair<State, Term> reduceMove(State S1,  Lifetime l, LVal w) {
		// Read contents of slot at given location
		Value v = S1.read(w);
		// Apply destructive update
		State S2 = S1.write(w, null);
		// Return value read
		return new Pair<>(S2, v);
	}

	/**
	 * Rule R-Box.
	 */
	protected Pair<State, Term> reduceBox(State S1, Lifetime l, Value v) {
		// Find global lifetime
		Lifetime global = l.getRoot();
		// Allocate new location
		Pair<State, Reference> pl = S1.allocate(global, v);
		State S2 = pl.first();
		Reference ln = pl.second();
		// Done
		return new Pair<>(S2, ln);
	}

	/**
	 * Rule R-Borrow.
	 */
	protected Pair<State, Term> reduceBorrow(State S, Lifetime l, LVal s) {
		// Locate operand
		Reference lx = s.locate(S);
		// NOTE: since this is a borrow, must obtain a reference to the location (i.e.
		// something which is not also an owner of that location). Otherwise, we risk
		// this borrow resulting in the location to which it refers being dropped.
		return new Pair<>(S, lx.toBorrowed());
	}

	/**
	 * Rule R-Assign.
	 */
	protected Pair<State, Term> reduceAssignment(State S1, Lifetime l, LVal w, Value v2) {
		// Extract value being overwritten
		Value v1 = S1.read(w);
		// Perform the assignment
		State S2 = S1.write(w, v2);
		// Drop overwritten value (and any owned boxes)
		State S3 = S2.drop(v1);
		// Done
		return new Pair<>(S3, Unit);
	}

	/**
	 * Rule R-Declare.
	 */
	protected Pair<State, Term> reduceLet(State S1, Lifetime l, String x, Value v) {
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
	 * Rule R-Seq
	 */
	protected Pair<State, Term> reduceSeq(State S1, Lifetime l, Value v, Term[] terms, Lifetime m) {
		// Drop value (and any owned boxes)
		State S2 = S1.drop(v);
		// Done
		return new Pair<>(S2, new Term.Block(m, terms));
	}

	/**
	 * Rule R-Block.
	 */
	protected Pair<State, Term> reduceBlock(State S1, Lifetime l, Value v, Lifetime m) {
		// Identify locations allocated in this lifetime
		BitSet phi = S1.findAll(m);
		// drop all matching locations
		State S2 = S1.drop(phi);
		//
		return new Pair<>(S2, v);
	}

	@Override
	final protected Pair<State, Term> apply(State S1, Lifetime l, Block b) {
		final int n = b.size();
		//
		if (n > 0) {
			Pair<State, Term> p = apply(S1, b.lifetime(), b.get(0));
			Term s = p.second();
			S1 = p.first();
			if (s instanceof Value) {
				if (n == 1) {
					return reduceBlock(S1, l, (Value) s, b.lifetime());
				} else {
					// Slice off head
					return reduceSeq(S1,l,(Value) s, slice(b,1),b.lifetime());
				}
			} else {
				// Statement hasn't completed
				Term[] stmts = Arrays.copyOf(b.toArray(), n);
				// Replace with partially reduced statement
				stmts[0] = s;
				// Go around again
				return new Pair<>(S1, new Term.Block(b.lifetime(), stmts, b.attributes()));
			}
		} else {
			// Empty block
			return reduceBlock(S1, l, Unit, b.lifetime());
		}
	}

	@Override
	final protected Pair<State, Term> apply(State S1, Lifetime l, Term.Let s) {
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
	final protected Pair<State, Term> apply(State S1, Lifetime l, Term.Assignment s) {
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
	final protected Pair<State, Term> apply(State S, Lifetime l, Term.Borrow e) {
		return reduceBorrow(S, l, e.operand());
	}

	@Override
	protected Pair<State, Term> apply(State S, Lifetime l, Term.Access e) {
		if(e.copy()) {
			return reduceCopy(S, l, e.operand());
		} else {
			return reduceMove(S, l, e.operand());
		}
	}

	@Override
	final protected Pair<State, Term> apply(State S1, Lifetime l, Term.Box e) {
		if (e.operand() instanceof Value) {
			// Statement can be completely reduced
			return reduceBox(S1, l, (Value) e.operand());
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
	protected Pair<State, Term> apply(State S, Lifetime lifetime, Value.Unit v) {
		return new Pair<>(S, v);
	}

	@Override
	protected Pair<State, Term> apply(State S, Lifetime lifetime, Value.Integer v) {
		return new Pair<>(S, v);
	}

	@Override
	protected Pair<State, Term> apply(State S, Lifetime lifetime, Value.Reference v) {
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

	public static void main(String[] args) throws IOException {
		Lifetime globalLifetime = new Lifetime();
		String input = "{let mut x = 0; let mut y = &mut x; {let mut z = 1; y = &mut z; }}";
		// Allocate the global lifetime. This is the lifetime where all heap allocated
		// data will reside.
		//
		try {
			List<Lexer.Token> tokens = new Lexer(new StringReader(input)).scan();
			// Parse block
			Term.Block stmt = new Parser(input, tokens).parseStatementBlock(new Parser.Context(), globalLifetime);
			// Execute block in outermost lifetime "*")
			Pair<State, Term> state = new Pair<>(new State(),stmt);
			// Execute continually until all reductions complete (or exception)
			Term result = CoreTests.SEMANTICS.execute(globalLifetime, state.second());
			//
			System.out.println("GOT: " + result);
			//
		} catch (SyntaxError e) {
			e.outputSourceError(System.err);
			e.printStackTrace();
		}
	}
}
