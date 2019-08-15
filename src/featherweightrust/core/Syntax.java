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
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;

import featherweightrust.core.Syntax.Expr.Variable;
import featherweightrust.util.SyntacticElement;
import jmodelgen.core.Domain;
import jmodelgen.core.Walker;
import jmodelgen.util.AbstractBigDomain;
import jmodelgen.util.AbstractDomain;
import jmodelgen.util.AbstractWalker;
import jmodelgen.core.Domains;
import jmodelgen.util.Walkers;

public class Syntax {

	public interface Stmt extends SyntacticElement {

		public static abstract class AbstractStmt extends SyntacticElement.Impl implements Stmt {
			public AbstractStmt(Attribute... attributes) {
				super(attributes);
			}
		}

		public abstract String toRustString();

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
		public class Let extends AbstractStmt {
			private final Expr.Variable variable;
			private final Expr initialiser;

			public Let(Expr.Variable variable, Expr initialiser, Attribute... attributes) {
				super(attributes);
				this.variable = variable;
				this.initialiser = initialiser;
			}

			/**
			 * Return the variable being declared.
			 *
			 * @return
			 */
			public Expr.Variable variable() {
				return variable;
			}

			/**
			 * Return the expression used to initialise variable
			 *
			 * @return
			 */
			public Expr initialiser() {
				return initialiser;
			}

			@Override
			public String toString() {
				return "let mut " + variable.name() + " = " + initialiser + ";";
			}

			@Override
			public String toRustString() {
				return "let mut " + variable.name() + " = " + initialiser.toRustString() + ";";
			}

			public static Let construct(String variable, Expr initialiser) {
				return new Let(new Expr.Variable(variable), initialiser);
			}

			public static Domain.Big<Let> toBigDomain(Domain.Small<String> first, Domain.Big<Expr> second) {
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
		public class Assignment extends AbstractStmt {
			public final Expr.Variable lhs;
			public final Expr rhs;

			public Assignment(Expr.Variable lhs, Expr rhs, Attribute... attributes) {
				super(attributes);
				this.lhs = lhs;
				this.rhs = rhs;
			}

			public Expr.Variable leftOperand() {
				return lhs;
			}

			public Expr rightOperand() {
				return rhs;
			}

			@Override
			public String toString() {
				return lhs.name + " = " + rhs + ";";
			}

			@Override
			public String toRustString() {
				return lhs.name + " = " + rhs.toRustString() + ";";
			}

			public static Assignment construct(String variable, Expr initialiser) {
				return new Assignment(new Expr.Variable(variable), initialiser);
			}

			public static Domain.Big<Assignment> toBigDomain(Domain.Small<String> first, Domain.Big<Expr> second) {
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
		public class IndirectAssignment extends AbstractStmt {
			private final Expr.Variable lhs;
			private final Expr rhs;

			public IndirectAssignment(Expr.Variable lhs, Expr rhs, Attribute... attributes) {
				super(attributes);
				this.lhs = lhs;
				this.rhs = rhs;
			}

			public Expr.Variable leftOperand() {
				return lhs;
			}

			public Expr rightOperand() {
				return rhs;
			}

			@Override
			public String toString() {
				return "*" + lhs.name + " = " + rhs + ";";
			}

			@Override
			public String toRustString() {
				return "*" + lhs.name + " = " + rhs.toRustString() + ";";
			}

			public static IndirectAssignment construct(String variable, Expr initialiser) {
				return new IndirectAssignment(new Expr.Variable(variable), initialiser);
			}

			public static Domain.Big<IndirectAssignment> toBigDomain(Domain.Small<String> first, Domain.Big<Expr> second) {
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
		public class Block extends AbstractStmt {
			private final Lifetime lifetime;
			private final Stmt[] stmts;

			public Block(Lifetime lifetime, Stmt[] stmts, Attribute... attributes) {
				super(attributes);
				this.lifetime = lifetime;
				this.stmts = stmts;
			}

			public Lifetime lifetime() {
				return lifetime;
			}

			public int size() {
				return stmts.length;
			}

			public Stmt get(int i) {
				return stmts[i];
			}

			public Stmt[] toArray() {
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
			@Override
			public String toRustString() {
				String contents = "";
				for (int i = 0; i != stmts.length; ++i) {
					contents += stmts[i].toRustString() + " ";
				}
				return "{ " + contents + "}";
			}

			public static Block construct(Lifetime lifetime, List<Stmt> items) {
				return new Block(lifetime, items.toArray(new Stmt[items.size()]));
			}

			public static Domain.Big<Block> toBigDomain(Lifetime lifetime, int min, int max,
					Domain.Big<Stmt> stmts) {
				return Domains.Adaptor(Domains.Array(min, max, stmts), (ss) -> new Block(lifetime, ss));
			}

			public static Walker<Block> toWalker(Lifetime lifetime, int min, int max, Walker.State<Stmt> seed) {
				return Walkers.Product(min, max, seed, (items) -> construct(lifetime, items));
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
		public static Domain.Big<Stmt> toBigDomain(int depth, int width, Lifetime lifetime, Domain.Big<Expr> expressions,
				Domain.Small<String> declared, Domain.Small<String> undeclared) {
			// Let statements can only be constructed from undeclared variables
			Domain.Big<Let> lets = Stmt.Let.toBigDomain(undeclared, expressions);
			// Assignments can only use declared variables
			Domain.Big<Assignment> assigns = Stmt.Assignment.toBigDomain(declared, expressions);
			// Indirect assignments can only use declared variables
			Domain.Big<IndirectAssignment> indirects = Stmt.IndirectAssignment.toBigDomain(declared, expressions);
			if (depth == 0) {
				return Domains.Union(lets, assigns, indirects);
			} else {
				// Determine lifetime for blocks at this level
				lifetime = lifetime.freshWithin();
				// Recursively construct subdomain generator
				Domain.Big<Stmt> subdomain = toBigDomain(depth - 1, width, lifetime, expressions, declared, undeclared);
				// Using this construct the block generator
				Domain.Big<Block> blocks = Stmt.Block.toBigDomain(lifetime, 1, width, subdomain);
				// Done
				return Domains.Union(lets, assigns, indirects, blocks);
			}
		}
	}

	/**
	 * Represents the set of all expressions which can, for example, appear on the
	 * right-hand side of an expression.
	 *
	 * @author djp
	 *
	 */
	public interface Expr extends Stmt {

		public class Variable extends AbstractStmt implements Expr {
			private final String name;

			public Variable(String name, Attribute... attributes) {
				super(attributes);
				this.name = name;
			}

			public String name() {
				return name;
			}

			@Override
			public String toString() {
				return name;
			}

			@Override
			public String toRustString() {
				return name;
			}

			public static Expr.Variable construct(String name) {
				return new Expr.Variable(name);
			}

			public static Domain.Big<Variable> toBigDomain(Domain.Small<String> subdomain) {
				return Domains.Adaptor(subdomain,Variable::construct);
			}
		}

		public class Dereference extends AbstractStmt implements Expr {
			private final Expr.Variable operand;

			public Dereference(Expr.Variable operand, Attribute... attributes) {
				super(attributes);
				this.operand = operand;
			}

			public Expr.Variable operand() {
				return operand;
			}

			@Override
			public String toString() {
				return "*" + operand.toString();
			}

			@Override
			public String toRustString() {
				return "*" + operand.toRustString();
			}
			public static Expr.Dereference construct(String name) {
				return new Expr.Dereference(new Expr.Variable(name));
			}

			public static Domain.Big<Dereference> toBigDomain(Domain.Small<String> subdomain) {
				return Domains.Adaptor(subdomain, Dereference::construct);
			}
		}

		public class Borrow extends AbstractStmt implements Expr {
			private final Expr.Variable operand;
			private final boolean mutable;

			public Borrow(Expr.Variable operand, boolean mutable, Attribute... attributes) {
				super(attributes);
				this.operand = operand;
				this.mutable = mutable;
			}

			public Expr.Variable operand() {
				return operand;
			}

			public boolean isMutable() {
				return mutable;
			}

			@Override
			public String toString() {
				if (mutable) {
					return "&mut " + operand.name;
				} else {
					return "&" + operand.name;
				}
			}

			@Override
			public String toRustString() {
				return toString();
			}

			public static Expr.Borrow construct(String name, Boolean mutable) {
				return new Expr.Borrow(new Expr.Variable(name), mutable);
			}

			public static Domain.Big<Borrow> toBigDomain(Domain.Small<String> subdomain) {
				return Domains.Product(subdomain, Domains.BOOL, Borrow::construct);
			}
		}

		public class Box extends AbstractStmt implements Expr {
			private final Expr operand;

			public Box(Expr operand, Attribute... attributes) {
				super(attributes);
				this.operand = operand;
			}

			public Expr operand() {
				return operand;
			}

			@Override
			public String toString() {
				return "box " + operand;
			}

			@Override
			public String toRustString() {
				return "Box::new(" + operand.toRustString() + ")";
			}

			public static Expr.Box construct(Expr operand) {
				return new Expr.Box(operand);
			}

			public static Domain.Big<Box> toBigDomain(Domain.Big<Expr> subdomain) {
				return Domains.Adaptor(subdomain, Box::construct);
			}
		}

		public static Domain.Big<Expr> toBigDomain(int depth, Domain.Small<Integer> ints, Domain.Small<String> names) {
			Domain.Big<Value.Integer> integers = Value.Integer.toBigDomain(ints);
			Domain.Big<Expr.Variable> moves = Expr.Variable.toBigDomain(names);
			Domain.Big<Expr.Copy> copys = Expr.Copy.toBigDomain(moves);
			Domain.Big<Expr.Borrow> borrows = Expr.Borrow.toBigDomain(names);
			Domain.Big<Expr.Dereference> derefs = Expr.Dereference.toBigDomain(names);
			if (depth == 0) {
				return Domains.Union(integers, moves, copys, borrows, derefs);
			} else {
				Domain.Big<Expr> subdomain = toBigDomain(depth - 1, ints, names);
				Domain.Big<Expr.Box> boxes = Expr.Box.toBigDomain(subdomain);
				return Domains.Union(integers, moves, copys, borrows, derefs, boxes);
			}
		}

		public class Copy extends AbstractStmt implements Expr {
			private final Expr.Variable operand;

			public Copy(Expr.Variable operand, Attribute... attributes) {
				super(attributes);
				this.operand = operand;
			}

			public Expr.Variable operand() {
				return operand;
			}

			@Override
			public String toString() {
				return "!" + operand.toString();
			}

			@Override
			public String toRustString() {
				return operand.toRustString();
			}

			public static Domain.Big<Copy> toBigDomain(Domain.Big<Expr.Variable> subdomain) {
				return Domains.Adaptor(subdomain, (e) -> new Copy(e));
			}
		}
	}

	public interface Value extends Expr {

		public class Integer extends AbstractStmt implements Value {
			private final int value;

			public Integer(int value, Attribute... attributes) {
				super(attributes);
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

			@Override
			public String toRustString() {
				return toString();
			}

			public static Integer construct(java.lang.Integer i) {
				return new Integer(i);
			}

			public static Domain.Big<Integer> toBigDomain(Domain.Big<java.lang.Integer> subdomain) {
				return Domains.Adaptor(subdomain, Integer::construct);
			}
		}

		public class Location extends AbstractStmt implements Value {
			private final int address;

			public Location(int value, Attribute... attributes) {
				super(attributes);
				this.address = value;
			}

			public int getAddress() {
				return address;
			}

			@Override
			public int hashCode() {
				return address;
			}

			@Override
			public boolean equals(Object o) {
				if (o instanceof Location) {
					Location l = ((Location) o);
					return l.address == address;
				}
				return false;
			}

			@Override
			public String toString() {
				return "&" + address;
			}

			@Override
			public String toRustString() {
				// NOTE: we cannot denote a location on the heap in Rust!
				throw new UnsupportedOperationException();
			}
		}
	}

	public interface Type {
		public class Int extends SyntacticElement.Impl implements Type {
			public Int(Attribute... attributes) {
				super(attributes);
			}

			@Override
			public String toString() { return "int"; }
		}

		public class Borrow extends SyntacticElement.Impl implements Type {

			private final boolean mut;
			private final String name;

			public Borrow(boolean mut, String name, Attribute... attributes) {
				super(attributes);
				this.mut = mut;
				this.name = name;
			}

			public boolean isMutable() {
				return mut;
			}

			public String name() {
				return name;
			}

			@Override
			public String toString() {
				if (mut) {
					return "&mut " + name;
				} else {
					return "&" + name;
				}
			}
		}

		public class Box extends SyntacticElement.Impl implements Type {
			protected final Type element;

			public Box(Type element, Attribute... attributes) {
				super(attributes);
				this.element = element;
			}

			public Type element() {
				return element;
			}
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
