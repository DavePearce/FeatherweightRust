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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import featherweightrust.core.OperationalSemantics;
import featherweightrust.core.ProgramSpace;
import featherweightrust.core.BorrowChecker;
import featherweightrust.core.Syntax.Lifetime;
import featherweightrust.core.Syntax.Stmt;
import featherweightrust.util.AbstractMachine;
import featherweightrust.util.OptArg;
import featherweightrust.util.SliceIterator;
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

	/**
	 * Configure number of threads to use. You may need to hand tune this a little
	 * to maximum performance, which makes a real difference on the big domains.
	 */
	private static final int NTHREADS = (Runtime.getRuntime().availableProcessors());

	/**
	 * Number of programs each thread to process in one go. You may need to hand
	 * tune this a little to maximum performance, which makes a real difference on
	 * the big domains.
	 */
	private static final int BATCHSIZE = 10000;

	/**
	 * Flag whether to report failures to the console or not.
	 */
	private static boolean VERBOSE;

	/**
	 * Construct a thread pool to use for parallel processing.
	 */
	private static final ExecutorService executor = Executors.newCachedThreadPool();

	/**
	 * Command-line options
	 */
	private static final OptArg[] OPTIONS = {
			new OptArg("verbose","v","set expected domain size"),
			new OptArg("expected","n",OptArg.LONG,"set expected domain size",-1L),
			new OptArg("pspace", "p", OptArg.LONGARRAY(4, 4), "set program space", new int[] { 1, 1, 1, 1 }),
			new OptArg("constrained", "c", OptArg.INT, "set maximum block count and constrain use-defs", -1),
			new OptArg("range", "r", OptArg.LONGARRAY(2, 2), "set index range of domain to iterate",
					null)
	};

	public static void main(String[] _args) throws Exception {
		List<String> args = new ArrayList<>(Arrays.asList(_args));
		if(args.size() == 0) {
			System.out.println("usage: java Main <options> target");
			System.out.println();
			OptArg.usage(System.out, OPTIONS);
		} else {
			Map<String, Object> options = OptArg.parseOptions(args, OPTIONS);
			long[] ivdw = (long[]) options.get("pspace");
			int c = (Integer) options.get("constrained");
			long expected = (Long) options.get("expected");
			long[] range = (long[]) options.get("range");
			//
			VERBOSE = options.containsKey("verbose");
			//
			ProgramSpace space = new ProgramSpace((int) ivdw[0], (int) ivdw[1], (int) ivdw[2], (int) ivdw[3]);
			Iterator<Stmt.Block> iterator;
			String label;
			// Create iterator
			if(c >= 0) {
				iterator = space.definedVariableWalker(c).iterator();
				label = space.toString() + "{def," + c + "}";
			} else {
				// Get domain
				Domain.Big<Stmt.Block> domain = space.domain();
				// Determine expected size
				expected = domain.bigSize().longValueExact();
				// Get iterator
				iterator = domain.iterator();
				//
				label = space.toString();
			}
			// Slice iterator (if applicable)
			if (range != null) {
				// Create sliced iterator
				iterator = new SliceIterator(iterator, range[0], range[1]);
				// Update expected
				expected = range[1] - range[0];
			}
			// Done
			check(iterator, expected, label);
		}
		System.exit(1);
	}

	public static void check(Iterator<Stmt.Block> iterator, long expected, String label) throws Exception {
		// Construct temporary memory areas
		Stmt.Block[][] arrays = new Stmt.Block[NTHREADS][BATCHSIZE];
		Future<Stats>[] threads = new Future[NTHREADS];
		//
		Stats stats = new Stats(label);
		//
		while (iterator.hasNext()) {
			// Create next batch
			for (int i = 0; i != NTHREADS; ++i) {
				copyToArray(arrays[i], iterator);
			}
			// Submit next batch for process
			for (int i = 0; i != NTHREADS; ++i) {
				final Stmt.Block[] batch = arrays[i];
				threads[i] = executor.submit(() -> check(batch, ProgramSpace.ROOT));
			}
			// Join all back together
			for (int i = 0; i != NTHREADS; ++i) {
				stats.join(threads[i].get());
			}
			// Report
			reportProgress(stats, expected);
		}
		//
		stats.print();
	}

	public static void reportProgress(Stats stats, long expected) {
		long count = stats.total();
		long time = System.currentTimeMillis() - stats.start;
		//
		if(expected < 0) {
			System.err.print("\r(" + count + ")");
		} else {
			double rate = ((double) time) / count;
			double remainingMS = (expected - count) * rate;
			long remainingS = ((long)remainingMS/1000) % 60;
			long remainingM = ((long)remainingMS/(60*1000)) % 60;
			long remainingH = ((long)remainingMS/(60*60*1000));
			long percent = (long) (100D * (count) / expected);
			String remaining = remainingH + "h " + remainingM + "m " + remainingS + "s";
			System.err.print("\r(" + percent +  "%, " + String.format("%.0f",(1000/rate)) +  "/s, remaining " + remaining + ")           ");
		}
	}

	public static Stats check(Stmt.Block[] batch, Lifetime lifetime) throws Exception {
		Stats stats = new Stats(null);
		for(int i=0;i!=batch.length;++i) {
			Stmt.Block block = batch[i];
			if(block != null) {
				check(block,lifetime,stats);
			}
		}
		return stats;
	}

	// NOTE: must use BigStep semantics because its more efficient!
	public static final OperationalSemantics semantics = new OperationalSemantics.BigStep();
	public static final BorrowChecker checker = new BorrowChecker("");

	public static void check(Stmt.Block stmt, Lifetime lifetime, Stats stats) {
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
			semantics.apply(AbstractMachine.EMPTY_STATE, lifetime, stmt);
			ran = true;
		} catch (Exception e) {
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

	private static <T> int copyToArray(T[] array, Iterator<T> b) {
		int i = 0;
		// Read items into array
		while (b.hasNext() && i < array.length) {
			array[i++] = b.next();
		}
		// Reset any trailing items
		for (; i < array.length; ++i) {
			array[i] = null;
		}
		// Done
		return i;
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

		public void join(Stats stats) {
			this.valid += stats.valid;
			this.invalid += stats.invalid;
			this.falsepos += stats.falsepos;
			this.falseneg += stats.falseneg;
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
