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
package featherweightrust.extensions;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import featherweightrust.core.BorrowChecker;
import featherweightrust.core.BorrowChecker.Environment;
import featherweightrust.core.BorrowChecker.Extension;
import featherweightrust.core.OperationalSemantics;
import featherweightrust.core.Syntax.LVal;
import featherweightrust.core.Syntax.Lifetime;
import featherweightrust.core.Syntax.Path;
import featherweightrust.core.Syntax.Term;
import featherweightrust.core.Syntax.Term.AbstractTerm;
import featherweightrust.core.Syntax.Type;
import featherweightrust.extensions.Functions.Syntax.Signature;
import featherweightrust.util.Pair;
import featherweightrust.util.SyntacticElement;

public class Functions {
	public final static int DECL_fn = 40;
	public final static int TERM_invoke = 41;

	/**
	 * Extensions to the core syntax of the language.
	 *
	 * @author David J. Pearce
	 *
	 */
	public static class Syntax {

		/**
		 * Represents a Function Declaration
		 *
		 * @author David J. Pearce
		 *
		 */
		public static class FunctionDeclaration {
			private final String name;
			private final Pair<String, Signature>[] params;
			private final Signature ret;
			private final Term.Block body;

			public FunctionDeclaration(String name, Pair<String, Signature>[] params, Signature ret, Term.Block body) {
				this.name = name;
				this.params = params;
				this.ret = ret;
				this.body = body;
			}

			/**
			 * Get the name of this function.
			 *
			 * @return
			 */
			public String getName() {
				return name;
			}

			/**
			 * Get the declared parameters for this function.
			 *
			 * @return
			 */
			public Pair<String, Signature>[] getParameters() {
				return params;
			}

			/**
			 * Get the return signature for this function.
			 *
			 * @return
			 */
			public Signature getReturn() {
				return ret;
			}

			/**
			 * Get the body of this function.
			 *
			 * @return
			 */
			public Term.Block getBody() {
				return body;
			}
		}

		/**
		 * Represents the invocation of a given function with a given set of argument
		 * operands.
		 *
		 * @author David J. Pearce
		 *
		 */
		public static class Invoke extends AbstractTerm implements Term {
			private final String name;
			private final Term[] operands;

			public Invoke(String name, Term[] operands, Attribute... attributes) {
				super(TERM_invoke, attributes);
				this.name = name;
				this.operands = operands;
			}

			public Term[] getOperands() {
				return operands;
			}

			@Override
			public String toString() {
				String r = name + "(";
				for(int i=0;i!=operands.length;++i) {
					if(i != 0) {
						r += ",";
					}
					r += operands[i];
				}
				return r + ")";
			}
		}

		/**
		 * Represents a type expressed at the source level.
		 *
		 * @author David J. Pearce
		 *
		 */
		public interface Signature extends SyntacticElement {
			/**
			 * Lower signature into a given environemnt.
			 *
			 * @param env
			 * @return
			 */
			public Pair<Environment,Type> lower(Map<String,Lifetime> binding, Environment env);

			/**
			 * Bind all lifetimes referenced in this signature such that they are within a
			 * given range of lifetimes.
			 *
			 * @return
			 */
			public void bind(Map<String,Lifetime> binding, Lifetime upper, Lifetime lower);

			public static class Unit extends SyntacticElement.Impl implements Signature {
				public Unit(Attribute... attributes) {
					super(attributes);
				}

				@Override
				public Pair<Environment, Type> lower(Map<String,Lifetime> binding, Environment env) {
					return new Pair<>(env,Type.Unit);
				}

				@Override
				public void bind(Map<String,Lifetime> binding, Lifetime upper, Lifetime lower) {
					// Do nout!
				}
			}

			public static class Int extends SyntacticElement.Impl implements Signature {
				public Int(Attribute... attributes) {
					super(attributes);
				}
				@Override
				public Pair<Environment, Type> lower(Map<String,Lifetime> binding, Environment env) {
					return new Pair<>(env,Type.Int);
				}

				@Override
				public void bind(Map<String,Lifetime> binding, Lifetime upper, Lifetime lower) {
					// Do nout!
				}
			}

			public static class Box extends SyntacticElement.Impl implements Signature {
				private final Signature operand;

				public Box(Signature element, Attribute... attributes) {
					super(attributes);
					this.operand = element;
				}

				public Signature getOperand() {
					return operand;
				}

				@Override
				public Pair<Environment, Type> lower(Map<String,Lifetime> binding, Environment env) {
					Pair<Environment,Type> p = operand.lower(binding,env);
					return new Pair<>(p.first(),new Type.Box(p.second()));
				}

				@Override
				public void bind(Map<String,Lifetime> binding, Lifetime upper, Lifetime lower) {
					operand.bind(binding, upper, lower);
				}
			}

			public static class Borrow extends SyntacticElement.Impl implements Signature {
				private final String lifetime;
				private final boolean mut;
				private final Signature operand;

				public Borrow(String lifetime, boolean mut, Signature element, Attribute... attributes) {
					super(attributes);
					this.lifetime = lifetime;
					this.mut = mut;
					this.operand = element;
				}

				public String getLifetime() {
					return lifetime;
				}

				public boolean isMut() {
					return mut;
				}

				public Signature getOperand() {
					return operand;
				}

				@Override
				public Pair<Environment, Type> lower(Map<String,Lifetime> binding, Environment R1) {
					Pair<Environment, Type> p = operand.lower(binding, R1);
					// Create a fresh variablename
					String fvar = BorrowChecker.fresh();
					// Allocation to environment
					Environment R2 = p.first().put(fvar, p.second(), binding.get(lifetime));
					// Done
					return new Pair<>(R2, new Type.Borrow(mut, new LVal(fvar, Path.EMPTY)));
				}

				@Override
				public void bind(Map<String, Lifetime> binding, Lifetime upper, Lifetime lower) {
					Lifetime self = binding.get(lifetime);
					if (self == null) {
						// This lifetime must be within upper
						self = new Lifetime(upper);
						binding.put(lifetime, self);
					}
					// Force lower to be within this
					lower.assertWithin(self);
					// Constrain any subsequents to be outside this
					operand.bind(binding, upper, self);
				}
			}
		}
	}

	public static class Semantics extends OperationalSemantics {
		private Map<String,Syntax.FunctionDeclaration> fns = new HashMap<>();

		public Semantics(List<Syntax.FunctionDeclaration> decls) {
			for(int i=0;i!=decls.size();++i) {
				Syntax.FunctionDeclaration ith = decls.get(i);
				fns.put(ith.getName(), ith);
			}
		}
	}

	public static class Typing extends BorrowChecker.Extension {
		private Map<String,Syntax.FunctionDeclaration> fns = new HashMap<>();

		public Typing(List<Syntax.FunctionDeclaration> decls) {
			for(int i=0;i!=decls.size();++i) {
				Syntax.FunctionDeclaration ith = decls.get(i);
				fns.put(ith.getName(), ith);
			}
		}

		@Override
		public Pair<Environment, Type> apply(Environment state, Lifetime lifetime, Term term) {
			return null;
		}
	}

	public static class Checker extends BorrowChecker {

		public Checker(boolean copyInference, String sourcefile, List<Syntax.FunctionDeclaration> fns) {
			super(copyInference, sourcefile, new Typing(fns));
		}

		public void apply(Lifetime l, List<Syntax.FunctionDeclaration> fns) {
			for(Syntax.FunctionDeclaration fn : fns) {
				apply(l,fn);
			}
		}

		public void apply(Lifetime l, Syntax.FunctionDeclaration fn) {
			Pair<String,Signature>[] params = fn.getParameters();
			// Our lifetime must be within all others
			Lifetime self = new Lifetime();
			// Extract all lifetimes
			HashMap<String,Lifetime> binding = new HashMap<>();
			for(int i=0;i!=params.length;++i) {
				Signature s = params[i].second();
				s.bind(binding, l, self);
			}
			Environment R1 = BorrowChecker.EMPTY_ENVIRONMENT;
			// Lower parameters into environment
			for(int i=0;i!=params.length;++i) {
				String p = params[i].first();
				Signature s = params[i].second();
				// Lower signature into environment
				Pair<Environment,Type> r = s.lower(binding,R1);
				R1 = r.first();
				// Bind parameter to resulting type
				R1 = R1.put(p, new Slot(r.second(),self));
			}
			// Done
			Pair<Environment,Type> p = super.apply(R1,self,fn.getBody());
			Pair<Environment,Type> s = fn.getReturn().lower(binding, p.first());
			Environment R2 = s.first();
			Type T1 = p.second();
			Type T2 = s.second();
			// Check type compatibility
			check(compatible(R2, T1, T2), INCOMPATIBLE_TYPE, fn.getReturn());
		}
	}
}
