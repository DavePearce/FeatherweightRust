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

import java.util.HashMap;
import java.util.Map;

import featherweightrust.core.Syntax.Expr;
import featherweightrust.core.Syntax.LVal;
import featherweightrust.core.Syntax.Stmt;
import featherweightrust.core.Syntax.Type;
import featherweightrust.core.Syntax.Value;
import featherweightrust.util.AbstractTransformer;
import featherweightrust.util.Pair;

public class BorrowChecker extends AbstractTransformer<BorrowChecker.Environment, Type, Type, Type> {


	@Override
	public Pair<Environment, Type> apply(Environment state, String lifetime, Stmt.Let stmt) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Pair<Environment, Type> apply(Environment state, String lifetime, Stmt.Assignment stmt) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Pair<Environment, Type> apply(Environment state, String lifetime, Stmt.Block stmt) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Pair<Environment, Type> apply(Environment env, LVal.Dereference expr) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Pair<Environment, Type> apply(Environment env, LVal.Variable expr) {
		// TODO Auto-generated method stub
		return null;
	}


	@Override
	public Pair<Environment, Type> apply(Environment env, Expr.Dereference expr) {
		// Type operand
		Pair<Environment, Type> p = apply(env, expr.operand());
		// Check operand has reference type
		if (p.second() instanceof Type.Reference) {
			Type.Reference type = (Type.Reference) p.second();
			return new Pair<>(p.first(), type.element());
		} else {
			throw new SyntaxError();
		}
		// TODO Auto-generated method stub
		return null;
	}

	/**
	 * T-Var
	 */
	@Override
	public Pair<Environment, Type> apply(Environment env, Expr.Variable expr) {
		// Extract type from current environment
		Type type = env.get(expr.name());
		//
		if(!copyable(type)) {
			// Implement destructive update (i.e. move)
			env = env.remove(expr.name());
		}
		return new Pair<>(env,type);
	}

	@Override
	public Pair<Environment, Type> apply(Environment env, Expr.Borrow expr) {
		// TODO Auto-generated method stub
		return null;
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

	public static class Environment {
		/**
		 * Name of the enclosing lifetime
		 */
		private final String lifetime;

		/**
		 * Mapping from variable names to types
		 */
		private final HashMap<String,Type> mapping;

		/**
		 * Construct an environment for a given lifetime
		 *
		 * @param lifetime
		 */
		public Environment(String lifetime) {
			this.lifetime = lifetime;
			this.mapping = new HashMap<>();
		}

		/**
		 * Construct an environment for a given liftime and variable mapping
		 *
		 * @param lifetime
		 * @param mapping
		 */
		public Environment(String lifetime, Map<String,Type> mapping) {
			this.lifetime = lifetime;
			this.mapping = new HashMap<>(mapping);
		}

		/**
		 * Get the type associated with a given variable name
		 *
		 * @param name
		 * @return
		 */
		public Type get(String name) {
			return mapping.get(name);
		}

		/**
		 * Update the type associated with a given variable name
		 *
		 * @param name
		 * @param type
		 * @return
		 */
		public Environment put(String name, Type type) {
			Environment nenv = new Environment(lifetime,mapping);
			nenv.mapping.put(name, type);
			return nenv;
		}

		/**
		 * Remove a given variable mapping.
		 *
		 * @param name
		 * @return
		 */
		public Environment remove(String name) {
			Environment nenv = new Environment(lifetime,mapping);
			nenv.mapping.remove(name);
			return nenv;
		}
	}
}
