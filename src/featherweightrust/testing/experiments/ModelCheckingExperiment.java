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

import java.util.Iterator;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import featherweightrust.core.OperationalSemantics;
import featherweightrust.core.ProgramSpace;
import featherweightrust.core.BorrowChecker;
import featherweightrust.core.Syntax.Lifetime;
import featherweightrust.core.Syntax.Stmt;
import featherweightrust.util.AbstractSemantics;
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
	 * Construct a thread pool to use for parallel processing.
	 */
	private static final ExecutorService executor = Executors.newCachedThreadPool();

	public static void main(String[] args) throws Exception {
		System.out.println("NUM THREADS: " + NTHREADS);
		// The set of program spaces to be considered.
//		check(new ProgramSpace(1, 1, 1, 1));
//		check(new ProgramSpace(1, 1, 1, 2));
//		check(new ProgramSpace(1, 1, 2, 2));
//		check(new ProgramSpace(1, 2, 2, 2));
//		check(new ProgramSpace(2, 2, 2, 2));
		check(new ProgramSpace(1, 2, 2, 3), 2, 34038368);
//		check(new ProgramSpace(1, 3, 2, 3), 2, 76524416);
//		check(new ProgramSpace(1, 3, 3, 2), 2, -1);
		// Really hard ones
//		check(new ProgramSpace(1, 2, 2, 2), 3, 9684);
//		check(new ProgramSpace(2, 2, 2, 2), 3, 40864);
//		check(new ProgramSpace(1, 2, 2, 3), 3, 40_925_161_340L);
		System.exit(1);
	}

	public static void check(ProgramSpace space) throws Exception {
		Domain.Big<Stmt.Block> domain = space.domain();
		check(domain,domain.bigSize().longValueExact(),space.toString());
	}

	public static void check(ProgramSpace space, int maxBlocks, long expected) throws Exception {
		check(space.definedVariableWalker(maxBlocks), expected, space.toString() + "{def," + maxBlocks + "}");
	}

	public static void check(Iterable<Stmt.Block> space, long expected, String label) throws Exception {
		// Construct temporary memory areas
		Stmt.Block[][] arrays = new Stmt.Block[NTHREADS][BATCHSIZE];
		Future<Stats>[] threads = new Future[NTHREADS];
		//
		Iterator<Stmt.Block> iterator = space.iterator();
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
		if (expected < 0) {
			System.out.print("\r(" + count + ")");
		} else {
			double programsPerMs = ((double) count) / time;
			double msPerProgram = ((double) time) / count;
			double remaining = ((expected - count) * msPerProgram) / 1000;
			long percent = (long) (100D * (count) / expected);
			System.out.print("\r(" + percent + "%, " + String.format("%.2f", (programsPerMs * 1000)) + "/s, remaining "
					+ String.format("%.2f", remaining) + "s)");
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
			System.out.println("================================");
			System.out.println("TIME: " + (time/1000) + "s");
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
