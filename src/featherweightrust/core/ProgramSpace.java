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
 * |P{1}{1}{1}{1}| = 54
 * |P{1}{1}{1}{2}| = 2970
 * |P{1}{1}{2}{2}| = 9_147_600
 * |P{1}{2}{2}{2}| = 1766_058_600
 * |P{2}{2}{2}{2}| = 2217_326_832
 *
 * |P{1}{1}{1}{1}_inf| = 42
 * |P{1}{1}{1}{2}_inf| = 1806
 * |P{1}{1}{2}{2}_inf| = 3_416_952
 * |P{1}{2}{2}{2}_inf| = 607_548_552
 * |P{2}{2}{2}{2}_inf| = 815_702_160
 *
 * |P{1,1,1,2}_def(2)| = 74
 * |P{1,1,2,2}_def(2)| = 2960
 * |P{1,2,2,2}_def(2)| = 9332
 * |P{2}{2}{2}{2}_def{2}| = 22824
 * |P{1}{2}{2}{3}_def{2}| = 182_401_748
 * |P{1}{3}{2}{3}_def{2}| = 418_496_660
 * |P{1}{3}{3}{3}_def{2}| = 418_496_660
 *
 * |P{1,1,1,2}_def(2)_inf| = 58
 * |P{1,1,2,2}_def(2)_inf| = 1856
 * |P{1,2,2,2}_inf_def(2)| = 5692
 * |P{2}{2}{2}{2}_def{2}_inf| = 14680
 * |P{1}{2}{2}{3}_def{2}_inf| = 64619500
 * |P{1}{3}{2}{3}_def{2}_inf| = 146_566_092
 * |P{1}{3}{3}{3}_def{2}_inf| = 146_566_092
 *
 * |P{1}{1}{2}{2}_def{3}| = 21432
 * |P{2}{2}{2}{2}_def{3}| = 82360
 * |P{1}{2}{2}{3}_def{3}| = 500_246_168_816
 * |P{1}{2}{3}{3}_def{3}| = 886_907_481_224
 * |P{1}{3}{2}{3}_def{3}| = 1_248_940_792_352
 *
 * |P{1}{2}{2}{2}_def{3}_inf| = 13088
 * |P{2}{2}{2}{2}_def{3}_inf| = 53096
 * |P{1}{2}{2}{3}_def{3}_inf| = 106_368_095_672
 * |P{1}{2}{3}{3}_def{3}_inf| = 1_836_510_205_414
 * |P{1}{3}{2}{3}_def{3}_inf| = 262_670_209_640
 *
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
	 * Specify whether or not to use copy inference. When copy inference is enabled,
	 * all dereferences (e.g. variable accesses) have their copy/move status
	 * inferred based on the type of the operand.
	 */
	private final boolean copyInference;

	/**
	 * The parameter names here coincide with those in the definition of a program
	 * space.
	 *
	 * @param i The number of distinct integer literals.
	 * @param v The number of distinct variable names.
	 * @param d The maximum nesting of statement blocks.
	 * @param w The maximum width of a statement block.
	 * @param inf Whether or not to enable copy inference.
	 */
	public ProgramSpace(int i, int v, int d, int w, boolean inf) {
		// Generate appropriately sized set of integer values
		this.ints = Domains.Int(0,i-1);
		this.maxVariables = v;
		this.maxBlockDepth = d;
		this.maxBlockWidth = w;
		this.copyInference = inf;
	}

	public Domain.Big<Term.Block> domain() {
		Lifetime lifetime = ROOT.freshWithin();
		// The specialised domain for creating statements
		Domain.Small<String> variables = Domains.Finite(Arrays.copyOfRange(VARIABLE_NAMES, 0, maxVariables));
		// Construct domain of statements
		Domain.Big<Term> stmts = Syntax.toBigDomain(maxBlockDepth, maxBlockWidth, copyInference, lifetime, ints,
				variables, variables);
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
		UseDefState seed = new UseDefState(maxBlockDepth - 1, maxBlocks - 1, maxBlockWidth, maxVariables, copyInference,
				lifetime, ints, Domains.EMPTY);
		// Construct outer block
		return Term.Block.toWalker(lifetime, 1, maxBlockWidth, seed);
	}

	@Override
	public String toString() {
		// Return the name of this particular space
		String c = "";
		if(copyInference) {
			c = "_inf";
		}
		return "P{" + ints.bigSize() + "," + maxVariables + "," + maxBlockDepth + "," + maxBlockWidth + "}" + c;
	}

	private static class UseDefState implements Walker.State<Term> {
		private final int depth;
		private final int blocks;
		private final int width;
		private final int vars;
		private final boolean copyInference;
		private final Lifetime lifetime;
		private final Domain.Small<Integer> ints;
		private final Domain.Small<String> declared;

		public UseDefState(int depth, int blocks, int width, int vars, boolean copyInference, Lifetime lifetime, Domain.Small<Integer> ints, Domain.Small<String> declared) {
			this.depth = depth;
			this.blocks = blocks;
			this.width = width;
			this.vars = vars;
			this.copyInference = copyInference;
			this.lifetime = lifetime;
			this.ints = ints;
			this.declared = declared;
		}

		@Override
		public Walker<Term> construct() {
			// Construct adaptor to convert from variable names to lvals.
			Domain.Big<LVal> lvals = LVal.toBigDomain(declared);
			// Construct domain of "expressions"
			Domain.Big<Term> expressions = Syntax.toBigDomain(1, copyInference, ints, declared);
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
						new UseDefState(depth - 1, blocks - 1, width, vars, copyInference, l, ints, declared));
				// Done
				return Walkers.Union(units, blks);
			}
		}

		@Override
		public State<Term> transfer(Term item) {
			if (item instanceof Term.Let) {
				int size = declared.bigSize().intValue() + 1;
				Domain.Small<String> d = Domains.Finite(Arrays.copyOfRange(VARIABLE_NAMES, 0, size));
				return new UseDefState(depth, blocks, width, vars, copyInference, lifetime, ints, d);
			} else if(item instanceof Term.Block) {
				int nblocks = blocks - count(item);
				return new UseDefState(depth, nblocks, width, vars, copyInference, lifetime, ints, declared);
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
		System.out.println("|" + p + "| = " + domain.bigSize());
	}

	public static void count(ProgramSpace p, int max) {
		Walker<Term.Block> programs = p.definedVariableWalker(max);
		long count = 0;
		while(!programs.finished()) {
			count = count + programs.advance(50000);
		}
		System.out.println("|" + p + "_def(" + max + ")| = " + count);
	}

	public static void main(String[] args) {
		// Determine exhaustive sizes
		count(new ProgramSpace(1,1,1,1,false));
		count(new ProgramSpace(1,1,1,1,true));
		count(new ProgramSpace(1,1,2,1,false));
		count(new ProgramSpace(1,1,2,1,true));
		count(new ProgramSpace(1,1,1,2,false));
		count(new ProgramSpace(1,1,1,2,true));
		//
		count(new ProgramSpace(1,1,2,2,false));
		count(new ProgramSpace(1,1,2,2,true));
		count(new ProgramSpace(1,2,2,2,false));
		count(new ProgramSpace(1,2,2,2,true));
		count(new ProgramSpace(2,2,2,2,false));
		count(new ProgramSpace(2,2,2,2,true));
		count(new ProgramSpace(1,2,2,3,false));
		count(new ProgramSpace(1,2,2,3,true));
		count(new ProgramSpace(1,2,3,3,false));
		count(new ProgramSpace(1,2,3,3,true));
		count(new ProgramSpace(1,3,2,3,false));
		count(new ProgramSpace(1,3,2,3,true));
		count(new ProgramSpace(1,3,3,2,false));
		count(new ProgramSpace(1,3,3,2,true));
		count(new ProgramSpace(1,3,3,3,false));
		count(new ProgramSpace(1,3,3,3,true));
		// Determine constrained sizes
		count(new ProgramSpace(1,1,1,1,false),2);
		count(new ProgramSpace(1,1,1,1,true),2);
		count(new ProgramSpace(1,1,1,2,false),2);
		count(new ProgramSpace(1,1,1,2,true),2);
		count(new ProgramSpace(1,1,2,2,false),2);
		count(new ProgramSpace(1,1,2,2,true),2);
		count(new ProgramSpace(1,2,2,2,false),2);
		count(new ProgramSpace(1,2,2,2,true),2);
		count(new ProgramSpace(2,2,2,2,false),2);
		count(new ProgramSpace(2,2,2,2,true),2);
		count(new ProgramSpace(1,2,2,3,false),2);
		count(new ProgramSpace(1,2,2,3,true),2);
		count(new ProgramSpace(1,2,3,3,false),2);
		count(new ProgramSpace(1,2,3,3,true),2);
		count(new ProgramSpace(1,3,2,3,false),2);
		count(new ProgramSpace(1,3,2,3,true),2);
		count(new ProgramSpace(1,3,3,3,false),2);
		count(new ProgramSpace(1,3,3,3,true),2);
//		// Determine constrained sizes
		count(new ProgramSpace(1,1,1,1,false),3);
		count(new ProgramSpace(1,1,1,1,true),3);
		count(new ProgramSpace(1,1,1,2,false),3);
		count(new ProgramSpace(1,1,1,2,true),3);
		count(new ProgramSpace(1,1,2,2,false),3);
		count(new ProgramSpace(1,1,2,2,true),3);
		count(new ProgramSpace(1,2,2,2,false),3);
		count(new ProgramSpace(1,2,2,2,true),3);
		count(new ProgramSpace(2,2,2,2,false),3);
		count(new ProgramSpace(2,2,2,2,true),3);
		count(new ProgramSpace(1,2,2,3,false),3); // <----
		count(new ProgramSpace(1,2,2,3,true),3); // <----
		count(new ProgramSpace(1,2,3,3,false),3);
		count(new ProgramSpace(1,2,3,3,true),3);
		count(new ProgramSpace(1,3,2,3,false),3);
		count(new ProgramSpace(1,3,2,3,true),3);
		count(new ProgramSpace(1,3,3,3,false),3);
		count(new ProgramSpace(1,3,3,3,true),3);
	}
}
