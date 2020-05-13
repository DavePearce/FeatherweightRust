package featherweightrust.extensions;

import featherweightrust.core.BorrowChecker;
import featherweightrust.core.BorrowChecker.Cell;
import featherweightrust.core.BorrowChecker.Environment;
import static featherweightrust.core.BorrowChecker.mutBorrowed;

import java.util.Arrays;

import static featherweightrust.core.BorrowChecker.borrowed;
import featherweightrust.core.OperationalSemantics;
import featherweightrust.core.Syntax.Lifetime;
import featherweightrust.core.Syntax.Path;
import featherweightrust.core.Syntax.Path.Element;
import featherweightrust.core.Syntax.Term;
import featherweightrust.core.Syntax.Term.AbstractTerm;
import featherweightrust.core.Syntax.Type;
import featherweightrust.core.Syntax.Value;
import featherweightrust.core.Syntax.Value.Location;
import featherweightrust.util.Pair;
import featherweightrust.util.AbstractMachine.State;

public class Tuples {
	public final static int TERM_tuple = 30;
	public final static int TERM_tupleaccess = 31;

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
		public static class TupleValue extends TupleTerm<Value> implements Value {
			public TupleValue(Term[] values, Attribute... attributes) {
				super(values,attributes);
			}

			public TupleValue(Term... values) {
				super(values);
			}

			@Override
			public Value read(int index, Path path) {
				// Corresponding element should be an index
				if(index == path.size()) {
					return this;
				} else {
					// Extract element index being read
					int i = ((Index) path.get(index)).index;
					// Select element
					Value t = (Value) terms[i];
					// Read element
					return t.read(index + 1, path);
				}
			}

			@Override
			public Value write(int index, Path path, Value value) {
				if (index == path.size()) {
					return value;
				} else {
					//
					Term[] nterms = Arrays.copyOf(terms, terms.length);
					// Extract element index being written
					int i = ((Index) path.get(index)).index;
					// Select element
					Value n = (Value) terms[i];
					// Write updated element
					nterms[i] = (n == null) ? value : n.write(index + 1, path, value);
					return new TupleValue(nterms);
				}
			}
		}

		public static class TupleAccess extends AbstractTerm {
			private final Term source;
			private final Path path;

			public TupleAccess(Term source, Path path, Attribute... attributes) {
				super(TERM_tupleaccess,attributes);
				this.source = source;
				this.path = path;
			}

			public Term source() {
				return source;
			}

			public Path path() {
				return path;
			}

			@Override
			public String toString() {
				return source.toString() + path;
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
			public boolean borrowed(String name, Path path, boolean mut) {
				for(int i=0;i!=types.length;++i) {
					if(types[i].borrowed(name, path, mut)) {
						return true;
					}
				}
				return false;
			}

			@Override
			public Type read(int index, Path path) {
				// Corresponding element should be an index
				if(index == path.size()) {
					return this;
				} else {
					// Extract element index being read
					int i = ((Index) path.get(index)).index;
					// Select element
					Type t = types[i];
					// Read element
					return t.read(index + 1, path);
				}
			}

			@Override
			public Type write(int index, Path path, Type type) {
				if (index == path.size()) {
					return type;
				} else {
					Type[] ntypes = Arrays.copyOf(types, types.length);
					// Extract element index being written
					int i = ((Index) path.get(index)).index;
					// Select element
					Type t = types[i];
					// write element
					ntypes[i] = t.write(index + 1, path, type);
					// Done
					return new TupleType(ntypes);
				}
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
			public boolean moveable() {
				for(int i=0;i!=types.length;++i) {
					if(!types[i].moveable()) {
						return false;
					}
				}
				return true;
			}

			@Override
			public boolean compatible(Environment R1, Type T2, Environment R2) {
				if(T2 instanceof TupleType) {
					TupleType tp = (TupleType) T2;
					if(types.length != tp.types.length) {
						return false;
					} else {
						for(int i=0;i!=types.length;++i) {
							Type ith = types[i];
							Type tp_ith = tp.types[i];
							if(!ith.compatible(R1,tp_ith,R2)) {
								return false;
							}
						}
					}
					return true;
				} else {
					return false;
				}
			}

			@Override
			public Type union(Type type) {
				if(type instanceof Type.Shadow) {
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
			public Type intersect(Type type) {
				if (type instanceof Type.Shadow) {
					return type.intersect(this);
				} else if (type instanceof TupleType) {
					TupleType p = (TupleType) type;
					if(types.length == p.types.length) {
						Type[] ts = new Type[types.length];
						for(int i=0;i!=ts.length;++i) {
							ts[i] = types[i].intersect(p.types[i]);
						}
						// Recursively join components.
						return new TupleType(ts);
					}
				}
				throw new IllegalArgumentException("invalid intersection");
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
		}
	}


	public static class Semantics extends OperationalSemantics.Extension {

		@Override
		public Pair<State, Term> apply(State S, Lifetime l, Term term) {
			switch(term.getOpcode()) {
			case TERM_tuple:
				return apply(S, l, ((Syntax.TupleTerm<?>) term));
			case TERM_tupleaccess:
				return apply(S, l, ((Syntax.TupleAccess) term));
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
				return new Pair<>(p.first(), new Syntax.TupleTerm<Term>(nelements));
			}
		}

		private Pair<State, Term> apply(State S, Lifetime l, Syntax.TupleAccess t) {
			Term src = t.source;
			//
			if (src instanceof Value) {
				Syntax.TupleValue v = (Syntax.TupleValue) src;
				return new Pair<>(S, v.read(0, t.path));
			} else {
				Term.Variable x = (Term.Variable) src;
				// Continue reducing operand.
				Location lx = S.locate(x.name());
				// Read value held by x
				Value v = S.read(lx);
				// Done
				return new Pair<>(S, new Syntax.TupleAccess(v, t.path));
			}
		}
	}

	public static class Typing extends BorrowChecker.Extension {

		@Override
		public Pair<Environment, Type> apply(Environment state, Lifetime lifetime, Term term) {
			switch(term.getOpcode()) {
			case TERM_tuple:
				return apply(state, lifetime, ((Syntax.TupleTerm<?>) term));
			case TERM_tupleaccess:
				return apply(state, lifetime, ((Syntax.TupleAccess) term));
			default:
				return null;
			}
		}

		public Pair<Environment, Type> apply(Environment R1, Lifetime l, Syntax.TupleTerm<?> t) {
			Term[] elements = t.terms;
			String[] vars = fresh(elements.length);
			Type[] types = new Type[elements.length];
			Environment Rn = R1;
			// Type each element individually
			for(int i=0;i!=elements.length;++i) {
				Term ith = elements[i];
				// Type left-hand side
				Pair<Environment, Type> p1 = self.apply(Rn, l, ith);
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
			return new Pair<>(R2, new Syntax.TupleType(types));
		}

		public Pair<Environment, Type> apply(Environment R1, Lifetime l, Syntax.TupleAccess t) {
			// Descructure term
			Term.Variable x = (Term.Variable) t.source();
			Path p = t.path();
			//
			Cell Cx = R1.get(x.name());
			// Check variable is declared
			self.check(Cx != null, BorrowChecker.UNDECLARED_VARIABLE, t);
			// Destructure cell
			Type Tx = Cx.type();
			// Check path component not moved
			self.check(Tx.read(0, t.path()) != null, BorrowChecker.VARIABLE_MOVED, t);
			// Check source is tuple
			self.check(Tx instanceof Syntax.TupleType, EXPECTED_TUPLE, t);
			// Check path not moved
			Type T2 = Tx.read(0, p);
			self.check(T2.moveable(), BorrowChecker.VARIABLE_MOVED, t);
			// Determine if copy or move
			if(T2.copyable()) {
				// Check slice not mutably borrowed
				self.check(!mutBorrowed(R1, x.name(), p), BorrowChecker.VARIABLE_BORROWED, t);
				//
				return new Pair<>(R1, T2);
			} else {
				// Check slice not borrowed at all
				self.check(!borrowed(R1, x.name(), p), BorrowChecker.VARIABLE_BORROWED, t);
				// Implement destructive update
				Environment R2 = R1.put(x.name(), Tx.write(0, p, T2.move()), Cx.lifetime());
				// Done
				return new Pair<>(R2, T2);
			}

		}
	}

	/**
	 * Identify fist index in array which is not a value.
	 *
	 * @param items
	 * @return
	 */
	private static int firstNonValue(Term[] items) {
		for(int i=0;i!=items.length;++i) {
			if(!(items[i] instanceof Value)) {
				return i;
			}
		}
		return -1;
	}

	private static int index = 0;

	/**
	 * Return a unique variable name everytime this is called.
	 *
	 * @return
	 */
	private static String fresh() {
		return "?" + (index++);
	}

	public static String[] fresh(int n) {
		String[] freshVars = new String[n];
		for(int i=0;i!=n;++i) {
			freshVars[i] = fresh();
		}
		return freshVars;
	}
}
