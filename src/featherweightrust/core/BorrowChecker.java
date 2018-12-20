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
import featherweightrust.core.Syntax.Stmt;
import featherweightrust.core.Syntax.Type;
import featherweightrust.core.Syntax.Value;
import featherweightrust.util.AbstractTransformer;
import featherweightrust.util.Pair;
import featherweightrust.util.SyntacticElement;
import featherweightrust.util.SyntaxError;
import featherweightrust.util.SyntacticElement.Attribute;

public class BorrowChecker extends AbstractTransformer<BorrowChecker.Environment, Type, Type> {
	private final static String UNDECLARED_VARIABLE = "variable undeclared";
	private final static String BORROWED_VARIABLE_ASSIGNMENT = "cannot assign because borrowed";
	private final static String INCOMPATIBLE_TYPE = "incompatible type";
	private final static String EXPECTED_REFERENCE = "expected reference type";
	private final static String VARIABLE_BORROWED = "variable already borrowed";
	private final static String VARIABLE_MUTABLY_BORROWED = "variable already mutably borrowed";

	private final String sourcefile;

	public BorrowChecker(String sourcefile) {
		this.sourcefile = sourcefile;
	}

	@Override
	public Pair<Environment, Type> apply(Environment env, String lifetime, Stmt.Let stmt) {
		// Type operand
		Pair<Environment, Type> p = apply(env, stmt.initialiser());
		// Update environment and discard type (as unused for statements)
		env = p.first().put(stmt.name(), p.second(), lifetime);
		// Done
		return new Pair<>(env, null);
	}

	/**
	 * T-Assign
	 */
	@Override
	public Pair<Environment, Type> apply(Environment R1, String lifetime, Stmt.Assignment stmt) {
		String var = stmt.leftOperand().name();
		// Extract variable's existing type
		Cell C1 = R1.get(var);
		check(C1 != null, UNDECLARED_VARIABLE, stmt.leftOperand());
		Type T1 = C1.type();
		// Type operand
		Pair<Environment, Type> p = apply(R1, stmt.rightOperand());
		Environment R2 = p.first();
		Type T2 = p.second();
		// Check borrow status
		check(!borrowed(R2,var), BORROWED_VARIABLE_ASSIGNMENT, stmt.leftOperand());
		// lifetime check

		// FIXME: implement lifetime check

		// Check compatibility
		check(compatible(T1, T2), INCOMPATIBLE_TYPE, stmt.rightOperand());
		// Update environment
		Environment R3 = R2.put(var, T2, C1.lifetime());
		//
		return new Pair<>(R3, null);
	}

	@Override
	public Pair<Environment, Type> apply(Environment state, String lifetime, Stmt.IndirectAssignment stmt) {
		// TODO:
		return null;
	}

	/**
	 * T-Seq & T-Block
	 */
	@Override
	public Pair<Environment, Type> apply(Environment R1, String lifetime, Stmt.Block stmt) {
		Pair<Environment,Type> p = apply(R1,stmt.lifetime(),stmt.toArray());
		Environment R2 = p.first();
		//
		Environment R3 = drop(R2,stmt.lifetime());
		//
		// TODO: return type
		//
		return new Pair<>(R3, null);
	}

	/**
	 * T-Seq
	 */
	public Pair<Environment, Type> apply(Environment env, String lifetime, Stmt... stmts) {
		for (int i = 0; i != stmts.length; ++i) {
			// Type statement
			Pair<Environment, Type> p = apply(env, lifetime, stmts[i]);
			// Update environment and discard type (as unused for statements)
			env = p.first();
		}
		// FIXME: expressions?
		//
		return new Pair<>(env, null);
	}

	/**
	 * T-Deref
	 */
	@Override
	public Pair<Environment, Type> apply(Environment env, Expr.Dereference expr) {
		// Type operand
		Pair<Environment, Type> p = apply(env, expr.operand());
		// Check operand has reference type
		if (p.second() instanceof Type.Box) {
			Type.Box type = (Type.Box) p.second();
			return new Pair<>(p.first(), type.element());
		} else if (p.second() instanceof Type.Borrow) {
			Type.Borrow type = (Type.Borrow) p.second();
			return new Pair<>(p.first(), env.get(type.name()).type());
		} else {
			syntaxError(EXPECTED_REFERENCE, expr.operand());
			return null; // deadcode
		}
	}

	/**
	 * T-Var
	 */
	@Override
	public Pair<Environment, Type> apply(Environment env, Expr.Variable expr) {
		// Extract type from current environment
		Type type = env.get(expr.name()).type();
		//
		if(!copyable(type)) {
			// Implement destructive update (i.e. move)
			env = env.remove(expr.name());
		}
		return new Pair<>(env,type);
	}

	/**
	 * T-MutBorrow and T-ImmBorrow
	 */
	@Override
	public Pair<Environment, Type> apply(Environment R, Expr.Borrow e) {
		// FIXME: problem if allow rebinding of variables as require existential.
		String name = e.operand().name();
		check(R.get(name) != null, UNDECLARED_VARIABLE, e.operand());
		//
		if(e.isMutable()) {
			// T-MutBorrow
			check(!borrowed(R,name),VARIABLE_BORROWED,e.operand());
		} else {
			// T-ImmBorrow
			check(!mutBorrowed(R,name),VARIABLE_MUTABLY_BORROWED,e.operand());
		}
		//
		return new Pair<>(R,new Type.Borrow(e.isMutable(), name));
	}

	/**
	 * T-Box
	 */
	@Override
	public Pair<Environment, Type> apply(Environment env, Expr.Box expr) {
		// Type operand
		Pair<Environment, Type> p = apply(env, expr.operand());
		//
		return new Pair<>(p.first(), new Type.Box(p.second()));
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
		return (t instanceof Type.Int) || (t instanceof Type.Borrow);
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
	public Environment drop(Environment env, String lifetime) {
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
		public Environment put(String name, Type type, String lifetime) {
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

	private static class Cell extends Pair<Type,String> {

		public Cell(Type f, String s) {
			super(f, s);
		}

		public Type type() {
			return first();
		}

		public String lifetime() {
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
		throw new SyntaxError(msg, sourcefile, loc.start, loc.end);
	}
}
