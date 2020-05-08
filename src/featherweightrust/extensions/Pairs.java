package featherweightrust.extensions;

import featherweightrust.core.BorrowChecker;
import featherweightrust.core.BorrowChecker.Cell;
import featherweightrust.core.BorrowChecker.Environment;
import static featherweightrust.core.BorrowChecker.mutBorrowed;
import featherweightrust.core.OperationalSemantics;
import featherweightrust.core.Syntax.Lifetime;
import featherweightrust.core.Syntax.Path;
import featherweightrust.core.Syntax.Path.Element;
import featherweightrust.core.Syntax.Slice;
import featherweightrust.core.Syntax.Term;
import featherweightrust.core.Syntax.Term.AbstractTerm;
import featherweightrust.core.Syntax.Type;
import featherweightrust.core.Syntax.Value;
import featherweightrust.core.Syntax.Value.Location;
import featherweightrust.extensions.ControlFlow.Syntax;
import featherweightrust.util.Pair;
import featherweightrust.util.SyntacticElement.Attribute;
import featherweightrust.util.AbstractMachine.State;

public class Pairs {
	public final static int TERM_pair = 30;
	public final static int TERM_pairaccess = 31;

	public final static String EXPECTED_PAIR = "expected pair type";
	public final static String INVALID_PAIR_ACCESS = "invalid pair accessor";

	/**
	 * Extensions to the core syntax of the language.
	 *
	 * @author David J. Pearce
	 *
	 */
	public static class Syntax {

		public static TermPair<? extends Term> Pair(Term first, Term second, Attribute... attributes) {
			if (first instanceof Value && second instanceof Value) {
				return new ValuePair((Value) first, (Value) second, attributes);
			} else {
				return new TermPair(first, second, attributes);
			}
		}

		/**
		 * Represents a pair of terms, e.g. <code>(1,2)</code>.
		 *
		 * @author David J. Pearce
		 *
		 */
		public static class TermPair<T extends Term> extends AbstractTerm {
			protected final T one;
			protected final T two;

			private TermPair(T first, T second, Attribute... attributes) {
				super(TERM_pair, attributes);
				this.one = first;
				this.two = second;
			}

			public T first() {
				return one;
			}

			public T second() {
				return two;
			}

			@Override
			public boolean equals(Object o) {
				if(o instanceof TermPair) {
					TermPair<?> p = (TermPair<?>) o;
					return one.equals(p.first()) && two.equals(p.second());
				}
				return false;
			}

			@Override
			public int hashCode() {
				return one.hashCode() ^ two.hashCode();
			}

			@Override
			public String toString() {
				return "(" + one + "," + two + ")";
			}
		}

		/**
		 * Represents a pair of values.
		 *
		 * @author David J. Pearce
		 *
		 */
		public static class ValuePair extends TermPair<Value> implements Value {
			private ValuePair(Value first, Value second, Attribute... attributes) {
				super(first,second,attributes);
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
					Value t = (i == 0) ? one : two;
					// Read element
					return t.read(index + 1, path);
				}
			}

			@Override
			public Value write(int index, Path path, Value value) {
				if (index == path.size()) {
					return value;
				} else {
					// Extract element index being written
					int i = ((Index) path.get(index)).index;
					// Select element
					if (i == 0) {
						Value n1 = (one == null) ? value : one.write(index + 1, path, value);
						return new ValuePair(n1, two);
					} else {
						Value n2 = (two == null) ? value : two.write(index + 1, path, value);
						return new ValuePair(one, n2);
					}
				}
			}
		}

		public static class PairAccess extends AbstractTerm {
			private final Term source;
			private final Path path;

			public PairAccess(Term source, Path path, Attribute... attributes) {
				super(TERM_pairaccess,attributes);
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
		 * Represents the type of pairs in the system.
		 *
		 * @author David J. Pearce
		 *
		 */
		public static class TypePair implements Type {
			private final Type first;
			private final Type second;

			public TypePair(Type first, Type second) {
				this.first = first;
				this.second = second;
			}

			@Override
			public boolean within(BorrowChecker self, Environment R, Lifetime l) {
				return first.within(self, R, l) && second.within(self, R, l);
			}

			@Override
			public boolean borrowed(String name, Path path, boolean mut) {
				return first.borrowed(name, path, mut) || second.borrowed(name, path, mut);
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
					Type t = (i == 0) ? first : second;
					// Read element
					return t.read(index + 1, path);
				}
			}

			@Override
			public Type write(int index, Path path, Type type) {
				if (index == path.size()) {
					return type;
				} else {
					// Extract element index being written
					int i = ((Index) path.get(index)).index;
					// Select element
					if(i == 0) {
						Type nfirst = first.write(index + 1, path, type);
						return new TypePair(nfirst,second);
					} else {
						Type nsecond = second.write(index + 1, path, type);
						return new TypePair(first,nsecond);
					}
				}
			}

			@Override
			public boolean copyable() {
				return first.copyable() && second.copyable();
			}

			@Override
			public boolean compatible(Environment R1, Type T2, Environment R2) {
				if(T2 instanceof TypePair) {
					TypePair tp = (TypePair) T2;
					return first.compatible(R1, tp.first, R2) && second.compatible(R1, tp.second, R2);
				} else {
					return false;
				}
			}

			@Override
			public Type join(Type type) {
				if (type instanceof TypePair) {
					TypePair p = (TypePair) type;
					// Recursively join components.
					return new TypePair(first.join(p.first()), second.join(p.second()));
				} else {
					throw new IllegalArgumentException("invalid join");
				}
			}

			public Type first() {
				return first;
			}

			public Type second() {
				return second;
			}

			@Override
			public String toString() {
				return "(" + first + "," + second + ")";
			}

		}

		/**
		 * A path element appropriate for pair types. Specifically, this is an integer
		 * index into the pair.
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
			case TERM_pair:
				return apply(S, l, ((Syntax.TermPair<?>) term));
			case TERM_pairaccess:
				return apply(S, l, ((Syntax.PairAccess) term));
			default:
				return null;
			}
		}

		private Pair<State, Term> apply(State S, Lifetime l, Syntax.TermPair<?> t1) {
			Term lhs = t1.first();
			Term rhs = t1.second();
			//
			if (lhs instanceof Value && rhs instanceof Value) {
				// Both lhs and rhs fully reduced
				return new Pair<>(S, Syntax.Pair(lhs, rhs));
			} else if (lhs instanceof Value) {
				// lhs is fully reduced
				Pair<State, Term> p = self.apply(S, l, rhs);
				return new Pair<>(p.first(), Syntax.Pair(lhs, p.second()));
			} else {
				// lhs not fully reduced
				Pair<State, Term> p = self.apply(S, l, lhs);
				return new Pair<>(p.first(), Syntax.Pair(p.second(), rhs));
			}
		}

		private Pair<State, Term> apply(State S, Lifetime l, Syntax.PairAccess t) {
			Term src = t.source;
			//
			if (src instanceof Value) {
				Syntax.ValuePair v = (Syntax.ValuePair) src;
				return new Pair<>(S, v.read(0, t.path));
			} else {
				Term.Variable x = (Term.Variable) src;
				// Continue reducing operand.
				Location lx = S.locate(x.name());
				// Read value held by x
				Value v = S.read(lx);
				// Done
				return new Pair<>(S, new Syntax.PairAccess(v, t.path));
			}
		}
	}

	public static class Typing extends BorrowChecker.Extension {

		@Override
		public Pair<Environment, Type> apply(Environment state, Lifetime lifetime, Term term) {
			switch(term.getOpcode()) {
			case TERM_pair:
				return apply(state, lifetime, ((Syntax.TermPair<?>) term));
			case TERM_pairaccess:
				return apply(state, lifetime, ((Syntax.PairAccess) term));
			default:
				return null;
			}
		}

		public Pair<Environment, Type> apply(Environment R1, Lifetime l, Syntax.TermPair<?> t) {
			// Type left-hand side
			Pair<Environment, Type> p1 = self.apply(R1, l, t.first());
			Environment R2 = p1.first();
			Type T1 = p1.second();
			// Type right-hand side
			Pair<Environment, Type> p2 = self.apply(R2.put(fresh(), T1, l.getRoot()), l, t.second());
			Environment R3 = p2.first();
			Type T2 = p2.second();
			return new Pair<>(R3, new Syntax.TypePair(T1,T2));
		}

		public Pair<Environment, Type> apply(Environment R1, Lifetime l, Syntax.PairAccess t) {
			// Descructure term
			Term.Variable x = (Term.Variable) t.source();
			Path p = t.path();
			//
			Cell Cx = R1.get(x.name());
			// Check variable is declared
			self.check(Cx != null, BorrowChecker.UNDECLARED_VARIABLE, t);
			// Check variable not moved
			// FIXME: doesn't make sense
			self.check(!Cx.moved(), BorrowChecker.VARIABLE_MOVED, t);
			// Extract type from current environment
			Type T = Cx.type();
			// Check source is pair
			self.check(T instanceof Syntax.TypePair, EXPECTED_PAIR, t);
			// Check slice not mutably borrowed
			self.check(!mutBorrowed(R1, x.name(), p), BorrowChecker.VARIABLE_BORROWED, t);
			// FIXME: structure not guaranteed?
			Type T2 = T.read(0, p);
			// Done
			return new Pair<>(R1, T2);
		}
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
}
