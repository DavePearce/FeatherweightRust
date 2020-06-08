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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import featherweightrust.core.OperationalSemantics;
import featherweightrust.core.ProgramSpace;
import featherweightrust.core.BorrowChecker;
import featherweightrust.core.Syntax.Lifetime;
import featherweightrust.core.Syntax.Term;
import featherweightrust.core.Syntax.Value;
import featherweightrust.util.AbstractMachine;
import featherweightrust.util.AbstractMachine.State;
import featherweightrust.util.OptArg;
import featherweightrust.util.SyntaxError;
import featherweightrust.util.Triple;
import featherweightrust.util.Pair;

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
	/**
	 * Flag whether to report failures to the console or not.
	 */
	private static boolean VERBOSE;

	/**
	 * Command-line options
	 */
	private static final OptArg[] OPTIONS = {
			// Standard options
			new OptArg("verbose","v","set verbose output"),
			new OptArg("quiet","q","disable progress reporting"),
			new OptArg("expected","n",OptArg.LONG,"set expected domain size",-1L),
			new OptArg("pspace", "p", OptArg.LONGARRAY(4, 4), "set program space"),
			new OptArg("constrained", "c", OptArg.INT, "set maximum block count and constrain use-defs", -1),
			new OptArg("batch", "b", OptArg.LONGARRAY(2, 2), "set batch index and batch count", null),
			// Specific options
			new OptArg("batchsize", "s", OptArg.INT, "set batch size", 10000),
	};

	public static void main(String[] _args) throws Exception {
		List<String> args = new ArrayList<>(Arrays.asList(_args));
		Map<String, Object> options = OptArg.parseOptions(args, OPTIONS);
		// Extract Fuzzing specific command-line arguments
		VERBOSE = options.containsKey("verbose");
		// Process generic command-line arguments
		boolean quiet = options.containsKey("quiet");
		Triple<Iterator<Term.Block>,Long,String> config = Util.parseDefaultConfiguration(options);
		// Construct and configure experiment
		ParallelExperiment<Term.Block> experiment = new ParallelExperiment<>(config.first());
		// Set expected items
		experiment = experiment.setExpected(config.second()).setQuiet(quiet);
		// Run the MapReduce experiment
		Stats result = experiment.run(new Stats(config.third()), ModelCheckingExperiment::check, (r1,r2) -> r1.join(r2));
		//
		result.print();
		// Done
		System.exit(1);
	}

	public static Stats check(Term.Block[] batch) {
		Lifetime lifetime = ProgramSpace.ROOT;
		Stats stats = new Stats(null);
		for(int i=0;i!=batch.length;++i) {
			Term.Block block = batch[i];
			if(block != null) {
				check(block,lifetime,stats);
			}
		}
		return stats;
	}

	// NOTE: must use BigStep semantics because its more efficient!
	public static final OperationalSemantics semantics = new OperationalSemantics();
	/**
	 * Copy inference disabled when model checking
	 */
	public static final BorrowChecker checker = new BorrowChecker(false,"");

	public static void check(Term.Block stmt, Lifetime lifetime, Stats stats) {
		boolean ran = false;
		boolean checked = false;
		Throwable error = null;
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
			semantics.execute(lifetime, stmt);
			// Execution must have completed yielding a value. Otherwise, an exception would
			// have necessarily been thrown.
			ran = true;
		} catch (Throwable e) {
			error = e;
		}
		// Update statistics
		if (checked && ran) {
			stats.valid++;
		} else if (checked) {
			if(VERBOSE) {
				error.printStackTrace(System.out);
				System.out.println("*** ERROR(" + error.getMessage() + "): " + stmt.toString());
			}
			stats.falseneg++;
		} else if (ran) {
			stats.falsepos++;
		} else {
			stats.invalid++;
		}
	}

	private final static class Stats {
		private final long start = System.currentTimeMillis();

		private String label;

		public long valid = 0;
		public long invalid = 0;
		public long falsepos = 0;
		public long falseneg = 0;

		public Stats(String label) {
			this.label = label;
		}

		public Stats join(Stats stats) {
			this.valid += stats.valid;
			this.invalid += stats.invalid;
			this.falsepos += stats.falsepos;
			this.falseneg += stats.falseneg;
			return this;
		}

		public long total() { return valid + invalid + falsepos; }

		public void print() {
			long time = System.currentTimeMillis() - start;
			System.out.println("{");
			System.out.println("\tTIME: " + (time/1000));
			System.out.println("\tSPACE: " + label);
			System.out.println("\tTOTAL: " + total());
			System.out.println("\tVALID: " + valid);
			System.out.println("\tINVALID: " + (invalid+falsepos));
			double percent = (100D*(falsepos) / (invalid+falsepos));
			System.out.println("\tFALSEPOS: " + falsepos);
			System.out.println("\tFALSEPOS_PERCENT: " + String.format("%.2f",percent));
			System.out.println("\tFALSENEG: " + falseneg);
			System.out.println("}");
		}
	}
}
