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

import java.util.Arrays;
import java.util.List;

import javax.lang.model.element.Element;

import featherweightrust.core.BorrowChecker.Cell;
import featherweightrust.core.BorrowChecker.Environment;
import featherweightrust.core.Syntax.Term.Variable;
import featherweightrust.core.Syntax.Type;
import featherweightrust.util.ArrayUtils;
import featherweightrust.util.SyntacticElement;
import jmodelgen.core.Domain;
import jmodelgen.core.Walker;
import jmodelgen.core.Domains;
import jmodelgen.util.Walkers;

public class Syntax {
	public final static int TERM_let = 0;
	public final static int TERM_assignment = 1;
	public final static int TERM_indirectassignment = 2;
	public final static int TERM_block = 3;
	public final static int TERM_move = 4;
	public final static int TERM_copy = 5;
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
			private final Term.Variable variable;
			private final Term initialiser;

			public Let(Term.Variable variable, Term initialiser, Attribute... attributes) {
				super(TERM_let, attributes);
				this.variable = variable;
				this.initialiser = initialiser;
			}

			/**
			 * Return the variable being declared.
			 *
			 * @return
			 */
			public Term.Variable variable() {
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
				return "let mut " + variable.name() + " = " + initialiser + ";";
			}

			public static Let construct(String variable, Term initialiser) {
				return new Let(new Term.Variable(variable), initialiser);
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
			public final Term.Variable lhs;
			public final Term rhs;

			public Assignment(Term.Variable lhs, Term rhs, Attribute... attributes) {
				super(TERM_assignment,attributes);
				this.lhs = lhs;
				this.rhs = rhs;
			}

			public Term.Variable leftOperand() {
				return lhs;
			}

			public Term rightOperand() {
				return rhs;
			}

			@Override
			public String toString() {
				return lhs.name + " = " + rhs + ";";
			}

			public static Assignment construct(String variable, Term initialiser) {
				return new Assignment(new Term.Variable(variable), initialiser);
			}

			public static Domain.Big<Assignment> toBigDomain(Domain.Small<String> first, Domain.Big<Term> second) {
				return Domains.Product(first, second, Assignment::construct);
			}
		}

		/**
		 * Represents an indirect assignment such as the following:
		 *
		 * <pre>
		 * *x = e
		 * </pre>
		 *
		 * @author David J. Pearce
		 *
		 */
		public class IndirectAssignment extends AbstractTerm {
			private final Term.Variable lhs;
			private final Term rhs;

			public IndirectAssignment(Term.Variable lhs, Term rhs, Attribute... attributes) {
				super(TERM_indirectassignment,attributes);
				this.lhs = lhs;
				this.rhs = rhs;
			}

			public Term.Variable leftOperand() {
				return lhs;
			}

			public Term rightOperand() {
				return rhs;
			}

			@Override
			public String toString() {
				return "*" + lhs.name + " = " + rhs + ";";
			}

			public static IndirectAssignment construct(String variable, Term initialiser) {
				return new IndirectAssignment(new Term.Variable(variable), initialiser);
			}

			public static Domain.Big<IndirectAssignment> toBigDomain(Domain.Small<String> first, Domain.Big<Term> second) {
				return Domains.Product(first, second, IndirectAssignment::construct);
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
		public class Block extends AbstractTerm {
			private final Lifetime lifetime;
			private final Term[] stmts;

			public Block(Lifetime lifetime, Term[] stmts, Attribute... attributes) {
				super(TERM_block,attributes);
				this.lifetime = lifetime;
				this.stmts = stmts;
			}

			public Lifetime lifetime() {
				return lifetime;
			}

			public int size() {
				return stmts.length;
			}

			public Term get(int i) {
				return stmts[i];
			}

			public Term[] toArray() {
				return stmts;
			}
			@Override
			public String toString() {
				String contents = "";
				for (int i = 0; i != stmts.length; ++i) {
					contents += stmts[i] + " ";
				}
				return "{ " + contents + "}";
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

		public class Variable extends AbstractTerm implements Term {
			private final String name;

			public Variable(String name, Attribute... attributes) {
				super(TERM_move, attributes);
				this.name = name;
			}

			public String name() {
				return name;
			}

			@Override
			public boolean equals(Object o) {
				if(o instanceof Variable) {
					Variable v = (Variable) o;
					return name.equals(v.name);
				} else {
					return false;
				}
			}

			@Override
			public int hashCode() {
				return name.hashCode();
			}
			@Override
			public String toString() {
				return name;
			}

			public static Term.Variable construct(String name) {
				return new Term.Variable(name);
			}

			public static Domain.Big<Variable> toBigDomain(Domain.Small<String> subdomain) {
				return Domains.Adaptor(subdomain,Variable::construct);
			}
		}

		public class Dereference extends AbstractTerm implements Term {
			private final Term.Variable operand;

			public Dereference(Term.Variable operand, Attribute... attributes) {
				super(TERM_dereference, attributes);
				this.operand = operand;
			}

			public Term.Variable operand() {
				return operand;
			}

			@Override
			public String toString() {
				return "*" + operand.toString();
			}

			public static Term.Dereference construct(String name) {
				return new Term.Dereference(new Term.Variable(name));
			}

			public static Domain.Big<Dereference> toBigDomain(Domain.Small<String> subdomain) {
				return Domains.Adaptor(subdomain, Dereference::construct);
			}
		}

		public class Borrow extends AbstractTerm implements Term {
			private final Slice operand;
			private final boolean mutable;

			public Borrow(Slice operand, boolean mutable, Attribute... attributes) {
				super(TERM_borrow,attributes);
				this.operand = operand;
				this.mutable = mutable;
			}

			public Slice operand() {
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

			public static Term.Borrow construct(String path, Boolean mutable) {
				//return new Term.Borrow(new Term.Variable(path), mutable);
				throw new RuntimeException("fix me");
			}

			public static Domain.Big<Borrow> toBigDomain(Domain.Small<String> subdomain) {
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

			public static Term.Box construct(Term operand) {
				return new Term.Box(operand);
			}

			public static Domain.Big<Box> toBigDomain(Domain.Big<Term> subdomain) {
				return Domains.Adaptor(subdomain, Box::construct);
			}
		}

		public class Copy extends AbstractTerm implements Term {
			private final Term.Variable operand;

			public Copy(Term.Variable operand, Attribute... attributes) {
				super(TERM_copy, attributes);
				this.operand = operand;
			}

			public Term.Variable operand() {
				return operand;
			}

			@Override
			public String toString() {
				return "!" + operand.toString();
			}

			public static Domain.Big<Copy> toBigDomain(Domain.Big<Term.Variable> subdomain) {
				return Domains.Adaptor(subdomain, (e) -> new Copy(e));
			}
		}
	}

	public interface Value extends Term {

		public static Unit Unit = new Unit();

		/**
		 * Read the value at a given path within this value. If path is empty, then that
		 * identifies this value to be returned. For example, given a tuple
		 * <code>(1,2)</code>, reading the value at path <code>1</code> gives
		 * <code>2</code>.
		 *
		 * @param path
		 * @return
		 */
		public Value read(Path path);

		/**
		 * Write a give value into this value at a given path, returned the updated
		 * value.
		 *
		 * @param path
		 * @param value
		 * @return
		 */
		public Value write(Path path, Value value);

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
			public Value read(Path path) {
				if(path.size() != 0) {
					throw new IllegalArgumentException("invalid path");
				}
				return this;
			}

			@Override
			public Value write(Path path, Value value) {
				if(path.size() != 0) {
					throw new IllegalArgumentException("invalid path");
				}
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

		public class Location extends Atom {
			private final int address;
			private final Path path;

			public Location(int address, Attribute... attributes) {
				this(address,Path.EMPTY,attributes);
			}

			public Location(int address, Path path, Attribute... attributes) {
				super(TERM_location, attributes);
				this.address = address;
				this.path = path;
			}

			/**
			 * Get the base address of the allocation unit this location refers to.
			 *
			 * @return
			 */
			public int getAddress() {
				return address;
			}

			/**
			 * Get the path within the allocation unit this location refers to.
			 *
			 * @return
			 */
			public Path getPath() {
				return path;
			}

			public Location append(Path p) {
				return new Location(address, path.append(p), attributes());
			}

			@Override
			public int hashCode() {
				return address;
			}

			@Override
			public boolean equals(Object o) {
				if (o instanceof Location) {
					Location l = ((Location) o);
					return l.address == address && path.equals(l.path);
				}
				return false;
			}

			@Override
			public String toString() {
				return "&" + address + path;
			}
		}
	}

	public interface Type {
		/**
		 * Join two types together.
		 *
		 * @param type
		 * @return
		 */
		public Type join(Type type);

		/**
		 * Check whether this type can safely live within a given lifetime. That is, the
		 * lifetime of any reachable object through this type does not outlive the
		 * lifetime.
		 *
		 * @param self
		 * @param R
		 * @param l
		 * @return
		 */
		public boolean within(BorrowChecker self, Environment R, Lifetime l);

		/**
		 * Check whether a type exhibits copy or move semantics. In some cases, this may
		 * depend on element types contained within.
		 *
		 * @param t
		 * @return
		 */
		public boolean copyable();

		/**
		 * Check whether this type is "compatible" with another. That is, for a variable
		 * declared with a given type, it can subsequently only be assigned values which
		 * are compatible with its type.
		 *
		 * @param R1 --- the environment in which this type is defined.
		 * @param t2 --- the other type being compared for compatibility.
		 * @param R2 --- the environment in which the other type is defined.
		 * @return
		 */
		public boolean compatible(Environment R1, Type t2, Environment R2);

		/**
		 * Determine whether this type indicates that a given path is already borrowed
		 * (either mutably or not).
		 *
		 * @param path The path being checked (which e.g. could be a variable)
		 * @param mut Flag indicating whether we're interested only mutable borrows, or
		 *            any borrows.
		 * @return
		 */
		public boolean borrowed(Slice slice, boolean mut);

		/**
		 * Extract the (sub)type to which a given path corresponds.
		 *
		 * @param path
		 * @return
		 */
		public Type read(Path path);

		/**
		 * Write a given type to a given path within this type. For example, writing
		 * <code>int</code> into <code>(bool,bool)</code> at position <code>1</code>
		 * gives <code>(int,bool)</code>.
		 *
		 * @param path
		 * @param type
		 * @return
		 */
		public Type write(Path path, Type type);

		public static Type Void = new Void();

		public static abstract class AbstractAtom extends SyntacticElement.Impl implements Type {
			public AbstractAtom(Attribute... attributes) {
				super(attributes);
			}

			@Override
			public boolean within(BorrowChecker self, Environment e, Lifetime l) {
				return true;
			}

			@Override
			public boolean borrowed(Slice slice, boolean mut) {
				return false;
			}

			@Override
			public boolean copyable() {
				return true;
			}

			@Override
			public Type join(Type t) {
				if(equals(t)) {
					return this;
				} else {
					throw new IllegalArgumentException("invalid join");
				}
			}

			@Override
			public Type read(Path p) {
				if(p.size() == 0) {
					return this;
				} else {
					throw new IllegalArgumentException("invalid position");
				}
			}

			@Override
			public Type write(Path p, Type t) {
				if (p.size() == 0) {
					return t;
				} else {
					throw new IllegalArgumentException("invalid position");
				}
			}
		}

		public class Void extends AbstractAtom implements Type {
			public Void(Attribute... attributes) {
				super(attributes);
			}

			@Override
			public boolean compatible(Environment R1, Type t2, Environment R2) {
				return t2 instanceof Type.Void;
			}

			@Override
			public boolean equals(Object o) {
				return o instanceof Type.Void;
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

		public class Int extends AbstractAtom implements Type {
			public Int(Attribute... attributes) {
				super(attributes);
			}

			@Override
			public boolean compatible(Environment R1, Type t2, Environment R2) {
				return t2 instanceof Type.Int;
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

		public class Borrow extends AbstractAtom implements Type {
			private final boolean mut;
			private final Slice[] items;

			public Borrow(boolean mut, Slice item, Attribute... attributes) {
				this(mut,new Slice[] {item}, attributes);
			}

			public Borrow(boolean mut, Slice[] paths, Attribute... attributes) {
				super(attributes);
				if(paths.length == 0) {
					throw new IllegalArgumentException("invalid names argumetn");
				}
				this.mut = mut;
				this.items = paths;
				// Ensure sorted invariant
				Arrays.sort(paths);
			}

			@Override
			public boolean borrowed(Slice slice, boolean mut) {
				if (!mut || isMutable()) {
					for (int i = 0; i != items.length; ++i) {
						// FIXME: this is broken!
						if (items[i].conflicts(slice)) {
							return true;
						}
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
			public boolean compatible(Environment R1, Type t2, Environment R2) {
				if (t2 instanceof Type.Borrow) {
					Type.Borrow b2 = (Type.Borrow) t2;
					// NOTE: follow holds because all members of a single borrow must be compatible
					// by construction.
					Type ti = R1.typeOf(items[0]);
					Type tj = R2.typeOf(b2.slices()[0]);
					//
					return mut == b2.isMutable() && ti.compatible(R1, tj, R2);
				} else {
					return false;
				}
			}

			@Override
			public boolean within(BorrowChecker checker, Environment R, Lifetime l) {
				boolean r = true;
				for (int i = 0; i != items.length; ++i) {
					Slice ith = items[i];
					checker.check(R.get(ith.name()) != null, BorrowChecker.UNDECLARED_VARIABLE, this);
					Cell C = R.get(ith.name());
					r &= C.lifetime().contains(l);
				}
				return r;
			}

			@Override
			public Type.Borrow join(Type t) {
				if(t instanceof Borrow) {
					Type.Borrow b = (Type.Borrow) t;
					if(mut == b.mut) {
						// Append both sets of names together
						Slice[] ps = ArrayUtils.append(items, b.items);
						// Remove any duplicates and ensure result is sorted
						ps = ArrayUtils.sortAndRemoveDuplicates(ps);
						// Done
						return new Type.Borrow(mut, ps);
					}
				}
				throw new IllegalArgumentException("invalid join");
			}

			public Slice[] slices() {
				return items;
			}

			@Override
			public boolean equals(Object o) {
				if(o instanceof Borrow) {
					Borrow b = (Borrow) o;
					return mut == b.mut && Arrays.equals(items, b.items);
				}
				return false;
			}

			@Override
			public int hashCode() {
				return Boolean.hashCode(mut) ^ Arrays.hashCode(items);
			}

			@Override
			public String toString() {
				if (mut) {
					return "&mut " + toString(items);
				} else {
					return "&" + toString(items);
				}
			}

			private static String toString(Slice[]  slices) {
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

		public class Box extends AbstractAtom implements Type {
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
			public boolean borrowed(Slice slice, boolean mut) {
				return element.borrowed(slice, mut);
			}

			@Override
			public boolean copyable() {
				// NOTE: boxes always exhibit linear semantics.
				return false;
			}

			@Override
			public boolean compatible(Environment R1, Type t2, Environment R2) {
				if (t2 instanceof Type.Box) {
					Type.Box b2 = (Type.Box) t2;
					return element.compatible(R1, b2.element, R2);
				} else {
					return false;
				}
			}

			public Type element() {
				return element;
			}

			@Override
			public Type join(Type t) {
				if (t instanceof Type.Box) {
					Type.Box b = (Type.Box) t;
					return new Type.Box(element.join(t));
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
				return "[]" + element;
			}
		}
	}

	public static class Slice extends SyntacticElement.Impl implements Comparable<Slice> {
		private final String name;
		private final Path path;

		public Slice(String name, Path path, Attribute... attributes) {
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

		public boolean conflicts(Slice s) {
			return name.equals(s.name) && path.conflicts(s.path);
		}

		@Override
		public boolean equals(Object o) {
			if(o instanceof Slice) {
				Slice s = (Slice) o;
				return name.equals(s.name) && path.equals(s.path);
			}
			return false;
		}

		@Override
		public int hashCode() {
			return name.hashCode() ^ path.hashCode();
		}

		@Override
		public int compareTo(Slice s) {
			int c = name.compareTo(s.name);
			if(c == 0) {
				c = path.compareTo(s.path);
			}
			return c;
		}

		@Override
		public String toString() {
			return name + path;
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
		/**
		 * A constant representing the empty path.
		 */
		public final static Path EMPTY = new Path() {
			@Override
			public Path append(Path p) {
				// Appending path to empty path returns path
				return p;
			}
		};

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

		public Path append(Path p) {
			throw new IllegalArgumentException("implement me");
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

		@Override
		public String toString() {
			String p = "";
			for(int i=0;i!=elements.length;++i) {
				p += "." + elements[i];
			}
			return p;
		}

		public interface Element extends Comparable<Element> {
			/**
			 * Determine whether a given path element conflicts with another path element.
			 *
			 * @param e
			 * @return
			 */
			public boolean conflicts(Element e);
		}
	}

	/**
	 * Construct a domain for statements with a maximum level of nesting.
	 *
	 * @param depth
	 *            The maximum depth of block nesting.
	 * @param width
	 *            The maximum width of a block.
	 * @param lifetime
	 *            The lifetime of the enclosing block
	 * @param expressions
	 *            The domain of expressions to use
	 * @param declared
	 *            The set of variable names for variables which have already been
	 *            declared.
	 * @param undeclared The set of variable names for variables which have not
	 *        already been declared.
	 * @return
	 */
	public static Domain.Big<Term> toBigDomain(int depth, int width, Lifetime lifetime, Domain.Big<Term> expressions,
			Domain.Small<String> declared, Domain.Small<String> undeclared) {
		// Let statements can only be constructed from undeclared variables
		Domain.Big<Term.Let> lets = Term.Let.toBigDomain(undeclared, expressions);
		// Assignments can only use declared variables
		Domain.Big<Term.Assignment> assigns = Term.Assignment.toBigDomain(declared, expressions);
		// Indirect assignments can only use declared variables
		Domain.Big<Term.IndirectAssignment> indirects = Term.IndirectAssignment.toBigDomain(declared, expressions);
		if (depth == 0) {
			return Domains.Union(lets, assigns, indirects);
		} else {
			// Determine lifetime for blocks at this level
			lifetime = lifetime.freshWithin();
			// Recursively construct subdomain generator
			Domain.Big<Term> subdomain = toBigDomain(depth - 1, width, lifetime, expressions, declared, undeclared);
			// Using this construct the block generator
			Domain.Big<Term.Block> blocks = Term.Block.toBigDomain(lifetime, 1, width, subdomain);
			// Done
			return Domains.Union(lets, assigns, indirects, blocks);
		}
	}

	public static Domain.Big<Term> toBigDomain(int depth, Domain.Small<Integer> ints, Domain.Small<String> names) {
		Domain.Big<Value.Integer> integers = Value.Integer.toBigDomain(ints);
		Domain.Big<Term.Variable> moves = Term.Variable.toBigDomain(names);
		Domain.Big<Term.Copy> copys = Term.Copy.toBigDomain(moves);
		Domain.Big<Term.Borrow> borrows = Term.Borrow.toBigDomain(names);
		Domain.Big<Term.Dereference> derefs = Term.Dereference.toBigDomain(names);
		if (depth == 0) {
			return Domains.Union(integers, moves, copys, borrows, derefs);
		} else {
			Domain.Big<Term> subdomain = toBigDomain(depth - 1, ints, names);
			Domain.Big<Term.Box> boxes = Term.Box.toBigDomain(subdomain);
			return Domains.Union(integers, moves, copys, borrows, derefs, boxes);
		}
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
		private final Lifetime parent;

		public Lifetime() {
			this.parent = null;
		}

		public Lifetime(Lifetime parent) {
			this.parent = parent;
		}

		/**
		 * Check whether a given lifetime is within this lifetime. This is achieved by
		 * traversing the tree of lifetimes looking for the given lifetime in question.
		 *
		 * @param l
		 * @return
		 */
		public boolean contains(Lifetime l) {
			if(l == null) {
				return false;
			} else if (l == this) {
				// Base case
				return true;
			} else {
				// Recursive case
				return contains(l.parent);
			}
		}

		/**
		 * Get the outermost lifetime which this lifetime is within.
		 *
		 * @return
		 */
		public Lifetime getRoot() {
			if (parent == null) {
				return this;
			} else {
				return parent.getRoot();
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

		@Override
		public String toString() {
			return "l" + System.identityHashCode(this);
		}
	}

	public static void main(String[] args) {

	}
}
