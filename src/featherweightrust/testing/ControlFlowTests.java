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
import org.junit.Test;

import featherweightrust.core.BorrowChecker;
import featherweightrust.core.OperationalSemantics;
import featherweightrust.core.Syntax.Value;
import featherweightrust.extensions.ControlFlow;
import static featherweightrust.core.Syntax.Value.Unit;

public class ControlFlowTests {
	private static Value.Integer One = new Value.Integer(1);
	private static Value.Integer Two = new Value.Integer(2);
	private static Value.Integer OneTwoThree = new Value.Integer(123);

	@Test
	public void test_0x001() throws IOException {
		String input = "{ let mut x = 1; if x == x { 1 } else { 2 } }";
		check(input,One);
	}

	@Test
	public void test_0x002() throws IOException {
		String input = "{ let mut x = 1; let mut y = 1; if x == y { 1 } else { 2 } }";
		check(input,One);
	}

		@Test
	public void test_0x003() throws IOException {
		String input = "{ let mut x = 1; let mut y = 2; if x == y { 1 } else { 2 } }";
		check(input,Two);
	}

	@Test
	public void test_0x004() throws IOException {
		String input = "{ let mut x = 1; let mut y = 1; let mut z = &y; if x == x { z = &x; } else { z = &x; } }";
		check(input,Unit);
	}

	@Test
	public void test_0x005() throws IOException {
		String input = "{ let mut x = 1; let mut y = 1; let mut z = &y; if x == x { z = &x; } else { } }";
		check(input,Unit);
	}

	@Test
	public void test_0x006() throws IOException {
		String input = "{ let mut x = 1; let mut y = 2; let mut z = &y; if x == x { z = &x; } else { } let mut w = !*z; w }";
		check(input, One);
	}

	@Test
	public void test_0x007() throws IOException {
		String input = "{ let mut x = 1; let mut y = 2; let mut z = &y; if x != x { z = &x; } else { } let mut w = !*z; w }";
		check(input, Two);
	}

	@Test
	public void test_0x008() throws IOException {
		String input = "{ let mut x = 1; let mut y = if x == x { } else { }; }";
		check(input, Unit);
	}

	@Test
	public void test_0x009() throws IOException {
		String input = "{ let mut x = 1; let mut y = box 1; if x == x { let mut p = *y; } else { let mut q = *y; } *y = 1; }";
		check(input, Unit);
	}

	@Test
	public void test_0x00A() throws IOException {
		String input = "{ let mut x = 1; let mut y = 1; let mut z = box &mut x; if y == y { let mut p = *z; } else { let mut q = *z; } *z = &mut y; }";
		check(input, Unit);
	}

	@Test
	public void test_0x00B() throws IOException {
		String input = "{ let mut x = 1; let mut p = &x; let mut q = &x; if *p == *q { 1 } else { 2 } }";
		check(input, One);
	}

	@Test
	public void test_0x00C() throws IOException {
		String input = "{ let mut x = box 1; if *x == *x { x = box 2; } else { x = box 3; } *x }";
		check(input, Two);
	}

	@Test
	public void test_0x00D() throws IOException {
		String input = "{ let mut x = box 3; if *x != *x { x = box 2; } else { x = box 1; } *x }";
		check(input, One);
	}

	@Test
	public void test_0x00E() throws IOException {
		String input = "{ let mut x = 1; let mut y = 2; let mut p = &x; if x == x { p = &y; } else { } !*p }";
		check(input, Two);
	}

	@Test
	public void test_0x010() throws IOException {
		String input = "{ let mut x = 1; let mut y = 2; let mut p = &x; if x != x { } else { p = &y; } !*p }";
		check(input, Two);
	}

	@Test
	public void test_0x011() throws IOException {
		String input = "{ let mut x = 1; let mut y = 2; {let mut p = &mut x; if y == y { p = &mut y; } else { } *p = 123;} x }";
		check(input, One);
	}

	@Test
	public void test_0x012() throws IOException {
		String input = "{ let mut x = 1; let mut y = 2; {let mut p = &mut x; if y == y { p = &mut y; } else { } *p = 123;} y }";
		check(input, OneTwoThree);
	}

	@Test
	public void test_0x013() throws IOException {
		String input = "{ let mut x = 1; let mut y = 2; {let mut p = &mut x; if y != y { p = &mut y; } else { } *p = 123;} x }";
		check(input, OneTwoThree);
	}

	@Test
	public void test_0x014() throws IOException {
		String input = "{ let mut x = 1; let mut y = 2; {let mut p = &mut x; if y == y { p = &mut y; } else { } *p = 123;} x }";
		check(input, One);
	}

	@Test
	public void test_0x015() throws IOException {
		String input = "{ let mut x = 1; let mut p = &x; let mut q = &x; if *p == *q { 1 } else { 2 } }";
		check(input, One);
	}

	@Test
	public void test_0x016() throws IOException {
		String input = "{ let mut a = 1; let mut x = 2; let mut y = 3; let mut p = &mut x; let mut q = &mut y; if a == a { p = &mut a; } else { q = &mut a; } }";
		check(input, Unit);
	}

	@Test
	public void test_0x050() throws IOException {
		String input = "{ let mut x = 1; let mut y = 2; let mut z = &y; if x == x { z = &x; } else { } let mut w = *z; w }";
		checkInvalid(input);
	}

	@Test
	public void test_0x051() throws IOException {
		String input = "{ let mut x = 1; let mut y = 2; let mut z = &y; if x != x { z = &x; } else { } let mut w = *z; w }";
		checkInvalid(input);
	}

	@Test
	public void test_0x052() throws IOException {
		String input = "{ if x == x { } else { } }";
		checkInvalid(input);
	}

	@Test
	public void test_0x053() throws IOException {
		String input = "{ let mut x = 1; if x == y { } else { } }";
		checkInvalid(input);
	}

	@Test
	public void test_0x056() throws IOException {
		String input = "{ let mut x = 1; let mut y = &mut x; if y == &mut x { } else { } }";
		checkInvalid(input);
	}

	@Test
	public void test_0x057() throws IOException {
		String input = "{ let mut x = 1; if x == x { } else { 1 } }";
		checkInvalid(input);
	}

	@Test
	public void test_0x058() throws IOException {
		String input = "{ let mut x = 1; if x == x { } else { &x } }";
		checkInvalid(input);
	}

	@Test
	public void test_0x059() throws IOException {
		String input = "{ let mut x = 1; if x == x { } else { &mut x } }";
		checkInvalid(input);
	}

	@Test
	public void test_0x05A() throws IOException {
		String input = "{ let mut x = 1; if x == x { } else { box 1 } }";
		checkInvalid(input);
	}
	@Test
	public void test_0x05B() throws IOException {
		String input = "{ let mut x = 1; if x == x { 1 } else { } }";
		checkInvalid(input);
	}

	@Test
	public void test_0x05C() throws IOException {
		String input = "{ let mut x = 1; if x == x { } else { 1 } }";
		checkInvalid(input);
	}

	@Test
	public void test_0x05D() throws IOException {
		String input = "{ let mut x = 1; if x == x { &x } else { } }";
		checkInvalid(input);
	}

	@Test
	public void test_0x05E() throws IOException {
		String input = "{ let mut x = 1; if x == x { &x } else { 1 } }";
		checkInvalid(input);
	}

	@Test
	public void test_0x05F() throws IOException {
		String input = "{ let mut x = 1; if x == x { &x } else { &mut x } }";
		checkInvalid(input);
	}

	@Test
	public void test_0x060() throws IOException {
		String input = "{ let mut x = 1; if x == x { &x } else { box 1 } }";
		checkInvalid(input);
	}

	@Test
	public void test_0x061() throws IOException {
		String input = "{ let mut x = 1; if x == x { box 1 } else { } }";
		checkInvalid(input);
	}

	@Test
	public void test_0x062() throws IOException {
		String input = "{ let mut x = 1; if x == x { box 1 } else { 1 } }";
		checkInvalid(input);
	}

	@Test
	public void test_0x063() throws IOException {
		String input = "{ let mut x = 1; if x == x { box 1 } else { &x } }";
		checkInvalid(input);
	}

	@Test
	public void test_0x064() throws IOException {
		String input = "{ let mut x = 1; if x == x { box 1 } else { &mut x } }";
		checkInvalid(input);
	}

	@Test
	public void test_0x065() throws IOException {
		String input = "{ let mut x = 1; let mut y = 2; let mut p = &x; if x == x { p = &y; } else { } *p }";
		checkInvalid(input);
	}

	@Test
	public void test_0x066() throws IOException {
		String input = "{ let mut x = 1; let mut y = box 1; if x == x { let mut p = y; } else { let mut q = y; } *y = box 1; }";
		checkInvalid(input);
	}

	@Test
	public void test_0x067() throws IOException {
		String input = "{ let mut x = 1; let mut y = box 1; if x == x { } else { let mut q = y; } *y }";
		checkInvalid(input);
	}

	@Test
	public void test_0x068() throws IOException {
		String input = "{ let mut x = 1; let mut y = box 1; if x == x { let mut p = y; } else { } *y }";
		checkInvalid(input);
	}

	@Test
	public void test_0x069() throws IOException {
		String input = "{ let mut x = 1; let mut y = 2; let mut p = &mut x; if y == y { p = &y; } else { } }";
		checkInvalid(input);
	}

	@Test
	public void test_0x06A() throws IOException {
		String input = "{ let mut x = 1; let mut p = &x; if x == x { let mut y = 2; p = &y; } else { } }";
		checkInvalid(input);
	}

	@Test
	public void test_0x06B() throws IOException {
		String input = "{ let mut x = 1; let mut y = &mut x; if &mut x == y { } else { } }";
		checkInvalid(input);
	}

	@Test
	public void test_0x06C() throws IOException {
		String input = "{ let mut x = 1; if &mut x == &mut x { } else { } }";
		checkInvalid(input);
	}


	@Test
	public void test_0x06D() throws IOException {
		String input = "{ let mut x1 = 1; let mut x2 = 2; let mut y1 = &mut x1; let mut y2 = &mut x2; if y1 == y2 { } else { } }";
		checkInvalid(input);
	}

	@Test
	public void test_0x06E() throws IOException {
		String input = "{ let mut x1 = box 1; if x1 == x1 { } else { } }";
		checkInvalid(input);
	}

	@Test
	public void test_0x06F() throws IOException {
		String input = "{ let mut x1 = box 1; let mut x2 = box 2; if x1 == x2 { } else { } }";
		checkInvalid(input);
	}

	@Test
	public void test_0x070() throws IOException {
		String input = "{ let mut x = 1; let mut y = 1; if &mut x == &mut y { 1 } else { 2 } }";
		checkInvalid(input);
	}

	@Test
	public void test_0x071() throws IOException {
		String input = "{ let mut x = 1; let mut y = 1; if &mut x == &mut y { 1 } else { 2 } x }";
		checkInvalid(input);
	}

	@Test
	public void test_0x072() throws IOException {
		String input = "{ let mut x = 1; let mut q = &mut x; { let mut y = 2; if y == y { } else { q = &mut y; } } !*q }";
		checkInvalid(input);
	}

	public static void check(String input, Value output) throws IOException {
		// Reuse existing checking facility
		CoreTests.check(input, output, ControlFlow.SEMANTICS, new ControlFlow.Checker(false, input));
	}

	public static void checkInvalid(String input) throws IOException {
		CoreTests.checkInvalid(input, new ControlFlow.Checker(false, input));
	}
}
