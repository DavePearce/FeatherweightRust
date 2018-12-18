package featherweightrust.core;

import java.util.Arrays;
import java.util.HashMap;

import featherweightrust.core.Syntax.Expr;
import featherweightrust.core.Syntax.Expr.Dereference;
import featherweightrust.core.Syntax.Expr.Box;
import featherweightrust.core.Syntax.Expr.Variable;
import featherweightrust.core.Syntax.Stmt;
import featherweightrust.core.Syntax.Stmt.Assignment;
import featherweightrust.core.Syntax.Stmt.Block;
import featherweightrust.core.Syntax.Stmt.Declaration;
import featherweightrust.core.Syntax.Value;
import featherweightrust.core.Syntax.Value.Location;
import featherweightrust.util.AbstractTransformer;
import featherweightrust.util.Pair;

/**
 * Encodes the operational semantics of Rust using a recursive decomposition of
 * evaluation functions.
 *
 * @author djp
 *
 */
public class BigStepSemantics extends
		AbstractTransformer<BigStepSemantics.State> {


	/**
	 * Rule R-Declare.
	 */
	@Override
	public Pair<State, Stmt> apply(State state, Declaration stmt) {
		// TODO Auto-generated method stub
		return null;
	}

	/**
	 * Rule R-Assign.
	 */
	@Override
	public Pair<State, Stmt> apply(State state, Assignment stmt) {
		// TODO Auto-generated method stub
		return null;
	}

	/**
	 * Rule R-Block.
	 */
	@Override
	public Pair<State, Stmt> apply(State state, Block stmt) {
		for (int i = 0; i != stmt.size(); ++i) {
			Pair<State, Stmt> p = apply(state, stmt.get(i));
			state = p.first();
		}
		return new Pair<>(state, new Block(stmt.lifetime()));
	}

	/**
	 * Rule R-Deref.
	 */
	@Override
	public Pair<State, Expr> apply(State state, Dereference expr) {
		// Evaluate operand
		Pair<State, Expr> p = apply(state, expr.operand());
		// Extract location, or throw exception otherwise
		Location loc = (Location) p.second();
		// Read contents of cell at given location
		return new Pair<>(p.first(), state.store.get(loc).contents());
	}

	/**
	 * Rule R-Box.
	 */
	@Override
	public Pair<State, Expr> apply(State state, Box expr) {
		// Evaluate operand
		Pair<State, Expr> pe = apply(state, expr.operand());
		// Allocate new location
		Pair<State, Location> pl = pe.first().allocate("*", (Value) pe.second());
		// Done
		return new Pair<>(pl.first(), pl.second());
	}

	/**
	 * Rule R-Var.
	 */
	@Override
	public Pair<State, Expr> apply(State state, Variable expr) {
		// Determine location bound by variable
		Location loc = state.stack.get(expr.name());
		// Read location from store
		return new Pair<>(state, state.store.get(loc).contents());
	}

	/**
	 * Represents the state before and after each transition by the operation
	 * semantics.
	 *
	 * @author djp
	 *
	 */
	public static class State {
		private final CallStack stack;
		private final Store store;

		public State(CallStack stack, Store store) {
			this.stack = stack;
			this.store = store;
		}

		public CallStack getStack() {
			return stack;
		}

		public Store getStore() {
			return store;
		}

		public Pair<State,Location> allocate(String lifetime, Value v) {
			// Allocate cell in store
			Pair<Store,Location> p = store.allocate(lifetime, v);
			// Return updated state
			return new Pair<>(new State(stack, p.first()), p.second());
		}
	}

	/**
	 * Represents a stack of bindings from variable names to abstract locations.
	 *
	 * @author djp
	 *
	 */
	public static class CallStack {
		private final HashMap<String, Location> locations;

		/**
		 * Construct an initial (empty) call stack
		 */
		public CallStack() {
			this.locations = new HashMap<>();
		}

		/**
		 * A copy constructor for call stacks.
		 *
		 * @param locations
		 */
		private CallStack(HashMap<String,Location> locations) {
			this.locations = locations;
		}

		/**
		 * Get the location bound to a given variable name
		 *
		 * @param name
		 *            Name of location to return
		 * @return
		 */
		public Location get(String name) {
			return locations.get(name);
		}

		/**
		 * Bind a given name to a given location producing an updated stack. Observe
		 * that the name may already exist, in which case the original binding is simply
		 * lost.
		 *
		 * @param name
		 *            Variable name to bind
		 * @param location
		 *            Location to be bound
		 * @return
		 */
		public CallStack bind(String name, Location location) {
			// Clone the locations map in order to update it
			HashMap<String,Location> nlocations = new HashMap<>(locations);
			// Update new mapping
			nlocations.put(name, location);
			//
			return new CallStack(nlocations);
		}
	}

	/**
	 * Represent the store which consists of an array of "cells".
	 *
	 * @author djp
	 *
	 */
	public class Store {
		private Cell[] cells;

		public Store() {
			this.cells = new Cell[0];
		}

		private Store(Cell[] cells) {
			this.cells = cells;
		}

		/**
		 * Allocate a new location in the store.
		 *
		 * @return
		 */
		public Pair<Store, Location> allocate(String lifetime, Value v) {
			// Create space for new cell at end of array
			Cell[] ncells = Arrays.copyOf(cells, cells.length + 1);
			// Create new cell using given contents
			ncells[cells.length] = new Cell(lifetime, v);
			// Return updated store and location
			return new Pair<>(new Store(ncells), new Location(cells.length));
		}

		/**
		 * Get the cell at a given location. If the cell does not exist, then an
		 * exception is raised signaling a dangling pointer.
		 *
		 * @param location
		 * @return
		 */
		public Cell get(Location location) {
			Cell cell = cells[location.getAddress()];
			if(cell == null) {
				throw new IllegalArgumentException("invalid cell");
			}
			return cell;
		}

		/**
		 * Drop all cells with a given lifetime.
		 *
		 * @param lifetime
		 * @return
		 */
		public Store drop(String lifetime) {
			// Prepare for drop by copying cells
			Cell[] ncells = Arrays.copyOf(cells, cells.length);
			// Action the drop
			for (int i = 0; i != ncells.length; ++i) {
				if (ncells[i].lifetime().equals(lifetime)) {
					// drop individual cell
					ncells[i] = null;
				}
			}
			//
			return new Store(ncells);
		}
	}

	/**
	 * A single heap location which has a given lifetime.
	 *
	 * @author djp
	 *
	 */
	public static class Cell {
		private final String lifetime;
		private final Value value;

		public Cell(String lifetime, Value value) {
			this.lifetime = lifetime;
			this.value = value;
		}

		/**
		 * Get the lifetime associated with this cell
		 *
		 * @return
		 */
		public String lifetime() {
			return lifetime;
		}

		/**
		 * Get the contents of this cell (i.e. the value stored in this cell).
		 *
		 * @return
		 */
		public Value contents() {
			return value;
		}
	}

}
