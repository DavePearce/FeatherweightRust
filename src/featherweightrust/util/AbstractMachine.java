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
import java.util.BitSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import featherweightrust.core.Syntax.Lifetime;
import featherweightrust.core.Syntax.Path;
import featherweightrust.core.Syntax.LVal;
import featherweightrust.core.Syntax.Term;
import featherweightrust.core.Syntax.Type;
import featherweightrust.core.Syntax.Value;
import featherweightrust.core.Syntax.Value.Reference;

public abstract class AbstractMachine {

	public static final State EMPTY_STATE = new AbstractMachine.State();

	/**
	 * Represents the state before and after each transition by the operation
	 * semantics.
	 *
	 * @author David J. Pearce
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

		public boolean isHeapEmpty() {
			return heap.isEmpty();
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
		 * Determine the location associated with a given variable name.
		 *
		 * @param name
		 * @return
		 */
		public Reference locate(String name) {
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
		public Pair<State, Reference> allocate(Lifetime lifetime, Value v) {
			// Allocate cell in store
			Pair<Store, Reference> p = heap.allocate(lifetime, v);
			// Return updated state
			return new Pair<>(new State(stack, p.first()), p.second());
		}

		/**
		 * Read the contents of a given lval.
		 *
		 * @param location
		 *            Location to read
		 * @return
		 */
		public Value read(LVal lv) {
			return heap.read(lv.locate(this));
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
		public State write(LVal lv, Value value) {
			Store nstore = heap.write(lv.locate(this), value);
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
		public State bind(String var, Reference location) {
			StackFrame nstack = stack.bind(var, location);
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
		 * @param temporaries items currently live which are not on the stack or in the
		 *                    heap. This is needed only for debugging.
		 * @return
		 */
		public State drop(Lifetime m, Value... temporaries) {
			BitSet locations = findAll(m);
			return new State(stack.pop(m), heap.drop(locations, temporaries));
		}

		/**
		 * Push a new stack frame onto this state.
		 *
		 * @param m
		 * @return
		 */
		public State push(Lifetime m) {
			return new State(stack.push(m), heap);
		}


		/**
		 * Find all locations allocated in a given lifetime
		 *
		 * @param lifetime
		 * @return
		 */
		public BitSet findAll(Lifetime lifetime) {
			return heap.findAll(lifetime);
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
		private final StackFrame parent;
		private final Lifetime lifetime;
		private final HashMap<String, Reference> locations;

		/**
		 * Construct an initial (empty) call stack
		 */
		public StackFrame() {
			this.parent = null;
			this.lifetime = null;
			this.locations = new HashMap<>();
		}

		/**
		 * A copy constructor for call stacks.
		 *
		 * @param locations
		 */
		private StackFrame(StackFrame parent, Lifetime lifetime, HashMap<String, Reference> locations) {
			this.parent = parent;
			this.lifetime = lifetime;
			this.locations = locations;
		}

		/**
		 * Check whether frame is empty or not.
		 *
		 * @return
		 */
		public boolean isEmpty() {
			return locations.isEmpty();
		}

		/**
		 * Get the location bound to a given variable name
		 *
		 * @param name
		 *            Name of location to return
		 * @return
		 */
		public Reference get(String name) {
			return locations.get(name);
		}

		/**
		 * Push an empty frame on this stack
		 */
		public StackFrame push(Lifetime lifetime) {
			return new StackFrame(this, lifetime, new HashMap<>());
		}

		/**
		 * Pop a given scope of this stack.
		 *
		 * @param lifetime
		 * @return
		 */
		public StackFrame pop(Lifetime lifetime) {
			if(this.lifetime == lifetime) {
				return parent;
			} else {
				return this;
			}
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
		public StackFrame bind(String name, Reference location) {
			// Clone the locations map in order to update it
			HashMap<String, Reference> nlocations = new HashMap<>(locations);
			// Update new mapping
			nlocations.put(name, location);
			//
			return new StackFrame(parent, lifetime, nlocations);
		}

		@Override
		public String toString() {
			String r = locations.toString();
			if(parent != null) {
				r += parent.toString();
			}
			return r;
		}
	}

	/**
	 * Represent the store which consists of an array of "cells".
	 *
	 * @author djp
	 *
	 */
	public static class Store {
		private Slot[] cells;

		public Store() {
			this.cells = new Slot[0];
		}

		private Store(Slot[] cells) {
			this.cells = cells;
		}

		/**
		 * Check whether heap is empty or not
		 * @return
		 */
		public boolean isEmpty() {
			for(int i=0;i!=cells.length;++i) {
				if(cells[i] != null) {
					return false;
				}
			}
			return true;
		}

		/**
		 * Allocate a new location in the store.
		 *
		 * @return
		 */
		public Pair<Store, Reference> allocate(Lifetime lifetime, Value v) {
			// Create space for new cell at end of array
			Slot[] ncells = Arrays.copyOf(cells, cells.length + 1);
			// Create new cell using given contents
			ncells[cells.length] = new Slot(lifetime, v);
			// Return updated store and location
			return new Pair<>(new Store(ncells), new Reference(cells.length));
		}

		/**
		 * Read the cell at a given location. If the cell does not exist, then an
		 * exception is raised signaling a dangling pointer.
		 *
		 * @param location
		 * @return
		 */
		public Value read(Reference location) {
			int address = location.getAddress();
			int[] path = location.getPath();
			Slot cell = cells[address];
			// Read value at location
			Value contents = cell.contents();
			// Extract path (if applicable)
			return (path.length == 0) ? contents : contents.read(path, 0);
		}

		/**
		 * Write the cell at a given location. If the cell does not exist, then an
		 * exception is raised signaling a dangling pointer.
		 *
		 * @param location
		 * @return
		 */
		public Store write(Reference location, Value value) {
			int address = location.getAddress();
			int[] path = location.getPath();
			// Read cell from given base address
			Slot cell = cells[address];
			// Read value at location
			Value n = cell.contents();
			// Construct new value
			Value nv = (path.length == 0) ? value : n.write(path, 0, value);
			// Copy cells ahead of write
			Slot[] ncells = Arrays.copyOf(cells, cells.length);
			// Perform actual write
			ncells[address] = new Slot(cell.lifetime, nv);
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
		public Store drop(BitSet locations, Value... temporaries) {
			Slot[] ncells = Arrays.copyOf(cells, cells.length);
			for (int i = locations.nextSetBit(0); i != -1; i = locations.nextSetBit(i + 1)) {
				destroy(ncells, i);
			}
			// Check the heap invariant still holds!
			assert heapInvariant(ncells,temporaries);
			// Done
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
			// NOTE: following is an optimisation to prevent allocating a new array when
			// nothing is actually going to change.
			if(containsOwnerReference(v)) {
				// Prepare for the drop by copying all cells
				Slot[] ncells = Arrays.copyOf(cells, cells.length);
				// Perform the physical drop
				finalise(ncells, v);
				// Check heap invariant still holds!
				assert heapInvariant(ncells);
				// Done
				return new Store(ncells);
			}
			return this;
		}

		/**
		 * Identify all cells with a given lifetime.
		 *
		 * @param lifetime
		 * @return
		 */
		public BitSet findAll(Lifetime lifetime) {
			BitSet matches = new BitSet();
			// Action the drop
			for (int i = 0; i != cells.length; ++i) {
				Slot ncell = cells[i];
				if (ncell != null && ncell.lifetime() == lifetime) {
					// Mark address
					matches.set(i);
				}
			}
			// Convert results to array
			return matches;
		}

		@Override
		public String toString() {
			return Arrays.toString(cells);
		}

		public Slot[] toArray() {
			return cells;
		}

		/**
		 * Check whether a given value is or contains an owner reference. This indicates
		 * that, should the value in question be dropped, then we need to do some
		 * additional work.
		 *
		 * @param v
		 * @return
		 */
		private static boolean containsOwnerReference(Value v) {
			if (v instanceof Value.Reference) {
				Value.Reference r = (Value.Reference) v;
				return r.owner();
			} else if (v instanceof Value.Compound) {
				Value.Compound c = (Value.Compound) v;
				for (int i = 0; i != c.size(); ++i) {
					if (containsOwnerReference(c.get(i))) {
						return true;
					}
				}
			}
			return false;
		}

		/**
		 * Destroy a location and ensure its contents (including anything contained
		 * within) are finalised. For example, if the location contains an owned
		 * reference some heap memory, then this is destroyed as well. Likewise, if the
		 * location contains a compound value then we must traverse this looking for any
		 * owner references within.
		 *
		 * @param cells   Cells to update
		 * @param address Address of location to destroy.
		 * @return
		 */
		private static void destroy(Slot[] cells, int address) {
			// Locate cell being dropped
			Slot cell = cells[address];
			// Save value for later
			Value v = cell.value;
			// Physically drop the location
			cells[address] = null;
			// Finalise value by dropping any owned values.
			finalise(cells,v);
		}

		/**
		 * Finalise a value after the location containing it has been destroyed. If this
		 * is an owner reference to heap memory, then we must collect that memory as
		 * well. Likewise, if its a compound value then we must traverse its contents
		 *
		 * @param cells
		 * @param v
		 */
		private static void finalise(Slot[] cells, Value v) {
			if(v instanceof Value.Reference) {
				Value.Reference ref = (Value.Reference) v;
				// Check whether is an owner reference or not. If it is, then it should be
				// deallocated.
				if(ref.owner()) {
					final int l = ref.getAddress();
					// NOTE: it's an invariant that all reachable owned references have global
					// lifetime. Likewise, since there is only ever one owning reference for a heap
					// location, we can be guaranteed that it hasn't been collected at this point
					// (i.e. that <code>cells[l] != null</code>).
					assert cells[l] != null;
					assert cells[l].hasGlobalLifetime();
					// Recursively finalise this cell.
					destroy(cells, l);
				}
			} else if (v instanceof Value.Compound) {
				Value.Compound c = (Value.Compound) v;
				// We have a compound value, therefore need to explore the values it contains to
				// check whether any contain references which need to be dropped.
				for (int i = 0; i != c.size(); ++i) {
					finalise(cells, c.get(i));
				}
			}
		}

		/**
		 * The heap invariant states that every heap location should have exactly one
		 * owning reference.
		 *
		 * @param slots
		 * @return
		 */
		private static boolean heapInvariant(Slot[] slots, Value... temporaries) {
			int[] owners = new int[slots.length];
			// First, mark all owned locations
			for(int i=0;i!=slots.length;++i) {
				Slot ith = slots[i];
				if(ith != null) {
					markOwners(ith.contents(),owners);
				}
			}
			// Second, mark temporary value(s)
			for(int i=0;i!=temporaries.length;++i) {
				markOwners(temporaries[i], owners);
			}
			// Third. look for any heap locations which are not owned.
			for(int i=0;i!=slots.length;++i) {
				Slot ith = slots[i];
				if(ith != null) {
					if (ith.hasGlobalLifetime() && owners[i] != 1) {
						return false;
					} else if(hasDanglingReference(ith.contents(),slots)) {
						return false;
					}
				}
			}
			return true;
		}

		private static void markOwners(Value v, int[] owners) {
			if(v instanceof Value.Reference) {
				Value.Reference r = (Value.Reference) v;
				int l = r.getAddress();
				if (r.owner()) {
					owners[l]++;
				}
			} else if(v instanceof Value.Compound) {
				Value.Compound c = (Value.Compound) v;
				for(int i=0;i!=c.size();++i) {
					markOwners(c.get(i),owners);
				}
			}
		}

		private static boolean hasDanglingReference(Value v, Slot[] slots) {
			if(v instanceof Value.Reference) {
				Value.Reference r = (Value.Reference) v;
				int l = r.getAddress();
				if(slots[l] == null) {
					return true;
				}
			} else if(v instanceof Value.Compound) {
				Value.Compound c = (Value.Compound) v;
				for(int i=0;i!=c.size();++i) {
					if(hasDanglingReference(c.get(i),slots)) {
						return true;
					}
				}
			}
			return false;
		}
	}

	/**
	 * A single heap location which has a given lifetime.
	 *
	 * @author djp
	 *
	 */
	public static class Slot {
		private final Lifetime lifetime;
		private final Value value;

		public Slot(Lifetime lifetime, Value value) {
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
