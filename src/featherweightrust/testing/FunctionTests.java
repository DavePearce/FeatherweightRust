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

	@Test
	public void test_0x000() throws IOException {
		String input = "fn f() -> int { 1 }";
		input += " { f() }";
		check(input,One);
	}

	@Test
	public void test_0x000a() throws IOException {
		String input = "fn f(x : int) -> int { 1 }";
		input += " { let mut a = 1 ; f(a) }";
		check(input,One);
	}

	@Test
	public void test_0x001() throws IOException {
		String input = "fn id(x : int) -> int { x }";
		input += " { id(1) }";
		check(input,One);
	}

	@Test
	public void test_0x001b() throws IOException {
		String input = "fn sel(x : int, y : int) -> int { x }";
		input += " { sel(1,2) }";
		check(input,One);
	}

	@Test
	public void test_0x001c() throws IOException {
		String input = "fn sel(x : int, y : int) -> int { y }";
		input += " { sel(1,2) }";
		check(input,Two);
	}

	@Test
	public void test_0x001d() throws IOException {
		String input = "fn id(x : int) -> int { x }";
		input += " { let mut x = 2; id(1); x }";
		check(input,Two);
	}

	@Test
	public void test_0x002() throws IOException {
		String input = "fn id(x : []int) -> []int { x }";
		input += " { let mut p = id(box 1); *p }";
		check(input,One);
	}

	@Test
	public void test_0x003() throws IOException {
		String input = "fn id(x : &'a int) -> &'a int { x }";
		input += " { let mut v = 1; let mut p = id(&v); *p }";
		check(input,One);
	}

	@Test
	public void test_0x004() throws IOException {
		String input = "fn id(x : &'a mut int) -> &'a mut int { x }";
		input += " { let mut v = 1; let mut p = id(&mut v); *p }";
		check(input,One);
	}

	@Test
	public void test_0x005() throws IOException {
		String input = "fn id(x : &'a &'b int) -> &'a &'b int { x }";
		input += " { let mut v = 1; let mut u = &v; let mut p = id(&u); **p }";
		check(input,One);
	}

	@Test
	public void test_0x006() throws IOException {
		String input = "fn id(x : &'a mut &'b int) -> &'a mut &'b int { x }";
		input += " { let mut v = 1; let mut u = &v; let mut p = id(&mut u); **p }";
		check(input,One);
	}

	@Test
	public void test_0x007() throws IOException {
		String input = "fn id(x : &'a mut &'b mut int) -> &'a mut &'b mut int { x }";
		input += " { 1 }";
		check(input,One);
	}

	@Test
	public void test_0x008() throws IOException {
		String input = "fn id(x : []&'a int) -> []&'a int { x }";
		input += " { 1 }";
		check(input,One);
	}

	@Test
	public void test_0x009() throws IOException {
		String input = "fn id(x : []&'a mut int) -> []&'a mut int { x }";
		input += " { 1 }";
		check(input,One);
	}

	@Test
	public void test_0x010() throws IOException {
		String input = "fn id(x : []int) -> int { !*x }";
		input += " { 1 }";
		check(input,One);
	}

	@Test
	public void test_0x011() throws IOException {
		String input = "fn id(x : &'a int) -> int { !*x }";
		input += " { 1 }";
		check(input,One);
	}
	@Test
	public void test_0x012() throws IOException {
		String input = "fn id(x : []&'a mut int) -> &'a mut int { *x }";
		input += " { 1 }";
		check(input,One);
	}

	@Test
	public void test_0x013() throws IOException {
		String input = "fn write(p : []int) { *p = 1; }";
		input += " { 1 }";
		check(input,One);
	}

	@Test
	public void test_0x014() throws IOException {
		String input = "fn write(p : &'a mut int) { *p = 1; }";
		input += " { 1 }";
		check(input,One);
	}

	@Test
	public void test_0x015() throws IOException {
		String input = "fn write(x : &'a mut &'a int, y : &'a int) { *x = y; }";
		input += " { }";
		check(input, Value.Unit);
	}

	@Test
	public void test_0x016() throws IOException {
		String input = "fn write(x : &'a mut &'b int, y : &'b int) { *x = y; }";
		input += " { }";
		check(input, Value.Unit);
	}

	@Test
	public void test_0x017() throws IOException {
		String input = "fn f(x : &'a &'b int, y : &'a int) -> &'a int { *x }";
		input += " { let mut u = 1; let mut v = &u; let mut w = f(&v,v); *w }";
		check(input, One);
	}

	@Test
	public void test_0x018() throws IOException {
		String input = "fn f(x : &'a &'b int, y : &'a int) -> &'a int { y }";
		input += " { let mut u = 1; let mut v = &u; let mut w = f(&v,v); *w }";
		check(input, One);
	}

	@Test
	public void test_0x050() throws IOException {
		String input = "fn id(x : int) -> int { y }";
		input += " { }";
		checkInvalid(input);
	}

	@Test
	public void test_0x051() throws IOException {
		String input = "fn id(x : int) -> []int { x }";
		input += " { }";
		checkInvalid(input);
	}

	@Test
	public void test_0x052() throws IOException {
		String input = "fn id(x : int) -> &'a int { x }";
		input += " { }";
		checkInvalid(input);
	}

	@Test
	public void test_0x053() throws IOException {
		String input = "fn id(x : int) -> &'a mut int { x }";
		input += " { }";
		checkInvalid(input);
	}

	@Test
	public void test_0x054() throws IOException {
		String input = "fn id(x : &'a int) -> &'a mut int { x }";
		input += " { }";
		checkInvalid(input);
	}

	@Test
	public void test_0x055() throws IOException {
		String input = "fn id(x : &'a int) -> &'a int { let mut y = 0; &y }";
		input += " { }";
		checkInvalid(input);
	}

	@Test
	public void test_0x056() throws IOException {
		String input = "fn id(x : &'a mut int) -> &'a mut int { let mut y = 0; &mut y }";
		input += " { }";
		checkInvalid(input);
	}

	@Test
	public void test_0x057() throws IOException {
		String input = "fn id(x : &'a mut int) -> &'b mut int { x }";
		input += " { }";
		checkInvalid(input);
	}

	@Test
	public void test_0x058() throws IOException {
		String input = "fn f(x : &'a mut &'b int) { let mut y = 0; *x = &y; }";
		input += " { }";
		checkInvalid(input);
	}

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
