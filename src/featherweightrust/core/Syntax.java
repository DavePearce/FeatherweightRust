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
import java.util.function.Supplier;

import featherweightrust.core.Syntax.Expr.Variable;
import featherweightrust.util.SyntacticElement;
import jmodelgen.core.BigDomain;
import jmodelgen.core.Domain;
import jmodelgen.core.Mutable;
import jmodelgen.core.Walker;
import jmodelgen.util.AbstractBigDomain;
import jmodelgen.util.AbstractDomain;
import jmodelgen.util.AbstractWalker;
import jmodelgen.util.BigDomains;
import jmodelgen.util.Domains;
import jmodelgen.util.Walkers;

public class Syntax {

	public interface Stmt extends SyntacticElement {

		public static class AbstractStmt extends SyntacticElement.Impl implements Stmt {
			public AbstractStmt(Attribute... attributes) {
				super(attributes);
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

			public static Let construct(String variable, Expr initialiser) {
				return new Let(new Expr.Variable(variable), initialiser);
			}

			public static BigDomain<Let> toBigDomain(BigDomain<String> first, BigDomain<Expr> second) {
				return BigDomains.Product(first, second, Let::construct);
			}

			public static Walker<Let> toWalker(Domain<String> first, Walker<Expr> second) {
				return Walkers.Product(first, second, Let::construct);
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

			public static Assignment construct(String variable, Expr initialiser) {
				return new Assignment(new Expr.Variable(variable), initialiser);
			}

			public static BigDomain<Assignment> toBigDomain(BigDomain<String> first, BigDomain<Expr> second) {
				return BigDomains.Product(first, second, Assignment::construct);
			}

			public static Walker<Assignment> toWalker(Domain<String> first, Walker<Expr> second) {
				return Walkers.Product(first, second, Assignment::construct);
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

			public static IndirectAssignment construct(String variable, Expr initialiser) {
				return new IndirectAssignment(new Expr.Variable(variable), initialiser);
			}

			public static BigDomain<IndirectAssignment> toBigDomain(BigDomain<String> first, BigDomain<Expr> second) {
				return BigDomains.Product(first, second, IndirectAssignment::construct);
			}

			public static Walker<IndirectAssignment> toWalker(Domain<String> first, Walker<Expr> second) {
				return Walkers.Product(first, second, IndirectAssignment::construct);
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

			public static Block construct(Lifetime lifetime, List<Stmt> items) {
				return new Block(lifetime, items.toArray(new Stmt[items.size()]));
			}

			public static BigDomain<Block> toBigDomain(Lifetime lifetime, int min, int max, BigDomain<Stmt> stmts) {
				return BigDomains.Product(min, max, stmts, (items) -> construct(lifetime, items));
			}

			public static Walker<Block> toWalker(Lifetime lifetime, int min, Walker<Stmt>... stmts) {
				return Walkers.Product(min, (items) -> construct(lifetime, items), stmts);
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
		public static BigDomain<Stmt> toBigDomain(int depth, int width, Lifetime lifetime, BigDomain<Expr> expressions,
				BigDomain<String> declared, BigDomain<String> undeclared) {
			// Let statements can only be constructed from undeclared variables
			BigDomain<Let> lets = Stmt.Let.toBigDomain(undeclared, expressions);
			// Assignments can only use declared variables
			BigDomain<Assignment> assigns = Stmt.Assignment.toBigDomain(declared, expressions);
			// Indirect assignments can only use declared variables
			BigDomain<IndirectAssignment> indirects = Stmt.IndirectAssignment.toBigDomain(declared, expressions);
			if (depth == 0) {
				return BigDomains.Union(lets, assigns, indirects);
			} else {
				// Determine lifetime for blocks at this level
				lifetime = lifetime.freshWithin();
				// Recursively construct subdomain generator
				BigDomain<Stmt> subdomain = toBigDomain(depth - 1, width, lifetime, expressions, declared, undeclared);
				// Using this construct the block generator
				BigDomain<Block> blocks = Stmt.Block.toBigDomain(lifetime, 1, width, subdomain);
				// Done
				return BigDomains.Union(lets, assigns, indirects, blocks);
			}
		}

		@SuppressWarnings("unchecked")
		public static Walker<Stmt> toWalker(int depth, int width, Lifetime lifetime, Supplier<Walker<Expr>> expressions,
				Domain<String> variables) {
			// Let statements can only be constructed from undeclared variables
			Walker<Let> lets = Stmt.Let.toWalker(variables, expressions.get());
			// Assignments can only use declared variables
			Walker<Assignment> assigns = Stmt.Assignment.toWalker(variables, expressions.get());
			// Indirect assignments can only use declared variables
			Walker<IndirectAssignment> indirects = Stmt.IndirectAssignment.toWalker(variables, expressions.get());
			if (depth == 0) {
				return Walkers.Union(lets, assigns, indirects);
			} else {
				// Determine lifetime for blocks at this level
				lifetime = lifetime.freshWithin();
				// Recursively construct subdomain generator
				Walker<Stmt>[] walkers = new Walker[width];
				for (int i = 0; i != width; ++i) {
					walkers[i] = toWalker(depth - 1, width, lifetime, expressions, variables);
				}
				// Using this construct the block generator
				Walker<Block> blocks = Stmt.Block.toWalker(lifetime, 1, walkers);
				// Done
				return Walkers.Union(lets, assigns, indirects, blocks);
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

			public static Expr.Variable construct(String name) {
				return new Expr.Variable(name);
			}

			public static BigDomain<Variable> toBigDomain(BigDomain<String> subdomain) {
				return new AbstractBigDomain.Unary<Variable, String>(subdomain) {
					@Override
					public Variable get(String name) {
						return new Variable(name);
					}
				};
			}

			public static Walker<Variable> toWalker(BigDomain<String> subdomain) {
				return Walkers.Adaptor(subdomain,Variable::construct);
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

			public static Expr.Dereference construct(String name) {
				return new Expr.Dereference(new Expr.Variable(name));
			}

			public static BigDomain<Dereference> toBigDomain(BigDomain<Expr.Variable> subdomain) {
				return new AbstractBigDomain.Unary<Dereference, Expr.Variable>(subdomain) {
					@Override
					public Dereference get(Expr.Variable e) {
						return new Dereference(e);
					}
				};
			}

			public static Walker<Dereference> toWalker(BigDomain<String> subdomain) {
				return Walkers.Adaptor(subdomain,Dereference::construct);
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

			public static Expr.Borrow construct(String name, Boolean mutable) {
				return new Expr.Borrow(new Expr.Variable(name), mutable);
			}

			public static BigDomain<Borrow> toBigDomain(BigDomain<Variable> first, BigDomain<Boolean> second) {
				return new AbstractBigDomain.Binary<Borrow, Variable, Boolean>(first, second) {
					@Override
					public Borrow get(Variable operand, Boolean mutable) {
						return new Borrow(operand, mutable);
					}
				};
			}

			public static Walker<Borrow> toWalker(BigDomain<String> subdomain) {
				return Walkers.Product(subdomain, BigDomains.Bool(), Borrow::construct);
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

			public static Expr.Box construct(Expr operand) {
				return new Expr.Box(operand);
			}

			public static BigDomain<Box> toBigDomain(BigDomain<Expr> subdomain) {
				return new AbstractBigDomain.Unary<Box, Expr>(subdomain) {
					@Override
					public Box get(Expr e) {
						return new Box(e);
					}
				};
			}

			public static Walker<Box> toWalker(Walker<Expr> subdomain) {
				return Walkers.Adaptor(subdomain,Box::construct);
			}
		}

		public static BigDomain<Expr> toBigDomain(int depth, BigDomain<Integer> ints, BigDomain<String> names) {
			BigDomain<Value.Integer> integers = Value.Integer.toBigDomain(ints);
			BigDomain<Expr.Variable> moves = Expr.Variable.toBigDomain(names);
			BigDomain<Expr.Copy> copys = Expr.Copy.toBigDomain(moves);
			BigDomain<Expr.Borrow> borrows = Expr.Borrow.toBigDomain(moves, BigDomains.Bool());
			BigDomain<Expr.Dereference> derefs = Expr.Dereference.toBigDomain(moves);
			if (depth == 0) {
				return BigDomains.Union(integers, moves, copys, borrows, derefs);
			} else {
				BigDomain<Expr> subdomain = toBigDomain(depth - 1, ints, names);
				BigDomain<Expr.Box> boxes = Expr.Box.toBigDomain(subdomain);
				return BigDomains.Union(integers, moves, copys, borrows, derefs, boxes);
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

			public static BigDomain<Copy> toBigDomain(BigDomain<Expr.Variable> subdomain) {
				return new AbstractBigDomain.Unary<Copy, Expr.Variable>(subdomain) {
					@Override
					public Copy get(Expr.Variable e) {
						return new Copy(e);
					}
				};
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

			public static BigDomain<Integer> toBigDomain(BigDomain<java.lang.Integer> subdomain) {
				return new AbstractBigDomain.Unary<Integer, java.lang.Integer>(subdomain) {

					@Override
					public Integer get(java.lang.Integer i) {
						return new Integer(i);
					}

				};
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
		}
	}

	public interface Type {
		public class Int extends SyntacticElement.Impl implements Type {
			public Int(Attribute... attributes) {
				super(attributes);
			}
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
		private Lifetime[] children;
		private int size;

		public Lifetime() {
			this.parent = null;
			this.children = new Lifetime[32];
			this.size = 0;
		}

		public Lifetime(Lifetime parent) {
			this.parent = parent;
			this.children = new Lifetime[32];
			this.size = 0;
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
			Lifetime l = new Lifetime(this);
			// Configure it as a child
			if(size == children.length) {
				// Need to create more space!
				children = Arrays.copyOf(children, children.length * 2);
			}
			children[size++] = l;
			//
			return l;
		}

		@Override
		public String toString() {
			return "l" + System.identityHashCode(this);
		}
	}
}
