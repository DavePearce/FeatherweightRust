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

import featherweightrust.core.OperationalSemantics.Extension;
import featherweightrust.core.Syntax.Lifetime;
import featherweightrust.core.Syntax.Path;
import featherweightrust.core.Syntax.Slice;
import featherweightrust.core.Syntax.Term;
import featherweightrust.core.Syntax.Type;
import featherweightrust.core.Syntax.Value;
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
	public final static String VARIABLE_MOVED = "use of moved variable";
	public final static String VARIABLE_ALREADY_DECLARED = "variable already declared";
	public final static String BORROWED_VARIABLE_ASSIGNMENT = "cannot assign because borrowed";
	public final static String NOTWITHIN_VARIABLE_ASSIGNMENT = "cannot assign because lifetime not within";
	public final static String INCOMPATIBLE_TYPE = "incompatible type";
	public final static String VARIABLE_NOT_COPY = "variable's type cannot be copied";
	public final static String EXPECTED_REFERENCE = "expected reference type";
	public final static String VARIABLE_BORROWED = "variable already borrowed";
	public final static String VARIABLE_MUTABLY_BORROWED = "variable already mutably borrowed";

	private final String sourcefile;

	public BorrowChecker(String sourcefile, Extension... extensions) {
		super(extensions);
		this.sourcefile = sourcefile;
		// Bind self in extensions
		for (Extension e : extensions) {
			e.self = this;
		}
	}

	/**
	 * T-Declare
	 */
	@Override
	public Pair<Environment, Type> apply(Environment R1, Lifetime l, Term.Let t) {
		// Sanity check variable not already declared
		Term.Variable x = t.variable();
		Cell C1 = R1.get(x.name());
		check(C1 == null, VARIABLE_ALREADY_DECLARED, t.variable());
		// Type operand
		Pair<Environment, Type> p = apply(R1, l, t.initialiser());
		Environment R2 = p.first();
		Type T = p.second();
		// Update environment and discard type (as unused for statements)
		Environment R3 = R2.put(x.name(), T, l);
		// Done
		return new Pair<>(R3, Type.Void);
	}

	/**
	 * T-Assign
	 */
	@Override
	public Pair<Environment, Type> apply(Environment R1, Lifetime l, Term.Assignment t) {
		Term.Variable x = t.leftOperand();
		// Extract variable's existing type
		Cell Cx = R1.get(x.name());
		check(Cx != null, UNDECLARED_VARIABLE, t.leftOperand());
		Type T1 = Cx.type();
		Lifetime m = Cx.lifetime();
		// Type operand
		Pair<Environment, Type> p = apply(R1, l, t.rightOperand());
		Environment R2 = p.first();
		Type T2 = p.second();
		// lifetime check
		check(T2.within(this, R2, m), NOTWITHIN_VARIABLE_ASSIGNMENT, t);
		// Check compatibility
		check(T1.compatible(R2, T2, R2), INCOMPATIBLE_TYPE, t.rightOperand());
		// Update environment
		Environment R3 = R2.put(x.name(), T2, m);
		// Check borrow status
		check(!borrowed(R3, x), BORROWED_VARIABLE_ASSIGNMENT, t.leftOperand());
		//
		return new Pair<>(R3, Type.Void);
	}

	/**
	 * T-BorrowAssign and T-BoxAssign
	 */
	@Override
	public Pair<Environment, Type> apply(Environment R1, Lifetime l, Term.IndirectAssignment t) {
		Environment R3;
		Term.Variable x = t.leftOperand();
		// (1) Type operand
		Pair<Environment, Type> p = apply(R1, l, t.rightOperand());
		Environment R2 = p.first();
		Type T1 = p.second();
		// (2) Extract x's type info
		Cell Cx = R2.get(x.name());
		// Check variable is declared
		check(Cx != null, UNDECLARED_VARIABLE, t.leftOperand());
		// Check variable not moved
		check(!Cx.moved(), VARIABLE_MOVED, t.leftOperand());
		Type T0 = Cx.type();
		//
		if (T0 instanceof Type.Borrow && ((Type.Borrow) T0).isMutable()) {
			// T-BorrowAssign
			Type.Borrow b = (Type.Borrow) T0;
			Slice[] ys = b.slices();
			// Prepare new environment
			R3 = R2;
			// Consider all targets
			for (int i = 0; i != ys.length; ++i) {
				Slice y = ys[i];
				// (2) Extract y's type
				Cell Cy = R2.get(y.name());
				check(Cy != null, UNDECLARED_VARIABLE, b);
				Type Ty = Cy.type();
				Lifetime m = Cy.lifetime();
				// Extract type of borrowed location
				Type T2 = Ty.read(0, y.path());
				// (4) Check lifetimes
				check(T1.within(this, R2, m), NOTWITHIN_VARIABLE_ASSIGNMENT, t);
				// (5) Check compatibility
				check(T2.compatible(R2, T1, R2), INCOMPATIBLE_TYPE, t.rightOperand());
				// Weak update for environment
				R3 = R3.put(y.name(), Cy.write(y.path(), T1.join(T2)));
			}
		} else if (T0 instanceof Type.Box) {
			Lifetime m = Cx.lifetime();
			// T-BoxAssign
			Type T2 = ((Type.Box) T0).element();
			// (3) Check lifetimes
			check(T1.within(this, R2, m), NOTWITHIN_VARIABLE_ASSIGNMENT, t);
			// (4) Check compatibility
			check(T2.compatible(R2, T1, R2), INCOMPATIBLE_TYPE, t.rightOperand());
			// Update environment
			R3 = R2.put(x.name(), new Type.Box(T1.join(T2)), m);
		} else {
			syntaxError("expected mutable reference", t.leftOperand());
			return null; // deadcode
		}
		//
		check(!borrowed(R3, x), BORROWED_VARIABLE_ASSIGNMENT, t.leftOperand());
		// Done
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
	 * T-BoxDeref & T-BorrowDeref
	 */
	@Override
	public Pair<Environment, Type> apply(Environment R, Lifetime l, Term.Dereference t) {
		Term.Variable x = t.operand();
		Cell Cx = R.get(x.name());
		// Check variable is declared
		check(Cx != null, UNDECLARED_VARIABLE, t);
		// Check variable not moved
		check(!Cx.moved(), VARIABLE_MOVED, t);
		// Check variable x not mutable borrowed
		check(!mutBorrowed(R, x), VARIABLE_MUTABLY_BORROWED, t.operand());
		// Check operand has reference type
		if (Cx.type() instanceof Type.Box) {
			// T-BoxDeref
			Type T = ((Type.Box) Cx.type()).element;
			//
			check(T.copyable(), VARIABLE_NOT_COPY, t);
			//
			return new Pair<>(R, T);
		} else if (Cx.type() instanceof Type.Borrow) {
			Type.Borrow Cx_type = (Type.Borrow) Cx.type();
			// T-BorrowDeref
			Type T = R.typeOf(Cx_type.slices());
			//
			check(T.copyable(), VARIABLE_NOT_COPY, t);
			//
			return new Pair<>(R, T);
		} else {
			syntaxError(EXPECTED_REFERENCE, t);
			return null; // deadcode
		}
	}

	/**
	 * T-MoveVar
	 */
	@Override
	public Pair<Environment, Type> apply(Environment R1, Lifetime l, Term.Variable x) {
		Cell Cx = R1.get(x.name());
		// Check variable is declared
		check(Cx != null, UNDECLARED_VARIABLE, x);
		// Check variable not moved
		check(!Cx.moved(), VARIABLE_MOVED, x);
		// Extract type from current environment
		Type T = Cx.type();
		// Check variable not borrowed
		check(!borrowed(R1, x), VARIABLE_BORROWED, x);
		// Implement destructive update
		Environment R2 = R1.move(x.name());
		//
		return new Pair<>(R2, T);
	}

	/**
	 * T-CopyVar
	 */
	@Override
	public Pair<Environment, Type> apply(Environment R, Lifetime l, Term.Copy t) {
		Term.Variable x = t.operand();
		Cell Cx = R.get(x.name());
		// Check variable is declared
		check(Cx != null, UNDECLARED_VARIABLE, t);
		// Check variable not moved
		check(!Cx.moved(), VARIABLE_MOVED, t);
		// Extract type from current environment
		Type T = Cx.type();
		// Check variable has copy type
		check(T.copyable(), VARIABLE_NOT_COPY, t.operand());
		// Check variable not mutably borrowed
		check(!mutBorrowed(R, x), VARIABLE_BORROWED, t);
		//
		return new Pair<>(R, T);
	}

	/**
	 * T-MutBorrow and T-ImmBorrow
	 */
	@Override
	public Pair<Environment, Type> apply(Environment R, Lifetime lifetime, Term.Borrow t) {
		Slice s = t.operand();
		Cell Cx = R.get(s.name());
		// Check variable is declared
		// FIXME: doesn't ensure borrowed variable has appropriate structure.
		check(Cx != null, UNDECLARED_VARIABLE, t);
		// Check variable not moved
		// FIXME: this is broken.
		check(!Cx.moved(), VARIABLE_MOVED, t);
		//
		if (t.isMutable()) {
			// T-MutBorrow
			check(!borrowed(R, s), VARIABLE_BORROWED, t.operand());
		} else {
			// T-ImmBorrow
			check(!mutBorrowed(R, s), VARIABLE_MUTABLY_BORROWED, t.operand());
		}
		//
		return new Pair<>(R, new Type.Borrow(t.isMutable(), s));
	}

	/**
	 * T-Box
	 */
	@Override
	public Pair<Environment, Type> apply(Environment R, Lifetime l, Term.Box t) {
		// Type operand
		Pair<Environment, Type> p = apply(R, l, t.operand());
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
		return new Pair<>(R, new Type.Int());
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

	/**
	 * Check whether the location bound to a given variable is borrowed or not.
	 *
	 * @param env
	 * @param var
	 * @return
	 */
	public static boolean borrowed(Environment env, String name, Path path) {
		// Look through all types to whether for matching borrow
		for (Cell cell : env.cells()) {
			Type type = cell.type();
			if (type.borrowed(name, path, false)) {
				return true;
			}
		}
		return false;
	}

	public static boolean borrowed(Environment env, Term.Variable x) {
		return borrowed(env,x.name(),Path.EMPTY);
	}

	public static boolean borrowed(Environment env, Slice s) {
		return borrowed(env,s.name(),s.path());
	}

	/**
	 * Check whether the location bound to a given variable is mutably borrowed or
	 * not.
	 *
	 * @param env
	 * @param var
	 * @return
	 */
	public static boolean mutBorrowed(Environment env, String name, Path path) {
		// Look through all types to whether for matching (mutable) borrow
		for (Cell cell : env.cells()) {
			Type type = cell.type();
			if (type.borrowed(name, path, true)) {
				return true;
			}
		}
		return false;
	}

	public static boolean mutBorrowed(Environment env, Term.Variable x) {
		return mutBorrowed(env, x.name(), Path.EMPTY);
	}

	public static boolean mutBorrowed(Environment env, Slice s) {
		return mutBorrowed(env, s.name(), s.path());
	}

	/**
	 * Drop all variables declared in a given lifetime.
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
	 * Environment maintains the mapping from variables to cells which characterise
	 * the typing and effect information for a given location.
	 *
	 * @author djp
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
		 * Get the type that a given slice corresponds to. This might be the type of a
		 * given cell, or some port of it.
		 *
		 * @param slice
		 * @return
		 */
		public Type typeOf(Slice slice) {
			Cell c = mapping.get(slice.name());
			return c.type().read(0, slice.path());
		}

		/**
		 * Determine a representative type for a sequence of one or more slices.
		 *
		 * @param slices
		 * @return
		 */
		public Type typeOf(Slice... slices) {
			Type type = typeOf(slices[0]);
			for (int i = 1; i != slices.length; ++i) {
				type = type.join(typeOf(slices[i]));
			}
			return type;
		}

		/**
		 * Update the cell associated with a given variable name
		 *
		 * @param name
		 * @param type
		 * @return
		 */
		public Environment put(String name, Type type, Lifetime lifetime) {
			return put(name, new Cell(type, lifetime, true));
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
		 * Update the type associated with a given variable name
		 *
		 * @param name
		 * @param type
		 * @return
		 */
		public Environment move(String name) {
			Cell old = mapping.get(name);
			Environment nenv = new Environment(mapping);
			nenv.mapping.put(name, new Cell(old.type(), old.lifetime(), false));
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

	public static class Cell {
		public final Type type;
		public final Lifetime lifetime;
		public final boolean valid;

		public Cell(Type f, Lifetime s, boolean valid) {
			this.type = f;
			this.lifetime = s;
			this.valid = valid;
		}

		public boolean moved() {
			return !valid;
		}

		public Type type() {
			return type;
		}

		public Lifetime lifetime() {
			return lifetime;
		}

		public Cell write(Path path, Type t) {
			t = type.write(0, path, t);
			// FIXME: valid is wrong
			return new Cell(t, lifetime, valid);
		}

		@Override
		public boolean equals(Object o) {
			if (o instanceof Cell) {
				Cell c = (Cell) o;
				return type.equals(c.type) && lifetime.equals(c.lifetime) && valid == c.valid;
			}
			return false;
		}

		@Override
		public int hashCode() {
			return type.hashCode() ^ lifetime.hashCode();
		}

		@Override
		public String toString() {
			String v = valid ? "" : "!";
			return "<" + v + type + ", " + lifetime + ">";
		}
	}

	public void check(boolean result, String msg, SyntacticElement e) {
		if (!result) {
			syntaxError(msg, e);
		}
	}

	public void syntaxError(String msg, SyntacticElement e) {
		Attribute.Source loc = e.attribute(Attribute.Source.class);
		if (loc != null) {
			throw new SyntaxError(msg, sourcefile, loc.start, loc.end);
		} else {
			throw new SyntaxError(msg, sourcefile, 0, 0);
		}
	}
}
