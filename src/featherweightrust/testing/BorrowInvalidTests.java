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
package featherweightrust.testing;

import static org.junit.Assert.fail;

import java.io.IOException;
import java.io.StringReader;
import java.util.List;

import org.junit.*;

import featherweightrust.core.OperationalSemantics;
import featherweightrust.core.BorrowChecker;
import featherweightrust.core.Syntax.Lifetime;
import featherweightrust.core.Syntax.Stmt;
import featherweightrust.core.Syntax.Value;
import featherweightrust.io.Lexer;
import featherweightrust.io.Parser;
import featherweightrust.util.Pair;
import featherweightrust.util.SyntaxError;

public class BorrowInvalidTests {

	// ==============================================================
	// Straightforward Examples
	// ==============================================================

	@Test
	public void test_01() throws IOException {
		String input = "{ x = 123; }";
		checkInvalid(input);
	}

	@Test
	public void test_02() throws IOException {
		String input = "{ let mut x = 123; y = 123; }";
		checkInvalid(input);
	}

	@Test
	public void test_03() throws IOException {
		String input = "{ let mut x = 123; { let mut y = 1; } y = 123; }";
		checkInvalid(input);
	}


	// ==============================================================
	// Immutable Borrowing Examples
	// ==============================================================

	@Test
	public void test_20() throws IOException {
		String input = "{ let mut x = &x; }";
		checkInvalid(input);
	}

	@Test
	public void test_21() throws IOException {
		String input = "{ let mut x = &y; }";
		checkInvalid(input);
	}

	@Test
	public void test_22() throws IOException {
		String input = "{ let mut x = &x; x = 2; }";
		checkInvalid(input);
	}

	@Test
	public void test_23() throws IOException {
		String input = "{ let mut x = 1; let mut y = &x; { let mut z = 1; y = &z; } }";
		checkInvalid(input);
	}

	@Test
	public void test_24() throws IOException {
		String input = "{ let mut x = 1; let mut y = &x; let mut z = &mut y; { let mut w = 1; *z = &w; } }";
		checkInvalid(input);
	}

	@Test
	public void test_25() throws IOException {
		String input = "{ let mut x = 0; let mut y = &mut x; let mut z = &mut y; *z = z; }";
		checkInvalid(input);
	}

	// ==============================================================
	// Mutable Borrowing Examples
	// ==============================================================


	public static void checkInvalid(String input) throws IOException {
		Lifetime globalLifetime = new Lifetime();
		try {
			List<Lexer.Token> tokens = new Lexer(new StringReader(input)).scan();
			// Parse block
			Stmt.Block stmt = new Parser(input,tokens).parseStatementBlock(new Parser.Context(), globalLifetime);
			// Borrow Check block
			new BorrowChecker(input).apply(new BorrowChecker.Environment(), globalLifetime, stmt);
			//
			fail("test shouldn't have passed borrow checking");
		} catch (SyntaxError e) {
			// If we get here, then the borrow checker raised an exception
			e.outputSourceError(System.out);
		}
	}
}
