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

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * A Syntactic Element represents any part of the file for which is relevant to
 * the syntactic structure of the file, and in particular parts we may wish to
 * add information too (e.g. line numbers, types, etc).
 *
 * @author David Pearce
 */
public interface SyntacticElement {

	/**
	 * Get the list of attributes associated with this syntactice element.
	 *
	 * @return
	 */
	public List<Attribute> attributes();

	/**
	 * Get the first attribute of the given class type. This is useful short-hand.
	 *
	 * @param c
	 * @return
	 */
	public <T extends Attribute> T attribute(Class<T> c);

	public class Impl implements SyntacticElement {

		private List<Attribute> attributes;

		public Impl() {
			// I use copy on write here, since for the most part I don't expect
			// attributes to change, and hence can be safely aliased. But, when they
			// do change I need fresh copies.
			attributes = new CopyOnWriteArrayList<>();
		}

		public Impl(Attribute x) {
			attributes = new ArrayList<>();
			attributes.add(x);
		}

		public Impl(Collection<Attribute> attributes) {
			this.attributes = new ArrayList<>(attributes);
		}

		public Impl(Attribute[] attributes) {
			this.attributes = new ArrayList<>(Arrays.asList(attributes));
		}

		@Override
		public List<Attribute> attributes() {
			return attributes;
		}

		@Override
		@SuppressWarnings("unchecked")
		public <T extends Attribute> T attribute(Class<T> c) {
			for (Attribute a : attributes) {
				if (c.isInstance(a)) {
					return (T) a;
				}
			}
			return null;
		}
	}

	/**
	 * Represents an attribute that can be associated with a syntactic element.
	 *
	 * @author djp
	 *
	 */
	public interface Attribute {

		public static class Source implements Attribute {

			public final int start;
			public final int end;

			public Source(int start, int end) {
				this.start = start;
				this.end = end;
			}

			@Override
			public String toString() {
				return "@" + start + ":" + end;
			}
		}
	}
}
