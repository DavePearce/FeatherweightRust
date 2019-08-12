package featherweightrust.core;

import java.math.BigInteger;
import java.util.Arrays;

import featherweightrust.core.Syntax.Lifetime;
import featherweightrust.core.Syntax.Stmt;
import featherweightrust.core.Syntax.Stmt.*;
import featherweightrust.core.Syntax.Expr;
import jmodelgen.core.Domain;
import jmodelgen.core.Domains;
import jmodelgen.core.Walker;
import jmodelgen.core.Walker.State;
import jmodelgen.util.Walkers;

/**
 * Provides machinery for representing and working with the space of all
 * programs.
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

	public Domain.Big<Stmt.Block> domain() {
		Lifetime lifetime = ROOT.freshWithin();
		// The specialised domain for creating statements
		Domain.Small<String> variables = Domains.Finite(Arrays.copyOfRange(VARIABLE_NAMES, 0, maxVariables));
		// Construct domain of expressions over *declared* variables
		Domain.Big<Expr> expressions = Expr.toBigDomain(1, ints, variables);
		// Construct domain of statements
		Domain.Big<Stmt> stmts = Stmt.toBigDomain(maxBlockDepth - 1, maxBlockWidth, lifetime, expressions, variables,
				variables);
		// Construct outer block
		return Stmt.Block.toBigDomain(lifetime, 1, maxBlockWidth, stmts);
	}

	/**
	 * Return a cursor over a constrained version of this program space.
	 * Specifically, where there are at most a given number of blocks, and every
	 * variable is declared before being used.
	 *
	 * @param maxBlocks Maximum number of blocks to permit
	 * @return
	 */
	public Walker<Stmt.Block> definedVariableWalker(int maxBlocks) {
		Lifetime lifetime = ROOT.freshWithin();
		// Construct domain of expressions over *declared* variables
		UseDefState seed = new UseDefState(maxBlockDepth - 1, maxBlocks - 1, maxBlockWidth, maxVariables, lifetime,
				ints, Domains.EMPTY);
		// Construct outer block
		return Stmt.Block.toWalker(lifetime, 1, maxBlockWidth, seed);
	}

	@Override
	public String toString() {
		// Return the name of this particular space
		return "P{" + ints.bigSize() + "," + maxVariables + "," + maxBlockDepth + "," + maxBlockWidth + "}";
	}

	private static class UseDefState implements Walker.State<Stmt> {
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
		public Walker<Stmt> construct() {
			Domain.Big<Expr> expressions = Expr.toBigDomain(1, ints, declared);
			Domain.Big<Let> lets;
			int size = declared.bigSize().intValue();
			if(size < vars) {
				Domain.Small<String> canDeclare = Domains.Finite(VARIABLE_NAMES[size]);
				// Let statements can only be constructed from undeclared variables
				lets = Stmt.Let.toBigDomain(canDeclare, expressions);
			} else {
				lets = Domains.EMPTY;
			}
			// Assignments can only use declared variables
			Domain.Big<Assignment> assigns = Stmt.Assignment.toBigDomain(declared, expressions);
			// Indirect assignments can only use declared variables
			Domain.Big<IndirectAssignment> indirects = Stmt.IndirectAssignment.toBigDomain(declared, expressions);
			// Create walker for unit statements
			Walker<Stmt> units = Walkers.Adaptor(Domains.Union(lets, assigns, indirects));
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
		public State<Stmt> transfer(Stmt item) {
			if (item instanceof Stmt.Let) {
				int size = declared.bigSize().intValue() + 1;
				Domain.Small<String> d = Domains.Finite(Arrays.copyOfRange(VARIABLE_NAMES, 0, size));
				return new UseDefState(depth, blocks, width, vars, lifetime, ints, d);
			} else if(item instanceof Stmt.Block) {
				int nblocks = blocks - count(item);
				return new UseDefState(depth, nblocks, width, vars, lifetime, ints, declared);
			} else {
				return this;
			}
		}

		private static int count(Stmt stmt) {
			if (stmt instanceof Stmt.Block) {
				int count = 1;
				Stmt.Block block = (Stmt.Block) stmt;
				for (int i = 0; i != block.size(); ++i) {
					count += count(block.get(i));
				}
				return count;
			} else {
				return 0;
			}
		}
	}

	public static void main(String[] args) {
		// Print some statistics about various domains
		ProgramSpace[] spaces = {
				new ProgramSpace(1,1,1,1),
				new ProgramSpace(1,1,1,2),
				new ProgramSpace(1,1,2,2),
				new ProgramSpace(1,2,2,2),
				new ProgramSpace(2,2,2,2),
				new ProgramSpace(1,2,2,3),
				new ProgramSpace(1,2,3,3),
				new ProgramSpace(1,3,2,3),
				new ProgramSpace(1,3,3,2),
				new ProgramSpace(1,3,3,3),
		};
		//
		for(ProgramSpace p : spaces) {
			Domain.Big<Stmt.Block> domain = p.domain();
			System.out.println("|" + p + "| = " + domain.bigSize().doubleValue());
		}
		//
		final int MAX_BLOCKS = 2;
		//
		for(ProgramSpace p : spaces) {
			Walker<Stmt.Block> programs = p.definedVariableWalker(MAX_BLOCKS);
			long count = 0;
			for(Stmt.Block b : programs) {
				count = count + 1;
			}
			System.out.println("|" + p + "_def(" + MAX_BLOCKS + ")| = " + count);
		}
	}
}
