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
import featherweightrust.core.Syntax.Stmt;
import featherweightrust.core.Syntax.Value;
import featherweightrust.io.Lexer;
import featherweightrust.io.Parser;
import featherweightrust.util.Pair;
import featherweightrust.util.SyntaxError;

public class RuntimeValidTests {

	// ==============================================================
	// Straightforward Examples
	// ==============================================================

	@Test
	public void test_01() throws IOException {
		String input = "{ let mut x = 123; x }";
		check(input,123);
	}

	@Test
	public void test_02() throws IOException {
		String input = "{ let mut x = 123; let mut y = x; y}";
		check(input, 123);
	}

	@Test
	public void test_03() throws IOException {
		String input = "{ let mut x = 1; let mut y = 123; y}";
		check(input, 123);
	}

	@Test
	public void test_04() throws IOException {
		String input = "{ let mut x = 123; let mut y = 1; x}";
		check(input, 123);
	}

	@Test
	public void test_05() throws IOException {
		String input = "{ let mut x = 1; let mut y = 123; x = 2; y}";
		check(input, 123);
	}

	@Test
	public void test_06() throws IOException {
		String input = "{ let mut x = 1; { let mut y = 123; y } }";
		check(input, 123);
	}


	@Test
	public void test_07() throws IOException {
		String input = "{ let mut x = 1; { x = 123; } x }";
		check(input, 123);
	}

	// ==============================================================
	// Allocation Examples
	// ==============================================================

	@Test
	public void test_20() throws IOException {
		String input = "{ let mut x = box 123; *x }";
		check(input, 123);
	}

	@Test
	public void test_21() throws IOException {
		String input = "{ let mut x = *(box 123); x }";
		check(input,123);
	}

	@Test
	public void test_22() throws IOException {
		String input = "{ let mut x = **(box (box 123)); x }";
		check(input, 123);
	}

	@Test
	public void test_23() throws IOException {
		String input = "{ let mut x = box 1; *x = 123; *x }";
		check(input, 123);
	}

	@Test
	public void test_24() throws IOException {
		String input = "{ let mut x = box 123; let mut y = box 1; *y = 2; *x }";
		check(input, 123);
	}

	// ==============================================================
	// Immutable Borrowing Examples
	// ==============================================================

	@Test
	public void test_40() throws IOException {
		String input = "{ let mut x = 123; let mut x = &x; *x }";
		check(input, 123);
	}

	@Test
	public void test_41() throws IOException {
		String input = "{ let mut x = 123; let mut y = &x; *y }";
		check(input, 123);
	}

	@Test
	public void test_42() throws IOException {
		String input = "{ let mut x = 123; let mut y = &x; let mut z = *y; z }";
		check(input, 123);
	}

	@Test
	public void test_43() throws IOException {
		String input = "{ let mut x = 123; { let mut y = &x; *y } }";
		check(input, 123);
	}

	// ==============================================================
	// Mutable Borrowing Examples
	// ==============================================================

	@Test
	public void test_60() throws IOException {
		String input = "{ let mut x = 1; let mut y = &mut x; { let mut y = 123; let mut z = &y; *z } }";
		check(input, 123);
	}

	@Test
	public void test_61() throws IOException {
		String input = "{ let mut x = 1; let mut y = &mut x; { let mut x = 123; let mut z = &x; *z } }";
		check(input, 123);
	}

	@Test
	public void test_100() throws IOException {
		String input = "{ let mut x = 1; let mut y = &x; { let mut z = 1; y = &z; } }";
		check(input,null);
	}

	public static void check(String input, Integer output) throws IOException {
		try {
			List<Lexer.Token> tokens = new Lexer(new StringReader(input)).scan();
			// Parse block
			Stmt.Block stmt = new Parser(input,tokens).parseStatementBlock(new Parser.Context());
			// Borrow Check block
			new BorrowChecker(input).apply(new BorrowChecker.Environment(), "*", stmt);
			// Execute block in outermost lifetime "*")
			Pair<OperationalSemantics.State,Stmt> r = new OperationalSemantics().apply(new OperationalSemantics.State(), "*", stmt);
			//
			check(output,r.second());
			//
			System.out.println(r.first() + " :> " + r.second());
		} catch (SyntaxError e) {
			e.outputSourceError(System.err);
			e.printStackTrace();
			fail();
		}
	}

	public static void check(Integer expected, Stmt actual) {
		//
		//
		if(expected != null) {
			if(actual instanceof Value.Integer) {
				Value.Integer i = (Value.Integer) actual;
				if(i.value() == expected) {
					return;
				}
			}
		} else if(expected == null && actual == null) {
			return;
		}
		// Failed
		fail("expected: " + expected + ", got: " + actual);
	}
}
