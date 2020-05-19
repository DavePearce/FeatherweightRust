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

import java.math.BigInteger;
import java.util.Arrays;

import featherweightrust.core.Syntax.LVal;
import featherweightrust.core.Syntax.Lifetime;
import featherweightrust.core.Syntax.Term;
import featherweightrust.core.Syntax.Term.*;
import jmodelgen.core.Domain;
import jmodelgen.core.Domains;
import jmodelgen.core.Walker;
import jmodelgen.core.Walker.State;
import jmodelgen.util.Walkers;

/**
 * Provides machinery for representing and working with the space of all
 * programs.  Some numbers:
 *
 *<pre>
 * |P{1}{1}{1}{1}| = 36
 * |P{1}{1}{1}{2}| = 1332
 * |P{1}{1}{2}{2}| = 1873792
 * |P{1}{2}{2}{2}| = 312883032
 * |P{2}{2}{2}{2}| = 442_029_600
 *
 * |P{2}{2}{2}{2}_def{2}| = 11280
 * |P{1}{2}{2}{3}_def{2}| = 34038368
 * |P{1}{3}{2}{3}_def{2}| = 76524416
 *
 * |P{1}{1}{2}{2}_def{3}| = 9684
 * |P{1}{2}{2}{2}_def{3}| = 40864
 * |P{1}{2}{2}{3}_def{3}| = 40_925_161_340L
 * |P{1}{3}{2}{3}_def{3}| = 100_213_706_876L
 * </pre>
 *
 * @author David J. Pearce
 *
 */
public class ProgramSpace {
	/**
	 * The set of all possible variable names.
	 */
	public static final String[] VARIABLE_NAMES = {
			"x","y","z","a","b","c","d","e","f","g","h","i","j","k","l","m","n","o","p","q","r","s","t","u","v","w"
	};

	/**
	 * The global lifetime from which all other lifetimes are created.
	 */
	public static Lifetime ROOT = new Lifetime();

	/**
	 * The domain of all integer literals which can be present in a generated program.
	 */
	private final Domain.Small<Integer> ints;

	/**
	 * The maximum number of variables which can be used.
	 */
	private final int maxVariables;

	/**
	 * The maximum number of blocks which can be present in a generated program.
	 */
	private final int maxBlockDepth;

	/**
	 * The maximum width of a block (i.e. number of statements it contains) within a
	 * generated program.
	 */
	private final int maxBlockWidth;

	/**
	 * The parameter names here coincide with those in the definition of a program
	 * space.
	 *
	 * @param i The number of distinct integer literals.
	 * @param v The number of distinct variable names.
	 * @param d The maximum nesting of statement blocks.
	 * @param w The maximum width of a statement block.
	 */
	public ProgramSpace(int i, int v, int d, int w) {
		// Generate appropriately sized set of integer values
		this.ints = Domains.Int(0,i-1);
		this.maxVariables = v;
		this.maxBlockDepth = d;
		this.maxBlockWidth = w;
	}

	public Domain.Big<Term.Block> domain() {
		Lifetime lifetime = ROOT.freshWithin();
		// The specialised domain for creating statements
		Domain.Small<String> variables = Domains.Finite(Arrays.copyOfRange(VARIABLE_NAMES, 0, maxVariables));
		// Construct domain of statements
		Domain.Big<Term> stmts = Syntax.toBigDomain(maxBlockDepth, maxBlockWidth, lifetime, ints, variables,
				variables);
		// Construct outer block
		return Term.Block.toBigDomain(lifetime, 1, maxBlockWidth, stmts);
	}

	/**
	 * Return a cursor over a constrained version of this program space.
	 * Specifically, where there are at most a given number of blocks, and every
	 * variable is declared before being used.
	 *
	 * @param maxBlocks Maximum number of blocks to permit
	 * @return
	 */
	public Walker<Term.Block> definedVariableWalker(int maxBlocks) {
		Lifetime lifetime = ROOT.freshWithin();
		// Construct domain of expressions over *declared* variables
		UseDefState seed = new UseDefState(maxBlockDepth - 1, maxBlocks - 1, maxBlockWidth, maxVariables, lifetime,
				ints, Domains.EMPTY);
		// Construct outer block
		return Term.Block.toWalker(lifetime, 1, maxBlockWidth, seed);
	}

	@Override
	public String toString() {
		// Return the name of this particular space
		return "P{" + ints.bigSize() + "," + maxVariables + "," + maxBlockDepth + "," + maxBlockWidth + "}";
	}

	private static class UseDefState implements Walker.State<Term> {
		private final int depth;
		private final int blocks;
		private final int width;
		private final int vars;
		private final Lifetime lifetime;
		private final Domain.Small<Integer> ints;
		private final Domain.Small<String> declared;

		public UseDefState(int depth, int blocks, int width, int vars, Lifetime lifetime, Domain.Small<Integer> ints, Domain.Small<String> declared) {
			this.depth = depth;
			this.blocks = blocks;
			this.width = width;
			this.vars = vars;
			this.lifetime = lifetime;
			this.ints = ints;
			this.declared = declared;
		}

		@Override
		public Walker<Term> construct() {
			// Construct adaptor to convert from variable names to lvals.
			Domain.Big<LVal> lvals = LVal.toBigDomain(declared);
			// Construct domain of "expressions"
			Domain.Big<Term> expressions = Syntax.toBigDomain(1, ints, declared);
			Domain.Big<Let> lets;
			int size = declared.bigSize().intValue();
			if(size < vars) {
				Domain.Small<String> canDeclare = Domains.Finite(VARIABLE_NAMES[size]);
				// Let statements can only be constructed from undeclared variables
				lets = Term.Let.toBigDomain(canDeclare, expressions);
			} else {
				lets = Domains.EMPTY;
			}
			// Assignments can only use declared variables
			Domain.Big<Assignment> assigns = Term.Assignment.toBigDomain(lvals, expressions);
			// Create walker for unit statements
			Walker<Term> units = Walkers.Adaptor(Domains.Union(lets, assigns));
			//
			if (depth == 0 || blocks <= 0) {
				return units;
			} else {
				// Determine lifetime for blocks at this level
				final Lifetime l = lifetime.freshWithin();
				// Using this construct the block generator
				Walker<Block> blks = Block.toWalker(l, 1, width,
						new UseDefState(depth - 1, blocks - 1, width, vars, l, ints, declared));
				// Done
				return Walkers.Union(units, blks);
			}
		}

		@Override
		public State<Term> transfer(Term item) {
			if (item instanceof Term.Let) {
				int size = declared.bigSize().intValue() + 1;
				Domain.Small<String> d = Domains.Finite(Arrays.copyOfRange(VARIABLE_NAMES, 0, size));
				return new UseDefState(depth, blocks, width, vars, lifetime, ints, d);
			} else if(item instanceof Term.Block) {
				int nblocks = blocks - count(item);
				return new UseDefState(depth, nblocks, width, vars, lifetime, ints, declared);
			} else {
				return this;
			}
		}

		private static int count(Term stmt) {
			if (stmt instanceof Term.Block) {
				int count = 1;
				Term.Block block = (Term.Block) stmt;
				for (int i = 0; i != block.size(); ++i) {
					count += count(block.get(i));
				}
				return count;
			} else {
				return 0;
			}
		}
	}

	public static void print(ProgramSpace p) {
		Domain.Big<Term.Block> domain = p.domain();
		for(long i=0;i!=domain.bigSize().longValue();++i) {
			System.out.println(domain.get(BigInteger.valueOf(i)));
		}
	}

	public static void print(ProgramSpace p, int max) {
		Walker<Term.Block> programs = p.definedVariableWalker(max);
		for(Term.Block b : programs) {
			System.out.println(b);
		}
	}

	public static void count(ProgramSpace p) {
		Domain.Big<Term.Block> domain = p.domain();
		System.out.println("|" + p + "| = " + domain.bigSize().doubleValue());
	}

	public static void count(ProgramSpace p, int max) {
		Walker<Term.Block> programs = p.definedVariableWalker(max);
		long count = 0;
		for(Term.Block b : programs) {
			count = count + 1;
		}
		System.out.println("|" + p + "_def(" + max + ")| = " + count);
	}

	public static void main(String[] args) {
		count(new ProgramSpace(1,1,1,1));
		//count(new ProgramSpace(1,1,2,1));
		//count(new ProgramSpace(1,1,1,2));
		count(new ProgramSpace(1,2,2,2),2);
		//count(new ProgramSpace(1,1,1,2),2);
		//count(new ProgramSpace(1,1,2,2),2);
		print(new ProgramSpace(1,2,2,2),2);


		// Determine exhaustive sizes
//		count(new ProgramSpace(1,1,1,1));
//		count(new ProgramSpace(1,1,2,1));
//		count(new ProgramSpace(1,1,1,2));
		//
//		count(new ProgramSpace(1,1,2,2));
//		count(new ProgramSpace(1,2,2,2));
//		count(new ProgramSpace(2,2,2,2));
//		count(new ProgramSpace(1,2,2,3));
//		count(new ProgramSpace(1,2,3,3));
//		count(new ProgramSpace(1,3,2,3));
//		count(new ProgramSpace(1,3,3,2));
//		count(new ProgramSpace(1,3,3,3));
//		// Determine constrained sizes
//		count(new ProgramSpace(1,1,1,1),2);
//		count(new ProgramSpace(1,1,1,2),2);
//		count(new ProgramSpace(1,1,2,2),2);
//		count(new ProgramSpace(1,2,2,2),2);
//		count(new ProgramSpace(2,2,2,2),2);
//		count(new ProgramSpace(1,2,2,3),2);
//		count(new ProgramSpace(1,2,3,3),2);
//		count(new ProgramSpace(1,3,2,3),2);
//		count(new ProgramSpace(1,3,3,3),2);
//		// Determine constrained sizes
//		count(new ProgramSpace(1,1,1,1),3);
//		count(new ProgramSpace(1,1,1,2),3);
//		count(new ProgramSpace(1,1,2,2),3);
//		count(new ProgramSpace(1,2,2,2),3);
//		count(new ProgramSpace(2,2,2,2),3);
//		count(new ProgramSpace(1,2,2,3),3); // <----
//		count(new ProgramSpace(1,2,3,3),3);
//		count(new ProgramSpace(1,3,2,3),3);
//		count(new ProgramSpace(1,3,3,3),3);
	}
}
