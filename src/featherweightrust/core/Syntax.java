package featherweightrust.core;

public class Syntax {

	public interface Stmt {

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
		public class Declaration implements Stmt {
			private final String name;
			private final Expr initialiser;

			public Declaration(String name, Expr initialiser) {
				this.name = name;
				this.initialiser = initialiser;
			}
		}

		/**
		 * Represents an assignment such as the following:
		 *
		 * <pre>
		 * x = e
		 * *x = e
		 * </pre>
		 *
		 * @author djp
		 *
		 */
		public class Assignment implements Stmt {
			public final LVal lhs;
			public final Expr rhs;

			public Assignment(LVal lhs, Expr rhs) {
				this.lhs = lhs;
				this.rhs = rhs;

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
		public class Block implements Stmt {
			private final String lifetime;
			private final Stmt[] stmts;

			public Block(String lifetime, Stmt... stmts) {
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
		}
	}

	/**
	 * Respresents the subset of expressions which can be used on the left-hand side
	 * of an assignment.
	 *
	 * @author djp
	 *
	 */
	public interface LVal {

	}

	public interface Expr {

		public class Variable implements Expr, LVal {
			private final String name;

			public Variable(String name) {
				this.name = name;
			}

			public String name() {
				return name;
			}
		}

		public class Dereference implements Expr, LVal {
			private final Expr operand;

			public Dereference(Expr operand) {
				this.operand = operand;
			}

			public Expr operand() {
				return operand;
			}
		}

		public class Box implements Expr {
			private final Expr operand;

			public Box(Expr operand) {
				this.operand = operand;
			}

			public Expr operand() {
				return operand;
			}
		}
	}

	public interface Value extends Expr {
		public class Integer implements Value {
			private final int value;

			public Integer(int value) {
				this.value = value;
			}

			@Override
			public int hashCode() {
				return value;
			}

			@Override
			public boolean equals(Object o) {
				return o instanceof Integer && ((Integer) o).value == value;
			}
		}

		public class Location implements Value {
			private final int address;

			public Location(int value) {
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
		}
	}

	public interface Type {
		public class Int implements Type {

		}
		public class Borrow implements Type {
			private final Type element;
			private final boolean mut;

			public Borrow(Type element, boolean mut) {
				this.element = element;
				this.mut = mut;
			}

			public Type getElement() {
				return element;
			}

			public boolean isMutable() {
				return mut;
			}
		}

		public class Box implements Type {
			private final Type element;

			public Box(Type element) {
				this.element = element;
			}

			public Type getElement() {
				return element;
			}
		}
	}
}
