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

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import featherweightrust.core.Syntax.Lifetime;
import featherweightrust.core.Syntax.Path;
import featherweightrust.core.BorrowChecker.Environment;
import featherweightrust.core.Syntax.LVal;
import featherweightrust.core.Syntax.Term;
import featherweightrust.core.Syntax.Type;
import featherweightrust.core.Syntax.Value;
import featherweightrust.util.AbstractTransformer;
import featherweightrust.util.ArrayUtils;
import featherweightrust.util.Pair;
import featherweightrust.util.SyntacticElement;
import featherweightrust.util.SyntaxError;
import featherweightrust.util.SyntacticElement.Attribute;

/**
 * Responsible for type checking and borrowing checking a program in
 * Featherweight Rust.
 *
 * @author David J. Pearce
 *
 */
public class BorrowChecker extends AbstractTransformer<BorrowChecker.Environment, Type, BorrowChecker.Extension> {
	/**
	 * Enable or disable debugging output.
	 */
	private static final boolean DEBUG = false;
	/**
	 * Constant to reduce unnecessary environment instances.
	 */
	public final static Environment EMPTY_ENVIRONMENT = new Environment();
	// Error messages
	public final static String UNDECLARED_VARIABLE = "variable undeclared";
	public final static String CANNOT_MOVEOUT_THROUGH_BORROW = "cannot move out through borrow";
	public final static String VARIABLE_ALREADY_DECLARED = "variable already declared";
	public final static String BORROWED_VARIABLE_ASSIGNMENT = "cannot assign because borrowed";
	public final static String NOTWITHIN_VARIABLE_ASSIGNMENT = "lifetime not within";
	public final static String INCOMPATIBLE_TYPE = "incompatible type";
	public final static String EXPECTED_REFERENCE = "expected reference type";
	public final static String EXPECTED_MUTABLE_BORROW = "expected mutable borrow";
	public final static String LVAL_INVALID = "lval is invalid (e.g. incorrectly typed)";
	public final static String LVAL_MOVED = "use of moved lval or attempt to move out of lval";
	public final static String LVAL_NOT_WRITEABLE = "lval cannot be written (e.g. is moved in part or whole)";
	public final static String LVAL_NOT_READABLE = "lval cannot be read (e.g. is moved in part or whole)";
	public final static String LVAL_NOT_COPY = "lval's type cannot be copied";
	public final static String LVAL_NOT_MUTABLE = "lval borrowed in part or whole";

	public final static String LVAL_WRITE_PROHIBITED = "lval borrowed in part or whole";
	public final static String LVAL_READ_PROHIBITED = "lval mutably borrowed in part or whole";

	/**
	 * Indicates whether or not to apply copy inference.
	 */
	protected final boolean copyInference;
	protected final String sourcefile;

	public BorrowChecker(boolean copyInference, String sourcefile, Extension... extensions) {
		super(extensions);
		this.copyInference = copyInference;
		this.sourcefile = sourcefile;
		// Bind self in extensions
		for (Extension e : extensions) {
			e.self = this;
		}
	}

	@Override
	public Pair<Environment, Type> apply(Environment R1, Lifetime l, Term t) {
		Pair<Environment, Type> p = super.apply(R1, l, t);
		if (DEBUG) {
			Environment R2 = p.first();
			Type T = p.second();
			// Debugging output
			final int CONSOLE_WIDTH = 120;
			String m = " |- " + ArrayUtils.centerPad(40, t + " : " + T) + " -| ";
			int lw = (CONSOLE_WIDTH - m.length()) / 2;
			int rw = (CONSOLE_WIDTH - m.length() - lw);
			String sl = ArrayUtils.leftPad(lw, R1.toString());
			String sr = ArrayUtils.rightPad(rw, R2.toString());
			String s = sl + m + sr;
			System.err.println(s);
			System.err.println(ArrayUtils.pad(s.length(), '='));
		}
		return p;
	}

	/**
	 * T-Const
	 */
	@Override
	protected Pair<Environment, Type> apply(Environment R, Lifetime l, Value.Integer t) {
		return new Pair<>(R, Type.Int);
	}

	@Override
	protected Pair<Environment, Type> apply(Environment R, Lifetime l, Value.Unit t) {
		// NOTE: Safe since unit not part of source level syntax.
		throw new UnsupportedOperationException();
	}

	@Override
	protected Pair<Environment, Type> apply(Environment R, Lifetime l, Value.Reference t) {
		// NOTE: Safe since locations not part of source level syntax.
		throw new UnsupportedOperationException();
	}


	/**
	 * T-Copy & T-Move
	 */
	@Override
	protected Pair<Environment, Type> apply(Environment R1, Lifetime l, Term.Access t) {
		final LVal w = t.operand();
		// Determine type being read
		Type T = typeOf(R1, w);
		// Sanity check type
		check(T != null, LVAL_INVALID, w);
		// Sanity check type is moveable
		check(T.defined(), LVAL_MOVED, w);
		// Decide if copy or move
		if (isCopy(t, T)) {
			// Sanity check can copy this
			check(T.copyable(), LVAL_NOT_COPY, w);
			// Check available for reading
			check(!readProhibited(R1, w), LVAL_NOT_READABLE, w);
			// Done
			return new Pair<>(R1, T);
		} else {
			// Check available for writing
			check(!writeProhibited(R1, w), LVAL_NOT_WRITEABLE, w);
			// Apply destructive update
			Environment R2 = move(R1,w);
			// Done
			return new Pair<>(R2, T);
		}
	}

	/**
	 * T-MutBorrow and T-ImmBorrow
	 */
	@Override
	protected Pair<Environment, Type> apply(Environment R, Lifetime lifetime, Term.Borrow t) {
		LVal w = t.operand();
		// Determine type being read
		Type T = typeOf(R, w);
		// Sanity check lval
		check(T != null, LVAL_INVALID, w);
		// Sanity check type is moveable
		check(T.defined(), LVAL_MOVED, w);
		//
		if (t.isMutable()) {
			// T-MutBorrow
			// Check lval can be written
			check(!writeProhibited(R, w), LVAL_NOT_WRITEABLE, w);
			// Check LVal is mutable
			check(mut(R, w), LVAL_NOT_MUTABLE, w);
		} else {
			// T-ImmBorrow
			// Check lval can be at least read
			check(!readProhibited(R, w), LVAL_NOT_READABLE, w);
		}
		//
		return new Pair<>(R, new Type.Borrow(t.isMutable(), w));
	}

	/**
	 * T-Box
	 */
	@Override
	protected Pair<Environment, Type> apply(Environment R1, Lifetime l, Term.Box t) {
		// Type operand
		Pair<Environment, Type> p = apply(R1, l, t.operand());
		Environment R2 = p.first();
		Type T = p.second();
		//
		return new Pair<>(R2, new Type.Box(T));
	}

	/**
	 * T-Seq
	 */
	protected Pair<Environment, Type> apply(Environment R1, Lifetime l, Term... ts) {
		Environment Rn = R1;
		Type Tn = Type.Unit;
		for (int i = 0; i != ts.length; ++i) {
			// Type statement
			Pair<Environment, Type> p = apply(Rn, l, ts[i]);
			// Update environment and discard type (as unused for statements)
			Rn = p.first();
			Tn = p.second();
		}
		//
		return new Pair<>(Rn, Tn);
	}

	/**
	 * T-Block
	 */
	@Override
	protected Pair<Environment, Type> apply(Environment R1, Lifetime l, Term.Block t) {
		Term last = t.size() == 0 ? t : t.get(t.size()-1);
		Pair<Environment, Type> p = apply(R1, t.lifetime(), t.toArray());
		Environment R2 = p.first();
		Type T = p.second();
		// lifetime check
		check(T.within(this, R2, l), NOTWITHIN_VARIABLE_ASSIGNMENT, last);
		//
		Environment R3 = drop(R2, t.lifetime());
		//
		return new Pair<>(R3, T);
	}

	/**
	 * T-Declare
	 */
	@Override
	protected Pair<Environment, Type> apply(Environment R1, Lifetime l, Term.Let t) {
		// Sanity check variable not already declared
		String x = t.variable();
		Slot Sx = R1.get(x);
		check(Sx == null, VARIABLE_ALREADY_DECLARED, t);
		// Type operand
		Pair<Environment, Type> p = apply(R1, l, t.initialiser());
		Environment R2 = p.first();
		Type T = p.second();
		// Update environment
		Environment R3 = R2.put(x, T, l);
		// Done
		return new Pair<>(R3, Type.Unit);
	}

	/**
	 * T-Assign
	 */
	@Override
	protected Pair<Environment, Type> apply(Environment R1, Lifetime l, Term.Assignment t) {
		LVal w = t.leftOperand();
		// Determine lval type and lifetime
		Type T1 = typeOf(R1, w);
		Lifetime m = lifetimeOf(R1, w);
		// Type operand
		Pair<Environment, Type> p = apply(R1, l, t.rightOperand());
		Environment R2 = p.first();
		Type T2 = p.second();
		// Check type compatibility
		check(compatible(R2, T1, T2), INCOMPATIBLE_TYPE, w);
		// lifetime check
		check(T2.within(this, R2, m), NOTWITHIN_VARIABLE_ASSIGNMENT, w);
		// Write the type
		Environment R3 = write(R2, w, T2, true);
		// Check lval not write prohibited
		check(!writeProhibited(R3, w), LVAL_WRITE_PROHIBITED, t.leftOperand());
		//
		return new Pair<>(R3, Type.Unit);
	}

	// ================================================================================
	// Carry Typing
	// ================================================================================

	/**
	 * Apply "carry typing" to a given sequence of terms.
	 *
	 * @param R1    Initial environment before left-most term
	 * @param l     Enclosing lifetime
	 * @param terms Sequence of terms
	 * @return Final environment after right-most term, along with a type for each
	 *         term.
	 */
	public Pair<Environment,Type[]> carry(Environment R1, Lifetime l, Term[] terms) {
		String[] vars = BorrowChecker.fresh(terms.length);
		Type[] types = new Type[terms.length];
		Environment Rn = R1;
		// Type each element individually
		for(int i=0;i!=terms.length;++i) {
			Term ith = terms[i];
			// Type left-hand side
			Pair<Environment, Type> p1 = apply(Rn, l, ith);
			Type Tn = p1.second();
			Rn = p1.first();
			// Add type into environment temporarily
			Rn = Rn.put(vars[i], Tn, l.getRoot());
			//
			types[i] = p1.second();
		}
		// Remove all temporary types
		Environment R2 = Rn.remove(vars);
		// Done
		return new Pair<>(R2,types);
	}

	// ================================================================================
	// Copy
	// ================================================================================

	/**
	 * Check whether a given dereference is copy or not. General speaking, this is
	 * determined explicitly in the source-level syntax for Featherweight Rust.
	 * However, it is possible to override this method in order to provide other
	 * behaviour (e.g. copy inference).
	 *
	 * @param t The dereference being considered.
	 * @param T The type of the operand being moved / copied.
	 * @return
	 */
	public boolean isCopy(Term.Access t, Type T) {
		if (copyInference) {
			boolean r = T.copyable();
			t.infer(r ? Term.Access.Kind.COPY : Term.Access.Kind.MOVE);
			return r;
		} else {
			return t.copy();
		}
	}

	// ================================================================================
	// Move
	// ================================================================================

	/**
	 * Update the environment after a given lval is moved somewhere else.
	 *
	 * @param R
	 * @param w
	 * @return
	 */
	protected Environment move(Environment R, LVal w) {
		String x = w.name();
		Slot Sw = R.get(x);
		Type T1 = Sw.type();
		Lifetime l = Sw.lifetime();
		Type T2 = strike(T1, w.path(), 0);
		return R.put(x, new Slot(T2, l));
	}

	protected Type strike(Type T, Path p, int i) {
		if (p.size() == i) {
			return T.undefine();
		} else if (T instanceof Type.Box) {
			// In core calculus, dereferences are only valid path elements.
			Type.Box B = (Type.Box) T;
			return new Type.Box(strike(B.element(), p, i + 1));
		} else {
			// T must be Type.Borrow
			syntaxError(CANNOT_MOVEOUT_THROUGH_BORROW, p);
			// Deadcode
			return null;
		}
	}

	// ================================================================================
	// Write
	// ================================================================================

	/**
	 * Write a given type to a given lval in a given environment producing a
	 * potentially updated environment. For example, the following illustates:
	 *
	 * <pre>
	 * let mut x = 1;
	 * x = 2;
	 * </pre>
	 *
	 * The second statement calls this method to write type <code>int</code> to lval
	 * <code>x</code>. As such, the environment is not updated. However, in the
	 * following case, it is:
	 *
	 * <pre>
	 * let mut x = 1;
	 * let mut y = 2;
	 * let mut u = &mut x;
	 * let mut v = &mut y;
	 * let mut p = &mut u;
	 *
	 * *p = v;
	 * </pre>
	 *
	 * In this case, the environment is updated such that the type of <code>u</code>
	 * after the final assignment is <code>&mut x,y</code>. This is because, for
	 * whatever reason, Rust chooses against implementing this assignment as a
	 * <i>strong update</i>.
	 *
	 * @param R1     The environment in which the assignment is taking place.
	 * @param lv     The lval being assigned
	 * @param T1     The type being assigned to the lval.
	 * @param strong Indicates whether or not to perfom a strong update. Currently,
	 *               in Rust, these only occur when assigning directly to variables.
	 * @return
	 */
	protected Environment write(Environment R1, LVal lv, Type T1, boolean strong) {
		Path path = lv.path();
		// Extract target cell
		Slot Cx = R1.get(lv.name());
		// Destructure
		Type T2 = Cx.type();
		Lifetime m = Cx.lifetime();
		// Apply write
		Pair<Environment, Type> p = update(R1, T2, path, 0, T1, strong);
		Environment R2 = p.first();
		Type T4 = p.second();
		// Update environment
		return R2.put(lv.name(), T4, m);
	}

	protected Pair<Environment, Type> update(Environment R, Type T1, Path p, int i, Type T2, boolean strong) {
		if (i == p.size()) {
			if (strong) {
				return new Pair<>(R, T2);
			} else {
				return new Pair<>(R, T1.union(T2));
			}
		} else {
			// NOTE: Path elements are always Deref in the core calculus.
			Path.Deref ith = (Path.Deref) p.get(i);
			//
			if(T1 instanceof Type.Box) {
				Type.Box T = (Type.Box) T1;
				// NOTE: this prohibits a strong update when, in fact, it's always be possible
				// to do this. It's not clear to me that this is always necessary or even
				// desirable. However, at the time of writing, this mimics the Rust
				// compiler.
				Pair<Environment, Type> r = update(R, T.element(), p, i + 1, T2, true);
				// Done
				return new Pair<>(r.first(), new Type.Box(r.second()));
			} else {
				Type.Borrow T = (Type.Borrow) T1;
				check(T.isMutable(), LVAL_NOT_MUTABLE, p);
				LVal[] ys = T.lvals();
				//
				Environment Rn = R;
				// Consider all targets
				for (int j = 0; j != ys.length; ++j) {
					// Traverse remainder of path from lval.
					LVal y = ys[j].traverse(p, i + 1);
					// NOTE: this prohibits a strong update in certain cases where it may, in fact,
					// be possible to do this. It's not clear to me that this is always necessary or
					// even desirable. However, at the time of writing, this mimics the Rust
					// compiler.
					Rn = write(Rn, y, T2, false);
				}
				//
				return new Pair<>(Rn, T);
			}
		}
	}

	// ================================================================================
	// Read Prohibited
	// ================================================================================

	/**
	 * Check whether a given LVal is prohibited from being read by some other type
	 * in the environment (e.g. a mutable borrow). For example, consider the
	 * following:
	 *
	 * <pre>
	 * let mut x = 1;
	 * // x->int
	 * let mut y = &mut x;
	 * // x->int, y->&mut x
	 * </pre>
	 *
	 * After the second statement, the lval <code>x</code> is prohibited from being
	 * read by the mutable borrow type <code>&mut x</code> stored in the environment
	 * for <code>y</code>.
	 *
	 * @param R  The environment in which we are checking for readability.
	 * @param lv The lval being checked for readability
	 * @return
	 */
	protected boolean readProhibited(Environment R, LVal lv) {
		// Look through all types to whether any prohibit reading this lval
		for (Slot cell : R.cells()) {
			Type type = cell.type();
			if (type.prohibitsReading(lv)) {
				return true;
			}
		}
		return false;
	}

	// ================================================================================
	// Write Prohibited
	// ================================================================================

	/**
	 * Check whether a given LVal is prohibited from being written by some other
	 * type in the environment (e.g. a borrow). For example, consider the following:
	 *
	 * <pre>
	 * let mut x = 1;
	 * // x->int
	 * let mut y = &x;
	 * // x->int, y->&x
	 * </pre>
	 *
	 * After the second statement, the lval <code>x</code> is prohibited from being
	 * written by the borrow type <code>&x</code> stored in the environment for
	 * <code>y</code>.
	 *
	 * @param R  The environment in which we are checking for writability.
	 * @param lv The lval being checked for writability
	 * @return
	 */
	protected boolean writeProhibited(Environment R, LVal lv) {
		// Check whether any type prohibits this being written
		for (Slot cell : R.cells()) {
			Type type = cell.type();
			if (type.prohibitsWriting(lv)) {
				return true;
			}
		}
		// Check whether writing through mutable references
		return false;
	}

	// ================================================================================
	// Mut
	// ================================================================================

	/**
	 * Check whether a given LVal is declared as mutable. For example, the following
	 * is prohibited:
	 *
	 * <pre>
	 * let mut x = 1;
	 * let mut y = &x;
	 * *y = 1;
	 * </pre>
	 *
	 * The problem here is that the lval <code>*y</code> is not mutable.
	 *
	 * @param R   Current environment
	 * @param w  LVal being checked for availability
	 * @param mut indicates whether mutable or immutable access
	 * @return
	 */
	protected boolean mut(Environment R, LVal w) {
		// NOTE: can assume here that declaration check on lv.name() has already
		// occurred. Hence, Cx != null
		Slot Cx = R.get(w.name());
		//
		return mutable(R, Cx.type(), w.path(), 0);
	}

	protected boolean mutable(Environment R, Type T, Path p, int i) {
		if (p.size() == i) {
			// NOTE: Can always write to the top-level, but cannot read if its been moved
			// previously.
			return true;
		} else if (T instanceof Type.Box) {
			Type.Box B = (Type.Box) T;
			// Check path element is dereference
			Path.Deref d = (Path.Deref) p.get(i);
			// Continue writing
			return mutable(R, B.element(), p, i + 1);
		} else if (T instanceof Type.Borrow) {
			Type.Borrow t = (Type.Borrow) T;
			// Check path element is dereference
			Path.Deref d = (Path.Deref) p.get(i);
			//
			if (!t.isMutable()) {
				// Cannot write through immutable borrow
				return false;
			} else {
				LVal[] borrows = t.lvals();
				// Determine type of first borrow
				Type Tj = typeOf(R, borrows[0]);
				// NOTE: is safe to ignore other lvals because every lval must have a compatible
				// type.
				return mutable(R, Tj, p, i + 1);
			}
		} else {
			return false;
		}
	}

	// ================================================================================
	// Compatible
	// ================================================================================

	/**
	 * Check whether this two types are "compatible" with each other. In particular,
	 * for a variable declared with a given type, it can subsequently only be
	 * assigned values which are compatible with its type. For example, consider the
	 * following:
	 *
	 * <pre>
	 * let mut x = 1;
	 * let mut y = 2;
	 * y = &x;
	 * </pre>
	 *
	 * The final statement attempts to assign an incompatible type to variable
	 * <code>y</code> and, hence, this program is rejected.
	 *
	 * @param R1 --- the environment in which the first type is defined.
	 * @param T1 --- the first type being compared for compatibility.
	 * @param T2 --- the second type being compared for compatibility.
	 * @param R2 --- the environment in which the second type is defined.
	 * @return
	 */
	public boolean compatible(Environment R1, Type T1, Type T2) {
		if (T1 instanceof Type.Unit && T2 instanceof Type.Unit) {
			return true;
		} else if (T1 instanceof Type.Int && T2 instanceof Type.Int) {
			return true;
		} else if (T1 instanceof Type.Borrow && T2 instanceof Type.Borrow) {
			Type.Borrow _T1 = (Type.Borrow) T1;
			Type.Borrow _T2 = (Type.Borrow) T2;
			// NOTE: follow holds because all members of a single borrow must be compatible
			// by construction.
			Type ti = typeOf(R1, _T1.lvals()[0]);
			Type tj = typeOf(R1, _T2.lvals()[0]);
			//
			return _T1.isMutable() == _T2.isMutable() && compatible(R1, ti, tj);
		} else if (T1 instanceof Type.Box && T2 instanceof Type.Box) {
			Type.Box _T1 = (Type.Box) T1;
			Type.Box _T2 = (Type.Box) T2;
			return compatible(R1, _T1.element(), _T2.element());
		} else if (T1 instanceof Type.Undefined && T2 instanceof Type.Undefined) {
			Type.Undefined _T1 = (Type.Undefined) T1;
			Type.Undefined _T2 = (Type.Undefined) T2;
			return compatible(R1, _T1.getType(), _T2.getType());
		} else if (T1 instanceof Type.Undefined) {
			Type.Undefined _T1 = (Type.Undefined) T1;
			return compatible(R1, _T1.getType(), T2);
		} else if (T2 instanceof Type.Undefined) {
			Type.Undefined _T2 = (Type.Undefined) T2;
			return compatible(R1, T1, _T2.getType());
		} else {
			return false;
		}
	}


	// ================================================================================
	// lifetimeOf
	// ================================================================================

	/**
	 * Determine the lifetime of a given lval. For example, consider the following:
	 *
	 * <pre>
	 * {
	 *   let mut x = 1;
	 *   let mut y = &mut x;
	 *   {
	 * 	   let mut z = &mut y;
	 *   }^m
	 * }^l
	 * </pre>
	 *
	 * The lifetime of <code>x</code> and <code>*y</code> is <code>l</code>.
	 * Likewise, <code>*z</code> and <code>**z</code> have lifetime <code>l</code>
	 * whilst <code>z</code> has lifetime <code>m</code>.
	 *
	 * @param env
	 * @param lv
	 * @return
	 */
	protected Lifetime lifetimeOf(Environment env, LVal lv) {
		final String name = lv.name();
		final Path path = lv.path();
		// Extract target cell
		Slot Cx = env.get(name);
		Type T = Cx.type();
		// Process path elements (if any)
		return lifetimeOf(env, Cx.lifetime(), T, path, 0);
	}

	protected Lifetime lifetimeOf(Environment env, Lifetime l, Type type, Path p, int i) {
		// NOTE: in the code calculus, the only form of path element is a Deref. Hence,
		// the following is safe.
		if (p.size() == i) {
			return l;
		} else if (type instanceof Type.Box) {
			Type.Box b = (Type.Box) type;
			return lifetimeOf(env, l, b.element(), p, i + 1);
		} else if (type instanceof Type.Borrow) {
			Type.Borrow b = (Type.Borrow) type;
			LVal[] lvals = b.lvals();
			Lifetime m = null;
			for (int k = 0; k != lvals.length; ++k) {
				LVal kth = lvals[k];
				Slot Ck = env.get(kth.name());
				Type Tk = typeOf(env, kth);
				Lifetime ith = lifetimeOf(env, Ck.lifetime(), Tk, p, i + 1);
				// Determine smallest lifetime
				if(m != null && m.contains(ith)) {
					m = ith;
				} else if(m == null) {
					m = ith;
				}
			}
			return m;
		} else {
			// No valid type exists when dereferencing e.g. an integer, or a shadow.
			return null;
		}
	}

	// ================================================================================
	// typeOf
	// ================================================================================

	/**
	 * Determine the type of an LVal in the given environment, assuming that the
	 * LVal is defined for the environment. For example, the type of <code>x</code>
	 * in the environment <code>{x->int}</code> is simply <code>int</code>.
	 * Likewise, the type of <code>*x</code> in the environment
	 * <code>{x->[]int}</code> is also <code>int</code>.
	 *
	 * @param env
	 * @return
	 */
	protected Type typeOf(Environment env, LVal w) {
		final String name = w.name();
		final Path path = w.path();
		// Extract target cell
		Slot Sw = env.get(name);
		check(Sw != null, BorrowChecker.UNDECLARED_VARIABLE, w);
		Type T = Sw.type();
		// Process path elements (if any)
		for (int i = 0; i != path.size(); ++i) {
			Path.Element ith = path.get(i);
			T = typeOf(env, T, ith);
		}
		return T;
	}

	protected Type typeOf(Environment env, Type type, Path.Element ith) {
		// NOTE: in the code calculus, the only form of path element is a Deref. Hence,
		// the following is safe.
		return typeOf(env, type, (Path.Deref) ith);
	}

	protected Type typeOf(Environment env, Type type, Path.Deref d) {
		if (type instanceof Type.Box) {
			Type.Box b = (Type.Box) type;
			return b.element();
		} else if (type instanceof Type.Borrow) {
			Type.Borrow b = (Type.Borrow) type;
			LVal[] lvals = b.lvals();
			Type T = null;
			for (int i = 0; i != lvals.length; ++i) {
				Type ith = typeOf(env, lvals[i]);
				// FIXME: does union make sense???
				T = (T == null) ? ith : T.union(ith);
			}
			return T;
		} else {
			// No valid type exists when dereferencing e.g. an integer, or a shadow.
			return null;
		}
	}

	// ================================================================================
	// Drop
	// ================================================================================

	/**
	 * Drop all variables declared in a given lifetime. For example, consider the
	 * following:
	 *
	 * <pre>
	 * let mut x = 1;
	 * {
	 *    let mut y = 2;
	 * }
	 * let mut z = y;
	 * </pre>
	 *
	 * Right before the final declaration, all variables allocated in the inner
	 * block are dropped and, subsequently, removed from the environment. This means
	 * that the use of <code>y</code> in the final statement is identified as
	 * referring to an undeclared variable.
	 *
	 * @param env
	 * @param lifetime
	 * @return
	 */
	protected Environment drop(Environment env, Lifetime lifetime) {
		for (String name : env.bindings()) {
			Slot cell = env.get(name);
			if (cell.lifetime().equals(lifetime)) {
				env = env.remove(name);
			}
		}
		return env;
	}

	private static int index = 0;

	/**
	 * Return a unique variable name everytime this is called.
	 *
	 * @return
	 */
	public static String fresh() {
		return "?" + (index++);
	}

	/**
	 * Return a sequence of zero or more fresh variable names
	 *
	 * @param n
	 * @return
	 */
	public static String[] fresh(int n) {
		String[] freshVars = new String[n];
		for (int i = 0; i != n; ++i) {
			freshVars[i] = fresh();
		}
		return freshVars;
	}

	/**
	 * Provides a specific extension mechanism for the borrow checker.
	 *
	 * @author David J. Pearce
	 *
	 */
	public abstract static class Extension implements AbstractTransformer.Extension<BorrowChecker.Environment, Type> {
		protected BorrowChecker self;
	}

	/**
	 * Environment maintains the mapping from variables to cells which characterize
	 * the typing and effect information for a given location.
	 *
	 * @author David J. Pearce
	 *
	 */
	public static class Environment {
		/**
		 * Mapping from variable names to types
		 */
		private final HashMap<String, Slot> mapping;

		/**
		 * Construct an environment for a given lifetime
		 *
		 * @param lifetime
		 */
		private Environment() {
			this.mapping = new HashMap<>();
		}

		/**
		 * Construct an environment for a given liftime and variable mapping
		 *
		 * @param lifetime
		 * @param mapping
		 */
		public Environment(Map<String, Slot> mapping) {
			this.mapping = new HashMap<>(mapping);
		}

		/**
		 * Get the cell associated with a given path
		 *
		 * @param name
		 * @return
		 */
		public Slot get(String name) {
			return mapping.get(name);
		}

		/**
		 * Update the cell associated with a given variable name
		 *
		 * @param name
		 * @param type
		 * @return
		 */
		public Environment put(String name, Type type, Lifetime lifetime) {
			return put(name, new Slot(type, lifetime));
		}

		/**
		 * Update the cell associated with a given variable name
		 *
		 * @param name
		 * @param type
		 * @return
		 */
		public Environment put(String name, Slot cell) {
			Environment nenv = new Environment(mapping);
			nenv.mapping.put(name, cell);
			return nenv;
		}

		/**
		 * Remove a given variable mapping.
		 *
		 * @param name
		 * @return
		 */
		public Environment remove(String name) {
			Environment nenv = new Environment(mapping);
			nenv.mapping.remove(name);
			return nenv;
		}

		/**
		 * Remove a given variable mapping.
		 *
		 * @param name
		 * @return
		 */
		public Environment remove(String... names) {
			Environment nenv = new Environment(mapping);
			for (int i = 0; i != names.length; ++i) {
				nenv.mapping.remove(names[i]);
			}
			return nenv;
		}

		/**
		 * Get collection of all cells in the environment.
		 *
		 * @return
		 */
		public Collection<Slot> cells() {
			return mapping.values();
		}

		/**
		 * Get set of all bound variables in the environment
		 *
		 * @return
		 */
		public Set<String> bindings() {
			return mapping.keySet();
		}

		@Override
		public String toString() {
			String body = "{";
			boolean firstTime = true;
			for (Map.Entry<String, Slot> e : mapping.entrySet()) {
				if (!firstTime) {
					body = body + ",";
				}
				firstTime = false;
				body = body + e.getKey() + ":" + e.getValue();
			}
			return body + "}";
		}
	}

	/**
	 * Represents the information associated about a given variable in the
	 * environment. This includes its <i>type</i> and <i>lifetime</i>.
	 *
	 * @author David J. Pearce
	 *
	 */
	public static class Slot {
		public final Type type;
		public final Lifetime lifetime;

		public Slot(Type f, Lifetime s) {
			this.type = f;
			this.lifetime = s;
		}

		public Type type() {
			return type;
		}

		public Lifetime lifetime() {
			return lifetime;
		}

		@Override
		public boolean equals(Object o) {
			if (o instanceof Slot) {
				Slot c = (Slot) o;
				return type.equals(c.type) && lifetime.equals(c.lifetime);
			}
			return false;
		}

		@Override
		public int hashCode() {
			return type.hashCode() ^ lifetime.hashCode();
		}

		@Override
		public String toString() {
			return "<" + type + ", " + lifetime + ">";
		}
	}

	public void check(boolean result, String msg, SyntacticElement e) {
		if (!result) {
			syntaxError(msg, e);
		}
	}

	protected void syntaxError(String msg, SyntacticElement e) {
		if (e != null) {
			Attribute.Source loc = e.attribute(Attribute.Source.class);
			if (loc != null) {
				throw new SyntaxError(msg, sourcefile, loc.start, loc.end);
			}
		}
		throw new SyntaxError(msg, sourcefile, 0, 0);
	}
}
