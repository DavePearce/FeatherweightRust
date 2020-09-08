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

import java.math.BigInteger;
import java.util.Arrays;
import java.util.List;


import featherweightrust.core.BorrowChecker.Slot;
import featherweightrust.core.BorrowChecker.Environment;
import featherweightrust.core.Syntax.Path;
import featherweightrust.core.Syntax.Path.Element;
import featherweightrust.core.Syntax.Type.Undefined;
import featherweightrust.core.Syntax.Value.Reference;
import featherweightrust.util.AbstractMachine.State;
import featherweightrust.util.AbstractMachine.Store;
import featherweightrust.util.SyntacticElement.Attribute;
import featherweightrust.util.ArrayUtils;
import featherweightrust.util.SyntacticElement;
import jmodelgen.core.Domain;
import jmodelgen.core.Walker;
import jmodelgen.core.Domains;
import jmodelgen.util.Walkers;

public class Syntax {
	public final static int TERM_let = 0;
	public final static int TERM_assignment = 1;
	public final static int TERM_block = 3;
	public final static int TERM_borrow = 6;
	public final static int TERM_dereference = 7;
	public final static int TERM_box = 8;
	public final static int TERM_unit = 9;
	public final static int TERM_integer = 10;
	public final static int TERM_location = 11;

	public interface Term extends SyntacticElement {

		/**
		 * Get the opcode associated with the syntactic form of this term.
		 *
		 * @return
		 */
		public int getOpcode();

		/**
		 * An abstract term to be implemented by all other terms.
		 * @author David J. Pearce
		 *
		 */
		public static abstract class AbstractTerm extends SyntacticElement.Impl implements Term {
			private final int opcode;

			public AbstractTerm(int opcode, Attribute... attributes) {
				super(attributes);
				this.opcode = opcode;
			}

			@Override
			public int getOpcode() {
				return opcode;
			}
		}

		/**
		 * A marker interface to indicates terms which contain other terms.
		 *
		 * @author David J. Pearce
		 *
		 */
		public interface Compound {

		}

		/**
		 * Represents a variable declaration of the form:
		 *
		 * <pre>
		 * let mut x = e
		 * </pre>
		 *
		 * @author David J. Pearce
		 *
		 */
		public class Let extends AbstractTerm {
			private final String variable;
			private final Term initialiser;

			public Let(String variable, Term initialiser, Attribute... attributes) {
				super(TERM_let, attributes);
				this.variable = variable;
				this.initialiser = initialiser;
			}

			/**
			 * Return the variable being declared.
			 *
			 * @return
			 */
			public String variable() {
				return variable;
			}

			/**
			 * Return the expression used to initialise variable
			 *
			 * @return
			 */
			public Term initialiser() {
				return initialiser;
			}

			@Override
			public String toString() {
				return "let mut " + variable + " = " + initialiser;
			}

			public static Let construct(String variable, Term initialiser) {
				return new Let(variable, initialiser);
			}

			public static Domain.Big<Let> toBigDomain(Domain.Small<String> first, Domain.Big<Term> second) {
				return Domains.Product(first, second, Let::construct);
			}
		}

		/**
		 * Represents an assignment such as the following:
		 *
		 * <pre>
		 * x = e
		 * </pre>
		 *
		 * @author David J. Pearce
		 *
		 */
		public class Assignment extends AbstractTerm {
			public final LVal lhs;
			public final Term rhs;

			public Assignment(LVal lhs, Term rhs, Attribute... attributes) {
				super(TERM_assignment,attributes);
				this.lhs = lhs;
				this.rhs = rhs;
			}

			public LVal leftOperand() {
				return lhs;
			}

			public Term rightOperand() {
				return rhs;
			}

			@Override
			public String toString() {
				return lhs + " = " + rhs;
			}

			public static Assignment construct(LVal lhs, Term rhs) {
				return new Assignment(lhs, rhs);
			}

			public static Domain.Big<Assignment> toBigDomain(Domain.Big<LVal> first, Domain.Big<Term> second) {
				return Domains.Product(first, second, Assignment::construct);
			}
		}

		/**
		 * Represents a group of statements, such as the following:
		 *
		 * <pre>
		 * { }^l
		 * { let mut x = 1; }^n
		 * </pre>
		 *
		 * @author David J. Pearce
		 *
		 */
		public class Block extends AbstractTerm implements Compound {
			private final Lifetime lifetime;
			private final Term[] terms;

			public Block(Lifetime lifetime, Term[] stmts, Attribute... attributes) {
				super(TERM_block,attributes);
				this.lifetime = lifetime;
				this.terms = stmts;
			}

			public Lifetime lifetime() {
				return lifetime;
			}

			public int size() {
				return terms.length;
			}

			public Term get(int i) {
				return terms[i];
			}

			public Term[] toArray() {
				return terms;
			}
			@Override
			public String toString() {
				String contents = "";
				for (int i = 0; i != terms.length; ++i) {
					if(i != 0) {
						contents += " ; ";
					}
					contents += terms[i];
				}
				return "{ " + contents + " }@" + lifetime;
			}

			public static Block construct(Lifetime lifetime, List<Term> items) {
				return new Block(lifetime, items.toArray(new Term[items.size()]));
			}

			public static Domain.Big<Block> toBigDomain(Lifetime lifetime, int min, int max,
					Domain.Big<Term> stmts) {
				return Domains.Adaptor(Domains.Array(min, max, stmts), (ss) -> new Block(lifetime, ss));
			}

			public static Walker<Block> toWalker(Lifetime lifetime, int min, int max, Walker.State<Term> seed) {
				return Walkers.Product(min, max, seed, (items) -> construct(lifetime, items));
			}
		}

		public class Access extends AbstractTerm implements Term {
			public enum Kind {
				/**
				 * Forces a move
				 */
				MOVE,
				/**
				 * Forces a copy
				 */
				COPY,
				/**
				 * Represents neither copy nor move!
				 */
				TEMP,
			}
			private Kind kind;
			private final LVal slice;

			public Access(Kind kind, LVal lv, Attribute... attributes) {
				super(TERM_dereference, attributes);
				this.kind = kind;
				this.slice = lv;
			}

			/**
			 * Determine whether this term is demarked as performing a copy of the value in
			 * question.
			 *
			 * @return
			 */
			public boolean copy() {
				return kind == Kind.COPY;
			}

			/**
			 * Determine whether this term is for a short-lived (i.e. temporary) value.
			 *
			 * @return
			 */
			public boolean temporary() {
				return kind == Kind.TEMP;
			}

			/**
			 * Get the LVal being read.
			 *
			 * @return
			 */
			public LVal operand() {
				return slice;
			}

			/**
			 * Infer the kind of operation this dereference corresponds to.
			 *
			 * @param kind
			 */
			public void infer(Kind kind) {
				this.kind = kind;
			}

			@Override
			public String toString() {
				if (kind == Kind.COPY) {
					return "!" + slice;
				} else {
					return slice.toString();
				}
			}

			public static Term.Access construct(LVal lv, Boolean b) {
				Kind kind = b ? Kind.MOVE : Kind.COPY;
				return new Term.Access(kind,lv);
			}

			public static Domain.Big<Access> toBigDomain(Domain.Big<LVal> subdomain) {
				return Domains.Product(subdomain, Domains.BOOL, Access::construct);
			}
		}

		public class Borrow extends AbstractTerm implements Term {
			private final LVal operand;
			private final boolean mutable;

			public Borrow(LVal operand, boolean mutable, Attribute... attributes) {
				super(TERM_borrow,attributes);
				this.operand = operand;
				this.mutable = mutable;
			}

			public LVal operand() {
				return operand;
			}

			public boolean isMutable() {
				return mutable;
			}

			@Override
			public String toString() {
				if (mutable) {
					return "&mut " + operand;
				} else {
					return "&" + operand;
				}
			}

			public static Term.Borrow construct(LVal operand, Boolean mutable) {
				return new Term.Borrow(operand, mutable);
			}

			public static Domain.Big<Borrow> toBigDomain(Domain.Big<LVal> subdomain) {
				return Domains.Product(subdomain, Domains.BOOL, Borrow::construct);
			}
		}

		public class Box extends AbstractTerm implements Term {
			private final Term operand;

			public Box(Term operand, Attribute... attributes) {
				super(TERM_box, attributes);
				this.operand = operand;
			}

			public Term operand() {
				return operand;
			}

			@Override
			public String toString() {
				return "box " + operand;
			}

			public static Term.Box construct(int dimension, Term operand) {
				Term.Box b = new Term.Box(operand);
				for(int i=1;i<=dimension;++i) {
					b = new Term.Box(b);
				}
				return b;
			}

			public static Domain.Big<Box> toBigDomain(int i, Domain.Big<Term> subdomain) {
				return Domains.Adaptor(subdomain, t -> construct(i,t));
			}
		}
	}

	public interface Value extends Term {

		public static Unit Unit = new Unit();

		/**
		 * A compound value contains zero or more sub-values which can be identified and
		 * extracted.
		 *
		 * @author David J. Pearce
		 *
		 */
		public interface Compound extends Value {
			/**
			 * Get the width of this value. For compound values, this determines the number
			 * of values contained within at the first level.
			 *
			 * @return
			 */
			public int size();

			/**
			 * Get the ith value contained within at the first level.
			 *
			 * @param i
			 * @return
			 */
			public Value get(int i);
		}

		/**
		 * Read the value at a given path within this value. If path is empty, then that
		 * identifies this value to be returned. For example, given a tuple
		 * <code>(1,2)</code>, reading the value at path <code>1</code> gives
		 * <code>2</code>.
		 *
		 * @param index Position in path being read
		 * @param path
		 * @return
		 */
		public Value read(int[] path, int index);

		/**
		 * Write a give value into this value at a given path, returned the updated
		 * value.
		 *
		 * @param index Position in path being written
		 * @param path
		 * @param value
		 * @return
		 */
		public Value write(int[] path, int index, Value value);

		/**
		 * An indivisible value which can be stored in exactly one location. For
		 * example, a compound value such as an array or tuple might require more than
		 * one location to store. In all cases, <code>size() == 1</code> should hold for
		 * an atom.
		 *
		 * @author David J. Pearce
		 *
		 */
		public class Atom extends AbstractTerm implements Value {
			public Atom(int opcode, Attribute... attributes) {
				super(opcode, attributes);
			}

			@Override
			public Value read(int[] path, int index) {
				assert index == path.length;
				return this;
			}

			@Override
			public Value write(int[] path, int index, Value value) {
				assert index == path.length;
				return value;
			}
		}

		public class Unit extends Atom {

			public Unit(Attribute... attributes) {
				super(TERM_unit,attributes);
			}

			@Override
			public int hashCode() {
				return 0;
			}

			@Override
			public boolean equals(Object o) {
				return o instanceof Unit;
			}

			@Override
			public String toString() {
				return "unit";
			}
		}

		public class Integer extends Atom {
			private final int value;

			public Integer(int value, Attribute... attributes) {
				super(TERM_integer,attributes);
				this.value = value;
			}

			public int value() {
				return value;
			}

			@Override
			public int hashCode() {
				return value;
			}

			@Override
			public boolean equals(Object o) {
				return o instanceof Integer && ((Integer) o).value == value;
			}

			@Override
			public String toString() {
				return java.lang.Integer.toString(value);
			}

			public static Integer construct(java.lang.Integer i) {
				return new Integer(i);
			}

			public static Domain.Big<Integer> toBigDomain(Domain.Big<java.lang.Integer> subdomain) {
				return Domains.Adaptor(subdomain, Integer::construct);
			}
		}

		/**
		 * Represents an <i>owned</i> or <i>unowned</i> reference to a give location (or
		 * part thereof) in the machines memory. An owning reference is one which
		 * assumes responsibility for memory deallocation. A reference can refer to a
		 * position within a given location, and this is determined by the <i>path</i>.
		 * That is a sequence of zero or more <i>indices</i> into the location. For
		 * example, a location holding a <i>pair</i> value would have two positions at
		 * the first level. If <code>l</code> refers is the address of this location,
		 * then the valid references are: <code>l</code>, <code>l:0</code> and
		 * <code>l:1</code> (along with the owning / non-owning variants).
		 *
		 * @author David J. Pearce
		 *
		 */
		public class Reference extends Atom {
			private static final int[] OWNER = new int[0];
			private static final int[] OTHER = new int[0];
			/**
			 * The location's address in the machines memory
			 */
			private final int address;
			/**
			 * Identifies the position within the location to which this reference refers.
			 */
			private final int[] path;

			public Reference(int address, Attribute... attributes) {
				this(address, OWNER, attributes);
			}

			private Reference(int address, int[] path, Attribute... attributes) {
				super(TERM_location, attributes);
				this.address = address;
				this.path = path;
			}

			/**
			 * Get the base address of the location this reference refers to.
			 *
			 * @return
			 */
			public int getAddress() {
				return address;
			}

			/**
			 * Get the path to the position within the location which this references refers
			 * to.
			 *
			 * @return
			 */
			public int[] getPath() {
				return path;
			}

			/**
			 * Determine the ownership status of this location. Specifically, whether or not
			 * this reference is responsible for deallocating its location.
			 *
			 * @return
			 */
			public boolean owner() {
				return path == OWNER;
			}

			/**
			 * Obtain a reference which is not an owner to which it refers.
			 *
			 * @return
			 */
			public Reference toBorrowed() {
				if (path == OWNER) {
					return new Reference(address, OTHER);
				} else {
					return this;
				}
			}

			/**
			 * Access a location within this location at the given offset.
			 *
			 * @param offset
			 * @return
			 */
			public Reference at(int offset) {
				int[] npath = Arrays.copyOf(path, path.length + 1);
				npath[path.length] = offset;
				return new Reference(address,npath);
			}

			@Override
			public int hashCode() {
				return address ^ Arrays.hashCode(path);
			}

			@Override
			public boolean equals(Object o) {
				if (o instanceof Reference) {
					Reference l = ((Reference) o);
					return l.address == address && Arrays.equals(path, l.path);
				}
				return false;
			}

			@Override
			public String toString() {
				String p = "";
				for(int i=0;i!=path.length;++i) {
					p = p + ":" + path[i];
				}
				String q = owner() ? "+" : "";
				return "&" + q + address + p;
			}
		}
	}

	public interface Type {
		/**
		 * Join two types together from different execution paths.
		 *
		 * @param type
		 * @return
		 */
		public Type union(Type type);

		/**
		 * Strip away any undefined components of this type.
		 *
		 * @return
		 */
		public Type concretize();

		/**
		 * Convert this type into its undefined equivalent
		 *
		 * @return
		 */
		public Type.Undefined undefine();

		/**
		 * Check whether this type can safely live within a given lifetime. That is, the
		 * lifetime does not outlive any object reachable through this type.
		 *
		 * @param self
		 * @param R
		 * @param l
		 * @return
		 */
		public boolean within(BorrowChecker self, Environment R, Lifetime l);

		/**
		 * Check whether a type is defined. That is, ensure it doesn't contain a shadow.
		 * Primitive types and references are always defined. However, boxes depend on
		 * their element type, whilst shadows are clearly not.
		 *
		 * @param t
		 * @return
		 */
		public boolean defined();

		/**
		 * Check whether this type can be copied or not. Some types (e.g. primitive
		 * integers) can be copied whilst others (e.g. mutable borrows) cannot.
		 *
		 * @param t
		 * @return
		 */
		public boolean copyable();

		/**
		 * Determine whether this type prohibits a given lval from being read. For
		 * example, if this type mutable borrows the lval then it cannot be read.
		 *
		 * @param LVal lval being checked.
		 * @return
		 */
		public boolean prohibitsReading(LVal lv);

		/**
		 * Determine whether this type prohibits a given lval from being written. For
		 * example, if this type borrows the lval then it cannot be written.
		 *
		 * @param lv
		 * @return
		 */
		public boolean prohibitsWriting(LVal lv);

		/**
		 * Constant representing the type void
		 */
		public static Type Unit = new Unit();
		/**
		 * Constant representing the type int
		 */
		public static Type Int = new Int();

		public static abstract class AbstractType extends SyntacticElement.Impl implements Type {
			public AbstractType(Attribute... attributes) {
				super(attributes);
			}

			@Override
			public Type concretize() {
				return this;
			}

			@Override
			public boolean defined() {
				return true;
			}
		}

		public static abstract class AbstractAtom extends AbstractType {
			public AbstractAtom(Attribute... attributes) {
				super(attributes);
			}

			@Override
			public boolean within(BorrowChecker self, Environment e, Lifetime l) {
				return true;
			}

			@Override
			public boolean prohibitsReading(LVal lv) {
				return false;
			}

			@Override
			public boolean prohibitsWriting(LVal lv) {
				return false;
			}

			@Override
			public boolean copyable() {
				return true;
			}

			@Override
			public Type.Undefined undefine() {
				return new Type.Undefined(this);
			}

			@Override
			public Type union(Type t) {
				if(t instanceof Undefined) {
					return t.union(this);
				} else if(equals(t)) {
					return this;
				} else {
					throw new IllegalArgumentException("invalid union");
				}
			}
		}

		public static class Unit extends AbstractAtom {
			public Unit(Attribute... attributes) {
				super(attributes);
			}

			@Override
			public boolean equals(Object o) {
				return o instanceof Type.Unit;
			}

			@Override
			public int hashCode() {
				return 0;
			}

			@Override
			public String toString() {
				return "void";
			}
		}

		public static class Int extends AbstractAtom {
			private Int(Attribute... attributes) {
				super(attributes);
			}

			@Override
			public boolean equals(Object o) {
				return o instanceof Type.Int;
			}

			@Override
			public int hashCode() {
				return 0;
			}

			@Override
			public String toString() { return "int"; }
		}

		public static class Borrow extends AbstractAtom {
			private final boolean mut;
			private final LVal[] lvals;

			public Borrow(boolean mut, LVal item, Attribute... attributes) {
				this(mut,new LVal[] {item}, attributes);
			}

			public Borrow(boolean mut, LVal[] paths, Attribute... attributes) {
				super(attributes);
				assert paths.length > 0;
				this.mut = mut;
				this.lvals = paths;
				// Ensure sorted invariant
				Arrays.sort(paths);
			}

			@Override
			public boolean prohibitsReading(LVal lv) {
				if(mut) {
					// Only a mutable borrow can prohibit other borrows from being read.
					return prohibitsWriting(lv);
				}
				return false;
			}

			@Override
			public boolean prohibitsWriting(LVal lv) {
				// Any conflicting borrow prohibits an lval from being written.
				for (int i = 0; i != lvals.length; ++i) {
					LVal ith = lvals[i];
					// Check whether potential conflict
					if (lv.conflicts(ith)) {
						return true;
					}
				}
				return false;
			}

			public boolean isMutable() {
				return mut;
			}

			@Override
			public boolean copyable() {
				// NOTE: mutable borrows have linear semantics.
				return !mut;
			}

			@Override
			public boolean within(BorrowChecker checker, Environment R, Lifetime l) {
				boolean r = true;
				for (int i = 0; i != lvals.length; ++i) {
					LVal ith = lvals[i];
					checker.check(R.get(ith.name()) != null, BorrowChecker.UNDECLARED_VARIABLE, this);
					Slot C = R.get(ith.name());
					// NOTE: this differs from the presentation as, in fact, we don't need to type
					// the lval fully.
					r &= C.lifetime().contains(l);
				}
				return r;
			}

			@Override
			public Type union(Type t) {
				if(t instanceof Undefined) {
					return t.union(this);
				} else if(t instanceof Borrow) {
					Type.Borrow b = (Type.Borrow) t;
					if(mut == b.mut) {
						// Append both sets of names together
						LVal[] ps = ArrayUtils.append(lvals, b.lvals);
						// Remove any duplicates and ensure result is sorted
						ps = ArrayUtils.sortAndRemoveDuplicates(ps);
						// Done
						return new Type.Borrow(mut, ps);
					}
				}
				throw new IllegalArgumentException("invalid union");
			}

			public LVal[] lvals() {
				return lvals;
			}

			@Override
			public boolean equals(Object o) {
				if(o instanceof Borrow) {
					Borrow b = (Borrow) o;
					return mut == b.mut && Arrays.equals(lvals, b.lvals);
				}
				return false;
			}

			@Override
			public int hashCode() {
				return Boolean.hashCode(mut) ^ Arrays.hashCode(lvals);
			}

			@Override
			public String toString() {
				if (mut) {
					return "&mut " + toString(lvals);
				} else {
					return "&" + toString(lvals);
				}
			}

			private static String toString(LVal[]  slices) {
				if(slices.length == 1) {
					return slices[0].toString();
				} else {
					String r = "";
					for(int i=0;i!=slices.length;++i) {
						if(i != 0) {
							r = r + ",";
						}
						r = r + slices[i];
					}
					return "(" + r + ")";
				}
			}
		}

		public static class Box extends AbstractAtom {
			protected final Type element;

			public Box(Type element, Attribute... attributes) {
				super(attributes);
				this.element = element;
			}

			@Override
			public boolean within(BorrowChecker self, Environment R, Lifetime l) {
				return element.within(self, R, l);
			}

			@Override
			public boolean prohibitsReading(LVal lv) {
				return element.prohibitsReading(lv);
			}

			@Override
			public boolean prohibitsWriting(LVal lv) {
				return element.prohibitsWriting(lv);
			}

			@Override
			public boolean copyable() {
				// NOTE: boxes always exhibit linear semantics.
				return false;
			}

			@Override
			public Type concretize() {
				return new Type.Box(element.concretize());
			}

			@Override
			public boolean defined() {
				return element.defined();
			}

			public Type element() {
				return element;
			}

			@Override
			public Type union(Type t) {
				if(t instanceof Undefined) {
					return t.union(this);
				} else if (t instanceof Type.Box) {
					Type.Box b = (Type.Box) t;
					return new Type.Box(element.union(b.element));
				}
				throw new IllegalArgumentException("invalid join");
			}

			@Override
			public boolean equals(Object o) {
				if(o instanceof Box) {
					Box b = (Box) o;
					return element.equals(b.element);
				}
				return false;
			}

			@Override
			public int hashCode() {
				return 123 ^ element.hashCode();
			}


			@Override
			public String toString() {
				return "\u2610" + element;
			}
		}

		/**
		 * Represents an effect applied to a given type. For example, the <i>shadow
		 * effect</i> represents a type which has been moved (and hence cannot be used
		 * further) but for which we wish to retain its "shape".
		 *
		 * @author David J. Pearce
		 *
		 */
		public static class Undefined extends AbstractType {
			private final Type type;

			public Undefined(Type type, Attribute... attributes) {
				super(attributes);
				assert !(type instanceof Undefined);
				this.type = type;
			}

			public Type getType() {
				return type;
			}

			@Override
			public Type.Undefined undefine() {
				return this;
			}

			@Override
			public boolean within(BorrowChecker self, Environment e, Lifetime l) {
				// Should never be able to assign an effected type
				throw new IllegalArgumentException("deadcode reached");
			}

			@Override
			public boolean prohibitsReading(LVal lv) {
				// NOTE: shadow types do not correspond with actual values and, instead, are
				// used purely to retain knowledge of the "structure". Hence, they do not
				// prohibit other types from being read/written.
				return false;
			}

			@Override
			public boolean prohibitsWriting(LVal lv) {
				// NOTE: shadow types do not correspond with actual values and, instead, are
				// used purely to retain knowledge of the "structure". Hence, they do not
				// prohibit other types from being read/written.
				return false;
			}

			@Override
			public boolean defined() {
				return false;
			}

			@Override
			public boolean copyable() {
				return false;
			}

			@Override
			public Type union(Type t) {
				// Strip effect for joining
				t = (t instanceof Undefined) ? ((Type.Undefined) t).type : t;
				//
				return new Type.Undefined(type.union(t));
			}

			@Override
			public Type concretize() {
				return type.concretize();
			}

			@Override
			public boolean equals(Object o) {
				if(o instanceof Undefined) {
					Undefined e = (Undefined) o;
					return type.equals(e.type);
				}
				return false;
			}

			@Override
			public int hashCode() {
				return type.hashCode();
			}

			@Override
			public String toString() {
				return "/" + type + "/";
			}
		}
	}

	public static class LVal extends SyntacticElement.Impl implements Comparable<LVal> {
		private final String name;
		private final Path path;

		public LVal(String name, Path path, Attribute... attributes) {
			super(attributes);
			this.name = name;
			this.path = path;
		}

		public String name() {
			return name;
		}

		public Path path() {
			return path;
		}

		public boolean conflicts(LVal lv) {
			return name.equals(lv.name) && path.conflicts(lv.path);
		}

		@Override
		public boolean equals(Object o) {
			if(o instanceof LVal) {
				LVal s = (LVal) o;
				return name.equals(s.name) && path.equals(s.path);
			}
			return false;
		}

		@Override
		public int hashCode() {
			return name.hashCode() ^ path.hashCode();
		}

		/**
		 * Locate the location to which this path refers in the given state.
		 *
		 * @param state
		 * @return
		 */
		public Reference locate(State state) {
			// Identify root of path
			Reference l = state.locate(name);
			// Apply each element in turn
			return path.apply(l, state.store());
		}

		public LVal traverse(Path p, int i) {
			final Path.Element[] path_elements = path.elements;
			final Path.Element[] p_elements = p.elements;
			// Handle common case
			if (p_elements.length == i) {
				return this;
			} else {
				final int n = path_elements.length;
				final int m = p_elements.length - i;
				Path.Element[] nelements = new Path.Element[n + m];
				System.arraycopy(path_elements, 0, nelements, 0, n);
				System.arraycopy(p_elements, i, nelements, n, m);
				return new LVal(name, new Path(nelements));
			}
		}

		@Override
		public int compareTo(LVal s) {
			int c = name.compareTo(s.name);
			if(c == 0) {
				c = path.compareTo(s.path);
			}
			return c;
		}

		@Override
		public String toString() {
			return path.toString(name);
		}

		public static LVal construct(String name, boolean flag) {
			Path path = flag ? Path.EMPTY : Path.DEREF;
			return new LVal(name,path);
		}

		public static Domain.Big<LVal> toBigDomain(Domain.Small<String> vars) {
			return Domains.Product(vars, Domains.BOOL, LVal::construct);
		}
	}

	/**
	 * A path is a sequence of path elements which describe positions within a value
	 * or type.
	 *
	 * @author David J. Pearce
	 *
	 */
	public static class Path extends SyntacticElement.Impl implements Comparable<Path> {
		public static Element DEREF_ELEMENT = new Deref();
		public final static Path EMPTY = new Path();
		public final static Path DEREF = new Path(new Path.Element[] {DEREF_ELEMENT});

		/**
		 * The sequence of elements making up this path
		 */
		private final Element[] elements;

		public Path(Attribute... attributes) {
			this(new Element[0],attributes);
		}

		public Path(Element[] elements, Attribute... attributes) {
			super(attributes);
			this.elements = elements;
		}

		/**
		 * Identifiers how many elements in this path.
		 *
		 * @return
		 */
		public int size() {
			return elements.length;
		}

		/**
		 * Read a particular element from this path.
		 *
		 * @param index
		 * @return
		 */
		public Path.Element get(int index) {
			return elements[index];
		}

		/**
		 * Apply this path to a given location. For example, if this path represents a
		 * dereference then we will read the location and returns its contents (as a
		 * location).
		 *
		 * @param location
		 * @param store
		 * @return
		 */
		public Reference apply(Reference location, Store store) {
			for (int i = 0; i != elements.length; ++i) {
				location = elements[i].apply(store, location);
			}
			return location;
		}

		/**
		 * Determine whether two paths conflict. That is, represent potentially
		 * overlapping locations. For example, variables <code>x</code> and
		 * <code>y</code> do not conflict. Likewise, tuple accesses <code>x.0</code> and
		 * <code>x.1</code> don't conflict. However, <code>x</code> conflicts with
		 * itself. Likewise, <code>x.1</code> conflicts with itself and <code>x</code>.
		 *
		 * @param p
		 * @return
		 */
		public boolean conflicts(Path p) {
			// Determine smallest path length
			final int n = Math.min(elements.length, p.elements.length);
			// Iterate elements looking for something which doesn't conflict.
			for(int i=0;i<n;++i) {
				Element ith = elements[i];
				Element pith = p.elements[i];
				if(!ith.conflicts(pith)) {
					return false;
				}
			}
			// Done
			return true;
		}

		public Path append(Path rhs) {
			final int n = elements.length;
			final int m = rhs.elements.length;
			Element[] es = new Element[n + m];
			System.arraycopy(elements, 0, es, 0, n);
			System.arraycopy(rhs.elements, 0, es, n, m);
			return new Path(es);
		}

		public Path subpath(int start) {
			Element[] es = Arrays.copyOfRange(elements, start, elements.length);
			return new Path(es);
		}

		@Override
		public boolean equals(Object o) {
			if(o instanceof Path) {
				Path p = (Path) o;
				return Arrays.deepEquals(elements, p.elements);
			}
			return false;
		}

		@Override
		public int hashCode() {
			return Arrays.hashCode(elements);
		}

		@Override
		public int compareTo(Path p) {
			if (elements.length < p.elements.length) {
				return -1;
			} else if (elements.length > p.elements.length) {
				return 1;
			}
			for (int i = 0; i != elements.length; ++i) {
				int c = elements[i].compareTo(p.elements[i]);
				if (c != 0) {
					return c;
				}
			}
			return 0;
		}

		public String toString(String src) {
			for(int i=0;i!=elements.length;++i) {
				if(i != 0) {
					src = "(" + src + ")";
				}
			  src = elements[i].toString(src);
			}
			return src;
		}

		public interface Element extends Comparable<Element> {
			/**
			 * Determine whether a given path element conflicts with another path element.
			 *
			 * @param e
			 * @return
			 */
			public boolean conflicts(Element e);

			/**
			 * Apply this element to a given location in a given store, producing an updated
			 * location.
			 *
			 * @param loc
			 * @param store
			 * @return
			 */
			public Reference apply(Store store, Reference loc);

			public String toString(String src);
		}

		/**
		 * Represents a dereference path element
		 */
		public static class Deref implements Element {

			@Override
			public int compareTo(Element arg0) {
				if (arg0 == DEREF_ELEMENT) {
					return 0;
				} else {
					// FIXME: do something here?
					throw new IllegalArgumentException("GOT HERE");
				}
			}

			@Override
			public boolean conflicts(Element e) {
				return true;
			}

			@Override
			public Reference apply(Store store, Reference loc) {
				return (Reference) store.read(loc);
			}

			@Override
			public String toString(String src) {
				return "*" + src;
			}

			@Override
			public boolean equals(Object o) {
				return o instanceof Deref;
			}

			@Override
			public int hashCode() {
				return 0;
			}
		};
	}

	/**
	 * Construct a domain for terms with a maximum level of nesting.
	 *
	 * @param depth
	 *            The maximum depth of block nesting.
	 * @param width
	 *            The maximum width of a block.
	 * @param lifetime
	 *            The lifetime of the enclosing block
	 * @param ints
	 *            The domain of integers to use
	 * @param declared
	 *            The set of variable names for variables which have already been
	 *            declared.
	 * @param undeclared The set of variable names for variables which have not
	 *        already been declared.
	 * @return
	 */
	public static Domain.Big<Term> toBigDomain(int depth, int width, Lifetime lifetime,
			Domain.Small<Integer> ints, Domain.Small<String> declared, Domain.Small<String> undeclared) {
		// Construct expressions
		Domain.Big<Term> expressions = toBigDomain(1, ints, declared);
		// Construct statements
		return toBigDomain(depth - 1, width, lifetime, expressions, declared, undeclared);
	}

	/**
	 * Construct a domain of statements.
	 *
	 * @param depth
	 * @param width
	 * @param lifetime
	 * @param expressions
	 * @param declared
	 * @param undeclared
	 * @return
	 */
	public static Domain.Big<Term> toBigDomain(int depth, int width, Lifetime lifetime, Domain.Big<Term> expressions,
			Domain.Small<String> declared, Domain.Small<String> undeclared) {
		// Construct adaptor to convert from variable names to lvals.
		Domain.Big<LVal> lvals = LVal.toBigDomain(declared);
		// Let statements can only be constructed from undeclared variables
		Domain.Big<Term.Let> lets = Term.Let.toBigDomain(undeclared, expressions);
		// Assignments can only use declared variables
		Domain.Big<Term.Assignment> assigns = Term.Assignment.toBigDomain(lvals, expressions);
		if (depth <= 0) {
			return Domains.Union(lets, assigns);
		} else {
			// Determine lifetime for blocks at this level
			lifetime = lifetime.freshWithin();
			// Recursively construct subdomain generator
			Domain.Big<Term> terms = toBigDomain(depth - 1, width, lifetime, expressions, declared, undeclared);
			// Using this construct the block generator
			Domain.Big<Term.Block> blocks = Term.Block.toBigDomain(lifetime, 1, width, terms);
			// Done
			return Domains.Union(lets, assigns, blocks);
		}
	}

	/**
	 * Construct a domain of expressions.
	 *
	 * @param ints
	 * @param declared
	 * @return
	 */
	public static Domain.Big<Term> toBigDomain(int depth, Domain.Small<Integer> ints, Domain.Small<String> declared) {
		// Construct adaptor to convert from variable names to lvals.
		Domain.Big<LVal> lvals = LVal.toBigDomain(declared);
		// Terminals
		Domain.Big<? extends Term> integers = Value.Integer.toBigDomain(ints);
		Domain.Big<? extends Term> borrows = Term.Borrow.toBigDomain(lvals);
		Domain.Big<? extends Term> derefs = Term.Access.toBigDomain(lvals);
		Domain.Big<Term> terminals = Domains.Union(integers, derefs, borrows);
		//
		Domain.Big<? extends Term>[] domains = new Domain.Big[depth+3];
		domains[0] = integers;
		domains[1] = derefs;
		domains[2] = borrows;
		//
		for(int i=0;i<depth;++i) {
			domains[i+3] = Term.Box.toBigDomain(i,terminals);
		}
		//
		return Domains.Union(domains);
	}

	/**
	 * Implements the concept of a lifetime which permits the creation of fresh
	 * "inner" lifetimes and the ability to test whether one lifetime is inside
	 * another.
	 *
	 * @author David J. Pearce
	 *
	 */
	public static class Lifetime {
		private static int LIFETIME_COUNTER = 0;
		private final int index;
		private Lifetime[] parents;

		public Lifetime() {
			this.parents = new Lifetime[0];
			this.index = LIFETIME_COUNTER++;
		}

		public Lifetime(Lifetime... parents) {
			this.parents = parents;
			this.index = LIFETIME_COUNTER++;
		}

		/**
		 * Check whether a given lifetime is within this lifetime. This is achieved by
		 * traversing the tree of lifetimes looking for the given lifetime in question.
		 *
		 * @param l
		 * @return
		 */
		public boolean contains(Lifetime l) {
			if (l == null) {
				return false;
			} else if (l == this) {
				// Base case
				return true;
			} else {
				// Recursive case
				for (int i = 0; i != l.parents.length; ++i) {
					if (contains(l.parents[i])) {
						return true;
					}
				}
				return false;
			}
		}

		/**
		 * Get the outermost lifetime which this lifetime is within.
		 *
		 * @return
		 */
		public Lifetime getRoot() {
			if (parents.length == 0) {
				return this;
			} else {
				return parents[0].getRoot();
			}
		}

		/**
		 * Construct a fresh lifetime within this lifetime.
		 *
		 * @return
		 */
		public Lifetime freshWithin() {
			// Create the new lifetime
			return new Lifetime(this);
		}

		/**
		 * Assert that this lifetime is within a given lifetime.
		 *
		 * @param l
		 */
		public void assertWithin(Lifetime l) {
			// Check whether contraint already exists
			if(!l.contains(this)) {
				// Nope
				final int n = parents.length;
				this.parents = Arrays.copyOf(parents, n + 1);
				this.parents[n] = l;
			}
		}

		@Override
		public String toString() {
			return "l" + index;
		}
	}

	public static void main(String[] args) {
		Lifetime root = new Lifetime();
		Domain.Small<Integer> ints = Domains.Int(0, 0);
		Domain.Small<String> declared = Domains.Finite("x");
		Domain.Big<Term> terms = toBigDomain(2, 1, root, ints, declared, declared);
		//
		for(int i=0;i!=terms.bigSize().intValue();++i) {
			Term t = terms.get(BigInteger.valueOf(i));
			System.out.println("[" + i + "] " + t);
		}
	}
}
