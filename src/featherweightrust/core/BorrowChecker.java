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

public class BorrowChecker extends AbstractTransformer<BorrowChecker.Environment, Type, Type> {
	public final static Environment EMPTY_ENVIRONMENT = new Environment();
	// Error messages
	private final static String UNDECLARED_VARIABLE = "variable undeclared";
	private final static String BORROWED_VARIABLE_ASSIGNMENT = "cannot assign because borrowed";
	private final static String NOTWITHIN_VARIABLE_ASSIGNMENT = "cannot assign because lifetime not within";
	private final static String INCOMPATIBLE_TYPE = "incompatible type";
	private final static String VARIABLE_NOT_COPY = "variable's type cannot be copied";
	private final static String EXPECTED_REFERENCE = "expected reference type";
	private final static String VARIABLE_BORROWED = "variable already borrowed";
	private final static String VARIABLE_MUTABLY_BORROWED = "variable already mutably borrowed";


	private final String sourcefile;

	public BorrowChecker(String sourcefile) {
		this.sourcefile = sourcefile;
	}

	@Override
	public Pair<Environment, Type> apply(Environment R1, Lifetime l, Stmt.Let s) {
		// Type operand
		Pair<Environment, Type> p = apply(R1, l, s.initialiser());
		Environment R2 = p.first();
		Type T = p.second();
		// Update environment and discard type (as unused for statements)
		Environment R3 = R2.put(s.variable().name(), T, l);
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
		Cell C1 = R1.get(x);
		check(C1 != null, UNDECLARED_VARIABLE, s.leftOperand());
		Type T1 = C1.type();
		Lifetime m = C1.lifetime();
		// Type operand
		Pair<Environment, Type> p = apply(R1, l, s.rightOperand());
		Environment R2 = p.first();
		Type T2 = p.second();
		// Check borrow status
		check(!borrowed(R2,x), BORROWED_VARIABLE_ASSIGNMENT, s.leftOperand());
		// lifetime check
		check(within(R2,T2,m),NOTWITHIN_VARIABLE_ASSIGNMENT,s);
		// Check compatibility
		check(compatible(T1, T2), INCOMPATIBLE_TYPE, s.rightOperand());
		// Update environment
		Environment R3 = R2.put(x, T2, m);
		//
		return new Pair<>(R3, null);
	}

	/**
	 * T-BorrowAssign and T-BoxAssign
	 */
	@Override
	public Pair<Environment, Type> apply(Environment R1, Lifetime l, Stmt.IndirectAssignment s) {
		String x = s.leftOperand().name();
		// (1) Type operand
		Pair<Environment, Type> p = apply(R1, l, s.rightOperand());
		Environment R2 = p.first();
		Type T2 = p.second();
		// (2) Extract x's type info
		// NOTE: differs from paper because of bug!!
		Cell C0 = R2.get(x);
		check(C0 != null, UNDECLARED_VARIABLE, s.leftOperand());
		Type T0 = C0.type();
		//
		if(T0 instanceof Type.Borrow && ((Type.Borrow) T0).isMutable()) {
			// T-BorrowAssign
			Type.Borrow b = (Type.Borrow) T0;
			String y = b.name();
			// (2) Extract y's type
			// NOTE: differs from paper because of bug!!
			Cell C1 = R2.get(y);
			check(C1 != null, UNDECLARED_VARIABLE, b);
			Type T1 = C1.type();
			Lifetime m = C1.lifetime();
			// (4) Check lifetimes
			check(within(R2,T2,m),NOTWITHIN_VARIABLE_ASSIGNMENT,s);
			// (5) Check compatibility
			check(compatible(T1, T2), INCOMPATIBLE_TYPE, s.rightOperand());
			// Update environment
			Environment R3 = R2.put(y, T2, m);
			//
			return new Pair<>(R3, null);
		} else if(T0 instanceof Type.Box) {
			Lifetime m = C0.lifetime();
			// T-BoxAssign
			Type T1 = ((Type.Box) T0).element();
			// (3) Check lifetimes
			check(within(R2,T2,m),NOTWITHIN_VARIABLE_ASSIGNMENT,s);
			// (4) Check compatibility
			check(compatible(T1, T2), INCOMPATIBLE_TYPE, s.rightOperand());
			// Update environment
			Environment R3 = R2.put(x, new Type.Box(T2), m);
			//
			return new Pair<>(R3, null);
		} else {
			syntaxError("expected mutable reference",s.leftOperand());
			return null; // deadcode
		}
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
		Expr.Variable x = e.operand();
		//
		check(R.get(x.name()) != null, UNDECLARED_VARIABLE, e);
		// Locate operand type
		Cell C1 = R.get(x.name());
		// Check operand has reference type
		if (C1.type() instanceof Type.Box) {
			// T-BoxDeref
			Type T = ((Type.Box) C1.type()).element;
			//
			check(copyable(T), VARIABLE_NOT_COPY, x);
			//
			return new Pair<>(R, T);
		} else if (C1.type() instanceof Type.Borrow) {
			// T-BorrowDeref
			Type T = R.get(((Type.Borrow) C1.type()).name()).type();
			//
			check(copyable(T), VARIABLE_NOT_COPY, x);
			//
			return new Pair<>(R, T);
		} else {
			syntaxError(EXPECTED_REFERENCE, x);
			return null; // deadcode
		}
	}

	/**
	 * T-MoveVar
	 */
	@Override
	public Pair<Environment, Type> apply(Environment R1, Lifetime l, Expr.Variable e) {
		String x = e.name();
		//
		check(R1.get(x) != null, UNDECLARED_VARIABLE, e);
		// Extract type from current environment
		Type T = R1.get(x).type();
		// Check variable not borrowed
		check(!borrowed(R1,x), VARIABLE_BORROWED, e);
		// Implement destructive update
		Environment R2 = R1.remove(x);
		//
		return new Pair<>(R2,T);
	}

	/**
	 * T-CopyVar
	 */
	@Override
	public Pair<Environment, Type> apply(Environment R, Lifetime l, Expr.Copy e) {
		String x = e.operand().name();
		// Check variable is declared
		check(R.get(x) != null, UNDECLARED_VARIABLE, e);
		// Extract type from current environment
		Type T = R.get(x).type();
		// Check variable has copy type
		check(copyable(T), VARIABLE_NOT_COPY, e.operand());
		//
		return new Pair<>(R,T);
	}

	/**
	 * T-MutBorrow and T-ImmBorrow
	 */
	@Override
	public Pair<Environment, Type> apply(Environment R, Lifetime lifetime, Expr.Borrow e) {
		String x = e.operand().name();
		check(R.get(x) != null, UNDECLARED_VARIABLE, e.operand());
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
	public boolean compatible(Type t1, Type t2) {
		if (t1 instanceof Type.Int && t2 instanceof Type.Int) {
			return true;
		} else if (t1 instanceof Type.Borrow && t2 instanceof Type.Borrow) {
			return true;
		} else if (t1 instanceof Type.Box && t2 instanceof Type.Box) {
			Type.Box b1 = (Type.Box) t1;
			Type.Box b2 = (Type.Box) t2;
			return compatible(b1.element(), b2.element());
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
			if (type instanceof Type.Borrow) {
				Type.Borrow b = (Type.Borrow) type;
				if (b.name().equals(var)) {
					return true;
				}
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
			if (type instanceof Type.Borrow) {
				Type.Borrow b = (Type.Borrow) type;
				if (b.isMutable() && b.name().equals(var)) {
					return true;
				}
			}
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
			nenv.mapping.put(name, new Cell(type,lifetime));
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
	}

	private static class Cell extends Pair<Type, Lifetime> {

		public Cell(Type f, Lifetime s) {
			super(f, s);
		}

		public Type type() {
			return first();
		}

		public Lifetime lifetime() {
			return second();
		}
	}

	private void check(boolean result, String msg, SyntacticElement e) {
		if(!result) {
			syntaxError(msg,e);
		}
	}

	private void syntaxError(String msg, SyntacticElement e) {
		Attribute.Source loc = e.attribute(Attribute.Source.class);
		if(loc != null) {
			throw new SyntaxError(msg, sourcefile, loc.start, loc.end);
		} else {
			throw new SyntaxError(msg, sourcefile, 0, 0);
		}
	}
}
