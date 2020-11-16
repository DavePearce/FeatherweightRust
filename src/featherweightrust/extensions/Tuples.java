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

import featherweightrust.core.BorrowChecker;
import featherweightrust.core.BorrowChecker.Environment;

import java.util.Arrays;
import java.util.function.Consumer;

import featherweightrust.core.OperationalSemantics;
import featherweightrust.core.Syntax.LVal;
import featherweightrust.core.Syntax.Lifetime;
import featherweightrust.core.Syntax.Path;
import featherweightrust.core.Syntax.Path.Element;
import featherweightrust.core.Syntax.Term;
import featherweightrust.core.Syntax.Term.AbstractTerm;
import featherweightrust.core.Syntax.Type;
import featherweightrust.core.Syntax.Value;
import featherweightrust.core.Syntax.Value.Reference;
import featherweightrust.util.Pair;
import featherweightrust.util.AbstractMachine.State;
import featherweightrust.util.AbstractMachine.Store;

public class Tuples {
	public final static int TERM_tuple = 30;

	public final static String EXPECTED_TUPLE = "expected tuple type";
	public final static String INVALID_TUPLE_ACCESS = "invalid tuple accessor";

	/**
	 * Extensions to the core syntax of the language.
	 *
	 * @author David J. Pearce
	 *
	 */
	public static class Syntax {

		/**
		 * Represents a tuple of terms, e.g. <code>(1,2)</code>.
		 *
		 * @author David J. Pearce
		 *
		 */
		public static class TupleTerm<T extends Term> extends AbstractTerm {
			protected final Term[] terms;

			public TupleTerm(Term[] terms, Attribute... attributes) {
				super(TERM_tuple, attributes);
				this.terms = terms;
			}

			public int size() {
				return terms.length;
			}

			public T get(int i) {
				return (T) terms[i];
			}

			@Override
			public boolean equals(Object o) {
				if(o instanceof TupleTerm) {
					TupleTerm<?> p = (TupleTerm<?>) o;
					return Arrays.equals(terms, p.terms);
				}
				return false;
			}

			@Override
			public int hashCode() {
				return Arrays.hashCode(terms);
			}

			@Override
			public String toString() {
				String r = "";
				for(int i=0;i!=terms.length;++i) {
					if(i != 0) {
						r += ",";
					}
					r += terms[i];
				}
				return "(" + r + ")";
			}
		}

		/**
		 * Represents a tuple of values.
		 *
		 * @author David J. Pearce
		 *
		 */
		public static class TupleValue extends TupleTerm<Value> implements Value.Compound {
			public TupleValue(Term[] values, Attribute... attributes) {
				super(values,attributes);
			}

			public TupleValue(Term... values) {
				super(values);
			}

			@Override
			public Value read(int[] path, int index) {
				// Corresponding element should be an index
				if(index == path.length) {
					return this;
				} else {
					// Extract element index being read
					int i = path[index];
					// Select element
					Value t = (Value) terms[i];
					// Read element
					return (t == null) ? t : t.read(path, index + 1);
				}
			}

			@Override
			public Value write(int[] path, int index, Value value) {
				if (index == path.length) {
					return value;
				} else {
					//
					Term[] nterms = Arrays.copyOf(terms, terms.length);
					// Extract element index being written
					int i = path[index];
					// Select element
					Value n = (Value) terms[i];
					// Write updated element
					nterms[i] = (n == null) ? value : n.write(path, index + 1, value);
					//
					return new TupleValue(nterms);
				}
			}
		}

		/**
		 * Represents the type of tuples in the system.
		 *
		 * @author David J. Pearce
		 *
		 */
		public static class TupleType extends Type.AbstractType {
			private final Type[] types;

			public TupleType(Type[] types, Attribute...attributes) {
				super(attributes);
				this.types = types;
			}

			public Type get(int i) {
				return types[i];
			}

			@Override
			public boolean within(BorrowChecker self, Environment R, Lifetime l) {
				for(int i=0;i!=types.length;++i) {
					if(!types[i].within(self, R, l)) {
						return false;
					}
				}
				return true;
			}

			@Override
			public boolean copyable() {
				for(int i=0;i!=types.length;++i) {
					if(!types[i].copyable()) {
						return false;
					}
				}
				return true;
			}

			@Override
			public boolean defined() {
				for(int i=0;i!=types.length;++i) {
					if(!types[i].defined()) {
						return false;
					}
				}
				return true;
			}

			@Override
			public boolean prohibitsReading(LVal lv) {
				for(int i=0;i!=types.length;++i) {
					if(types[i].prohibitsReading(lv)) {
						return true;
					}
				}
				return false;
			}

			@Override
			public boolean prohibitsWriting(LVal lv) {
				for(int i=0;i!=types.length;++i) {
					if(types[i].prohibitsWriting(lv)) {
						return true;
					}
				}
				return false;
			}

			@Override
			public Type union(Type type) {
				if(type instanceof Type.Undefined) {
					return type.union(this);
				} else if (type instanceof TupleType) {
					TupleType p = (TupleType) type;
					if(types.length == p.types.length) {
						Type[] ts = new Type[types.length];
						for(int i=0;i!=ts.length;++i) {
							ts[i] = types[i].union(p.types[i]);
						}
						// Recursively join components.
						return new TupleType(ts);
					}
				}
				throw new IllegalArgumentException("invalid union");
			}

			@Override
			public Type.Undefined undefine() {
				return new Undefined(this);
			}

			@Override
			public String toString() {
				String r = "";
				for(int i=0;i!=types.length;++i) {
					if(i != 0) {
						r += ",";
					}
					r += types[i];
				}
				return "(" + r + ")";
			}

			@Override
			public <T extends Type> void consume(Class<T> type, Consumer<T> pred) {
				// NOTE: don't need to implement this yet (though we could).
				throw new UnsupportedOperationException();
			}
		}

		/**
		 * A path element appropriate for tuple types. Specifically, this is an integer
		 * index into the tuple.
		 *
		 * @author David J. Pearce
		 *
		 */
		public static class Index implements featherweightrust.core.Syntax.Path.Element {
			private final int index;

			public Index(int index) {
				this.index = index;
			}

			@Override
			public int compareTo(Element e) {
				if(e instanceof Index) {
					Index i = (Index) e;
					return Integer.compare(index, i.index);
				} else {
					throw new IllegalArgumentException("cannot compare path elements!");
				}
			}

			@Override
			public boolean conflicts(Element e) {
				if(e instanceof Index) {
					Index i = (Index) e;
					return index == i.index;
				} else {
					throw new IllegalArgumentException("cannot compare path elements!");
				}
			}

			@Override
			public String toString() {
				return Integer.toString(index);
			}

			@Override
			public Reference apply(Store store, Reference loc) {
				return loc.at(index);
			}

			@Override
			public String toString(String src) {
				return src + "." + index;
			}

			@Override
			public Pair<Type, Lifetime> apply(Environment env, Type T, Lifetime l) {
				if (T instanceof Syntax.TupleType) {
					Syntax.TupleType t = (Syntax.TupleType) T;
					Type[] ts = t.types;
					if (index < ts.length) {
						return new Pair<>(ts[index], l);
					}
				}
				return null;
			}

			@Override
			public boolean equals(Object o) {
				if(o instanceof Index) {
					Index i = (Index) o;
					return index == i.index;
				}
				return false;
			}

			@Override
			public int hashCode() {
				return index;
			}
		}
	}


	public static class Semantics extends OperationalSemantics.Extension {

		// FIXME: dropping tuple values doesn't recursively drop boxes.

		@Override
		public Pair<State, Term> apply(State S, Lifetime l, Term term) {
			switch(term.getOpcode()) {
			case TERM_tuple:
				return apply(S, l, ((Syntax.TupleTerm<?>) term));
			default:
				return null;
			}
		}

		private Pair<State, Term> apply(State S, Lifetime l, Syntax.TupleTerm<?> t1) {
			final Term[] elements = t1.terms;
			// Determine whether all reduced
			int i = firstNonValue(elements);
			//
			if(i < 0) {
				// All operands fully reduced
				return new Pair<>(S, new Syntax.TupleValue(elements));
			} else {
				Term ith = elements[i];
				// lhs is fully reduced
				Pair<State, Term> p = self.apply(S, l, ith);
				//
				Term[] nelements = Arrays.copyOf(elements, elements.length);
				nelements[i] = p.second();
				return new Pair<>(p.first(), new Syntax.TupleTerm<>(nelements));
			}
		}

	}

	public static class Typing extends BorrowChecker.Extension {

		@Override
		public Pair<Environment, Type> apply(Environment state, Lifetime lifetime, Term term) {
			switch(term.getOpcode()) {
			case TERM_tuple:
				return apply(state, lifetime, ((Syntax.TupleTerm<?>) term));
			default:
				return null;
			}
		}

		public Pair<Environment, Type> apply(Environment R1, Lifetime l, Syntax.TupleTerm<?> t) {
			// Apply "carry typing"
			Pair<Environment,Type[]> p = self.carry(R1, l, t.terms);
			// Done
			return new Pair<>(p.first(), new Syntax.TupleType(p.second()));
		}
	}

	public static class Checker extends BorrowChecker {

		public Checker(boolean copyInference, String sourcefile) {
			super(copyInference, sourcefile, TYPING);
		}

		@Override
		public Type strike(Type T, Path p, int i) {
			if (i < p.size() && T instanceof Syntax.TupleType) {
				Path.Element ith = p.get(i);
				if (ith instanceof Syntax.Index) {
					Syntax.TupleType _T = (Syntax.TupleType) T;
					Type[] ts = _T.types;
					int index = ((Syntax.Index) p.get(i)).index;
					if (index < ts.length) {
						ts = Arrays.copyOf(ts, ts.length);
						ts[index] = strike(ts[index], p, i + 1);
						return new Syntax.TupleType(ts);
					}
				}
				syntaxError("Invalid tuple access \"" + ith.toString(T.toString()) + "\"", null);
			}
			// Default fallback
			return super.strike(T, p, i);
		}

		@Override
		public Pair<Environment, Type> update(Environment R, Type T1, Path p, int i, Type T2, boolean strong) {
			if (i < p.size() && T1 instanceof Syntax.TupleType) {
				Path.Element ith = p.get(i);
				if (ith instanceof Syntax.Index) {
					Syntax.TupleType _T1 = (Syntax.TupleType) T1;
					Type[] ts = _T1.types;
					int index = ((Syntax.Index) p.get(i)).index;
					if (index < ts.length) {
						ts = Arrays.copyOf(ts, ts.length);
						Pair<Environment, Type> r = update(R, ts[i], p, i + 1, T2, strong);
						ts[index] = r.second();;
						return new Pair<>(r.first(), new Syntax.TupleType(ts));
					}
				}
				syntaxError("Invalid tuple access \"" + ith.toString(T1.toString()) + "\"", null);
			}
			return super.update(R, T1, p, i, T2, strong);
		}

		@Override
		public boolean mutable(Environment R, Type T, Path p, int i) {
			if (i < p.size() && T instanceof Syntax.TupleType) {
				Path.Element ith = p.get(i);
				if (ith instanceof Syntax.Index) {
					Syntax.TupleType _T = (Syntax.TupleType) T;
					Type[] ts = _T.types;
					int index = ((Syntax.Index) p.get(i)).index;
					if (index < ts.length) {
						return mutable(R, ts[index], p, i + 1);
					}
				}
				syntaxError("Invalid tuple access \"" + ith.toString(T.toString()) + "\"", null);
			}
			// Default fallback
			return super.mutable(R, T, p, i);
		}

		@Override
		public boolean compatible(Environment R1, Type T1, Type T2) {
			if(T1 instanceof Syntax.TupleType && T2 instanceof Syntax.TupleType) {
				Syntax.TupleType _T1 = (Syntax.TupleType) T1;
				Syntax.TupleType _T2 = (Syntax.TupleType) T2;
				if(_T1.types.length != _T2.types.length) {
					return false;
				} else {
					for(int i=0;i!=_T1.types.length;++i) {
						Type t1 = _T1.types[i];
						Type t2 = _T2.types[i];
						if(!compatible(R1,t1,t2)) {
							return false;
						}
					}
				}
				return true;
			} else {
				return super.compatible(R1, T1, T2);
			}
		}
	}

	/**
	 * Identify fist index in array which is not a value.
	 *
	 * @param items
	 * @return
	 */
	public static int firstNonValue(Term[] items) {
		for(int i=0;i!=items.length;++i) {
			if(!(items[i] instanceof Value)) {
				return i;
			}
		}
		return -1;
	}

	public static final BorrowChecker.Extension TYPING = new Typing();
	public static final OperationalSemantics SEMANTICS = new OperationalSemantics(new Tuples.Semantics());
}
