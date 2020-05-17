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