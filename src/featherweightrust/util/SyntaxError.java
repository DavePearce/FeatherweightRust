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

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;

import featherweightrust.util.SyntacticElement.Attribute;

/**
 * This exception is thrown when a syntax error occurs in the parser.
 *
 * @author David Pearce
 */
public class SyntaxError extends RuntimeException {

	private String msg;
	private String src;
	private int start;
	private int end;

	/**
	 * Identify a syntax error at a particular point in a file.
	 *
	 * @param msg
	 *            Message detailing the problem.
	 * @param src
	 *            The program source this error is referring to.
	 * @param line
	 *            Line number within file containing problem.
	 * @param column
	 *            Column within line of file containing problem.
	 */
	public SyntaxError(String msg, String src, int start, int end) {
		this.msg = msg;
		this.src = src;
		this.start = start;
		this.end = end;
	}

	/**
	 * Identify a syntax error at a particular point in a file.
	 *
	 * @param msg
	 *            Message detailing the problem.
	 * @param filename
	 *            The source file that this error is referring to.
	 * @param line
	 *            Line number within file containing problem.
	 * @param column
	 *            Column within line of file containing problem.
	 */
	public SyntaxError(String msg, String filename, int start, int end, Throwable ex) {
		super(ex);
		this.msg = msg;
		this.src = filename;
		this.start = start;
		this.end = end;
	}

	@Override
	public String getMessage() {
		if (msg != null) {
			return msg;
		} else {
			return "";
		}
	}

	/**
	 * Error message
	 *
	 * @return
	 */
	public String msg() {
		return msg;
	}

	/**
	 * Filename for file where the error arose.
	 *
	 * @return
	 */
	public String filename() {
		return src;
	}

	/**
	 * Get index of first character of offending location.
	 *
	 * @return
	 */
	public int start() {
		return start;
	}

	/**
	 * Get index of last character of offending location.
	 *
	 * @return
	 */
	public int end() {
		return end;
	}

	/**
	 * Output the syntax error to a given output stream.
	 */
	public void outputSourceError(PrintStream output) {
		if (src == null) {
			output.println("syntax error: " + getMessage());
		} else {
			int line = 0;
			int lineStart = 0;
			int lineEnd = 0;

			while (lineEnd < src.length() && lineEnd <= start) {
				lineStart = lineEnd;
				lineEnd = parseLine(src, lineEnd);
				line = line + 1;
			}
			lineEnd = Math.min(lineEnd, src.length());

			output.println("line " + line + ": " + getMessage());
			// NOTE: in the following lines I don't print characters
			// individually. The reason for this is that it messes up the ANT
			// task output.
			String str = "";
			for (int i = lineStart; i < lineEnd; ++i) {
				str = str + src.charAt(i);
			}
			if (str.length() > 0 && str.charAt(str.length() - 1) == '\n') {
				output.print(str);
			} else {
				// this must be the very last line of output and, in this
				// particular case, there is no new-line character provided.
				// Therefore, we need to provide one ourselves!
				output.println(str);
			}
			str = "";
			for (int i = lineStart; i < start; ++i) {
				if (src.charAt(i) == '\t') {
					str += "\t";
				} else {
					str += " ";
				}
			}
			for (int i = start; i <= end; ++i) {
				str += "^";
			}
			output.println(str);
		}
	}

	private static int parseLine(String text, int index) {
		while (index < text.length() && text.charAt(index) != '\n') {
			index++;
		}
		return index + 1;
	}

	public static final long serialVersionUID = 1l;

	public static void syntaxError(String msg, String filename, SyntacticElement elem) {
		int start = -1;
		int end = -1;

		Attribute.Source attr = (Attribute.Source) elem.attribute(Attribute.Source.class);
		if (attr != null) {
			start = attr.start;
			end = attr.end;
		}

		throw new SyntaxError(msg, filename, start, end);
	}

	public static void syntaxError(String msg, String filename, SyntacticElement elem, Throwable ex) {
		int start = -1;
		int end = -1;

		Attribute.Source attr = (Attribute.Source) elem.attribute(Attribute.Source.class);
		if (attr != null) {
			start = attr.start;
			end = attr.end;
		}

		throw new SyntaxError(msg, filename, start, end, ex);
	}

	/**
	 * An internal failure is a special form of syntax error which indicates
	 * something went wrong whilst processing some piece of syntax. In other words,
	 * is an internal error in the compiler, rather than a mistake in the input
	 * program.
	 *
	 * @author David J. Pearce
	 *
	 */
	public static class InternalFailure extends SyntaxError {
		public InternalFailure(String msg, String filename, int start, int end) {
			super(msg, filename, start, end);
		}

		public InternalFailure(String msg, String filename, int start, int end, Throwable ex) {
			super(msg, filename, start, end, ex);
		}

		@Override
		public String getMessage() {
			String msg = super.getMessage();
			if (msg == null || msg.equals("")) {
				return "internal failure";
			} else {
				return "internal failure, " + msg;
			}
		}
	}

	public static void internalFailure(String msg, String filename, SyntacticElement elem) {
		int start = -1;
		int end = -1;

		Attribute.Source attr = (Attribute.Source) elem.attribute(Attribute.Source.class);
		if (attr != null) {
			start = attr.start;
			end = attr.end;
		}

		throw new InternalFailure(msg, filename, start, end);
	}

	public static void internalFailure(String msg, String filename, SyntacticElement elem, Throwable ex) {
		int start = -1;
		int end = -1;

		Attribute.Source attr = (Attribute.Source) elem.attribute(Attribute.Source.class);
		if (attr != null) {
			start = attr.start;
			end = attr.end;
		}

		throw new InternalFailure(msg, filename, start, end, ex);
	}
}
