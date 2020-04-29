package featherweightrust.extensions;

import featherweightrust.core.BorrowChecker;
import featherweightrust.core.BorrowChecker.Environment;
import featherweightrust.core.OperationalSemantics;
import featherweightrust.core.Syntax.Lifetime;
import featherweightrust.core.Syntax.Term;
import featherweightrust.core.Syntax.Term.AbstractTerm;
import featherweightrust.core.Syntax.Type;
import featherweightrust.core.Syntax.Value;
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
			private final T first;
			private final T second;

			private TermPair(T first, T second, Attribute... attributes) {
				super(TERM_pair, attributes);
				this.first = first;
				this.second = second;
			}

			public T first() {
				return first;
			}

			public T second() {
				return second;
			}

			@Override
			public boolean equals(Object o) {
				if(o instanceof TermPair) {
					TermPair<?> p = (TermPair<?>) o;
					return first.equals(p.first()) && second.equals(p.second());
				}
				return false;
			}

			@Override
			public int hashCode() {
				return first.hashCode() ^ second.hashCode();
			}

			@Override
			public String toString() {
				return "(" + first + "," + second + ")";
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
		}

		public static class PairAccess extends AbstractTerm {
			private final Term source;
			private final int index;

			public PairAccess(Term source, int index, Attribute... attributes) {
				super(TERM_pairaccess,attributes);
				this.source = source;
				this.index = index;
			}

			public Term source() {
				return source;
			}

			public int index() {
				return index;
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
			public boolean borrowed(String var, boolean mut) {
				return first.borrowed(var, mut) || second.borrowed(var, mut);
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

			public featherweightrust.core.Syntax.Type first() {
				return first;
			}

			public featherweightrust.core.Syntax.Type second() {
				return second;
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
			if (src instanceof Value) {
				Syntax.ValuePair v = (Syntax.ValuePair) src;
				switch (t.index) {
				case 0:
					return new Pair<>(S, v.first());
				case 1:
					return new Pair<>(S, v.second());
				default:
					throw new RuntimeException("invalid pair access");
				}
			} else {
				// continue reducing operand.
				Pair<State, Term> p = self.apply(S, l, src);
				return new Pair<>(p.first(), new Syntax.PairAccess(p.second(), t.index));
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

		public Pair<Environment, Type> apply(Environment R1, Lifetime l, Syntax.PairAccess _t) {
			Term t = _t.source();
			// Type source expression
			Pair<Environment, Type> p = self.apply(R1, l, t);
			Environment R2 = p.first();
			Type T1 = p.second();
			//
			if (T1 instanceof Syntax.TypePair) {
				Syntax.TypePair T2 = (Syntax.TypePair) T1;
				switch (_t.index) {
				case 0:
					return new Pair<>(R2, T2.first());
				case 1:
					return new Pair<>(R2, T2.second());
				default:
					self.syntaxError(INVALID_PAIR_ACCESS, _t);
					return null;
				}
			} else {
				self.syntaxError(EXPECTED_PAIR, t);
				return null;
			}
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
