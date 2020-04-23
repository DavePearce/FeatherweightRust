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
package featherweightrust.util;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import featherweightrust.core.Syntax.Expr;
import featherweightrust.core.Syntax.Lifetime;
import featherweightrust.core.Syntax.Stmt;
import featherweightrust.core.Syntax.Value;
import featherweightrust.core.Syntax.Value.Location;

public abstract class AbstractMachine {

	public static final State EMPTY_STATE = new AbstractMachine.State();

	/**
	 * Represents the state before and after each transition by the operation
	 * semantics.
	 *
	 * @author djp
	 *
	 */
	public static class State {
		private final StackFrame stack;
		private final Store heap;

		public State() {
			this.stack = new StackFrame();
			this.heap = new Store();
		}

		public State(StackFrame stack, Store store) {
			this.stack = stack;
			this.heap = store;
		}

		/**
		 * Return the current store
		 *
		 * @return
		 */
		public Store store() {
			return heap;
		}

		/**
		 * Return the topmost stack frame.
		 *
		 * @return
		 */
		public StackFrame frame() {
			return stack;
		}

		/**
		 * Determine the location associated with a given variable name
		 *
		 * @param name
		 * @return
		 */
		public Location locate(String name) {
			return stack.get(name);
		}

		/**
		 * Allocate a new cell in memory with a given lifetime and initial value.
		 *
		 * @param lifetime
		 *            Lifetime of cell to be allocated
		 * @param v
		 *            Initial value to be used for allocated cell
		 * @return
		 */
		public Pair<State, Location> allocate(Lifetime lifetime, Value v) {
			// Allocate cell in store
			Pair<Store, Location> p = heap.allocate(lifetime, v);
			// Return updated state
			return new Pair<>(new State(stack, p.first()), p.second());
		}

		/**
		 * Read the contents of a given location
		 *
		 * @param location
		 *            Location to read
		 * @return
		 */
		public Value read(Location location) {
			return heap.read(location);
		}

		/**
		 * Update the value of a given location, thus creating an updated state.
		 *
		 * @param location
		 *            Location to update
		 * @param value
		 *            Value to be written
		 * @return
		 */
		public State write(Location location, Value value) {
			Store nstore = heap.write(location, value);
			return new State(stack, nstore);
		}


		/**
		 * Remove a given location from the store, thus rendering it inaccessible.
		 *
		 * @param location
		 *            Location to update
		 * @return
		 */
		public State remove(Location location) {
			Store nstore = heap.remove(location);
			return new State(stack, nstore);
		}

		/**
		 * Bind a name to a given location, thus creating an updated state.
		 *
		 * @param name
		 *            Name to bind
		 * @param location
		 *            Location to be bound
		 * @return
		 */
		public State bind(String name, Location location) {
			StackFrame nstack = stack.bind(name, location);
			return new State(nstack, heap);
		}

		/**
		 * Drop an overwritten value, this creating an updated state. In the process,
		 * any owned locations will be dropped.
		 *
		 * @param location
		 *            Location to overwrite
		 * @param value
		 *            Value to be written
		 * @return
		 */
		public State drop(Value value) {
			return new State(stack, heap.drop(value));
		}

		/**
		 * Drop all locations created within a given lifetime.
		 *
		 * @param lifetime
		 * @return
		 */
		public State drop(Set<Location> locations) {
			return new State(stack, heap.drop(locations));
		}

		/**
		 * Find all locations allocated in a given lifetime
		 *
		 * @param lifetime
		 * @return
		 */
		public Set<Location> findAll(Lifetime lifetime) {
			return heap.findAll(lifetime);
		}

		public void push(Map<String, Location> frame) {

		}

		@Override
		public String toString() {
			return heap.toString() + ":" + stack.toString();
		}
	}

	/**
	 * Represents a stack of bindings from variable names to abstract locations.
	 *
	 * @author djp
	 *
	 */
	public static class StackFrame {
		private final HashMap<String, Location> locations;

		/**
		 * Construct an initial (empty) call stack
		 */
		public StackFrame() {
			this.locations = new HashMap<>();
		}

		/**
		 * A copy constructor for call stacks.
		 *
		 * @param locations
		 */
		private StackFrame(HashMap<String, Location> locations) {
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
		public StackFrame bind(String name, Location location) {
			// Clone the locations map in order to update it
			HashMap<String, Location> nlocations = new HashMap<>(locations);
			// Update new mapping
			nlocations.put(name, location);
			//
			return new StackFrame(nlocations);
		}

		@Override
		public String toString() {
			return locations.toString();
		}
	}

	/**
	 * Represent the store which consists of an array of "cells".
	 *
	 * @author djp
	 *
	 */
	public static class Store {
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
		public Pair<Store, Location> allocate(Lifetime lifetime, Value v) {
			// Create space for new cell at end of array
			Cell[] ncells = Arrays.copyOf(cells, cells.length + 1);
			// Create new cell using given contents
			ncells[cells.length] = new Cell(lifetime, v);
			// Return updated store and location
			return new Pair<>(new Store(ncells), new Location(cells.length));
		}

		/**
		 * Read the cell at a given location. If the cell does not exist, then an
		 * exception is raised signaling a dangling pointer.
		 *
		 * @param location
		 * @return
		 */
		public Value read(Location location) {
			Cell cell = cells[location.getAddress()];
			if (cell == null) {
				throw new IllegalArgumentException("invalid cell");
			}
			return cell.contents();
		}

		/**
		 * Write the cell at a given location. If the cell does not exist, then an
		 * exception is raised signaling a dangling pointer.
		 *
		 * @param location
		 * @return
		 */
		public Store write(Location location, Value value) {
			Cell cell = cells[location.getAddress()];
			if (cell == null) {
				throw new IllegalArgumentException("invalid cell");
			}
			// Copy cells ahead of write
			Cell[] ncells = Arrays.copyOf(cells, cells.length);
			// Perform actual write
			ncells[location.getAddress()] = new Cell(cell.lifetime, value);
			// Done
			return new Store(ncells);
		}

		public Store remove(Location location) {
			Cell cell = cells[location.getAddress()];
			if (cell == null) {
				throw new IllegalArgumentException("invalid cell");
			}
			// Copy cells ahead of write
			Cell[] ncells = Arrays.copyOf(cells, cells.length);
			// Perform actual write
			ncells[location.getAddress()] = null;
			// Done
			return new Store(ncells);
		}

		/**
		 * Drop all cells at the given locations. This recursively drops all reachable
		 * and uniquely owned locations.
		 *
		 * @param location
		 * @return
		 */
		public Store drop(Set<Location> locations) {
			Cell[] ncells = Arrays.copyOf(cells, cells.length);
			for(Location location : locations) {
				internalDrop(ncells,location);
			}
			// Check reference invariant);
			checkReferenceInvariant(ncells);
			//
			return new Store(ncells);
		}

		/**
		 * Drop locations based on a value being overwritten. Thus, if the value is a
		 * reference to an owned location then that will be recursively dropped.
		 *
		 * @param l
		 *            location being overwritten
		 * @param w
		 *            value being written to location
		 * @return
		 */
		public Store drop(Value v) {
			// Check whether we need to drop anything.
			if (v instanceof Location) {
				// Value overwritten pointed to something. Therefore, it that something was a
				// heap location, we'll need to drop it.
				Location location = (Location) v;
				// Check whether reference location is heap (i.e. owned)
				if (cells[location.getAddress()].hasGlobalLifetime()) {
					// Prepare for the drop by copying all cells
					Cell[] ncells = Arrays.copyOf(cells, cells.length);
					// Perform the physical drop
					internalDrop(ncells, location);
					// Check reference invariant
					checkReferenceInvariant(ncells);
					// Done
					return new Store(ncells);
				}
			}
			return this;
		}

		/**
		 * Identify all cells with a given lifetime.
		 *
		 * @param lifetime
		 * @return
		 */
		public Set<Location> findAll(Lifetime lifetime) {
			HashSet<Location> matches = new HashSet<>();
			// Action the drop
			for (int i = 0; i != cells.length; ++i) {
				Cell ncell = cells[i];
				if (ncell != null && ncell.lifetime() == lifetime) {
					matches.add(new Location(i));
				}
			}
			// Convert results to array
			return matches;
		}

		@Override
		public String toString() {
			return Arrays.toString(cells);
		}

		public Cell[] toArray() {
			return cells;
		}

		/**
		 * Drop a location without worrying about the reference invariant. This is
		 * important so that we can drop a whole bunch of locations in one go.
		 *
		 * @param l
		 * @return
		 */
		private static void internalDrop(Cell[] ncells, Location location) {
			// Locate cell being dropped
			Cell ncell = ncells[location.getAddress()];
			// Recursively drop owned locations
			finalise(ncells, ncell);
			// Physically drop the location
			ncells[location.getAddress()] = null;
		}

		/**
		 * Check the reference invariant for a given array of cells. Specifically, for
		 * every cell which points to another cell (i.e. holds its address), that other
		 * cell must exist (i.e. is not null).
		 *
		 * @param ncells
		 */
		private static void checkReferenceInvariant(Cell[] ncells) {
			for (int i = 0; i != ncells.length; ++i) {
				Cell ncell = ncells[i];
				if(ncell != null && ncell.value instanceof Value.Location) {
					Value.Location loc = (Location) ncell.value;
					if(ncells[loc.getAddress()] == null) {
						throw new IllegalArgumentException("dangling reference created: &" + i);
					}
				}
			}
		}

		/**
		 * When a given cell is collected, we need to finalise it by collected any owned
		 * locations.
		 *
		 * @param cells Cells to update
		 * @param cell Cell being finalised
		 * @return
		 */
		private static void finalise(Cell[] cells, Cell cell) {
			Value v = cell.value;
			if(v instanceof Value.Location) {
				Value.Location loc = (Value.Location) v;
				Cell lcell = cells[loc.getAddress()];
				if (lcell != null && lcell.hasGlobalLifetime()) {
					cells[loc.getAddress()] = null;
					finalise(cells, lcell);
				}
			}
		}
	}

	/**
	 * A single heap location which has a given lifetime.
	 *
	 * @author djp
	 *
	 */
	public static class Cell {
		private final Lifetime lifetime;
		private final Value value;

		public Cell(Lifetime lifetime, Value value) {
			this.lifetime = lifetime;
			this.value = value;
		}

		/**
		 * Get the lifetime associated with this cell
		 *
		 * @return
		 */
		public Lifetime lifetime() {
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

		/**
		 * Check whether this cell was allocated in the global space or not. This
		 * indicates whether or not the cell was allocated on the heap.
		 *
		 * @return
		 */
		public boolean hasGlobalLifetime() {
			Lifetime globalLifetime = lifetime.getRoot();
			return lifetime == globalLifetime;
		}

		@Override
		public String toString() {
			return "<" + value + ";" + lifetime + ">";
		}
	}

}
