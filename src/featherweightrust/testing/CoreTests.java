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
import org.junit.Test;

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
public class CoreTests {
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


	@Test
	public void test_20() throws IOException {
		String input = "{ let mut x = 123; let mut y = &mut x; x = 1; }";
		checkInvalid(input);
	}

	@Test
	public void test_21() throws IOException {
		String input = "{ x = 123; }";
		checkInvalid(input);
	}

	@Test
	public void test_22() throws IOException {
		String input = "{ let mut x = 123; y = 123; }";
		checkInvalid(input);
	}

	@Test
	public void test_23() throws IOException {
		String input = "{ let mut x = 123; { let mut y = 1; } y = 123; }";
		checkInvalid(input);
	}

	@Test
	public void test_24() throws IOException {
		String input = "{ let mut x = box 1; let mut y = x; *x = 123; }";
		checkInvalid(input);
	}


	// ==============================================================
	// Allocation Examples
	// ==============================================================

	@Test
	public void test_40() throws IOException {
		String input = "{ let mut x = box 123; *x }";
		check(input, OneTwoThree);
	}

	@Test
	public void test_41() throws IOException {
		String input = "{ let mut y = box 123; let mut x = *y; x }";
		check(input,OneTwoThree);
	}

	@Test
	public void test_42() throws IOException {
		String input = "{ let mut y = 123; let mut z = box &y; let mut x = *z; *x }";
		check(input, OneTwoThree);
	}

	@Test
	public void test_43() throws IOException {
		String input = "{ let mut x = box 1; *x = 123; *x }";
		check(input, OneTwoThree);
	}

	@Test
	public void test_44() throws IOException {
		String input = "{ let mut x = box 123; let mut y = box 1; *y = 2; *x }";
		check(input, OneTwoThree);
	}


	@Test
	public void test_45() throws IOException {
		// Moved out of box
		String input = "{ let mut x = 123; let mut y = box box x; let mut z = *y; *z }";
		check(input, OneTwoThree);
	}

	@Test
	public void test_46() throws IOException {
		// Moved out of box
		String input = "{ let mut x = box box 1; **x = 123; **x }";
		check(input,OneTwoThree);
	}

	@Test
	public void test_47() throws IOException {
		String input = "{ let mut x = box 1; { let mut y = box 123; x = y; } *x }";
		check(input,OneTwoThree);
	}

	@Test
	public void test_48() throws IOException {
		String input = "{ let mut x = box box 123; let mut y = *x; *y }";
		check(input,OneTwoThree);
	}

	// ==============================================================
	// Immutable Borrowing Examples
	// ==============================================================

	@Test
	public void test_50() throws IOException {
		String input = "{ let mut x = 123; let mut y = &x; *y }";
		check(input, OneTwoThree);
	}

	@Test
	public void test_51() throws IOException {
		String input = "{ let mut x = 123; let mut y = &x; *y }";
		check(input, OneTwoThree);
	}

	@Test
	public void test_52() throws IOException {
		String input = "{ let mut x = 123; let mut y = &x; let mut z = *y; z }";
		check(input, OneTwoThree);
	}

	@Test
	public void test_53() throws IOException {
		String input = "{ let mut x = 123; { let mut y = &x; *y } }";
		check(input, OneTwoThree);
	}


	@Test
	public void test_60() throws IOException {
		String input = "{ let mut x = &x; }";
		checkInvalid(input);
	}

	@Test
	public void test_61() throws IOException {
		String input = "{ let mut x = &y; }";
		checkInvalid(input);
	}

	@Test
	public void test_62() throws IOException {
		String input = "{ let mut x = &x; x = 2; }";
		checkInvalid(input);
	}

	@Test
	public void test_63() throws IOException {
		String input = "{ let mut x = 1; let mut y = &x; { let mut z = 1; y = &z; } }";
		checkInvalid(input);
	}

	@Test
	public void test_64() throws IOException {
		String input = "{ let mut x = 123; let mut y = box 0; { let mut z = &x; z = &y; } }";
		checkInvalid(input);
	}

	@Test
	public void test_65() throws IOException {
		String input = "{ let mut x = 123; let mut y = &x; let mut z = &mut x; }";
		checkInvalid(input);
	}


	// ==============================================================
	// Mutable Borrowing Examples
	// ==============================================================

	@Test
	public void test_80() throws IOException {
		String input = "{ let mut x = 1; let mut y = &mut x; { let mut w = 123; let mut z = &w; *z } }";
		check(input, OneTwoThree);
	}

	@Test
	public void test_81() throws IOException {
		String input = "{ let mut x = 1; let mut y = &mut x; { let mut w = 123; let mut z = &w; *z } }";
		check(input, OneTwoThree);
	}

	@Test
	public void test_82() throws IOException {
		// FIXME: support moving out of boxes
		String input = "{ let mut x = 123; let mut y = box &mut x; let mut z = *y; *z }";
		check(input, OneTwoThree);
	}

	@Test
	public void test_83() throws IOException {
		// NOTE: this test case appears to be something of an issue. It conflicts with
		// core invalid #59 and the issue of strong updaes for boxes.
		String input = "{ let mut x = 123; let mut y = box &mut x; let mut z = *y; *y = z; let mut w = *y; *w }";
		check(input, OneTwoThree);
	}

	@Test
	public void test_84() throws IOException {
		// Moved out of box
		String input = "{ let mut x = 123; let mut y = &x; let mut z = &mut y; **z }";
		check(input,OneTwoThree);
	}

	@Test
	public void test_85() throws IOException {
		// Moved out of box
		String input = "{ let mut x1 = 1; let mut x2 = 123; let mut y = &x1; let mut z = &mut y; *z = &x2; **z }";
		check(input,OneTwoThree);
	}

	@Test
	public void test_86() throws IOException {
		// Moved out of box
		String input = "{ let mut x = 1; let mut y = &mut x; let mut z = &mut y; **z = 123; **z }";
		check(input,OneTwoThree);
	}

	@Test
	public void test_87() throws IOException {
		// Moved out of box
		String input = "{ let mut x = 1; let mut y = box &mut x; **y = 123; **y }";
		check(input,OneTwoThree);
	}

	@Test
	public void test_88() throws IOException {
		// Moved out of box
		String input = "{ let mut x = box 1; let mut y = &mut x; **y = 123; **y }";
		check(input,OneTwoThree);
	}


	@Test
	public void test_100() throws IOException {
		String input = "{ let mut x = 1; let mut y = &x; let mut z = &mut y; { let mut w = 1; *z = &w; } }";
		checkInvalid(input);
	}

	@Test
	public void test_101() throws IOException {
		String input = "{ let mut x = 0; let mut y = &mut x; let mut z = &mut y; *z = z; }";
		checkInvalid(input);
	}

	@Test
	public void test_102() throws IOException {
		String input = "{ let mut x = 1; let mut y = &mut x; { let mut x = 123; let mut z = &x; *z } }";
		checkInvalid(input);
	}

	@Test
	public void test_103() throws IOException {
		String input = "{ let mut x = 1; let mut y = &mut x; { let mut w = 123; let mut z = &x; *z } }";
		checkInvalid(input);
	}

	@Test
	public void test_104() throws IOException {
		//  cannot assign to `y` because it is borrowed
		String input = "{ let mut x = 0; { let mut y = &mut x; y = &mut y; } }";
		checkInvalid(input);
	}

	@Test
	public void test_105() throws IOException {
		// mismatched types (types differ in mutability)
		String input = "{ let mut x = 0; { let mut y = &mut x; y = &y; } }";
		checkInvalid(input);
	}

	@Test
	public void test_106() throws IOException {
		// [E0506]: cannot assign to `*x` because it is borrowed
		String input = "{ let mut x = box 0; { let mut y = &mut x; *x = 0; } }";
		checkInvalid(input);
	}

	@Test
	public void test_107() throws IOException {
		// [E0503]: cannot use `x` because it was mutably borrowed
		String input = "{ let mut x = 0; { let mut y = &mut x; *y = x; } }";
		checkInvalid(input);
	}


	@Test
	public void test_120() throws IOException {
		String input = "{ let mut x = box 0; let mut y = x; let mut z = x; }";
		checkInvalid(input);
	}

	@Test
	public void test_121() throws IOException {
		String input = "{ let mut x = box 0; let mut y = x; let mut z = *x; }";
		checkInvalid(input);
	}

	@Test
	public void test_123() throws IOException {
		String input = "{ let mut x = box 0; let mut y = x; let mut z = &x; }";
		checkInvalid(input);
	}

	@Test
	public void test_124() throws IOException {
		String input = "{ let mut x = box 0; let mut y = x; let mut z = box x; }";
		checkInvalid(input);
	}

	@Test
	public void test_125() throws IOException {
		String input = "{ let mut x = box 0; let mut y = x; *y = *x; }";
		checkInvalid(input);
	}

	@Test
	public void test_126() throws IOException {
		// [E0503]: cannot use `*x` because it was mutably borrowed
		String input = "{ let mut x = box 0; { let mut y = &mut x; *y = box *x; } }";
		checkInvalid(input);
	}

	@Test
	public void test_127() throws IOException {
		// [E0503]: cannot assign to `x` because it was mutably borrowed
		String input = "{ let mut x = 0; { let mut y = box &mut x; x = x; } }";
		checkInvalid(input);
	}

	@Test
	public void test_128() throws IOException {
		// [E0506]: cannot assign to `*y` because it is borrowed
		String input = "{ let mut x = 0; { let mut y = box &mut x; *y = &mut y; } }";
		checkInvalid(input);
	}

	@Test
	public void test_129() throws IOException {
		// Fails because no support for strong updates.
		String input = "{ let mut x = 1; let mut y = 1; let mut z = &mut x; let mut p = &mut z; *p = &mut y; x}";
		checkInvalid(input);
	}

	@Test
	public void test_130() throws IOException {
		// Fails because no support for strong updates.
		String input = "{ let mut x = 1; let mut y = 1; let mut p = box &mut x; *p = &mut y; x}";
		checkInvalid(input);
	}

	@Test
	public void test_131() throws IOException {
		// Moved out of box
		String input = "{ let mut x = 123; let mut y = box &mut x; let mut z = *y; *y }";
		checkInvalid(input);
	}

	@Test
	public void test_132() throws IOException {
		// Moved out of box
		String input = "{ let mut x = 123; let mut y = box box x; let mut z = *y; *y }";
		checkInvalid(input);
	}

	@Test
	public void test_133() throws IOException {
		// Moved out of box
		String input = "{ let mut x = 123; let mut y = box &mut x; let mut z = *y; let mut w = *y; }";
		checkInvalid(input);
	}

	@Test
	public void test_134() throws IOException {
		// Moved out of box
		String input = "{ let mut x = 123; let mut y = box box x; let mut z = *y; let mut w = *y; }";
		checkInvalid(input);
	}

	@Test
	public void test_135() throws IOException {
		// Moved out of box
		String input = "{" +
				"let mut x = 123;" +
				"let mut y = 234;" +
				"let mut bx = box &mut x;" +
				"let mut p = *bx;" +
				"let mut q = bx; " +
				"}";
		checkInvalid(input);
	}

	@Test
	public void test_136() throws IOException {
		// Moved out of box
		String input = "{" +
				"let mut x = 123;" +
				"let mut y = 234;" +
				"let mut bx = box box x;" +
				"let mut p = *bx;" +
				"let mut q = bx; " +
				"}";
		checkInvalid(input);
	}

	@Test
	public void test_137() throws IOException {
		// Moved out of box
		String input = "{" +
				"let mut x = 123;" +
				"let mut y = 234;" +
				"let mut bx = box &mut x;" +
				"let mut by = box &mut y;" +
				"let mut p = *bx;" +
				"let mut q = by; " +
				"q = bx;" +
				"}";
		checkInvalid(input);
	}

	@Test
	public void test_138() throws IOException {
		// Moved out of box
		String input = "{" +
				"let mut x = 123;" +
				"let mut y = 234;" +
				"let mut bx = box box x;" +
				"let mut by = box box y;" +
				"let mut p = *bx;" +
				"let mut q = by; " +
				"q = bx;" +
				"}";
		checkInvalid(input);
	}

	@Test
	public void test_139() throws IOException {
		// Moved out of box
		String input = "{" +
				"let mut x = 123;" +
				"let mut y = 234;" +
				"let mut bx = box &mut x;" +
				"let mut by = box &mut y;" +
				"let mut p = *bx;" +
				"let mut q = box by; " +
				"*q = bx;" +
				"}";
		checkInvalid(input);
	}

	@Test
	public void test_140() throws IOException {
		// Moved out of box
		String input = "{" +
				"let mut x = 123;" +
				"let mut y = 234;" +
				"let mut bx = box box x;" +
				"let mut by = box box y;" +
				"let mut p = *bx;" +
				"let mut q = box by; " +
				"*q = bx;" +
				"}";
		checkInvalid(input);
	}

	@Test
	public void test_141() throws IOException {
		// Moved out of box
		String input = "{ let mut x = 123; let mut y = box box x; let mut z = *y; let mut w = &y; }";
		checkInvalid(input);
	}

	@Test
	public void test_142() throws IOException {
		String input = "{ let mut x = 123; let mut y = &x; let mut z = &mut y; **z = 1; }";
		checkInvalid(input);
	}

	@Test
	public void test_143() throws IOException {
		String input = "{ let mut x1 = 1; let mut x2 = 123; let mut y = &x1; let mut z = &mut y; *z = &mut x2; }";
		checkInvalid(input);
	}
	// ==============================================================
	// Reborrowing Examples
	// ==============================================================

	@Test
	public void test_160() throws IOException {
		String input = "{ let mut x = 1; let mut y = &mut x; { let mut z = &mut *y; *z = 123; } *y }";
		check(input,OneTwoThree);
	}

	@Test
	public void test_161() throws IOException {
		String input = "{ let mut x = 1; let mut y = &mut x; let mut z = &*y; *y }";
		check(input,One);
	}

	@Test
	public void test_162() throws IOException {
		String input = "{ let mut x = 1; let mut y = &mut x; let mut z = &*y; *z }";
		check(input,One);
	}

	@Test
	public void test_163() throws IOException {
		String input = "{ let mut x = 1; let mut y = &mut x; let mut z = &mut *y; let mut w = &mut *z; *w }";
		check(input,One);
	}

	@Test
	public void test_164() throws IOException {
		String input = "{ let mut x = 1; let mut y = box x; { let mut z = &mut *y; *z = 123; } *y }";
		check(input,OneTwoThree);
	}

	@Test
	public void test_165() throws IOException {
		String input = "{ let mut x = box 1; { let mut y = &mut *x; *y = 123; } *x }";
		check(input,OneTwoThree);
	}

	@Test
	public void test_180() throws IOException {
		String input = "{ let mut x = 1; let mut y = &mut x; let mut z = &mut *y; *y }";
		checkInvalid(input);
	}

	@Test
	public void test_181() throws IOException {
		String input = "{ let mut x = 1; let mut y = &mut x; let mut z = &*y; *y = 2; }";
		checkInvalid(input);
	}

	@Test
	public void test_182() throws IOException {
		String input = "{ let mut x = 1; let mut y = &mut x; let mut z = &*y; *z = 2; }";
		checkInvalid(input);
	}

	@Test
	public void test_183() throws IOException {
		String input = "{ let mut x = 1; let mut y = &mut x; let mut z = &mut *y; let mut w = &mut *y; }";
		checkInvalid(input);
	}

	@Test
	public void test_184() throws IOException {
		String input = "{ let mut x = 1; let mut y = &mut x; let mut z = &mut *y; let mut w = &mut *z; *y }";
		checkInvalid(input);
	}


	@Test
	public void test_185() throws IOException {
		String input = "{ let mut x = 1; let mut y = &mut x; let mut z = &mut *y; let mut w = &mut *z; *z }";
		checkInvalid(input);
	}

	@Test
	public void test_186() throws IOException {
		String input = "{ let mut x = 1; let mut y = &mut x; let mut z = &*y; let mut w = &mut *z; }";
		checkInvalid(input);
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


	public static void checkInvalid(String input) throws IOException {
		checkInvalid(input, new BorrowChecker(input));
	}

	public static void checkInvalid(String input, BorrowChecker typing) throws IOException {
		Lifetime globalLifetime = new Lifetime();
		try {
			List<Lexer.Token> tokens = new Lexer(new StringReader(input)).scan();
			// Parse block
			Term.Block stmt = new Parser(input,tokens).parseStatementBlock(new Parser.Context(), globalLifetime);
			// Borrow Check block
			typing.apply(new BorrowChecker.Environment(), globalLifetime, stmt);
			//
			fail("test shouldn't have passed borrow checking");
		} catch (SyntaxError e) {
			// If we get here, then the borrow checker raised an exception
			e.outputSourceError(System.out);
		}
	}

	public static final OperationalSemantics SEMANTICS = new OperationalSemantics();
}
