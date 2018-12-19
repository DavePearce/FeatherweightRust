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
package featherweightrust.io;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

import featherweightrust.util.SyntaxError;

/**
 * Responsible for turning a stream of characters into a sequence of tokens.
 *
 * @author Daivd J. Pearce
 *
 */
public class Lexer {

	private String filename;
	private StringBuffer input;
	private int pos;

	public Lexer(String filename) throws IOException {
		this(new InputStreamReader(new FileInputStream(filename), "UTF8"));
		this.filename = filename;
	}

	public Lexer(InputStream instream) throws IOException {
		this(new InputStreamReader(instream, "UTF8"));
	}

	public Lexer(Reader reader) throws IOException {
		BufferedReader in = new BufferedReader(reader);

		StringBuffer text = new StringBuffer();
		String tmp;
		while ((tmp = in.readLine()) != null) {
			text.append(tmp);
			text.append("\n");
		}

		input = text;
	}

	/**
	 * Scan all characters from the input stream and generate a corresponding
	 * list of tokens, whilst discarding all whitespace and comments.
	 *
	 * @return
	 */
	public List<Token> scan() {
		ArrayList<Token> tokens = new ArrayList<>();
		pos = 0;

		while (pos < input.length()) {
			char c = input.charAt(pos);
			if (Character.isDigit(c)) {
				tokens.add(scanNumericConstant());
			} else if (c == '/' && (pos + 1) < input.length() && input.charAt(pos + 1) == '/') {
				scanLineComment();
			} else if (c == '/' && (pos + 1) < input.length() && input.charAt(pos + 1) == '*') {
				scanBlockComment();
			} else if (isOperatorStart(c)) {
				tokens.add(scanOperator());
			} else if (Character.isJavaIdentifierStart(c)) {
				tokens.add(scanIdentifier());
			} else if (Character.isWhitespace(c)) {
				skipWhitespace();
			} else {
				syntaxError("syntax error");
			}
		}

		return tokens;
	}

	/**
	 * Scan a numeric constant. That is a sequence of digits which gives an
	 * integer constant.
	 *
	 * @return
	 */
	public Token scanNumericConstant() {
		int start = pos;
		while (pos < input.length() && Character.isDigit(input.charAt(pos))) {
			pos = pos + 1;
		}
		int r = new BigInteger(input.substring(start, pos)).intValue();
		return new Int(r, input.substring(start, pos), start);
	}

	static final char[] opStarts = { '&', ',', '(', ')', '{', '}', '*', '=', ';' };

	public boolean isOperatorStart(char c) {
		for (char o : opStarts) {
			if (c == o) {
				return true;
			}
		}
		return false;
	}

	public Token scanOperator() {
		char c = input.charAt(pos);

		if (c == '&') {
			return new Ampersand(pos++);
		} else if (c == ',') {
			return new Comma(pos++);
		} else if (c == ';') {
			return new SemiColon(pos++);
		} else if (c == '(') {
			return new LeftBrace(pos++);
		} else if (c == ')') {
			return new RightBrace(pos++);
		} else if (c == '{') {
			return new LeftCurly(pos++);
		} else if (c == '}') {
			return new RightCurly(pos++);
		} else if (c == '=') {
			return new Equals(pos++);
		} if (c == '*') {
			return new Star(pos++);
		}

		syntaxError("unknown operator encountered: " + c);
		return null;
	}

	public static final String[] keywords = {  "int", "let", "mut", "box" };

	public Token scanIdentifier() {
		int start = pos;
		while (pos < input.length()
				&& Character.isJavaIdentifierPart(input.charAt(pos))) {
			pos++;
		}
		String text = input.substring(start, pos);

		// now, check for keywords
		for (String keyword : keywords) {
			if (keyword.equals(text)) {
				return new Keyword(text, start);
			}
		}

		// otherwise, must be identifier
		return new Identifier(text, start);
	}

	public void scanLineComment() {
		while (pos < input.length() && input.charAt(pos) != '\n') {
			pos++;
		}
	}

	public void scanBlockComment() {
		while((pos+1) < input.length() && (input.charAt(pos) != '*' || input.charAt(pos+1) != '/')) {
			pos++;
		}
		pos++;
		pos++;

	}

	/**
	 * Skip over any whitespace at the current index position in the input
	 * string.
	 *
	 * @param tokens
	 */
	public void skipWhitespace() {
		while (pos < input.length()
				&& Character.isWhitespace(input.charAt(pos))) {
			pos++;
		}
	}

	/**
	 * Raise a syntax error with a given message at the current index.
	 *
	 * @param msg
	 * @param index
	 */
	private void syntaxError(String msg) {
		throw new SyntaxError(msg, filename, pos, pos);
	}

	/**
	 * The base class for all tokens.
	 *
	 * @author David J. Pearce
	 *
	 */
	public static abstract class Token {

		public final String text;
		public final int start;

		public Token(String text, int pos) {
			this.text = text;
			this.start = pos;
		}

		public int end() {
			return start + text.length() - 1;
		}
	}

	/**
	 * Represents an integer constant. That is, a sequence of 1 or more digits.
	 *
	 * @author David J. Pearce
	 *
	 */
	public static class Int extends Token {

		public final int value;

		public Int(int r, String text, int pos) {
			super(text, pos);
			value = r;
		}
	}

	/**
	 * Represents a variable or function name. That is, a alphabetic character
	 * (or '_'), followed by a sequence of zero or more alpha-numeric
	 * characters.
	 *
	 * @author David J. Pearce
	 *
	 */
	public static class Identifier extends Token {

		public Identifier(String text, int pos) {
			super(text, pos);
		}
	}

	/**
	 * Represents a known keyword. In essence, a keyword is a sequence of one or
	 * more alphabetic characters which is defined in advance.
	 *
	 * @author David J. Pearce
	 *
	 */
	public static class Keyword extends Token {

		public Keyword(String text, int pos) {
			super(text, pos);
		}
	}

	public static class Ampersand extends Token {
		public Ampersand(int pos) {
			super("&", pos);
		}
	}

	public static class Comma extends Token {
		public Comma(int pos) {
			super(",", pos);
		}
	}

	public static class SemiColon extends Token {
		public SemiColon(int pos) {
			super(";", pos);
		}
	}

	public static class LeftBrace extends Token {

		public LeftBrace(int pos) {
			super("(", pos);
		}
	}

	public static class RightBrace extends Token {

		public RightBrace(int pos) {
			super(")", pos);
		}
	}

	public static class LeftCurly extends Token {

		public LeftCurly(int pos) {
			super("{", pos);
		}
	}

	public static class RightCurly extends Token {

		public RightCurly(int pos) {
			super("}", pos);
		}
	}

	public static class Equals extends Token {

		public Equals(int pos) {
			super("=", pos);
		}
	}

	public static class Star extends Token {
		public Star(int pos) {
			super("*", pos);
		}
	}
}
