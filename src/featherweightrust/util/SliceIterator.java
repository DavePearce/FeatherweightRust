package featherweightrust.util;

import java.util.Iterator;

/**
 * A simple iterator which slices a given range out of an existing iterator. For
 * example, we can iterate only the 1st -- 10th elements from the original
 * iterator.
 *
 * @author David J. Pearce
 *
 * @param <T>
 */
public final class SliceIterator<T> implements Iterator<T> {
	private final Iterator<T> iter;
	private long count;

	public SliceIterator(Iterator<T> iter, long start, long end) {
		this.count = (end - start);
		// Skip forward
		while (start > 0) {
			iter.next();
			start = start - 1;
		}
		this.iter = iter;
	}

	@Override
	public boolean hasNext() {
		return count > 0 && iter.hasNext();
	}

	@Override
	public T next() {
		count = count - 1;
		return iter.next();
	}

}