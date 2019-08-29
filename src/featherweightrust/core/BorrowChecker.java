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

import featherweightrust.core.Syntax.Expr;
import featherweightrust.core.Syntax.Expr.Copy;
import featherweightrust.core.Syntax.Lifetime;
import featherweightrust.core.Syntax.Stmt;
import featherweightrust.core.Syntax.Type;
import featherweightrust.core.Syntax.Value;
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
public class BorrowChecker extends AbstractTransformer<BorrowChecker.Environment, Type, Type> {
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

	public BorrowChecker(String sourcefile) {
		this.sourcefile = sourcefile;
	}

	/**
	 * T-Declare
	 */
	@Override
	public Pair<Environment, Type> apply(Environment R1, Lifetime l, Stmt.Let s) {
		// Sanity check variable not already declared
		String x = s.variable().name();
		Cell C1 = R1.get(x);
		check(C1 == null, VARIABLE_ALREADY_DECLARED, s.variable());
		// Type operand
		Pair<Environment, Type> p = apply(R1, l, s.initialiser());
		Environment R2 = p.first();
		Type T = p.second();
		// Update environment and discard type (as unused for statements)
		Environment R3 = R2.put(x, T, l);
		// Done
		return new Pair<>(R3, null);
	}

	/**
	 * T-Assign
	 */
	@Override
	public Pair<Environment, Type> apply(Environment R1, Lifetime l, Stmt.Assignment s) {
		String x = s.leftOperand().name();
		// Extract variable's existing type
		Cell Cx = R1.get(x);
		check(Cx != null, UNDECLARED_VARIABLE, s.leftOperand());
		Type T1 = Cx.type();
		Lifetime m = Cx.lifetime();
		// Type operand
		Pair<Environment, Type> p = apply(R1, l, s.rightOperand());
		Environment R2 = p.first();
		Type T2 = p.second();
		// lifetime check
		check(within(R2,T2,m),NOTWITHIN_VARIABLE_ASSIGNMENT,s);
		// Check compatibility
		check(compatible(R2, T1, T2), INCOMPATIBLE_TYPE, s.rightOperand());
		// Update environment
		Environment R3 = R2.put(x, T2, m);
		// Check borrow status
		check(!borrowed(R3,x), BORROWED_VARIABLE_ASSIGNMENT, s.leftOperand());
		//
		return new Pair<>(R3, null);
	}

	/**
	 * T-BorrowAssign and T-BoxAssign
	 */
	@Override
	public Pair<Environment, Type> apply(Environment R1, Lifetime l, Stmt.IndirectAssignment s) {
		Environment R3;
		String x = s.leftOperand().name();
		// (1) Type operand
		Pair<Environment, Type> p = apply(R1, l, s.rightOperand());
		Environment R2 = p.first();
		Type T1 = p.second();
		// (2) Extract x's type info
		Cell Cx = R2.get(x);
		// Check variable is declared
		check(Cx != null, UNDECLARED_VARIABLE, s.leftOperand());
		// Check variable not moved
		check(!Cx.moved(), VARIABLE_MOVED, s.leftOperand());
		Type T0 = Cx.type();
		//
		if(T0 instanceof Type.Borrow && ((Type.Borrow) T0).isMutable()) {
			// T-BorrowAssign
			Type.Borrow b = (Type.Borrow) T0;
			String y = b.name();
			// (2) Extract y's type
			Cell Cy = R2.get(y);
			check(Cy != null, UNDECLARED_VARIABLE, b);
			Type T2 = Cy.type();
			Lifetime m = Cy.lifetime();
			// (4) Check lifetimes
			check(within(R2,T1,m),NOTWITHIN_VARIABLE_ASSIGNMENT,s);
			// (5) Check compatibility
			check(compatible(R2, T2, T1), INCOMPATIBLE_TYPE, s.rightOperand());
			// Update environment
			R3 = R2.put(y, T1, m);
		} else if(T0 instanceof Type.Box) {
			Lifetime m = Cx.lifetime();
			// T-BoxAssign
			Type T2 = ((Type.Box) T0).element();
			// (3) Check lifetimes
			check(within(R2,T1,m),NOTWITHIN_VARIABLE_ASSIGNMENT,s);
			// (4) Check compatibility
			check(compatible(R2, T2, T1), INCOMPATIBLE_TYPE, s.rightOperand());
			// Update environment
			R3 = R2.put(x, new Type.Box(T1), m);
		} else {
			syntaxError("expected mutable reference",s.leftOperand());
			return null; // deadcode
		}
		//
		check(!borrowed(R3,x), BORROWED_VARIABLE_ASSIGNMENT, s.leftOperand());
		// Done
		return new Pair<>(R3, null);
	}

	/**
	 * T-Block
	 */
	@Override
	public Pair<Environment, Type> apply(Environment R1, Lifetime l, Stmt.Block s) {
		Pair<Environment,Type> p = apply(R1,s.lifetime(),s.toArray());
		Environment R2 = p.first();
		// FIXME: need to add phi
		//
		Environment R3 = drop(R2,s.lifetime());
		//
		return new Pair<>(R3, null);
	}

	/**
	 * T-Seq
	 */
	public Pair<Environment, Type> apply(Environment R1, Lifetime l, Stmt... stmts) {
		Environment Rn = R1;
		for (int i = 0; i != stmts.length; ++i) {
			// Type statement
			Pair<Environment, Type> p = apply(Rn, l, stmts[i]);
			// Update environment and discard type (as unused for statements)
			Rn = p.first();
		}
		//
		return new Pair<>(Rn, null);
	}

	/**
	 * T-BoxDeref & T-BorrowDeref
	 */
	@Override
	public Pair<Environment, Type> apply(Environment R, Lifetime l, Expr.Dereference e) {
		String x = e.operand().name();
		Cell Cx = R.get(x);
		// Check variable is declared
		check(Cx != null, UNDECLARED_VARIABLE, e);
		// Check variable not moved
		check(!Cx.moved(), VARIABLE_MOVED, e);
		// Check variable x not mutable borrowed
		check(!mutBorrowed(R, x), VARIABLE_MUTABLY_BORROWED, e.operand());
		// Check operand has reference type
		if (Cx.type() instanceof Type.Box) {
			// T-BoxDeref
			Type T = ((Type.Box) Cx.type()).element;
			//
			check(copyable(T), VARIABLE_NOT_COPY, e);
			//
			return new Pair<>(R, T);
		} else if (Cx.type() instanceof Type.Borrow) {
			// T-BorrowDeref
			Type T = R.get(((Type.Borrow) Cx.type()).name()).type();
			//
			check(copyable(T), VARIABLE_NOT_COPY, e);
			//
			return new Pair<>(R, T);
		} else {
			syntaxError(EXPECTED_REFERENCE, e);
			return null; // deadcode
		}
	}

	/**
	 * T-MoveVar
	 */
	@Override
	public Pair<Environment, Type> apply(Environment R1, Lifetime l, Expr.Variable e) {
		String x = e.name();
		Cell Cx = R1.get(x);
		// Check variable is declared
		check(Cx != null, UNDECLARED_VARIABLE, e);
		// Check variable not moved
		check(!Cx.moved(), VARIABLE_MOVED, e);
		// Extract type from current environment
		Type T = Cx.type();
		// Check variable not borrowed
		check(!borrowed(R1,x), VARIABLE_BORROWED, e);
		// Implement destructive update
		Environment R2 = R1.move(x);
		//
		return new Pair<>(R2,T);
	}

	/**
	 * T-CopyVar
	 */
	@Override
	public Pair<Environment, Type> apply(Environment R, Lifetime l, Expr.Copy e) {
		String x = e.operand().name();
		Cell Cx = R.get(x);
		// Check variable is declared
		check(Cx != null, UNDECLARED_VARIABLE, e);
		// Check variable not moved
		check(!Cx.moved(), VARIABLE_MOVED, e);
		// Extract type from current environment
		Type T = Cx.type();
		// Check variable has copy type
		check(copyable(T), VARIABLE_NOT_COPY, e.operand());
		// Check variable not mutably borrowed
		check(!mutBorrowed(R,x), VARIABLE_BORROWED, e);
		//
		return new Pair<>(R,T);
	}

	/**
	 * T-MutBorrow and T-ImmBorrow
	 */
	@Override
	public Pair<Environment, Type> apply(Environment R, Lifetime lifetime, Expr.Borrow e) {
		String x = e.operand().name();
		Cell Cx = R.get(x);
		// Check variable is declared
		check(Cx != null, UNDECLARED_VARIABLE, e);
		// Check variable not moved
		check(!Cx.moved(), VARIABLE_MOVED, e);
		//
		if(e.isMutable()) {
			// T-MutBorrow
			check(!borrowed(R,x),VARIABLE_BORROWED,e.operand());
		} else {
			// T-ImmBorrow
			check(!mutBorrowed(R,x),VARIABLE_MUTABLY_BORROWED,e.operand());
		}
		//
		return new Pair<>(R,new Type.Borrow(e.isMutable(), x));
	}

	/**
	 * T-Box
	 */
	@Override
	public Pair<Environment, Type> apply(Environment R, Lifetime l, Expr.Box e) {
		// Type operand
		Pair<Environment, Type> p = apply(R, l, e.operand());
		Environment R2 = p.first();
		Type T = p.second();
		//
		return new Pair<>(R2, new Type.Box(T));
	}

	/**
	 * T-Const
	 */
	@Override
	public Pair<Environment, Type> apply(Environment env, Value.Integer expr) {
		return new Pair<>(env, new Type.Int());
	}

	@Override
	public Pair<Environment, Type> apply(Environment env, Value.Location expr) {
		// NOTE: Safe since locations not part of source level syntax.
		throw new UnsupportedOperationException();
	}

	/**
	 * Check whether a type exhibits copy or move semantics.
	 *
	 * @param t
	 * @return
	 */
	public boolean copyable(Type t) {
		// NOTE: checking whether the borrow is mutable is necessary to follow Rust
		// semantics. However, in the calculus as presented we can actually allow
		// mutable references to be copied within creating dangling references. Why is
		// that?
		if(t instanceof Type.Borrow) {
			Type.Borrow b = (Type.Borrow) t;
			// Don't allow copying mutable borrows
			return !b.isMutable();
		} else {
			return (t instanceof Type.Int);
		}
	}

	/**
	 * Check whether a given lifetime is "within" a given type. That is, the
	 * lifetime of any reachable object through this type does not outlive the
	 * lifetime.
	 *
	 * @param T
	 * @param m
	 * @return
	 */
	public boolean within(Environment R, Type T, Lifetime l) {
		if(T instanceof Type.Int) {
			return true;
		} else if(T instanceof Type.Borrow) {
			Type.Borrow t = (Type.Borrow) T;
			check(R.get(t.name()) != null, UNDECLARED_VARIABLE, t);
			Cell C = R.get(t.name());
			return C.lifetime().contains(l);
		} else {
			Type.Box t = (Type.Box) T;
			return within(R, t.element(), l);
		}
	}

	/**
	 * Check two types are compatible.
	 *
	 * @param t1
	 * @param t2
	 * @return
	 */
	public boolean compatible(Environment R, Type t1, Type t2) {
		if (t1 instanceof Type.Int && t2 instanceof Type.Int) {
			return true;
		} else if (t1 instanceof Type.Borrow && t2 instanceof Type.Borrow) {
			Type.Borrow b1 = (Type.Borrow) t1;
			Type.Borrow b2 = (Type.Borrow) t2;
			Cell c1 = R.get(b1.name());
			Cell c2 = R.get(b2.name());
			return b1.isMutable() == b2.isMutable() && compatible(R, c1.type(), c2.type());
		} else if (t1 instanceof Type.Box && t2 instanceof Type.Box) {
			Type.Box b1 = (Type.Box) t1;
			Type.Box b2 = (Type.Box) t2;
			return compatible(R, b1.element(), b2.element());
		} else {
			return false;
		}
	}

	/**
	 * Check whether the location bound to a given variable is borrowed or not.
	 *
	 * @param env
	 * @param var
	 * @return
	 */
	public boolean borrowed(Environment env, String var) {
		// Look through all types to whether for matching borrow
		for (Cell cell : env.cells()) {
			Type type = cell.type();
			if (borrowed(type, var, false)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Check whether the location bound to a given variable is mutably borrowed or not.
	 *
	 * @param env
	 * @param var
	 * @return
	 */
	public boolean mutBorrowed(Environment env, String var) {
		// Look through all types to whether for matching (mutable) borrow
		for (Cell cell : env.cells()) {
			Type type = cell.type();
			if(borrowed(type,var,true)) {
				return true;
			}
		}
		return false;
	}

	public boolean borrowed(Type type, String var, boolean mut) {
		if (type instanceof Type.Borrow) {
			Type.Borrow b = (Type.Borrow) type;
			if (b.name().equals(var) && (!mut || b.isMutable())) {
				return true;
			}
		} else if (type instanceof Type.Box) {
			Type.Box t = (Type.Box) type;
			return borrowed(t.element, var, mut);
		}
		return false;
	}

	/**
	 * Drop all variables declared in a given lifetime.
	 *
	 * @param env
	 * @param lifetime
	 * @return
	 */
	public Environment drop(Environment env, Lifetime lifetime) {
		for(String name : env.bindings()) {
			Cell cell = env.get(name);
			if(cell.lifetime().equals(lifetime)) {
				env = env.remove(name);
			}
		}
		return env;
	}

	/**
	 * Environment maintains the mapping
	 *
	 * @author djp
	 *
	 */
	public static class Environment {
		/**
		 * Mapping from variable names to types
		 */
		private final HashMap<String,Cell> mapping;

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
		public Environment(Map<String,Cell> mapping) {
			this.mapping = new HashMap<>(mapping);
		}

		/**
		 * Get the type associated with a given variable name
		 *
		 * @param name
		 * @return
		 */
		public Cell get(String name) {
			return mapping.get(name);
		}

		/**
		 * Update the type associated with a given variable name
		 *
		 * @param name
		 * @param type
		 * @return
		 */
		public Environment put(String name, Type type, Lifetime lifetime) {
			Environment nenv = new Environment(mapping);
			nenv.mapping.put(name, new Cell(type, lifetime, true));
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
		 * @return
		 */
		public Set<String> bindings() {
			return mapping.keySet();
		}

		@Override
		public String toString() {
			String body = "{";
			boolean firstTime=true;
			for(Map.Entry<String, Cell> e : mapping.entrySet()) {
				if(!firstTime) {
					body = body + ",";
				}
				firstTime=false;
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

		@Override
		public boolean equals(Object o) {
			if(o instanceof Cell) {
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
		if(!result) {
			syntaxError(msg,e);
		}
	}

	public void syntaxError(String msg, SyntacticElement e) {
		Attribute.Source loc = e.attribute(Attribute.Source.class);
		if(loc != null) {
			throw new SyntaxError(msg, sourcefile, loc.start, loc.end);
		} else {
			throw new SyntaxError(msg, sourcefile, 0, 0);
		}
	}
}
