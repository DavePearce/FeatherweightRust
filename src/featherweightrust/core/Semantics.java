package featherweightrust.core;

import java.util.Arrays;
import java.util.Map;

import featherweightrust.core.Syntax.Value;
import featherweightrust.core.Syntax.Value.Location;
import featherweightrust.util.AbstractFunction;
import featherweightrust.util.Pair;

public class Semantics extends
		AbstractFunction<Semantics.Store, Pair<Semantics.Store, Syntax.Stmt>, Pair<Semantics.Store, Syntax.Expr>> {

	/**
	 * Represents a stack of bindings from variable names to abstract locations.
	 *
	 * @author djp
	 *
	 */
	public static class CallStack {
		private Map<String, Location> locations;

		public void put(String name, Location location) {
			if (locations.containsKey(name)) {
				throw new IllegalArgumentException("variable already declared");
			}
			locations.put(name, location);
		}

		public Location get(String name) {
			return locations.get(name);
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
		 * Get the cell at a given location.
		 *
		 * @param location
		 * @return
		 */
		public Cell get(Location location) {
			return cells[location.getAddress()];
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
				if (ncells[i].getLifetime().equals(lifetime)) {
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

		public String getLifetime() {
			return lifetime;
		}

		public Value getContents() {
			return value;
		}
	}
}
