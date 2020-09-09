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

import java.io.IOException;
import java.io.StringReader;
import java.util.List;

import org.junit.Test;

import featherweightrust.core.BorrowChecker;
import featherweightrust.core.OperationalSemantics;
import featherweightrust.core.Syntax.Lifetime;
import featherweightrust.core.Syntax.Term;
import featherweightrust.core.Syntax.Value;
import featherweightrust.extensions.Functions;
import featherweightrust.extensions.Functions.Syntax.FunctionDeclaration;
import featherweightrust.io.Lexer;
import featherweightrust.io.Parser;
import featherweightrust.util.Pair;
import featherweightrust.util.SyntaxError;
import featherweightrust.util.AbstractMachine.State;

import static org.junit.Assert.fail;

public class FunctionTests {
	private static Value.Integer One = new Value.Integer(1);
	private static Value.Integer Two = new Value.Integer(2);
	private static Value.Integer OneTwoThree = new Value.Integer(123);

	// =================================================================
	// Straightforward (Valid) Tests
	// =================================================================

	@Test
	public void test_0x001() throws IOException {
		String input = "fn f() -> int { 1 }";
		input += " { f() }";
		check(input,One);
	}

	@Test
	public void test_0x002() throws IOException {
		String input = "fn f(mut x : int) -> int { 1 }";
		input += " { let mut a = 1 ; f(a) }";
		check(input,One);
	}

	@Test
	public void test_0x003() throws IOException {
		String input = "fn id(mut x : int) -> int { x }";
		input += " { id(1) }";
		check(input,One);
	}

	@Test
	public void test_0x004() throws IOException {
		String input = "fn sel(mut x : int, mut y : int) -> int { x }";
		input += " { sel(1,2) }";
		check(input,One);
	}

	@Test
	public void test_0x005() throws IOException {
		String input = "fn sel(mut x : int, mut y : int) -> int { y }";
		input += " { sel(1,2) }";
		check(input,Two);
	}

	@Test
	public void test_0x006() throws IOException {
		String input = "fn id(mut x : int) -> int { x }";
		input += " { let mut x = 2; id(1); x }";
		check(input,Two);
	}

	@Test
	public void test_0x007() throws IOException {
		String input = "fn id(mut x : []int) -> []int { x }";
		input += " { let mut p = id(box 1); *p }";
		check(input,One);
	}

	@Test
	public void test_0x008() throws IOException {
		String input = "fn id(mut x : &'a int) -> &'a int { x }";
		input += " { let mut v = 1; let mut p = id(&v); !*p }";
		check(input,One);
	}

	@Test
	public void test_0x009() throws IOException {
		String input = "fn id(mut x : &'a mut int) -> &'a mut int { x }";
		input += " { let mut v = 1; let mut p = id(&mut v); !*p }";
		check(input,One);
	}

	@Test
	public void test_0x00A() throws IOException {
		String input = "fn id(mut x : &'a &'b int) -> &'a &'b int { x }";
		input += " { let mut v = 1; let mut u = &v; let mut p = id(&u); !**p }";
		check(input,One);
	}

	@Test
	public void test_0x00B() throws IOException {
		String input = "fn id(mut x : &'a mut &'b int) -> &'a mut &'b int { x }";
		input += " { let mut v = 1; let mut u = &v; let mut p = id(&mut u); !**p }";
		check(input,One);
	}

	@Test
	public void test_0x00C() throws IOException {
		String input = "fn id(mut x : &'a mut &'b mut int) -> &'a mut &'b mut int { x }";
		input += " { 1 }";
		check(input,One);
	}

	@Test
	public void test_0x00D() throws IOException {
		String input = "fn id(mut x : []&'a int) -> []&'a int { x }";
		input += " { 1 }";
		check(input,One);
	}

	@Test
	public void test_0x00E() throws IOException {
		String input = "fn id(mut x : []&'a mut int) -> []&'a mut int { x }";
		input += " { 1 }";
		check(input,One);
	}

	@Test
	public void test_0x00F() throws IOException {
		String input = "fn id(mut x : []int) -> int { !*x }";
		input += " { 1 }";
		check(input,One);
	}

	@Test
	public void test_0x010() throws IOException {
		String input = "fn id(mut x : &'a int) -> int { !*x }";
		input += " { 1 }";
		check(input,One);
	}
	@Test
	public void test_0x011() throws IOException {
		String input = "fn id(mut x : []&'a mut int) -> &'a mut int { *x }";
		input += " { 1 }";
		check(input,One);
	}

	// =================================================================
	// Return Type (Valid) Tests
	// =================================================================

	@Test
	public void test_0x020() throws IOException {
		// b :> a
		String input = "fn f(mut x : &'a &'b int, mut y : &'a int) -> &'a int { !*x }";
		input += " { let mut u = 1; let mut v = &u; let mut w = f(&v,!v); !*w }";
		check(input, One);
	}

	@Test
	public void test_0x021() throws IOException {
		// b :> a
		String input = "fn f(mut x : &'a &'b int, mut y : &'a int) -> &'a int { y }";
		input += " { let mut u = 1; let mut v = &u; let mut w = f(&v,!v); !*w }";
		check(input, One);
	}

	@Test
	public void test_0x022() throws IOException {
		// Prove covariance of immutable borrow
		String input = "fn f(mut x : &'a &'b int) -> &'a &'a int { x }";
		input += " { let mut u = 1; let mut v = &u; let mut w = f(&v); !**w }";
		check(input, One);
	}

	@Test
	public void test_0x023() throws IOException {
		String input = "fn f(mut x : &'a mut &'b int) -> &'a int { !*x }";
		input += " { let mut u = 1; { let mut v = &u; let mut w = f(&mut v); !*w } }";
		check(input, One);
	}

	@Test
	public void test_0x024() throws IOException {
		// Immutable borrows co-variant
		String input = "fn f(mut x : &'a &'b int, mut y1 : &'b &'c int, mut y2 : &'d &'b int) -> &'a &'d int { x }";
		input += "{ }";
		check(input,Value.Unit);
	}

	// =================================================================
	// Parameter (Valid) Tests
	// =================================================================

	@Test
	public void test_0x030() throws IOException {
		String input = "fn f(mut x : &'a int, mut y : &'a int) { x = y; }";
		input += " { let mut u = 1; f(&u,&u); }";
		check(input,Value.Unit);
	}

	@Test
	public void test_0x031() throws IOException {
		String input = "fn f(mut x : &'a mut int, mut y : &'a mut int) { x = y; }";
		input += " { let mut u = 1; let mut v = 2; f(&mut u,&mut v); }";
		check(input,Value.Unit);
	}

	@Test
	public void test_0x032() throws IOException {
		String input = "fn f(mut x : &'a mut &'b int, mut y : &'b int) { *x = y; }";
		input += " { let mut x = 0; { let mut y = 1; { let mut p = &y; f(&mut p,&x) } } }";
		check(input,Value.Unit);
	}

	@Test
	public void test_0x033() throws IOException {
		String input = "fn f(mut x : &'a mut &'b int, mut y : &'c int, mut z : &'b &'c int) { }";
		input += " { let mut u = 1; { let mut v = 2; let mut p = &u; { let mut q = &v; let mut w = f(&mut q, &u, &p); } } }";
		check(input,Value.Unit);
	}


	// =================================================================
	// Side-Effect (Valid) Tests
	// =================================================================

	@Test
	public void test_0x040() throws IOException {
		String input = "fn write(mut x : []int) { *x = 1; }";
		input += " { 1 }";
		check(input,One);
	}

	@Test
	public void test_0x041() throws IOException {
		String input = "fn write(mut x : &'a mut int) { *x = 1; }";
		input += " { 1 }";
		check(input,One);
	}

	@Test
	public void test_0x042() throws IOException {
		String input = "fn write(mut x : &'a mut &'a int, mut y : &'a int) { *x = y; }";
		input += " { }";
		check(input, Value.Unit);
	}

	@Test
	public void test_0x043() throws IOException {
		String input = "fn write(mut x : &'a mut &'b int, mut y : &'b int) { *x = y; }";
		input += " { }";
		check(input, Value.Unit);
	}

	@Test
	public void test_0x044() throws IOException {
		String input = "fn f(mut x : &'a int, mut y : &'a mut int) -> &'a mut int { y }";
		input += " { let mut u = 1; let mut v = 2; let mut w = f(&u, &mut v); let mut a = &mut u; }";
		check(input, Value.Unit);
	}

	@Test
	public void test_0x045() throws IOException {
		String input = "fn f(mut x : &'a int, mut y : &'a mut int) -> &'a int { x }";
		input += " { let mut u = 1; let mut v = 2; let mut w = f(&u, &mut v); let mut a = &mut v; }";
		check(input, Value.Unit);
	}

	@Test
	public void test_0x046() throws IOException {
		String input = "fn f(mut x : &'a int, mut y : &'b int) -> &'b int { y }";
		input += " { let mut u = 1; let mut v = 2; let mut w = f(&u, &v); let mut a = &mut u; }";
		check(input, Value.Unit);
	}

	@Test
	public void test_0x047() throws IOException {
		String input = "fn f(mut x : &'a int, mut y : &'b int) -> &'a int { x }";
		input += " { let mut u = 1; let mut v = 2; let mut w = f(&u, &v); let mut a = &mut v; }";
		check(input, Value.Unit);
	}

	@Test
	public void test_0x048() throws IOException {
		String input = "fn f(mut x : &'a mut int, mut y : &'b mut int) -> &'b mut int { y }";
		input += " { let mut u = 1; let mut v = 2; let mut w = f(&mut u, &mut v); let mut a = &mut u; }";
		check(input, Value.Unit);
	}

	@Test
	public void test_0x049() throws IOException {
		String input = "fn f(mut x : &'a mut int, mut y : &'b mut int) -> &'a mut int { x }";
		input += " { let mut u = 1; let mut v = 2; let mut w = f(&mut u, &mut v); let mut a = &mut v; }";
		check(input, Value.Unit);
	}

	@Test
	public void test_0x04A() throws IOException {
		String input = "fn f(mut x : &'a mut &'b int, mut y : &'b mut int) { }";
		input += " { let mut u = 1; let mut v = 2; let mut p = &u; let mut w = f(&mut p, &mut v); let mut a = &mut v; }";
		check(input, Value.Unit);
	}

	@Test
	public void test_0x04B() throws IOException {
		String input = "fn f(mut x : &'a mut &'b mut int, mut y : &'b int) { }";
		input += " { let mut u = 1; let mut v = 2; let mut p = &mut u; let mut w = f(&mut p, &v); let mut a = &mut v; }";
		check(input, Value.Unit);
	}

	@Test
	public void test_0x04C() throws IOException {
		String input = "fn f(mut x : &'a mut &'b int, mut y : &'c int) { }";
		input += " { let mut u = 1; let mut v = 2; let mut p = &u; let mut w = f(&mut p, &v); let mut a = &mut v; }";
		check(input, Value.Unit);
	}

	@Test
	public void test_0x04D() throws IOException {
		String input = "fn f(mut x : &'a mut &'b mut int, mut y : &'c mut int) { }";
		input += " { let mut u = 1; let mut v = 2; let mut p = &mut u; let mut w = f(&mut p, &mut v); let mut a = &mut v; }";
		check(input, Value.Unit);
	}

	@Test
	public void test_0x04E() throws IOException {
		String input = "fn f(mut x : &'a mut &'b int, mut y : &'c int, mut z : &'b &'c int) { }";
		input += " { let mut u = 1; { let mut v = 2;  let mut p = &u; { let mut q = &v; f(&mut q, &u, &p);  } let mut a = &mut v; } } }";
		check(input,Value.Unit);
	}

	@Test
	public void test_0x04F() throws IOException {
		String input = "fn f(mut x : &'a mut &'b int, mut y : &'c mut int, mut z : &'b &'c int) { }";
		input += " { let mut u = 1; { let mut v = 2; let mut w = 3; let mut p = &u; { let mut q = &v; f(&mut q, &mut w, &p);  let mut a = &mut w; } } }";
		check(input,Value.Unit);
	}

	// =================================================================
	// Straightforward (Invalid) Tests
	// =================================================================


	@Test
	public void test_0x050() throws IOException {
		String input = "fn id(mut x : int) -> int { y }";
		input += " { }";
		checkInvalid(input);
	}

	// =================================================================
	// Return Type (Invalid) Tests
	// =================================================================

	@Test
	public void test_0x060() throws IOException {
		String input = "fn id(mut x : int) -> []int { x }";
		input += " { }";
		checkInvalid(input);
	}

	@Test
	public void test_0x061() throws IOException {
		String input = "fn id(mut x : int) -> &'a int { x }";
		input += " { }";
		checkInvalid(input);
	}

	@Test
	public void test_0x062() throws IOException {
		String input = "fn id(mut x : int) -> &'a mut int { x }";
		input += " { }";
		checkInvalid(input);
	}

	@Test
	public void test_0x063() throws IOException {
		String input = "fn id(mut x : &'a int) -> &'a mut int { x }";
		input += " { }";
		checkInvalid(input);
	}

	@Test
	public void test_0x064() throws IOException {
		String input = "fn id(mut x : &'a int) -> &'a int { let mut y = 0; &y }";
		input += " { }";
		checkInvalid(input);
	}

	@Test
	public void test_0x065() throws IOException {
		String input = "fn id(mut x : &'a mut int) -> &'a mut int { let mut y = 0; &mut y }";
		input += " { }";
		checkInvalid(input);
	}

	@Test
	public void test_0x066() throws IOException {
		String input = "fn id(mut x : &'a mut int) -> &'b mut int { x }";
		input += " { }";
		checkInvalid(input);
	}

	@Test
	public void test_0x067() throws IOException {
		// Prove covariance of immutable borrow
		String input = "fn f(mut x : &'a &'b int, mut y : &'a &'a int) -> &'a &'b int { y }";
		input += " { }";
		checkInvalid(input);
	}

	@Test
	public void test_0x068() throws IOException {
		// Prove contravariance of mutable borrow
		String input = "fn f(mut x : &'a mut &'b int) -> &'a mut &'a int { y }";
		input += " { }";
		checkInvalid(input);
	}

	@Test
	public void test_0x069() throws IOException {
		// This should work I think?
		//
		// 'a smaller than 'b
		// &'a T :> &'b T
		// &'c mut &'a T <: &'c mut 'b T
		String input = "fn f(mut x : &'a &'b int, mut y : &'a mut &'a int) -> &'a mut &'b int { y }";
		input += " { }";
		checkInvalid(input);
	}


	// =================================================================
	// Parameter (Invalid) Tests
	// =================================================================

	@Test
	public void test_0x070() throws IOException {
		String input = "fn f(mut x : &'a mut &'b int, mut y : &'b int) { *x = y; }";
		input += " { let mut x = 0; { let mut p = &x; { let mut y = 1; f(&mut p,&y) } } }";
		checkInvalid(input);
	}

	@Test
	public void test_0x071() throws IOException {
		String input = "fn f(mut x : &'a mut &'b int, mut y : &'b &'c int) -> &'c int { !*x }";
		input += " { let mut u = 1; { let mut v = &u; let mut w = &u; let mut x = f(&mut v, &w); !*w } }";
		checkInvalid(input);
	}

	@Test
	public void test_0x072() throws IOException {
		// Immutable borrows not contra-variant
		String input = "fn f(mut x : &'a &'b int, mut y1 : &'b &'c int, mut y2 : &'d &'b int) -> &'a &'c int { x }";
		input += "{ }";
		checkInvalid(input);
	}

	@Test
	public void test_0x073() throws IOException {
		// Mutable borrows not covariant
		String input = "fn f(mut x : &'a mut &'b int, mut y1 : &'b &'c int, mut y2 : &'d &'b int) -> &'a mut &'d int { x }";
		input += "{ }";
		checkInvalid(input);
	}

	@Test
	public void test_0x074() throws IOException {
		// Mutable borrows not contra-variant
		String input = "fn f(mut x : &'a mut &'b int, mut y1 : &'b &'c int, mut y2 : &'d &'b int) -> &'a mut &'c int { x }";
		input += "{ }";
		checkInvalid(input);
	}

	@Test
	public void test_0x075() throws IOException {
		String input = "fn f(mut x : &'a mut &'b int, mut y : &'b int) { *x = y; }";
		input += " { let mut x = 0; { let mut y = 1; { let mut p = &x; f(&mut p,&y) } } }";
		checkInvalid(input);
	}

	// =================================================================
	// Side-Effect (Invalid) Tests
	// =================================================================

	@Test
	public void test_0x080() throws IOException {
		String input = "fn f(mut x : &'a mut &'b int) { let mut y = 0; *x = &y; }";
		input += " { }";
		checkInvalid(input);
	}


	@Test
	public void test_0x081() throws IOException {
		String input = "fn f(mut x : &'a int, mut y : &'a mut int) -> &'a mut int { y }";
		input += " { let mut u = 1; let mut v = 2; let mut w = f(&u, &mut v); let mut a = &mut v; }";
		checkInvalid(input);
	}

	@Test
	public void test_0x082() throws IOException {
		String input = "fn f(mut x : &'a int, mut y : &'a mut int) -> &'a int { x }";
		input += " { let mut u = 1; let mut v = 2; let mut w = f(&u, &mut v); let mut a = &mut u; }";
		checkInvalid(input);
	}

	@Test
	public void test_0x083() throws IOException {
		String input = "fn f(mut x : &'a int, mut y : &'b int) -> &'b int { y }";
		input += " { let mut u = 1; let mut v = 2; let mut w = f(&u, &v); let mut a = &mut v; }";
		checkInvalid(input);
	}

	@Test
	public void test_0x084() throws IOException {
		String input = "fn f(mut x : &'a int, mut y : &'b int) -> &'a int { x }";
		input += " { let mut u = 1; let mut v = 2; let mut w = f(&u, &v); let mut a = &mut u; }";
		checkInvalid(input);
	}

	@Test
	public void test_0x085() throws IOException {
		String input = "fn f(mut x : &'a mut int, mut y : &'b mut int) -> &'b mut int { y }";
		input += " { let mut u = 1; let mut v = 2; let mut w = f(&mut u, &mut v); let mut a = &mut v; }";
		checkInvalid(input);
	}

	@Test
	public void test_0x086() throws IOException {
		String input = "fn f(mut x : &'a mut int, mut y : &'b mut int) -> &'a mut int { x }";
		input += " { let mut u = 1; let mut v = 2; let mut w = f(&mut u, &mut v); let mut a = &mut u; }";
		checkInvalid(input);
	}


	@Test
	public void test_0x087() throws IOException {
		String input = "fn f(mut x : &'a mut &'b int, mut y : &'b int) { }";
		input += " { let mut u = 1; let mut v = 2; let mut p = &u; let mut w = f(&mut p, &v); let mut a = &mut u; }";
		checkInvalid(input);
	}

	@Test
	public void test_0x088() throws IOException {
		String input = "fn f(mut x : &'a mut &'b int, mut y : &'b int) { }";
		input += " { let mut u = 1; let mut v = 2; let mut p = &u; let mut w = f(&mut p, &v); let mut a = &mut v; }";
		checkInvalid(input);
	}

	@Test
	public void test_0x089() throws IOException {
		String input = "fn f(mut x : &'a mut &'b mut int, mut y : &'b mut int) { }";
		input += " { let mut u = 1; let mut v = 2; let mut p = &mut u; let mut w = f(&mut p, &mut v); let mut a = &mut u; }";
		checkInvalid(input);
	}

	@Test
	public void test_0x08A() throws IOException {
		String input = "fn f(mut x : &'a mut &'b mut int, mut y : &'b mut int) { }";
		input += " { let mut u = 1; let mut v = 2; let mut p = &mut u; let mut w = f(&mut p, &mut v); let mut a = &mut v; }";
		checkInvalid(input);
	}

	@Test
	public void test_0x08B() throws IOException {
		String input = "fn f(mut x : &'a mut &'b int, mut y : &'c int, mut z : &'b &'c int) { }";
		input += " { let mut u = 1; { let mut v = 2;  let mut p = &u; { let mut q = &v; f(&mut q, &u, &p); } let mut a = &mut u; } } }";
		checkInvalid(input);
	}

	@Test
	public void test_0x08C() throws IOException {
		String input = "fn f(mut x : &'a mut &'b int, mut y : &'c int, mut z : &'b &'c int) { }";
		input += " { let mut u = 1; { let mut v = 2;  let mut p = &u; { let mut q = &v; f(&mut q, &u, &p); let mut a = &mut v; } } }";
		checkInvalid(input);
	}

	// =================================================================
	// Helpers
	// =================================================================

	public static void check(String input, Value output) throws IOException {
		// Allocate the global lifetime. This is the lifetime where all heap allocated
		// data will reside.
		Lifetime globalLifetime = new Lifetime();
		//
		try {
			List<Lexer.Token> tokens = new Lexer(new StringReader(input)).scan();
			Parser parser = new Parser(input,tokens);
			// Parse any declarations
			List<FunctionDeclaration> decls = parser.parseDeclarations();
			// Parse block
			Term.Block stmt = parser.parseStatementBlock(new Parser.Context(), globalLifetime);
			Functions.Checker checker = new Functions.Checker(false,input,decls);
			// Borrow Check declarations
			checker.apply(globalLifetime, decls);
			// Borrow Check block
			checker.apply(BorrowChecker.EMPTY_ENVIRONMENT, globalLifetime, stmt);
			// Execute block in outermost lifetime "*")
			Pair<State, Term> state = new Pair<>(new State(),stmt);
			// Execute continually until all reductions complete (or exception)
			Term result = new OperationalSemantics(new Functions.Semantics(decls)).execute(globalLifetime,
					state.second());
			//
			CoreTests.check(output, (Value) result);
		} catch (SyntaxError e) {
			e.outputSourceError(System.err);
			e.printStackTrace();
			fail();
		}
	}

	public static void checkInvalid(String input) throws IOException {
		Lifetime globalLifetime = new Lifetime();
		try {
			List<Lexer.Token> tokens = new Lexer(new StringReader(input)).scan();
			Parser parser = new Parser(input,tokens);
			// Parse any declarations
			List<FunctionDeclaration> decls = parser.parseDeclarations();
			// Parse block
			Term.Block stmt = parser.parseStatementBlock(new Parser.Context(), globalLifetime);
			Functions.Checker checker = new Functions.Checker(false,input,decls);
			// Borrow Check declarations
			checker.apply(globalLifetime, decls);
			// Borrow Check block
			checker.apply(BorrowChecker.EMPTY_ENVIRONMENT, globalLifetime, stmt);
			//
			fail("test shouldn't have passed borrow checking");
		} catch (SyntaxError e) {
			// If we get here, then the borrow checker raised an exception
			e.outputSourceError(System.out);
		}
	}
}
