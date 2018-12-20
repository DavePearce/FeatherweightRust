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

import featherweightrust.util.SyntacticElement;

public class Syntax {

	public interface Stmt extends SyntacticElement {

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
		public class Let extends SyntacticElement.Impl implements Stmt  {
			private final String name;
			private final Expr initialiser;

			public Let(String name, Expr initialiser, Attribute... attributes) {
				super(attributes);
				this.name = name;
				this.initialiser = initialiser;
			}

			/**
			 * Return the name of the variable being declared.
			 *
			 * @return
			 */
			public String name() {
				return name;
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
				return "let mut " + name + " = " + initialiser + ";";
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
		public class Assignment extends SyntacticElement.Impl implements Stmt {
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
		public class IndirectAssignment extends SyntacticElement.Impl implements Stmt {
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
		public class Block extends SyntacticElement.Impl implements Stmt {
			private final String lifetime;
			private final Stmt[] stmts;

			public Block(String lifetime, Stmt[] stmts, Attribute... attributes) {
				super(attributes);
				this.lifetime = lifetime;
				this.stmts = stmts;
			}

			public int size() {
				return stmts.length;
			}

			public String lifetime() {
				return lifetime;
			}

			public Stmt get(int index) {
				return stmts[index];
			}

			public Stmt[] toArray() {
				return stmts;
			}

			@Override
			public String toString() {
				String contents = "";
				for(int i=0;i!=stmts.length;++i) {
					contents += stmts[i] + " ";
				}
				return "{ " + contents + "}";
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

		public class Variable extends SyntacticElement.Impl implements Expr {
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
		}

		public class Dereference extends SyntacticElement.Impl implements Expr {
			private final Expr operand;

			public Dereference(Expr operand, Attribute... attributes) {
				super(attributes);
				this.operand = operand;
			}

			public Expr operand() {
				return operand;
			}

			@Override
			public String toString() {
				return "*" + operand.toString();
			}
		}


		public class Borrow extends SyntacticElement.Impl implements Expr {
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
				if(mutable) {
					return "&mut " + operand.name;
				} else {
					return "&" + operand.name;
				}
			}
		}


		public class Box extends SyntacticElement.Impl implements Expr {
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
		}
	}

	public interface Value extends Expr {
		public class Integer extends SyntacticElement.Impl implements Value {
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
		}

		public class Location extends SyntacticElement.Impl implements Value {
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
				return o instanceof Location && ((Location) o).address == address;
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
				if(mut) {
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
}
