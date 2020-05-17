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
import featherweightrust.core.Syntax.Term;
import featherweightrust.core.Syntax.Value;
import featherweightrust.io.Lexer;
import featherweightrust.io.Parser;
import featherweightrust.util.AbstractMachine.State;
import featherweightrust.util.Pair;
import featherweightrust.util.SyntaxError;

/**
 * Runtime test cases for the core syntax. Each test should pass borrow checking
 * and execute without raising a fault.
 *
 * @author David J. Pearce
 *
 */
public class CoreRuntimeTests {
	private static Value.Integer One = new Value.Integer(1);
	private static Value.Integer OneTwoThree = new Value.Integer(123);

	// ==============================================================
	// Straightforward Examples
	// ==============================================================

	@Test
	public void test_01() throws IOException {
		String input = "{ let mut x = 123; x }";
		check(input,OneTwoThree);
	}

	@Test
	public void test_02() throws IOException {
		String input = "{ let mut x = 123; let mut y = x; y}";
		check(input, OneTwoThree);
	}

	@Test
	public void test_03() throws IOException {
		String input = "{ let mut x = 1; let mut y = 123; y}";
		check(input, OneTwoThree);
	}

	@Test
	public void test_04() throws IOException {
		String input = "{ let mut x = 123; let mut y = 1; x}";
		check(input, OneTwoThree);
	}

	@Test
	public void test_05() throws IOException {
		String input = "{ let mut x = 1; let mut y = 123; x = 2; y}";
		check(input, OneTwoThree);
	}

	@Test
	public void test_06() throws IOException {
		String input = "{ let mut x = 1; { let mut y = 123; y } }";
		check(input, OneTwoThree);
	}


	@Test
	public void test_07() throws IOException {
		String input = "{ let mut x = 1; { x = 123; } x }";
		check(input, OneTwoThree);
	}

	@Test
	public void test_08() throws IOException {
		// Fails incorrectly because move semantics should retain the "shadow" of x.
		String input = "{ let mut x = box 0; { let mut y = x; x = box 1; } *x }";
		check(input, One);
	}

	@Test
	public void test_09() throws IOException {
		// Fails incorrectly because move semantics should retain the "shadow" of x.
		String input = "{ let mut x = box 0; x = box 1; *x }";
		check(input, One);
	}

	// ==============================================================
	// Allocation Examples
	// ==============================================================

	@Test
	public void test_20() throws IOException {
		String input = "{ let mut x = box 123; *x }";
		check(input, OneTwoThree);
	}

	@Test
	public void test_21() throws IOException {
		String input = "{ let mut y = box 123; let mut x = *y; x }";
		check(input,OneTwoThree);
	}

	@Test
	public void test_22() throws IOException {
		String input = "{ let mut y = 123; let mut z = box &y; let mut x = *z; *x }";
		check(input, OneTwoThree);
	}

	@Test
	public void test_23() throws IOException {
		String input = "{ let mut x = box 1; *x = 123; *x }";
		check(input, OneTwoThree);
	}

	@Test
	public void test_24() throws IOException {
		String input = "{ let mut x = box 123; let mut y = box 1; *y = 2; *x }";
		check(input, OneTwoThree);
	}


	@Test
	public void test_25() throws IOException {
		// Moved out of box
		String input = "{ let mut x = 123; let mut y = box box x; let mut z = *y; *z }";
		check(input, OneTwoThree);
	}


	@Test
	public void test_26() throws IOException {
		// Moved out of box
		String input = "{ let mut x = box box 1; **x = 123; **x }";
		check(input,OneTwoThree);
	}

	@Test
	public void test_27() throws IOException {
		String input = "{ let mut x = box 1; { let mut y = box 123; x = y; } *x }";
		check(input,OneTwoThree);
	}

	@Test
	public void test_28() throws IOException {
		String input = "{ let mut x = box box 123; let mut y = *x; *y }";
		check(input,OneTwoThree);
	}

	// ==============================================================
	// Immutable Borrowing Examples
	// ==============================================================

	@Test
	public void test_40() throws IOException {
		String input = "{ let mut x = 123; let mut y = &x; *y }";
		check(input, OneTwoThree);
	}

	@Test
	public void test_41() throws IOException {
		String input = "{ let mut x = 123; let mut y = &x; *y }";
		check(input, OneTwoThree);
	}

	@Test
	public void test_42() throws IOException {
		String input = "{ let mut x = 123; let mut y = &x; let mut z = *y; z }";
		check(input, OneTwoThree);
	}

	@Test
	public void test_43() throws IOException {
		String input = "{ let mut x = 123; { let mut y = &x; *y } }";
		check(input, OneTwoThree);
	}

	// ==============================================================
	// Mutable Borrowing Examples
	// ==============================================================

	@Test
	public void test_60() throws IOException {
		String input = "{ let mut x = 1; let mut y = &mut x; { let mut w = 123; let mut z = &w; *z } }";
		check(input, OneTwoThree);
	}

	@Test
	public void test_61() throws IOException {
		String input = "{ let mut x = 1; let mut y = &mut x; { let mut w = 123; let mut z = &w; *z } }";
		check(input, OneTwoThree);
	}

	@Test
	public void test_62() throws IOException {
		// FIXME: support moving out of boxes
		String input = "{ let mut x = 123; let mut y = box &mut x; let mut z = *y; *z }";
		check(input, OneTwoThree);
	}

	@Test
	public void test_63() throws IOException {
		// NOTE: this test case appears to be something of an issue. It conflicts with
		// core invalid #59 and the issue of strong updaes for boxes.
		String input = "{ let mut x = 123; let mut y = box &mut x; let mut z = *y; *y = z; let mut w = *y; *w }";
		check(input, OneTwoThree);
	}

	@Test
	public void test_64() throws IOException {
		// Moved out of box
		String input = "{ let mut x = 123; let mut y = &x; let mut z = &mut y; **z }";
		check(input,OneTwoThree);
	}

	@Test
	public void test_65() throws IOException {
		// Moved out of box
		String input = "{ let mut x1 = 1; let mut x2 = 123; let mut y = &x1; let mut z = &mut y; *z = &x2; **z }";
		check(input,OneTwoThree);
	}

	@Test
	public void test_66() throws IOException {
		// Moved out of box
		String input = "{ let mut x = 1; let mut y = &mut x; let mut z = &mut y; **z = 123; **z }";
		check(input,OneTwoThree);
	}

	@Test
	public void test_67() throws IOException {
		// Moved out of box
		String input = "{ let mut x = 1; let mut y = box &mut x; **y = 123; **y }";
		check(input,OneTwoThree);
	}

	@Test
	public void test_68() throws IOException {
		// Moved out of box
		String input = "{ let mut x = box 1; let mut y = &mut x; **y = 123; **y }";
		check(input,OneTwoThree);
	}

	// ==============================================================
	// Reborrowing Examples
	// ==============================================================

	@Test
	public void test_70() throws IOException {
		String input = "{ let mut x = 1; let mut y = &mut x; { let mut z = &mut *y; *z = 123; } *y }";
		check(input,OneTwoThree);
	}

	@Test
	public void test_71() throws IOException {
		String input = "{ let mut x = 1; let mut y = &mut x; let mut z = &*y; *y }";
		check(input,One);
	}

	@Test
	public void test_72() throws IOException {
		String input = "{ let mut x = 1; let mut y = &mut x; let mut z = &*y; *z }";
		check(input,One);
	}

	@Test
	public void test_73() throws IOException {
		String input = "{ let mut x = 1; let mut y = &mut x; let mut z = &mut *y; let mut w = &mut *z; *w }";
		check(input,One);
	}

	@Test
	public void test_74() throws IOException {
		String input = "{ let mut x = 1; let mut y = box x; { let mut z = &mut *y; *z = 123; } *y }";
		check(input,OneTwoThree);
	}

	@Test
	public void test_75() throws IOException {
		String input = "{ let mut x = box 1; { let mut y = &mut *x; *y = 123; } *x }";
		check(input,OneTwoThree);
	}

	// ==============================================================
	// Helpers
	// ==============================================================

	public static void check(String input, Value output) throws IOException {
		check(input, output, SEMANTICS, new BorrowChecker(input));
	}

	public static void check(String input, Value output, OperationalSemantics semantics, BorrowChecker typing) throws IOException {
		// Allocate the global lifetime. This is the lifetime where all heap allocated
		// data will reside.
		Lifetime globalLifetime = new Lifetime();
		//
		try {
			List<Lexer.Token> tokens = new Lexer(new StringReader(input)).scan();
			// Parse block
			Term.Block stmt = new Parser(input, tokens).parseStatementBlock(new Parser.Context(), globalLifetime);
			// Borrow Check block
			typing.apply(new BorrowChecker.Environment(), globalLifetime, stmt);
			// Execute block in outermost lifetime "*")
			Pair<State, Term> state = new Pair<>(new State(),stmt);
			// Execute continually until all reductions complete
			Term result;
			do {
				state = semantics.apply(state.first(), globalLifetime, state.second());
				result = state.second();
			} while (result != null && !(result instanceof Value));
			//
			check(output, (Value) result);
		} catch (SyntaxError e) {
			e.outputSourceError(System.err);
			e.printStackTrace();
			fail();
		}
	}

	public static void check(Value expected, Value actual) {
		if(!expected.equals(actual)) {
			// Failed
			fail("expected: " + expected + ", got: " + actual);
		}
	}

	public static final OperationalSemantics SEMANTICS = new OperationalSemantics();
}
