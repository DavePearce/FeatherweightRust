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
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

import featherweightrust.core.BorrowChecker;
import featherweightrust.core.BorrowChecker.Environment;
import featherweightrust.core.BorrowChecker.Extension;
import featherweightrust.core.BorrowChecker.Slot;
import featherweightrust.core.OperationalSemantics;
import featherweightrust.core.Syntax.LVal;
import featherweightrust.core.Syntax.Lifetime;
import featherweightrust.core.Syntax.Path;
import featherweightrust.core.Syntax.Term;
import featherweightrust.core.Syntax.Term.AbstractTerm;
import featherweightrust.core.Syntax.Type.Box;
import featherweightrust.core.Syntax.Type;
import featherweightrust.core.Syntax.Value;
import featherweightrust.core.Syntax.Path.Element;
import featherweightrust.extensions.ControlFlow.Syntax;
import featherweightrust.extensions.Functions.Syntax.Signature;
import featherweightrust.util.Pair;
import featherweightrust.util.SyntacticElement;
import featherweightrust.util.AbstractMachine.State;
import featherweightrust.util.ArrayUtils;

public class Functions {
	public final static int DECL_fn = 40;
	public final static int TERM_invoke = 41;
	public final static String UNKNOWN_FUNCTION = "unknown function";
	public final static String INSUFFICIENT_ARGUMENTS = "insufficient arguments";
	public final static String TOO_MANY_ARGUMENTS = "too many arguments";
	public final static String INCOMPATIBLE_ARGUMENT = "incompatible argument";
	public final static String INCOMPATIBLE_ARGUMENTS = "incompatible argument(s)";

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

			public String getName() {
				return name;
			}

			public Term[] getOperands() {
				return operands;
			}

			@Override
			public String toString() {
				String r = name + "(";
				for (int i = 0; i != operands.length; ++i) {
					if (i != 0) {
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
			 * Lower signature into a given environment. This may add anonymous locations to
			 * the environment as necessary, and instantiate lifetimes for them.
			 *
			 * @param binding The binding from syntactic lifetimes (e.g. <code>'a</code>) to
			 *                semantic ones (e.g. <code>l</code>).
			 * @param R       the environment into which we are lowering.
			 * @param l       Lifetime of slot this signature is being lowered into. This is
			 *                important as any locations this location refers to (i.e.
			 *                borrows) must live longer than it.
			 * @return
			 */
			public Pair<Environment, Type> lower(Map<String, Lifetime> binding, Environment R, Lifetime l);

			/**
			 * Lift a given signature into a type in the enclosing environment. This will
			 * reuse existing locations from the environment as necessary.
			 *
			 * @param binding
			 * @return
			 */
			public Type lift(Map<String, Lifetime> binding, Environment env, Type[] types);

			/**
			 * Check whether a given type is a subtype of this signature,under a given
			 * binding or not.
			 *
			 * @param binding
			 * @param type
			 */
			public boolean isSubtype(Map<String, Lifetime> binding, Environment env, Type type);

			/**
			 * Visit all matching signature types.
			 *
			 * @param <T>
			 * @param kind
			 * @param consumer
			 */
			public <T extends Signature> void consume(Class<T> kind, Consumer<T> consumer);

			public static class Unit extends SyntacticElement.Impl implements Signature {
				public Unit(Attribute... attributes) {
					super(attributes);
				}

				@Override
				public Pair<Environment, Type> lower(Map<String, Lifetime> binding, Environment R, Lifetime l) {
					return new Pair<>(R, Type.Unit);
				}

				@Override
				public Type lift(Map<String, Lifetime> binding, Environment env, Type[] types) {
					return Type.Unit;
				}

				@Override
				public boolean isSubtype(Map<String, Lifetime> binding, Environment env, Type type) {
					return type instanceof Type.Unit;
				}

				@Override
				public <T extends Signature> void consume(Class<T> kind, Consumer<T> consumer) {
					if(kind.isInstance(this)) {
						consumer.accept((T) this);
					}
				}

				@Override
				public String toString() {
					return "void";
				}
			}

			public static class Int extends SyntacticElement.Impl implements Signature {
				public Int(Attribute... attributes) {
					super(attributes);
				}

				@Override
				public Pair<Environment, Type> lower(Map<String, Lifetime> binding, Environment R, Lifetime l) {
					return new Pair<>(R, Type.Int);
				}

				@Override
				public Type lift(Map<String, Lifetime> binding, Environment env, Type[] types) {
					return Type.Int;
				}

				@Override
				public boolean isSubtype(Map<String, Lifetime> binding, Environment env, Type type) {
					return type instanceof Type.Int;
				}

				@Override
				public <T extends Signature> void consume(Class<T> kind, Consumer<T> consumer) {
					if(kind.isInstance(this)) {
						consumer.accept((T) this);
					}
				}
				@Override
				public String toString() {
					return "int";
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
				public Pair<Environment, Type> lower(Map<String, Lifetime> binding, Environment R, Lifetime l) {
					Pair<Environment, Type> p = operand.lower(binding, R, l);
					return new Pair<>(p.first(), new Type.Box(p.second()));
				}

				@Override
				public Type lift(Map<String, Lifetime> binding, Environment env, Type[] types) {
					return new Type.Box(operand.lift(binding, env, types));
				}

				@Override
				public boolean isSubtype(Map<String, Lifetime> binding, Environment env, Type type) {
					return (type instanceof Type.Box) && operand.isSubtype(binding, env, ((Type.Box) type).element());
				}

				@Override
				public <T extends Signature> void consume(Class<T> kind, Consumer<T> consumer) {
					if(kind.isInstance(this)) {
						consumer.accept((T) this);
					}
					operand.consume(kind, consumer);
				}

				@Override
				public String toString() {
					return "[]" + operand.toString();
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
				public Pair<Environment, Type> lower(Map<String, Lifetime> binding, Environment R1, Lifetime l) {
					// Initialise target lifetime
					Lifetime tl = instantiateTargetLifetime(binding, l);
					// lower operand
					Pair<Environment, Type> p = operand.lower(binding, R1, tl);
					// Create a fresh variablename
					String fvar = BorrowChecker.fresh();
					// Allocation to environment
					Environment R2 = p.first().put(fvar, p.second(), tl);
					// Done
					return new Pair<>(R2, new Type.Borrow(mut, new LVal(fvar, Path.EMPTY)));
				}

				@Override
				public Type lift(Map<String, Lifetime> binding, Environment R, Type[] types) {
					ArrayList<Type.Borrow> candidates = new ArrayList<>();
					for (int i = 0; i != types.length; ++i) {
						lift(binding,R,types[i],candidates);
					}
					Type.Borrow r = null;
					for(int i=0;i!=candidates.size();++i) {
						Type.Borrow ith = candidates.get(i);
						r = (r == null) ? ith : (Type.Borrow) r.union(ith);
					}
					return r;
				}

				public void lift(Map<String, Lifetime> binding, Environment R, Type type, List<Type.Borrow> candidates) {
					type.consume(Type.Borrow.class, b -> {
						LVal[] lvals = b.lvals();
						if(isSubtype(binding,R,b)) {
							candidates.add(b);
						} else {
							// Continue searching
							for(int i=0;i!=lvals.length;++i) {
								LVal w = lvals[i];
								Pair<Type,Lifetime> p = w.typeOf(R);
								lift(binding,R,p.first(),candidates);
							}
						}
					});
				}

				@Override
				public <T extends Signature> void consume(Class<T> kind, Consumer<T> consumer) {
					if(kind.isInstance(this)) {
						consumer.accept((T) this);
					}
					operand.consume(kind, consumer);
				}

				@Override
				public boolean isSubtype(Map<String, Lifetime> binding, Environment env, Type type) {
					if (type instanceof Type.Borrow) {
						Type.Borrow b = (Type.Borrow) type;
						if (b.isMutable() == mut) {
							Lifetime l = binding.get(lifetime);
							//
							for (LVal lv : b.lvals()) {
								// Need to decide whether this is a candidate for this borrow, or not.
								Pair<Type, Lifetime> p = lv.typeOf(env);
								Type T = p.first();
								Lifetime m = p.second();

								// FIXME: mutable borrows should be invariant.

								// Check whether outlives bound lifetime
								if (!m.contains(l) || !operand.isSubtype(binding, env, T)) {
									return false;
								}
							}
							return true;
						}
					}
					return false;
				}

				@Override
				public String toString() {
					return "&'" + lifetime + (mut ? " mut " : " ") + operand.toString();
				}

				/**
				 * Instantiate a new lifetime for the target of this borrow. Observe that this
				 * lifetime may already have been instantiated, in which case we just need to
				 * ensure it outlives the lifetime of this borrow.
				 *
				 * @param binding
				 * @param upper
				 * @param lower
				 * @return
				 */
				private Lifetime instantiateTargetLifetime(Map<String, Lifetime> binding, Lifetime l) {
					Lifetime tl = binding.get(lifetime);
					if (tl == null) {
						// Target lifetime must be within root
						tl = new Lifetime(l.getRoot());
						binding.put(lifetime, tl);
					}
					// Force lifetime of location holding borrow to be within lifetime of referenced
					// location.
					l.assertWithin(tl);
					//
					return tl;
				}
			}
		}
	}

	public static class Semantics extends OperationalSemantics.Extension {
		private Map<String, Syntax.FunctionDeclaration> fns = new HashMap<>();

		public Semantics(List<Syntax.FunctionDeclaration> decls) {
			for (int i = 0; i != decls.size(); ++i) {
				Syntax.FunctionDeclaration ith = decls.get(i);
				fns.put(ith.getName(), ith);
			}
		}

		@Override
		public Pair<State, Term> apply(State S, Lifetime l, Term t) {
			if (t instanceof Syntax.Invoke) {
				return apply(S, l, (Syntax.Invoke) t);
			} else {
				return null;
			}
		}

		public Pair<State, Term> apply(State S, Lifetime l, Syntax.Invoke t) {
			final Term[] arguments = t.operands;
			// Determine whether all reduced
			int i = Tuples.firstNonValue(arguments);
			//
			if (i < 0) {
				// All operands fully reduced, so perform invocation
				return invoke(S, l, t.name, t.operands);
			} else {
				Term ith = arguments[i];
				// Reduce ith
				Pair<State, Term> p = self.apply(S, l, ith);
				// Done
				Term[] nelements = Arrays.copyOf(arguments, arguments.length);
				nelements[i] = p.second();
				return new Pair<>(p.first(), new Syntax.Invoke(t.name, nelements));
			}
		}

		private Pair<State, Term> invoke(State S, Lifetime l, String name, Term[] args) {
			Syntax.FunctionDeclaration decl = fns.get(name);
			// Extract parameters
			Pair<String, Signature>[] params = decl.getParameters();
			// Instantiate new lifetimes within this term
			Term.Block body = instantiate(l, decl.getBody());
			// Push empty stack frame
			S = S.push(body.lifetime());
			// Allocate parameters
			for (int i = 0; i != args.length; ++i) {
				Value ith = (Value) args[i];
				Pair<State, Value.Reference> p = S.allocate(body.lifetime(), ith);
				S = p.first().bind(params[i].first(), p.second());
			}
			// Done
			return new Pair<>(S, body);
		}

		/**
		 * Instantiate a given block with fresh lifetimes such that they respect the
		 * existing (static) nesting and are all within the given lifetime.
		 *
		 * @param l
		 * @param blk
		 * @return
		 */
		private Term.Block instantiate(Lifetime l, Term.Block blk) {
			Lifetime m = l.freshWithin();
			Term[] stmts = new Term[blk.size()];
			for (int i = 0; i != stmts.length; ++i) {
				Term ith = blk.get(i);
				if (ith instanceof Term.Block) {
					stmts[i] = instantiate(m, (Term.Block) ith);
				} else {
					stmts[i] = ith;
				}
			}
			return new Term.Block(m, stmts, blk.attributes());
		}
	}

	public static class Typing extends BorrowChecker.Extension {
		private Map<String, Syntax.FunctionDeclaration> fns = new HashMap<>();

		public Typing(List<Syntax.FunctionDeclaration> decls) {
			for (int i = 0; i != decls.size(); ++i) {
				Syntax.FunctionDeclaration ith = decls.get(i);
				fns.put(ith.getName(), ith);
			}
		}

		@Override
		public Pair<Environment, Type> apply(Environment state, Lifetime lifetime, Term term) {
			if (term instanceof Syntax.Invoke) {
				return apply(state, lifetime, (Syntax.Invoke) term);
			}
			return null;
		}

		public Pair<Environment, Type> apply(Environment R1, Lifetime lifetime, Syntax.Invoke term) {
			// Determine function being invoked
			Syntax.FunctionDeclaration decl = fns.get(term.getName());
			self.check(decl != null, UNKNOWN_FUNCTION, term);
			// Extract parameters
			Pair<String, Signature>[] parameters = decl.getParameters();
			self.check(term.getOperands().length >= parameters.length, INSUFFICIENT_ARGUMENTS, term);
			self.check(term.getOperands().length <= parameters.length, TOO_MANY_ARGUMENTS, term);
			// Apply "carry typing" of arguments
			Pair<Environment, Type[]> p = self.carry(R1, lifetime, term.getOperands());
			// Construct binding
			Environment R2 = p.first();
			Type[] args = p.second();
			Map<String, Lifetime> binding = bind(parameters,args,R2);
			self.check(binding != null, INCOMPATIBLE_ARGUMENTS, term);
			// Apply lifting
			Type rt = decl.getReturn().lift(binding, R2, args);
			// FIXME: apply side effects!!
			Environment R3 = R2;
			// Done
			return new Pair<>(R3, rt);
		}

		/**
		 * Attempt to find a suitable binding signatures to arguments. In principle,
		 * there might be more than one. However, this implementation simply returns the
		 * first.
		 *
		 * @param parameters
		 * @param arguments
		 * @param R
		 * @return
		 */
		public Map<String,Lifetime> bind(Pair<String, Signature>[] parameters, Type[] arguments, Environment R) {
			// Extract all known abstract lifetimes
			String[] abstractLifetimes = extractLifetimes(parameters);
			// Extract all reachable concrete lifetimes
			Lifetime[] concreteLifetimes = extractLifetimes(R, arguments);
			// Enumerate every possible binding
			outer:
			for(Map<String,Lifetime> binding : generate(abstractLifetimes,concreteLifetimes)) {
				for (int i = 0; i != arguments.length; ++i) {
					Signature sith = parameters[i].second();
					Type tith = arguments[i];
					if(!sith.isSubtype(binding, R, tith)) {
						continue outer;
					}
				}
				// Found candidate!
				return binding;
			}
			// failed
			return null;
		}

		/**
		 * Provide an iterator over every possible binding of the abstract lifetimes to
		 * the concrete lifetimes. This is a simple brute-force operation where the
		 * intention is that, having generated all bindings, we select the best
		 * candidate.
		 *
		 * @param abstractLifetimes
		 * @param concreteLifetimes
		 * @return
		 */
		private static Iterable<Map<String,Lifetime>> generate(String[] abstractLifetimes, Lifetime[] concreteLifetimes) {
			ArrayList<Map<String,Lifetime>> results = new ArrayList<>();
			generate(0, new int[abstractLifetimes.length], abstractLifetimes, concreteLifetimes, results);
			return results;
		}

		private static void generate(int i, int[] mapping, String[] abstracts, Lifetime[] concretes, List<Map<String,Lifetime>> candidates) {
			if(i == mapping.length) {
				// Base case
				HashMap<String,Lifetime> binding = new HashMap<>();
				for(int j=0;j!=mapping.length;++j) {
					binding.put(abstracts[j], concretes[mapping[j]]);
				}
				candidates.add(binding);
			} else {
				for (int j = 0; j != concretes.length; ++j) {
					mapping[i] = j;
					generate(i + 1, mapping, abstracts, concretes, candidates);
				}
			}
		}

		private static String[] extractLifetimes(Pair<String, Signature>[] parameters) {
			ArrayList<String> lifetimes = new ArrayList<>();
			for(int i=0;i!=parameters.length;++i) {
				Signature ith = parameters[i].second();
				ith.consume(Signature.Borrow.class, b -> lifetimes.add(b.getLifetime()));
			}
			String[] ls = lifetimes.toArray(new String[lifetimes.size()]);
			return ArrayUtils.removeDuplicates(ls);
		}

		private static Lifetime[] extractLifetimes(Environment R, Type[] arguments) {
			ArrayList<Lifetime> lifetimes = new ArrayList<>();
			for(int i=0;i!=arguments.length;++i) {
				arguments[i].consume(Type.Borrow.class, b -> extractLifetimes(R,b.lvals(),lifetimes));
			}
			Lifetime[] ls = lifetimes.toArray(new Lifetime[lifetimes.size()]);
			return ArrayUtils.removeDuplicates(ls);
		}

		private static void extractLifetimes(Environment R, LVal[] lvals, List<Lifetime> lifetimes) {
			for (int i = 0; i != lvals.length; ++i) {
				LVal w = lvals[i];
				Pair<Type, Lifetime> p = w.typeOf(R);
				// Record lifetime
				lifetimes.add(p.second());
				// Continue traversal
				p.first().consume(Type.Borrow.class, b -> extractLifetimes(R, b.lvals(), lifetimes));
			}
		}
	}

	public static class Checker extends BorrowChecker {

		public Checker(boolean copyInference, String sourcefile, List<Syntax.FunctionDeclaration> fns) {
			super(copyInference, sourcefile, new Typing(fns));
		}

		public void apply(Lifetime l, List<Syntax.FunctionDeclaration> fns) {
			for (Syntax.FunctionDeclaration fn : fns) {
				apply(l, fn);
			}
		}

		public void apply(Lifetime l, Syntax.FunctionDeclaration fn) {
			Pair<String, Signature>[] params = fn.getParameters();
			// Our lifetime must be within all others
			Lifetime self = new Lifetime(l);
			// Extract all lifetimes
			HashMap<String, Lifetime> binding = new HashMap<>();
			Environment R1 = BorrowChecker.EMPTY_ENVIRONMENT;
			// Lower parameters into environment
			for (int i = 0; i != params.length; ++i) {
				String p = params[i].first();
				Signature s = params[i].second();
				// Lower signature into environment
				Pair<Environment, Type> r = s.lower(binding, R1, self);
				R1 = r.first();
				// Bind parameter to resulting type
				R1 = R1.put(p, new Slot(r.second(), self));
			}
			// Type method body
			Pair<Environment, Type> p = super.apply(R1, self, fn.getBody());
			// Check type compatibility
			check(fn.getReturn().isSubtype(binding, p.first(), p.second()), INCOMPATIBLE_TYPE, fn.getBody());
		}
	}
}
