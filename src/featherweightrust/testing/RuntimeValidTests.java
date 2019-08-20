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
import org.junit.jupiter.api.Test;

import featherweightrust.core.OperationalSemantics;
import featherweightrust.core.BorrowChecker;
import featherweightrust.core.Syntax.Lifetime;
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
		String input = "{ let mut x = 123; let mut y = !x; y}";
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
		String input = "{ let mut x = 1; let mut y = 123; x = 2; !y}";
		check(input, 123);
	}

	@Test
	public void test_06() throws IOException {
		String input = "{ let mut x = 1; { let mut y = 123; !y } }";
		check(input, 123);
	}


	@Test
	public void test_07() throws IOException {
		String input = "{ let mut x = 1; { x = 123; } x }";
		check(input, 123);
	}

	@Test
	public void test_08() throws IOException {
		// Fails incorrectly because move semantics should retain the "shadow" of x.
		String input = "{ let mut x = box 0; { let mut y = x; x = box 1; } *x }";
		check(input, 1);
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
		String input = "{ let mut y = box 123; let mut x = *y; !x }";
		check(input,123);
	}

	@Test
	public void test_22() throws IOException {
		String input = "{ let mut y = 123; let mut z = box &y; let mut x = *z; *x }";
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
		String input = "{ let mut x = 123; let mut y = &x; *y }";
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
		String input = "{ let mut x = 1; let mut y = &mut x; { let mut w = 123; let mut z = &w; *z } }";
		check(input, 123);
	}

	@Test
	public void test_61() throws IOException {
		String input = "{ let mut x = 1; let mut y = &mut x; { let mut w = 123; let mut z = &w; *z } }";
		check(input, 123);
	}


	public static void check(String input, Integer output) throws IOException {
		check(input,output,BIG_STEP);
		check(input,output,SMALL_STEP);
	}

	public static void check(String input, Integer output, OperationalSemantics semantics) throws IOException {
		Lifetime globalLifetime = new Lifetime();
		try {
			List<Lexer.Token> tokens = new Lexer(new StringReader(input)).scan();
			// Parse block
			Stmt.Block stmt = new Parser(input, tokens).parseStatementBlock(new Parser.Context(), globalLifetime);
			// Borrow Check block
			new BorrowChecker(input).apply(new BorrowChecker.Environment(), globalLifetime, stmt);
			// Execute block in outermost lifetime "*")
			Pair<OperationalSemantics.State, Stmt> state = new Pair<>(new OperationalSemantics.State(),stmt);
			// Execute continually until all reductions complete
			Stmt result;
			do {
				state = semantics.apply(state.first(), globalLifetime, state.second());
				result = state.second();
			} while (result != null && !(result instanceof Value));
			//
			check(output, state.second());
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

	public static final OperationalSemantics BIG_STEP = new OperationalSemantics.BigStep();
	public static final OperationalSemantics SMALL_STEP = new OperationalSemantics.SmallStep();
}
