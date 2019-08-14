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
package featherweightrust.testing.experiments;

import java.io.IOException;
import java.math.BigInteger;
import java.util.Iterator;

import featherweightrust.core.OperationalSemantics;
import featherweightrust.core.ProgramSpace;
import featherweightrust.core.BorrowChecker;
import featherweightrust.core.Syntax.Lifetime;
import featherweightrust.core.Syntax.Stmt;
import featherweightrust.core.Syntax.Value;
import featherweightrust.util.AbstractSemantics;
import featherweightrust.util.Pair;
import featherweightrust.util.SyntaxError;
import jmodelgen.core.Domain;

/**
 * The purpose of this experiment is to check soundness of the calculus with
 * respect to borrow and type checking. Specifically, by exhaustively checking
 * large spaces of programs to see whether any violate the fundamental
 * guarantees (e.g. create dangling references).
 *
 * @author David J. Pearce
 *
 */
public class ModelCheckingExperiment {

	public static void main(String[] args) throws IOException {
		// The set of program spaces to be considered.
		check(new ProgramSpace(1, 1, 1, 1));
		check(new ProgramSpace(1, 1, 1, 2));
		check(new ProgramSpace(1, 1, 2, 2));
		check(new ProgramSpace(1, 2, 2, 2));
		check(new ProgramSpace(2, 2, 2, 2));
//		check(new ProgramSpace(1, 2, 2, 3), 2, 34038368);
//		check(new ProgramSpace(1, 3, 2, 3), 2, 76524416);
//		check(new ProgramSpace(1, 3, 3, 2), 2, -1);
		// Really hard ones
//		check(new ProgramSpace(2, 2, 2, 2), 3, -1);
//		check(new ProgramSpace(1, 2, 2, 3), 3, -1);
	}

	public static void check(ProgramSpace space) {
		Domain.Big<Stmt.Block> domain = space.domain();
		check(domain,domain.bigSize().longValueExact(),space.toString());
	}

	public static void check(ProgramSpace space, int maxBlocks, long expected) {
		check(space.definedVariableWalker(maxBlocks), expected, space.toString() + "{def," + maxBlocks + "}");
	}

	public static void check(Iterable<Stmt.Block> space, long expected, String label) {
		Stats stats = new Stats(label);
		long count = 0;
		for(Stmt.Block s : space) {
			runAndCheck(s, ProgramSpace.ROOT, stats);
			// Report
			reportProgress(++count,expected);
		}
		//
		stats.print();
	}

	public static void reportProgress(long count, long expected) {
		if(expected < 0) {
			if((count % 10_000_000) == 0) {
				System.out.print("\r(" + count + ")");
			}
		} else {
			long delta = expected / 100;
			if(delta == 0 || count % delta == 0) {
				long percent = (long) (100D * (count) / expected);
				System.out.print("\r(" + percent + "%)");
			}
		}
	}

	// NOTE: must use BigStep semantics because its more efficient!
	public static final OperationalSemantics semantics = new OperationalSemantics.BigStep();
	public static final BorrowChecker checker = new BorrowChecker("");

	public static void runAndCheck(Stmt.Block stmt, Lifetime lifetime, Stats stats) {
		boolean ran = false;
		boolean checked = false;
		Exception error = null;
		// See whether or not it borrow checks
		try {
			checker.apply(BorrowChecker.EMPTY_ENVIRONMENT, lifetime, stmt);
			checked = true;
		} catch (SyntaxError e) {
			error = e;
		}
		// See whether or not it executes
		try {
			// Execute block in outermost lifetime "*")
			semantics.apply(AbstractSemantics.EMPTY_STATE, lifetime, stmt);
			ran = true;
		} catch (Exception e) {
			error = e;
		}
		// Update statistics
		if (checked && ran) {
			stats.valid++;
		} else if (checked) {
			error.printStackTrace(System.out);
			System.out.println("*** ERROR(" + error.getMessage() + "): " + stmt.toString());
			stats.falseneg++;
		} else if (ran) {
			stats.falsepos++;
		} else {
			stats.invalid++;
		}
	}

	public static class Stats {
		private final long start = System.currentTimeMillis();

		private String label;

		public long valid = 0;
		public long invalid = 0;
		public long falsepos = 0;
		public long falseneg = 0;

		public Stats(String label) {
			this.label = label;
		}

		public long total() { return valid + invalid + falsepos; }

		public void print() {
			long time = System.currentTimeMillis() - start;
			System.out.println("================================");
			System.out.println("TIME: " + time + "ms");
			System.out.println("SPACE: " + label);
			System.out.println("TOTAL: " + total());
			System.out.println("VALID: " + valid);
			System.out.println("INVALID: " + (invalid+falsepos));
			double percent = (100D*(falsepos) / (invalid+falsepos));
			System.out.println("FALSEPOS: " + falsepos + " (" + String.format("%.2f",percent) + "%)");
			System.out.println("FALSENEG: " + falseneg);
		}
	}
}
