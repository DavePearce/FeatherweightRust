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

import featherweightrust.core.BorrowChecker.Cell;
import featherweightrust.core.BorrowChecker.Environment;
import featherweightrust.core.OperationalSemantics.Extension;
import featherweightrust.core.Syntax.Lifetime;
import featherweightrust.core.Syntax.Path;
import featherweightrust.core.Syntax.LVal;
import featherweightrust.core.Syntax.Term;
import featherweightrust.core.Syntax.Type;
import featherweightrust.core.Syntax.Value;
import featherweightrust.core.Syntax.Type.Box;
import featherweightrust.core.Syntax.Type.Shadow;
import featherweightrust.extensions.ControlFlow;
import featherweightrust.util.AbstractTransformer;
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
	public final static Environment EMPTY_ENVIRONMENT = new Environment();
	// Error messages
	public final static String UNDECLARED_VARIABLE = "variable undeclared";
	public final static String CANNOT_MOVEOUT_THROUGH_BORROW = "cannot move out through borrow";
	public final static String VARIABLE_MOVED = "use of moved variable";
	public final static String VARIABLE_ALREADY_DECLARED = "variable already declared";
	public final static String BORROWED_VARIABLE_ASSIGNMENT = "cannot assign because borrowed";
	public final static String NOTWITHIN_VARIABLE_ASSIGNMENT = "cannot assign because lifetime not within";
	public final static String INCOMPATIBLE_TYPE = "incompatible type";
	public final static String VARIABLE_NOT_COPY = "variable's type cannot be copied";
	public final static String EXPECTED_REFERENCE = "expected reference type";
	public final static String EXPECTED_MUTABLE_BORROW = "expected mutable borrow";
	public final static String LVAL_NOT_WRITEABLE = "lval cannot be written (e.g. is moved in part or whole)";
	public final static String LVAL_NOT_READABLE = "lval cannot be read (e.g. is moved in part or whole)";

	public final static String LVAL_WRITE_PROHIBITED = "lval borrowed in part or whole";
	public final static String LVAL_READ_PROHIBITED = "lval mutably borrowed in part or whole";

	protected final String sourcefile;

	public BorrowChecker(String sourcefile, Extension... extensions) {
		super(extensions);
		this.sourcefile = sourcefile;
		// Bind self in extensions
		for (Extension e : extensions) {
			e.self = this;
		}
	}
	@Override
	public Pair<Environment, Type> apply(Environment R1, Lifetime l, Term t) {
		Pair<Environment,Type> p = super.apply(R1,l,t);
		Environment R2 = p.first();
		Type T = p.second();
		System.out.println(R1 + " |- " + t + " : " + T + " -| " + R2);
		System.out.println("---------------------------------------");
		return p;
	}

	/**
	 * T-Declare
	 */
	@Override
	public Pair<Environment, Type> apply(Environment R1, Lifetime l, Term.Let t) {
		// Sanity check variable not already declared
		String x = t.variable();
		Cell C1 = R1.get(x);
		check(C1 == null, VARIABLE_ALREADY_DECLARED, t);
		// Type operand
		Pair<Environment, Type> p = apply(R1, l, t.initialiser());
		Environment R2 = p.first();
		Type T = p.second();
		// Update environment and discard type (as unused for statements)
		Environment R3 = R2.put(x, T, l);
		// Done
		return new Pair<>(R3, Type.Void);
	}

	/**
	 * T-Assign
	 */
	@Override
	public Pair<Environment, Type> apply(Environment R1, Lifetime l, Term.Assignment t) {
		LVal lv = t.leftOperand();
		// Declaration check
		check(R1.get(lv.name()) != null, UNDECLARED_VARIABLE, lv);
		// Type operand
		Pair<Environment, Type> p = apply(R1, l, t.rightOperand());
		Environment R2 = p.first();
		Type T2 = p.second();
		check(available(R2, lv, true), LVAL_NOT_WRITEABLE, t.leftOperand());
		// Write prohibited check
		check(!writeProhibited(R2,lv), LVAL_WRITE_PROHIBITED, t.leftOperand());
		// Write the type
		Environment R3 = write(R2, lv, T2, true);
		//
		return new Pair<>(R3, Type.Void);
	}

	/**
	 * T-Block
	 */
	@Override
	public Pair<Environment, Type> apply(Environment R1, Lifetime l, Term.Block t) {
		Pair<Environment, Type> p = apply(R1, t.lifetime(), t.toArray());
		Environment R2 = p.first();
		// FIXME: need to add phi
		//
		Environment R3 = drop(R2, t.lifetime());
		//
		return new Pair<>(R3, p.second());
	}

	/**
	 * T-Seq
	 */
	public Pair<Environment, Type> apply(Environment R1, Lifetime l, Term... ts) {
		Environment Rn = R1;
		Type Tn = Type.Void;
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
	 * T-Deref
	 */
	@Override
	public Pair<Environment, Type> apply(Environment R, Lifetime l, Term.Dereference t) {
		return read(R, t.operand());
	}

	/**
	 * T-MutBorrow and T-ImmBorrow
	 */
	@Override
	public Pair<Environment, Type> apply(Environment R, Lifetime lifetime, Term.Borrow t) {
		LVal lv = t.operand();
		Cell Cx = R.get(lv.name());
		// Check variable is declared
		check(Cx != null, UNDECLARED_VARIABLE, t);
		// Check lval can be at least read
		check(available(R,lv,false), LVAL_NOT_READABLE, lv);
		//
		if (t.isMutable()) {
			// T-MutBorrow
			// Check lval can be also written
			check(available(R,lv,true), LVAL_NOT_WRITEABLE, lv);
			// Check nothing else prohibiting lval being written
			check(!writeProhibited(R, lv), LVAL_WRITE_PROHIBITED, t.operand());
		} else {
			// T-ImmBorrow
			// Check nothing else prohibiting lval being read
			check(!readProhibited(R, lv), LVAL_READ_PROHIBITED, t.operand());
		}
		//
		return new Pair<>(R, new Type.Borrow(t.isMutable(), lv));
	}

	/**
	 * T-Box
	 */
	@Override
	public Pair<Environment, Type> apply(Environment R1, Lifetime l, Term.Box t) {
		// Type operand
		Pair<Environment, Type> p = apply(R1, l, t.operand());
		Environment R2 = p.first();
		Type T = p.second();
		//
		return new Pair<>(R2, new Type.Box(T));
	}

	/**
	 * T-Const
	 */
	@Override
	public Pair<Environment, Type> apply(Environment R, Lifetime l, Value.Integer t) {
		return new Pair<>(R, Type.Int);
	}

	@Override
	public Pair<Environment, Type> apply(Environment R, Lifetime l, Value.Unit t) {
		// NOTE: Safe since unit not part of source level syntax.
		throw new UnsupportedOperationException();
	}

	@Override
	public Pair<Environment, Type> apply(Environment R, Lifetime l, Value.Location t) {
		// NOTE: Safe since locations not part of source level syntax.
		throw new UnsupportedOperationException();
	}

	// ================================================================================
	// Read
	// ================================================================================

	/**
	 * Read the value of a given lval from the environment, leading to a potentially
	 * updated environment. For example, if we are reading a type which is not copy,
	 * then it is moved and this must be reflected in the updated environment.
	 *
	 * @param R  The environment in which the read is occuring.
	 * @param lv The lval being read.
	 * @return
	 */
	public Pair<Environment, Type> read(Environment R, LVal lv) {
		final String x = lv.name();
		final Path path = lv.path();
		// Extract target cell
		Cell Cx = R.get(x);
		check(Cx != null, BorrowChecker.UNDECLARED_VARIABLE, lv);
		Type T1 = Cx.type();
		// Check available at least for reading
		check(available(R, lv, false), VARIABLE_MOVED, lv);
		// Determine type being read
		Type T2 = typeOf(R,lv);
		// Decide if copy or move
		if(T2.copyable()) {
			// Check variable readable (e.g. not mutably borrowed)
			check(!readProhibited(R, lv), LVAL_READ_PROHIBITED, lv);
			// Done
			return new Pair<Environment, Type>(R,T2);
		} else {
			Lifetime m = Cx.lifetime();
			// Check available for writing
			check(available(R, lv, true), VARIABLE_MOVED, lv);
			// Check variable writeable (e.g. not borrowed). This is necessary because we
			// going to move this value out completely and, hence, we must have ownership to
			// do this safely.
			check(!writeProhibited(R, lv), LVAL_WRITE_PROHIBITED, lv);
			// Apply destructive update
			Environment R2 = R.put(x, new Cell(move(T1,path,0),m));
			// Done
			return new Pair<Environment, Type>(R2, T2);
		}
	}

	public Type move(Type T, Path p, int i) {
		if (p.size() == i) {
			return new Type.Shadow(T);
		} else if (T instanceof Type.Box) {
			// In core calculus, dereferences are only valid path elements.
			Path.Deref ith = (Path.Deref) p.get(i);
			Type.Box B = (Type.Box) T;
			return new Type.Box(move(B.element(), p, i + 1));
		} else {
			Type.Borrow b = (Type.Borrow) T;
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
	public Environment write(Environment R1, LVal lv, Type T1, boolean strong) {
		System.out.println("WRITING: " + lv + "=" + T1 + " in " + R1);
		Path path = lv.path();
		// Extract target cell
		Cell Cx = R1.get(lv.name());
		// Declaration check
		check(Cx != null, BorrowChecker.UNDECLARED_VARIABLE, lv);
		// Destructure
		Type T2 = Cx.type();
		Lifetime m = Cx.lifetime();
		// lifetime check
		check(T1.within(this, R1, m), NOTWITHIN_VARIABLE_ASSIGNMENT, lv);
		// Determine type of lval
		Type T3 = typeOf(R1,lv);
		// Check compatibility
		check(compatible(R1, T3, T1, R1), INCOMPATIBLE_TYPE, lv);
		// Apply write
		System.out.println("WRITING(2): " + path.toString(T2.toString()) + "=" + T1);
		Pair<Environment, Type> p = write(R1, T2, path, 0, T1, strong);
		Environment R2 = p.first();
		Type T4 = p.second();
		// Update environment
		return R2.put(lv.name(), T4, m);
	}

	public Pair<Environment, Type> write(Environment R, Type T1, Path p, int i, Type T2, boolean strong) {
		if (i == p.size()) {
			if(strong) {
				return new Pair<>(R, T2);
			} else {
				return new Pair<>(R, T1.intersect(T2));
			}
		} else {
			// NOTE: Path elements are always Deref in the core calculus.
			Path.Deref ith = (Path.Deref) p.get(i);
			//
			if (T1 instanceof Type.Borrow) {
				return write(R, (Type.Borrow) T1, p, i, T2);
			} else {
				return write(R, (Type.Box) T1, p, i, T2);
			}
		}
	}

	public Pair<Environment,Type> write(Environment R, Type.Borrow T1, Path p, int i, Type T2) {
		// T-BorrowAssign
		Type.Borrow b = (Type.Borrow) T1;
		LVal[] ys = b.slices();
		// Mutability check
		check(b.isMutable(), EXPECTED_MUTABLE_BORROW, p);
		//
		Environment Rn = R;
		// Consider all targets
		for (int j = 0; j != ys.length; ++j) {
			LVal y = ys[j];
			Cell Cy = R.get(y.name());
			Type Ty = typeOf(Rn, y);
			System.out.println("IN: " + y + " : " + T2);
			// NOTE: this prohibits a strong update in certain cases where it may, in fact,
			// be possible to do this. It's not clear to me that this is always necessary or
			// even desirable. However, at the time of writing, this mimics the Rust
			// compiler.
//			Pair<Environment,Type> r = write(Rn, Ty, p, i + 1, T2, false);
//			Rn = r.first();
//			// Update environment
//			Rn = Rn.put(y.name(), r.second(), Cy.lifetime());
			Rn = write(R, y, T2, false);
		}
		//
		return new Pair<>(Rn, T1);
	}

	public Pair<Environment, Type> write(Environment R, Type.Box T1, Path p, int i, Type T2) {
		// T-BoxAssign
		Type.Box T3 = (Type.Box) T1;
		// NOTE: this prohibits a strong update when, in fact, it's always be possible
		// to do this. It's not clear to me that this is always necessary or even
		// desirable. However, at the time of writing, this mimics the Rust
		// compiler.
		Pair<Environment, Type> r = write(R, T3.element(), p, i + 1, T2, false);
		// Done
		return new Pair<Environment, Type>(r.first(), new Type.Box(r.second()));
	}

	// ================================================================================
	// Compatible
	// ================================================================================

	/**
	 * Check whether a given LVal is available for reading or writing. Roughly
	 * speaking, whether or not some subcomponent has been moved. For example:
	 *
	 * <pre>
	 * let mut x = box box 1;
	 * let mut y = *x;
	 * let mut z = x;
	 * </pre>
	 *
	 * The above program is rejected because it attempts to perform a <i>partial
	 * move</i> in the terminology of Rust. At the point of the final assignment,
	 * <code>x</code> has the type <code>☐/☐int/</code> (box shadow box int). Such a
	 * type (i.e. which contains an alias) is said to be <i>unavailable</i> for
	 * reading. In contrast, we could actually write to this type under some
	 * circumstances. For example, the following is acceptable:
	 *
	 * <pre>
	 * let mut x = box box 1;
	 * let mut y = *x;
	 * *x = box 2
	 * </pre>
	 *
	 * However, we need to be careful that we are not trying to "write thru" a
	 * shadow, as the following illustrates:
	 *
	 * <pre>
	 * let mut x = box box box 1;
	 * let mut y = *x;
	 * **x = box 2
	 * </pre>
	 *
	 * @param R   Current environment
	 * @param lv  LVal being checked for availability
	 * @param mut indicates whether mutable or immutable access
	 * @return
	 */
	public boolean available(Environment R, LVal lv, boolean mut) {
		System.out.println("CHECKING AVAILABLE: " + lv + " in " + R);
		Cell Cx = R.get(lv.name());
		// NOTE: can assume here that declaration check on lv.name() has already
		// occurred.  Hence, Cx != null
		return available(R, Cx.type(), lv.path(), 0, mut);
	}

	public boolean available(Environment R, Type T, Path p, int i, boolean mut) {
		if(p.size() == i) {
			// NOTE: Can always write to the top-level, but cannot read if its been moved
			// previously.
			return mut || T.moveable();
		} else if(T instanceof Type.Shadow) {
			// Otherwise, must read value to e.g. dereference it. Hence, if has been moved,
			// then this doesn't work.
			return false;
		} else if(T instanceof Type.Box) {
			Type.Box B = (Type.Box) T;
			// Check path element is dereference
			Path.Deref d = (Path.Deref) p.get(i);
			// Continue writing
			return available(R, B.element(), p, i + 1, mut);
		} else if(T instanceof Type.Borrow) {
			Type.Borrow t = (Type.Borrow) T;
			// Check path element is dereference
			Path.Deref d = (Path.Deref) p.get(i);
			//
			if(!t.isMutable() && mut) {
				// Cannot write through immutable borrow
				return false;
			} else {
				// NOTE: following is safe because of the invariant that the type of anything
				// borrowed is available. This is because one cannot "move out" or a borrow.
				return true;
			}
		} else {
			throw new IllegalArgumentException("unknown type encountered: " + T);
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
	public boolean compatible(Environment R1, Type T1, Type T2, Environment R2) {
		if(T1 instanceof Type.Void && T2 instanceof Type.Void) {
			return true;
		} else if(T1 instanceof Type.Int && T2 instanceof Type.Int) {
			return true;
		} else if(T1 instanceof Type.Borrow && T2 instanceof Type.Borrow) {
			Type.Borrow _T1 = (Type.Borrow) T1;
			Type.Borrow _T2 = (Type.Borrow) T2;
			// NOTE: follow holds because all members of a single borrow must be compatible
			// by construction.
			Type ti = typeOf(R1,_T1.slices()[0]);
			Type tj = typeOf(R2,_T2.slices()[0]);
			//
			return _T1.isMutable() == _T2.isMutable() && compatible(R1, ti, tj, R2);
		} else if(T1 instanceof Type.Box && T2 instanceof Type.Box) {
			Type.Box _T1 = (Type.Box) T1;
			Type.Box _T2 = (Type.Box) T2;
			return compatible(R1, _T1.element(), _T2.element(), R2);
		} else if(T1 instanceof Type.Shadow && T2 instanceof Type.Shadow) {
			Type.Shadow _T1 = (Type.Shadow) T1;
			Type.Shadow _T2 = (Type.Shadow) T2;
			return compatible(R1, _T1.getType(), _T2.getType(), R2);
		} else if(T1 instanceof Type.Shadow) {
			Type.Shadow _T1 = (Type.Shadow) T1;
			return compatible(R1, _T1.getType(), T2, R2);
		} else if(T2 instanceof Type.Shadow) {
			Type.Shadow _T2 = (Type.Shadow) T2;
			return compatible(R1, T1, _T2.getType(), R2);
		} else {
			return false;
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
	public Type typeOf(Environment env, LVal lv) {
		final String name = lv.name();
		final Path path = lv.path();
		// Extract target cell
		Cell Cx = env.get(name);
		Type T = Cx.type();
		// Process path elements (if any)
		for (int i = 0; i != path.size(); ++i) {
			Path.Element ith = path.get(i);
			T = typeOf(env,T,ith);
		}
		return T;
	}

	public Type typeOf(Environment env, Type type, Path.Element ith) {
		// NOTE: in the code calculys, the only form of path element is a Deref. Hence,
		// the following is safe.
		return typeOf(env,type,(Path.Deref) ith);
	}

	public Type typeOf(Environment env, Type type, Path.Deref d) {
		if (type instanceof Type.Box) {
			Type.Box b = (Type.Box) type;
			return b.element();
		} else if (type instanceof Type.Borrow) {
			Type.Borrow b = (Type.Borrow) type;
			LVal[] lvals = b.slices();
			Type T = null;
			for (int i = 0; i != lvals.length; ++i) {
				Type ith = typeOf(env, lvals[i]);
				// FIXME: does union make sense???
				T = (T == null) ? ith : T.union(ith);
			}
			return T;
		} else {
			throw new IllegalArgumentException("unknown type encountered: " + type);
		}
	}

	// ================================================================================
	// Read / Write Probibited
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
	public static boolean writeProhibited(Environment R, LVal lv) {
		// Look through all types to whether any prohibit writing this lval
		for (Cell cell : R.cells()) {
			Type type = cell.type();
			if (type.prohibitsWriting(lv)) {
				return true;
			}
		}
		return false;
	}

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
	public static boolean readProhibited(Environment R, LVal lv) {
		// Look through all types to whether any prohibit reading this lval
		for (Cell cell : R.cells()) {
			Type type = cell.type();
			if (type.prohibitsReading(lv)) {
				return true;
			}
		}
		return false;
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
	public Environment drop(Environment env, Lifetime lifetime) {
		for (String name : env.bindings()) {
			Cell cell = env.get(name);
			if (cell.lifetime().equals(lifetime)) {
				env = env.remove(name);
			}
		}
		return env;
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
		private final HashMap<String, Cell> mapping;

		/**
		 * Construct an environment for a given lifetime
		 *
		 * @param lifetime
		 */
		public Environment() {
			this.mapping = new HashMap<>();
		}

		/**
		 * Construct an environment for a given liftime and variable mapping
		 *
		 * @param lifetime
		 * @param mapping
		 */
		public Environment(Map<String, Cell> mapping) {
			this.mapping = new HashMap<>(mapping);
		}

		/**
		 * Get the cell associated with a given path
		 *
		 * @param name
		 * @return
		 */
		public Cell get(String name) {
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
			return put(name, new Cell(type, lifetime));
		}

		/**
		 * Update the cell associated with a given variable name
		 *
		 * @param name
		 * @param type
		 * @return
		 */
		public Environment put(String name, Cell cell) {
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
			for(int i=0;i!=names.length;++i) {
				nenv.mapping.remove(names[i]);
			}
			return nenv;
		}

		/**
		 * Get collection of all cells in the environment.
		 *
		 * @return
		 */
		public Collection<Cell> cells() {
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
			for (Map.Entry<String, Cell> e : mapping.entrySet()) {
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
	public static class Cell {
		public final Type type;
		public final Lifetime lifetime;

		public Cell(Type f, Lifetime s) {
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
			if (o instanceof Cell) {
				Cell c = (Cell) o;
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

	public void syntaxError(String msg, SyntacticElement e) {
		if(e != null) {
			Attribute.Source loc = e.attribute(Attribute.Source.class);
			if (loc != null) {
				throw new SyntaxError(msg, sourcefile, loc.start, loc.end);
			}
		}
		throw new SyntaxError(msg, sourcefile, 0, 0);
	}
}
