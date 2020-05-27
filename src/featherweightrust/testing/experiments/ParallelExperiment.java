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
import java.io.PrintStream;
import java.util.Arrays;
import java.util.Iterator;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.BiFunction;
import java.util.function.Function;


public class ParallelExperiment<T> {
	/**
	 * Construct a thread pool to use for parallel processing.
	 */
	private static final ExecutorService executor = Executors.newCachedThreadPool();

	/**
	 * Determines the specifics of the space being executed.
	 */
	private Iterator<T> iterator;

	/**
	 * Configure number of threads to use.
	 */
	private int nthreads = Runtime.getRuntime().availableProcessors();

	/**
	 * Flag whether to report progress or not.
	 */
	private boolean quiet = false;

	/**
	 * Number of programs each thread to process in one go. This can make a real
	 * difference to the overall performance.
	 */
	private int batchSize = 200;

	/**
	 * An estimate for how many items there are to iterate over. This is only used
	 * for reporting purposes during the computation.
	 */
	private long expected = -1;

	/**
	 * Default output stream for experiment
	 */
	private PrintStream out = System.err;

	public ParallelExperiment(Iterator<T> iterator) throws IOException {
		this.iterator = iterator;
	}

	/**
	 * Configure the batch size used for each thread.
	 *
	 * @param batchSize
	 * @return
	 */
	public ParallelExperiment<T> setBatchSize(int batchSize) {
		this.batchSize = batchSize;
		return this;
	}

	/**
	 * Set quiet reporting mode.
	 *
	 * @param quiet
	 * @return
	 */
	public ParallelExperiment<T> setQuiet(boolean quiet) {
		this.quiet = quiet;
		return this;
	}

	/**
	 * Configure the number of expected items to iterate over. This is only used for
	 * status reporting during the run.
	 *
	 * @param expected the expected size, or -1 if not known.
	 * @return
	 */
	public ParallelExperiment<T> setExpected(long expected) {
		this.expected = expected;
		return this;
	}

	/**
	 * Configure the number of distinct batches to be dispatched in a given
	 * iteration. Normally, this should not exceed the number of available threads.
	 * Potentially have a lower number than the available threads may be beneficial
	 * in some cases.
	 *
	 * @param nthreads
	 * @return
	 */
	public ParallelExperiment<T> setDispatchWidth(int nthreads) {
		this.nthreads = nthreads;
		return this;
	}

	@SuppressWarnings("unchecked")
	public <R> R run(R data, Function<T[],R> map, BiFunction<R,R,R> reduce, T... items) throws InterruptedException, ExecutionException {
		final long startTime = System.currentTimeMillis();
		long count = 0;
		// Construct temporary memory areas
		final Future<R>[] threads = new Future[nthreads];
		final T[][] arrays = (T[][]) new Object[nthreads][];
		// Initialise data arrays
		for(int i=0;i!=arrays.length;++i) {
			arrays[i] = Arrays.copyOf(items, batchSize);
		}
		//
		while(iterator.hasNext()) {
			// Create next batch
			for (int i = 0; i != nthreads; ++i) {
				count += copyToArray(arrays[i], iterator);
			}
			// Submit next batch for process
			for (int i = 0; i != nthreads; ++i) {
				final T[] batch = arrays[i];
				threads[i] = executor.submit(() -> map.apply(batch));
			}
			// Join all back together
			for (int i = 0; i != nthreads; ++i) {
				data = reduce.apply(data, threads[i].get());
			}
			// Report
			report(count, startTime);
		}
		//
		return data;
	}

	/**
	 * Report progress made after each iteration.
	 *
	 * @param count Number of items processed thus far
	 * @param start Start time for processing
	 */
	public void report(long count, long start) {
		if(!quiet) {
			long time = System.currentTimeMillis() - start;
			//
			if(expected < 0) {
				out.print("\r(" + count + ")");
			} else {
				double rate = ((double) time) / count;
				double remainingMS = (expected - count) * rate;
				long remainingS = ((long)remainingMS/1000) % 60;
				long remainingM = ((long)remainingMS/(60*1000)) % 60;
				long remainingH = ((long)remainingMS/(60*60*1000));
				long percent = (long) (100D * (count) / expected);
				String remaining = remainingH + "h " + remainingM + "m " + remainingS + "s";
				out.print("\r(" + percent +  "%, " + String.format("%.0f",(1000/rate)) +  "/s, remaining " + remaining + ")           ");
			}
		}
	}

	/**
	 * Copy a given number of items from an iterator into an array.
	 *
	 * @param <T>
	 * @param array
	 * @param b
	 * @return
	 */
	private static <T> long copyToArray(T[] array, Iterator<T> b) {
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

}
