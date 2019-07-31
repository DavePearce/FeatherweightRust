package featherweightrust.testing;

import java.math.BigInteger;
import java.util.Arrays;

import featherweightrust.core.Syntax.Expr;
import featherweightrust.core.Syntax.Lifetime;
import featherweightrust.core.Syntax.Stmt;
import jmodelgen.core.BigDomain;
import jmodelgen.core.Domain;
import jmodelgen.core.Walker;
import jmodelgen.util.BigDomains;
import jmodelgen.util.Domains;
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
	private static final String[] VARIABLE_NAMES = {
			"x","y","z","a","b","c","d","e","f","g","h","i","j","k","l","m","n","o","p","q","r","s","t","u","v","w"
	};

	/**
	 * The global lifetime from which all other lifetimes are created.
	 */
	private static Lifetime root = new Lifetime();

	/**
	 * The domain of all integer literals which can be present in a generated program.
	 */
	private final BigDomain<Integer> ints;

	/**
	 * The domain of all variable names which can used in a generated program.
	 */
	private final BigDomain<String> variables;

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
		this.ints = BigDomains.Int(0,i-1);
		// Slice out given number of variable names
		this.variables = BigDomains.Finite(Arrays.copyOfRange(VARIABLE_NAMES, 0, v));
		//
		this.maxBlockDepth = d;
		this.maxBlockWidth = w;
	}

	public BigDomain<Stmt.Block> domain() {
		Lifetime lifetime = root.freshWithin();
		// The specialised domain for creating statements
		// Construct domain of expressions over *declared* variables
		BigDomain<Expr> expressions = Expr.toBigDomain(1, ints, variables);
		// Construct domain of statements
		BigDomain<Stmt> stmts = Stmt.toBigDomain(maxBlockDepth - 1, maxBlockWidth, lifetime, expressions, variables, variables);
		// Construct outer block
		return Stmt.Block.toBigDomain(lifetime, 1, maxBlockWidth, stmts);
	}

	public Walker<Stmt.Block> walker() {
		Lifetime lifetime = root.freshWithin();
		// The specialised domain for creating statements
		// Construct domain of expressions over *declared* variables
		BigDomain<Expr> expressions = Expr.toBigDomain(1, ints, variables);
		//
		Walker<Stmt>[] walkers = new Walker[maxBlockWidth];
		for(int i=0;i!=walkers.length;++i) {
			walkers[i] = Stmt.toWalker(maxBlockDepth - 1, maxBlockWidth, lifetime, expressions, variables);
		}
		// Construct outer block
		return Stmt.Block.toWalker(lifetime, 1, walkers);
	}

	@Override
	public String toString() {
		// Return the name of this particular space
		return "P{" + ints.bigSize() + "," + variables.bigSize() + "," + maxBlockDepth + "," + maxBlockWidth + "}";
	}

	public static void main(String[] args) {
		ProgramSpace[] spaces = {
				new ProgramSpace(1,1,1,1),
				new ProgramSpace(1,1,1,2),
				new ProgramSpace(1,1,2,2),
				new ProgramSpace(1,2,2,2),
//				new ProgramSpace(2,2,2,2),
//				new ProgramSpace(1,2,2,3),
//				new ProgramSpace(1,2,3,3),
//				new ProgramSpace(1,3,2,3),
//				new ProgramSpace(1,3,3,2),
//				new ProgramSpace(1,3,3,3),
		};
		//
		for(ProgramSpace p : spaces) {
			BigDomain<Stmt.Block> domain = p.domain();
			System.out.println("|" + p + "| = " + domain.bigSize().doubleValue());
//			for(int i=0;i!=domain.bigSize().intValue();++i) {
//				System.out.println(i + " : " + domain.get(BigInteger.valueOf(i)));
//			}
		}
		//
		for(ProgramSpace p : spaces) {
			Walker<Stmt.Block> programs = p.walker();
			int count = 0;
			for(Stmt.Block b : programs) {
				count = count + 1;
			//	System.out.println("GOT: " + p);
			}
			System.out.println("|" + p + "| = " + count);
		}
//
//		BigDomain<Integer> ints = BigDomains.Int(0,0);
//		// Slice out given number of variable names
//		BigDomain<String> variables = BigDomains.Finite(Arrays.copyOfRange(VARIABLE_NAMES, 0, 2));
//		BigDomain<Expr> exprs = Expr.toBigDomain(1, ints, variables);
//		//
//		System.out.println("INTS: " + ints);
//		System.out.println("VARS: " + variables);
//		System.out.println("EXPRS: " + exprs);
	}
}
