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

import featherweightrust.util.SyntacticElement;
import jmodelgen.core.Domain;
import jmodelgen.core.Mutable;
import jmodelgen.util.AbstractDomain;
import jmodelgen.util.Domains;

public class Syntax {

	public interface Stmt extends Mutable<Stmt>, SyntacticElement {

		public static class AbstractStmt extends SyntacticElement.Impl implements Stmt {
			public AbstractStmt(Attribute... attributes) {
				super(attributes);
			}

			@Override
			public int size() {
				return 0;
			}

			@Override
			public Stmt get(int i) {
				throw new IndexOutOfBoundsException();
			}

			@Override
			public Stmt replace(int i, Stmt child) {
				throw new IndexOutOfBoundsException();
			}
		}

		/**
		 * Represents a variable declaration of the form:
		 *
		 * <pre>
		 * let mut x = e
		 * </pre>
		 *
		 * @author djp
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

			public static Domain<Let> toDomain(Domain<Expr.Variable> first, Domain<Expr> second) {
				return new AbstractDomain.Binary<Let, Expr.Variable, Expr>(first, second) {
					@Override
					public Let get(Expr.Variable variable, Expr initialiser) {
						return new Let(variable, initialiser);
					}
				};
			}
		}

		/**
		 * Represents an assignment such as the following:
		 *
		 * <pre>
		 * x = e
		 * </pre>
		 *
		 * @author djp
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

			public static Domain<Assignment> toDomain(Domain<Expr.Variable> first, Domain<Expr> second) {
				return new AbstractDomain.Binary<Assignment, Expr.Variable, Expr>(first, second) {
					@Override
					public Assignment get(Expr.Variable variable, Expr initialiser) {
						return new Assignment(variable, initialiser);
					}
				};
			}
		}

		/**
		 * Represents an indirect assignment such as the following:
		 *
		 * <pre>
		 * *x = e
		 * </pre>
		 *
		 * @author djp
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

			public static Domain<IndirectAssignment> toDomain(Domain<Expr.Variable> first, Domain<Expr> second) {
				return new AbstractDomain.Binary<IndirectAssignment, Expr.Variable, Expr>(first, second) {
					@Override
					public IndirectAssignment get(Expr.Variable variable, Expr initialiser) {
						return new IndirectAssignment(variable, initialiser);
					}
				};
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
		 * @author djp
		 *
		 */
		public class Block extends AbstractStmt implements Extensible<Stmt> {
			private final Lifetime lifetime;
			private final Stmt[] stmts;

			public Block(Lifetime lifetime, Stmt[] stmts, Attribute... attributes) {
				super(attributes);
				this.lifetime = lifetime;
				this.stmts = stmts;
			}

			@Override
			public int size() {
				return stmts.length;
			}

			public Lifetime lifetime() {
				return lifetime;
			}

			@Override
			public Stmt get(int index) {
				return stmts[index];
			}

			/**
			 * Replace a given statement in this block, producing an updated copy of the
			 * block.
			 *
			 * @param i
			 * @param stmt
			 * @return
			 */
			@Override
			public Stmt.Block replace(int i, Stmt stmt) {
				Stmt[] nstmts = Arrays.copyOf(stmts, stmts.length);
				nstmts[i] = stmt;
				return new Stmt.Block(lifetime, nstmts);
			}

			/**
			 * Append a given statement to this block, producing an updated copy of the
			 * block.
			 *
			 * @param stmt
			 * @return
			 */
			@Override
			public Stmt.Block append(Stmt stmt) {
				Stmt[] nstmts = Arrays.copyOf(stmts, stmts.length + 1);
				nstmts[stmts.length] = stmt;
				return new Stmt.Block(lifetime, nstmts);
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

			public static Domain<Block> toDomain(Lifetime lifetime, int maxWidth, Domain<Stmt> first) {
				return new AbstractDomain.Nary<Block, Stmt>(maxWidth, first) {
					@Override
					public Block generate(List<Stmt> items) {
						return new Block(lifetime, items.toArray(new Stmt[items.size()]));
					}
				};
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
		public static Domain<Stmt> toDomain(int depth, int width, Lifetime lifetime, Domain<Expr> expressions,
				Domain<String> declared, Domain<String> undeclared) {
			// Construct corresponding domains for variable expressions.
			Domain<Expr.Variable> undeclaredVariables = Expr.Variable.toDomain(undeclared);
			Domain<Expr.Variable> declaredVariables = Expr.Variable.toDomain(declared);
			// Let statements can only be constructed from undeclared variables
			Domain<Let> lets = Stmt.Let.toDomain(undeclaredVariables, expressions);
			// Assignments can only use declared variables
			Domain<Assignment> assigns = Stmt.Assignment.toDomain(declaredVariables, expressions);
			// Indirect assignments can only use declared variables
			Domain<IndirectAssignment> indirects = Stmt.IndirectAssignment.toDomain(declaredVariables, expressions);
			if (depth == 0) {
				return Domains.Union(lets, assigns, indirects);
			} else {
				// Determine lifetime for blocks at this level
				lifetime = lifetime.freshWithin();
				// Recursively construct subdomain generator
				Domain<Stmt> subdomain = toDomain(depth - 1, width, lifetime, expressions, declared, undeclared);
				// Using this construct the block generator
				Domain<Block> blocks = Stmt.Block.toDomain(lifetime, width, subdomain);
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

			public static Domain<Variable> toDomain(Domain<String> subdomain) {
				return new AbstractDomain.Unary<Variable, String>(subdomain) {
					@Override
					public Variable get(String name) {
						return new Variable(name);
					}
				};
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

			public static Domain<Dereference> toDomain(Domain<Expr.Variable> subdomain) {
				return new AbstractDomain.Unary<Dereference, Expr.Variable>(subdomain) {
					@Override
					public Dereference get(Expr.Variable e) {
						return new Dereference(e);
					}
				};
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

			public static Domain<Borrow> toDomain(Domain<Variable> first, Domain<Boolean> second) {
				return new AbstractDomain.Binary<Borrow, Variable, Boolean>(first, second) {
					@Override
					public Borrow get(Variable operand, Boolean mutable) {
						return new Borrow(operand, mutable);
					}
				};
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

			public static Domain<Box> toDomain(Domain<Expr> subdomain) {
				return new AbstractDomain.Unary<Box, Expr>(subdomain) {
					@Override
					public Box get(Expr e) {
						return new Box(e);
					}
				};
			}
		}

		public static Domain<Expr> toDomain(int depth, Domain<Integer> ints, Domain<String> names) {
			Domain<Value.Integer> integers = Value.Integer.toDomain(ints);
			Domain<Expr.Variable> moves = Expr.Variable.toDomain(names);
			Domain<Expr.Copy> copys = Expr.Copy.toDomain(moves);
			Domain<Expr.Borrow> borrows = Expr.Borrow.toDomain(moves, Domains.Bool());
			Domain<Expr.Dereference> derefs = Expr.Dereference.toDomain(moves);
			if (depth == 0) {
				return Domains.Union(integers, moves, copys, borrows, derefs);
			} else {
				Domain<Expr> subdomain = toDomain(depth - 1, ints, names);
				Domain<Expr.Box> boxes = Expr.Box.toDomain(subdomain);
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

			public static Domain<Copy> toDomain(Domain<Expr.Variable> subdomain) {
				return new AbstractDomain.Unary<Copy, Expr.Variable>(subdomain) {
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

			public static Domain<Integer> toDomain(Domain<java.lang.Integer> subdomain) {
				return new AbstractDomain.Unary<Integer, java.lang.Integer>(subdomain) {

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
