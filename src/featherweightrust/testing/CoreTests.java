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
import featherweightrust.testing.experiments.Util;
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
	public void test_0x0001() throws IOException {
		String input = "{ let mut x = 123; x }";
		check(input,OneTwoThree);
	}

	@Test
	public void test_0x0002() throws IOException {
		String input = "{ let mut x = 123; !x }";
		check(input,OneTwoThree);
	}

	@Test
	public void test_0x0003() throws IOException {
		String input = "{ let mut x = 123; let mut y = x; y}";
		check(input, OneTwoThree);
	}

	@Test
	public void test_0x0004() throws IOException {
		String input = "{ let mut x = 123; let mut y = x; !y}";
		check(input, OneTwoThree);
	}

	@Test
	public void test_0x0005() throws IOException {
		String input = "{ let mut x = 123; let mut y = !x; y}";
		check(input, OneTwoThree);
	}

	@Test
	public void test_0x0006() throws IOException {
		String input = "{ let mut x = 1; let mut y = 123; y}";
		check(input, OneTwoThree);
	}


	@Test
	public void test_0x0007() throws IOException {
		String input = "{ let mut x = 1; let mut y = 123; !y}";
		check(input, OneTwoThree);
	}

	@Test
	public void test_0x0008() throws IOException {
		String input = "{ let mut x = 123; let mut y = 1; x}";
		check(input, OneTwoThree);
	}

	@Test
	public void test_0x0009() throws IOException {
		String input = "{ let mut x = 123; let mut y = 1; !x}";
		check(input, OneTwoThree);
	}

	@Test
	public void test_0x000B() throws IOException {
		String input = "{ let mut x = 1; let mut y = 123; x = 2; y}";
		check(input, OneTwoThree);
	}

	@Test
	public void test_0x000C() throws IOException {
		String input = "{ let mut x = 1; let mut y = 123; x = 2; !y}";
		check(input, OneTwoThree);
	}

	@Test
	public void test_0x000D() throws IOException {
		String input = "{ let mut x = 1; { let mut y = 123; y } }";
		check(input, OneTwoThree);
	}

	@Test
	public void test_0x000E() throws IOException {
		String input = "{ let mut x = 1; { let mut y = 123; !y } }";
		check(input, OneTwoThree);
	}

	@Test
	public void test_0x000F() throws IOException {
		String input = "{ let mut x = 1; { x = 123; } x }";
		check(input, OneTwoThree);
	}

	@Test
	public void test_0x0010() throws IOException {
		String input = "{ let mut x = 1; { x = 123; } !x }";
		check(input, OneTwoThree);
	}

	@Test
	public void test_0x0011() throws IOException {
		String input = "{ let mut x = box 0; { let mut y = x; x = box 1; } *x }";
		check(input, One);
	}

	@Test
	public void test_0x0012() throws IOException {
		String input = "{ let mut x = box 0; { let mut y = x; x = box 1; } !*x }";
		check(input, One);
	}

	@Test
	public void test_0x0013() throws IOException {
		String input = "{ let mut x = box 0; x = box 1; *x }";
		check(input, One);
	}

	@Test
	public void test_0x0014() throws IOException {
		String input = "{ let mut x = box 0; x = box 1; !*x }";
		check(input, One);
	}

	@Test
	public void test_0x0015() throws IOException {
		String input = "{ 123 }";
		check(input,OneTwoThree);
	}

	@Test
	public void test_0x0016() throws IOException {
		String input = "{ { 1 } 123 }";
		check(input,OneTwoThree);
	}

	@Test
	public void test_0x0017() throws IOException {
		String input = "{ { { 1 } 2 } 123 }";
		check(input,OneTwoThree);
	}

	@Test
	public void test_0x0018() throws IOException {
		String input = "{ let mut x = 123; let mut y = 1 }";
		check(input,Value.Unit);
	}


	@Test
	public void test_0x0050() throws IOException {
		String input = "{ let mut x = 123; let mut y = x; x }";
		checkInvalid(input);
	}

	@Test
	public void test_0x0051() throws IOException {
		String input = "{ let mut x = box 1; let mut y = !x; }";
		checkInvalid(input);
	}

	@Test
	public void test_0x0052() throws IOException {
		String input = "{ let mut x = 123; let mut y = &mut x; let mut z = !y; }";
		checkInvalid(input);
	}

	@Test
	public void test_0x0053() throws IOException {
		String input = "{ let mut x = 123; let mut y = &mut x; x = 1; }";
		checkInvalid(input);
	}

	@Test
	public void test_0x0054() throws IOException {
		String input = "{ x = 123; }";
		checkInvalid(input);
	}

	@Test
	public void test_0x0055() throws IOException {
		String input = "{ let mut x = 123; y = 123; }";
		checkInvalid(input);
	}

	@Test
	public void test_0x0056() throws IOException {
		String input = "{ let mut x = 123; { let mut y = 1; } y = 123; }";
		checkInvalid(input);
	}

	@Test
	public void test_0x0057() throws IOException {
		String input = "{ let mut x = box 1; let mut y = x; *x = 123; }";
		checkInvalid(input);
	}

	@Test
	public void test_0x0058() throws IOException {
		String input = "{ let mut x = 123; let mut y = &mut x; !y }";
		checkInvalid(input);
	}

	@Test
	public void test_0x0059() throws IOException {
		String input = "{ let mut x = 0 ; *x = 0 }";
		checkInvalid(input);
	}

	// ==============================================================
	// Allocation Examples
	// ==============================================================

	@Test
	public void test_0x0100() throws IOException {
		String input = "{ let mut x = box 123; *x }";
		check(input, OneTwoThree);
	}

	@Test
	public void test_0x0101() throws IOException {
		String input = "{ let mut y = box 123; let mut x = *y; x }";
		check(input,OneTwoThree);
	}

	@Test
	public void test_0x0102() throws IOException {
		String input = "{ let mut y = box 123; let mut x = !*y; *y }";
		check(input,OneTwoThree);
	}

	@Test
	public void test_0x0103() throws IOException {
		String input = "{ let mut y = 123; let mut z = box &y; let mut x = *z; !*x }";
		check(input, OneTwoThree);
	}

	@Test
	public void test_0x0104() throws IOException {
		String input = "{ let mut y = 123; let mut z = box &y; let mut x = !*z; !*x }";
		check(input, OneTwoThree);
	}

	@Test
	public void test_0x0105() throws IOException {
		String input = "{ let mut y = 123; let mut z = box &y; let mut x = !*z; !**z }";
		check(input, OneTwoThree);
	}

	@Test
	public void test_0x0106() throws IOException {
		String input = "{ let mut x = box 1; *x = 123; *x }";
		check(input, OneTwoThree);
	}

	@Test
	public void test_0x0107() throws IOException {
		String input = "{ let mut x = box 1; *x = 123; !*x }";
		check(input, OneTwoThree);
	}

	@Test
	public void test_0x0108() throws IOException {
		String input = "{ let mut x = box 123; let mut y = box 1; *y = 2; *x }";
		check(input, OneTwoThree);
	}

	@Test
	public void test_0x0109() throws IOException {
		String input = "{ let mut x = box 123; let mut y = box 1; *y = 2; !*x }";
		check(input, OneTwoThree);
	}

	@Test
	public void test_0x010A() throws IOException {
		String input = "{ let mut x = 123; let mut y = box box x; let mut z = *y; *z }";
		check(input, OneTwoThree);
	}

	@Test
	public void test_0x010B() throws IOException {
		String input = "{ let mut x = box box 1; **x = 123; **x }";
		check(input,OneTwoThree);
	}

	@Test
	public void test_0x010C() throws IOException {
		String input = "{ let mut x = box box 1; **x = 123; !**x }";
		check(input,OneTwoThree);
	}

	@Test
	public void test_0x010D() throws IOException {
		String input = "{ let mut x = box 1; { let mut y = box 123; x = y; } *x }";
		check(input,OneTwoThree);
	}

	@Test
	public void test_0x010E() throws IOException {
		String input = "{ let mut x = box 1; { let mut y = box 123; x = y; } !*x }";
		check(input,OneTwoThree);
	}

	@Test
	public void test_0x010F() throws IOException {
		String input = "{ let mut x = box box 123; let mut y = *x; *y }";
		check(input,OneTwoThree);
	}

	@Test
	public void test_0x0110() throws IOException {
		String input = "{ let mut x = box box 123; let mut y = *x; !*y }";
		check(input,OneTwoThree);
	}

	@Test
	public void test_0x0150() throws IOException {
		String input = "{ let mut y = 123; let mut z = box &y; let mut x = *z; *x }";
		checkInvalid(input);
	}

	@Test
	public void test_0x0151() throws IOException {
		String input = "{ let mut y = 123; let mut z = box &y; let mut x = !*z; *x }";
		checkInvalid(input);
	}

	@Test
	public void test_0x0152() throws IOException {
		String input = "{ let mut y = 123; let mut z = box &y; let mut x = !*z; **z }";
		checkInvalid(input);
	}


	// ==============================================================
	// Immutable Borrowing Examples
	// ==============================================================

	@Test
	public void test_0x0201() throws IOException {
		String input = "{ let mut x = 123; let mut y = &x; !*y }";
		check(input, OneTwoThree);
	}

	@Test
	public void test_0x0203() throws IOException {
		String input = "{ let mut x = 123; let mut y = &x; let mut z = !*y; z }";
		check(input, OneTwoThree);
	}


	@Test
	public void test_0x0205() throws IOException {
		String input = "{ let mut x = 123; { let mut y = &x; !*y } }";
		check(input, OneTwoThree);
	}

	@Test
	public void test_0x0206() throws IOException {
		String input = "{ let mut x = 123; { let mut y = &x; let mut z = !y; !*y } }";
		check(input, OneTwoThree);
	}


	@Test
	public void test_0x0250() throws IOException {
		String input = "{ let mut x = 123; let mut y = &x; *y }";
		checkInvalid(input);
	}

	@Test
	public void test_0x0251() throws IOException {
		String input = "{ let mut x = 123; let mut y = &x; let mut z = *y; z }";
		checkInvalid(input);
	}

	@Test
	public void test_0x0252() throws IOException {
		String input = "{ let mut x = 123; let mut y = &x; let mut z = !*y; *y }";
		checkInvalid(input);
	}

	@Test
	public void test_0x0253() throws IOException {
		String input = "{ let mut x = &x; }";
		checkInvalid(input);
	}

	@Test
	public void test_0x0254() throws IOException {
		String input = "{ let mut x = &y; }";
		checkInvalid(input);
	}
	@Test
	public void test_0x0255() throws IOException {
		// Moved out of box
		String input = "{ let mut x = 123; let mut y = box box x; let mut z = !*y; }";
		checkInvalid(input);
	}

	@Test
	public void test_0x0256() throws IOException {
		String input = "{ let mut x = 123; let mut y = &x; let mut z = *y; x }";
		checkInvalid(input);
	}

	@Test
	public void test_0x0257() throws IOException {
		String input = "{ let mut x = 123; { let mut y = &x; let mut z = y; *y } }";
		checkInvalid(input);
	}

	@Test
	public void test_0x0258() throws IOException {
		String input = "{ let mut x = &x; x = 2; }";
		checkInvalid(input);
	}

	@Test
	public void test_0x0259() throws IOException {
		String input = "{ let mut x = 1; let mut y = &x; { let mut z = 1; y = &z; } }";
		checkInvalid(input);
	}

	@Test
	public void test_0x025A() throws IOException {
		String input = "{ let mut x = 123; let mut y = box 0; { let mut z = &x; z = &y; } }";
		checkInvalid(input);
	}

	@Test
	public void test_0x025B() throws IOException {
		String input = "{ let mut x = 123; let mut y = &x; let mut z = &mut x; }";
		checkInvalid(input);
	}

	// ==============================================================
	// Mutable Borrowing Examples
	// ==============================================================

	@Test
	public void test_0x0300() throws IOException {
		String input = "{ let mut x = 1; let mut y = &mut x; { let mut w = 123; let mut z = &w; !*z } }";
		check(input, OneTwoThree);
	}

	@Test
	public void test_0x0301() throws IOException {
		String input = "{ let mut x = 1; let mut y = &mut x; { let mut w = 123; let mut z = &w; !*z } }";
		check(input, OneTwoThree);
	}

	@Test
	public void test_0x0302() throws IOException {
		String input = "{ let mut x = 123; let mut y = box &mut x; let mut z = *y; !*z }";
		check(input, OneTwoThree);
	}

	@Test
	public void test_0x0303() throws IOException {
		String input = "{ let mut x = 123; let mut y = box &mut x; let mut z = *y; *y = z; let mut w = *y; !*w }";
		check(input, OneTwoThree);
	}

	@Test
	public void test_0x0304() throws IOException {
		String input = "{ let mut x = 123; let mut y = &x; let mut z = &mut y; !**z }";
		check(input,OneTwoThree);
	}

	@Test
	public void test_0x0305() throws IOException {
		String input = "{ let mut x = 123; let mut y = &x; let mut z = &mut y; !**z }";
		check(input,OneTwoThree);
	}

	@Test
	public void test_0x0306() throws IOException {
		String input = "{ let mut x1 = 1; let mut x2 = 123; let mut y = &x1; let mut z = &mut y; *z = &x2; !**z }";
		check(input,OneTwoThree);
	}

	@Test
	public void test_0x0307() throws IOException {
		String input = "{ let mut x1 = 1; let mut x2 = 123; let mut y = &x1; let mut z = &mut y; *z = &x2; !**z }";
		check(input,OneTwoThree);
	}

	@Test
	public void test_0x0308() throws IOException {
		String input = "{ let mut x = 1; let mut y = &mut x; let mut z = &mut y; **z = 123; !**z }";
		check(input,OneTwoThree);
	}

	@Test
	public void test_0x0309() throws IOException {
		String input = "{ let mut x = 1; let mut y = &mut x; let mut z = &mut y; **z = 123; !**z }";
		check(input,OneTwoThree);
	}

	@Test
	public void test_0x030A() throws IOException {
		String input = "{ let mut x = 1; let mut y = box &mut x; **y = 123; !**y }";
		check(input,OneTwoThree);
	}

	@Test
	public void test_0x030B() throws IOException {
		String input = "{ let mut x = box 1; let mut y = &mut x; **y = 123; !**y }";
		check(input,OneTwoThree);
	}

	@Test
	public void test_0x030C() throws IOException {
		// NOTE: this is accepted by rust!
		String input = "{ let mut x = 0 ; let mut y = &mut x ; y = &mut x }";
		check(input,Value.Unit);
	}

	@Test
	public void test_0x0350() throws IOException {
		String input = "{ let mut x = 1; let mut y = &mut x; { let mut w = 123; let mut z = &w; *z } }";
		checkInvalid(input);
	}

	@Test
	public void test_0x0351() throws IOException {
		String input = "{ let mut x = 1; let mut y = &mut x; { let mut w = 123; let mut z = &w; *z } }";
		checkInvalid(input);
	}

	@Test
	public void test_0x0352() throws IOException {
		String input = "{ let mut x = 123; let mut y = box &mut x; let mut z = *y; *z }";
		checkInvalid(input);
	}

	@Test
	public void test_0x0353() throws IOException {
		String input = "{ let mut x = 123; let mut y = box &mut x; let mut z = *y; *y = z; let mut w = *y; *w }";
		checkInvalid(input);
	}

	@Test
	public void test_0x0354() throws IOException {
		String input = "{ let mut x = 123; let mut y = &x; let mut z = &mut y; **z }";
		checkInvalid(input);
	}

	@Test
	public void test_0x0355() throws IOException {
		String input = "{ let mut x = 123; let mut y = &mut x; let mut z = &y; !*z }";
		checkInvalid(input);
	}

	@Test
	public void test_0x0356() throws IOException {
		String input = "{ let mut x1 = 1; let mut x2 = 123; let mut y = &x1; let mut z = &mut y; *z = &x2; **z }";
		checkInvalid(input);
	}

	@Test
	public void test_0x0357() throws IOException {
		String input = "{ let mut x1 = 1; let mut x2 = 123; let mut y = &mut x1; let mut z = &mut y; *z = &mut x2; !*z }";
		checkInvalid(input);
	}

	@Test
	public void test_0x0358() throws IOException {
		String input = "{ let mut x = 1; let mut y = &mut x; let mut z = &mut y; **z = 123; **z }";
		checkInvalid(input);
	}

	@Test
	public void test_0x0359() throws IOException {
		String input = "{ let mut x = 1; let mut y = &x; let mut z = &mut y; { let mut w = 1; *z = &w; } }";
		checkInvalid(input);
	}

	@Test
	public void test_0x035A() throws IOException {
		// FIXME: support moving out of boxes
		String input = "{ let mut x = 123; let mut y = box &mut x; let mut z = !*y; }";
		checkInvalid(input);
	}
	@Test
	public void test_0x035B() throws IOException {
		// NOTE: this test case appears to be something of an issue. It conflicts with
		// core invalid #59 and the issue of strong updaes for boxes.
		String input = "{ let mut x = 123; let mut y = box &mut x; let mut z = *y; *y = !z; }";
		checkInvalid(input);
	}

	@Test
	public void test_0x035C() throws IOException {
		String input = "{ let mut x = 0; let mut y = &mut x; let mut z = &mut y; *z = z; }";
		checkInvalid(input);
	}

	@Test
	public void test_0x035D() throws IOException {
		String input = "{ let mut x = 0; let mut y = &mut x; let mut z = &mut y; *z = !z; }";
		checkInvalid(input);
	}

	@Test
	public void test_0x035E() throws IOException {
		String input = "{ let mut x = 1; let mut y = &mut x; { let mut x = 123; let mut z = &x; *z } }";
		checkInvalid(input);
	}

	@Test
	public void test_0x035F() throws IOException {
		String input = "{ let mut x = 1; let mut y = &mut x; { let mut w = 123; let mut z = &x; *z } }";
		checkInvalid(input);
	}

	@Test
	public void test_0x0360() throws IOException {
		String input = "{ let mut x = 0; { let mut y = &mut x; y = &mut y; } }";
		checkInvalid(input);
	}

	@Test
	public void test_0x0361() throws IOException {
		String input = "{ let mut x = 0; { let mut y = &mut x; y = &y; } }";
		checkInvalid(input);
	}

	@Test
	public void test_0x0362() throws IOException {
		String input = "{ let mut x = box 0; { let mut y = &mut x; *x = 0; } }";
		checkInvalid(input);
	}

	@Test
	public void test_0x0363() throws IOException {
		String input = "{ let mut x = 0; { let mut y = &mut x; *y = x; } }";
		checkInvalid(input);
	}

	@Test
	public void test_0x0364() throws IOException {
		String input = "{ let mut x = 0; { let mut y = &mut x; *y = !x; } }";
		checkInvalid(input);
	}

	@Test
	public void test_0x0365() throws IOException {
		String input = "{ let mut x = box 0; let mut y = x; let mut z = x; }";
		checkInvalid(input);
	}

	@Test
	public void test_0x0366() throws IOException {
		String input = "{ let mut x = box 0; let mut y = !x; let mut z = x; }";
		checkInvalid(input);
	}

	@Test
	public void test_0x0367() throws IOException {
		String input = "{ let mut x = box 0; let mut y = x; let mut z = !x; }";
		checkInvalid(input);
	}

	@Test
	public void test_0x0368() throws IOException {
		String input = "{ let mut x = box 0; let mut y = x; let mut z = *x; }";
		checkInvalid(input);
	}

	@Test
	public void test_0x0369() throws IOException {
		String input = "{ let mut x = box 0; let mut y = x; let mut z = !*x; }";
		checkInvalid(input);
	}

	@Test
	public void test_0x036A() throws IOException {
		String input = "{ let mut x = box 0; let mut y = x; let mut z = &x; }";
		checkInvalid(input);
	}

	@Test
	public void test_0x036B() throws IOException {
		String input = "{ let mut x = box 0; let mut y = x; let mut z = box x; }";
		checkInvalid(input);
	}

	@Test
	public void test_0x036C() throws IOException {
		String input = "{ let mut x = box 0; let mut y = x; let mut z = box !x; }";
		checkInvalid(input);
	}

	@Test
	public void test_0x036D() throws IOException {
		String input = "{ let mut x = box 0; let mut y = x; *y = *x; }";
		checkInvalid(input);
	}

	@Test
	public void test_0x036E() throws IOException {
		String input = "{ let mut x = box 0; let mut y = x; *y = !*x; }";
		checkInvalid(input);
	}

	@Test
	public void test_0x036F() throws IOException {
		String input = "{ let mut x = box 0; { let mut y = &mut x; *y = box *x; } }";
		checkInvalid(input);
	}

	@Test
	public void test_0x0370() throws IOException {
		String input = "{ let mut x = box 0; { let mut y = &mut x; *y = box !*x; } }";
		checkInvalid(input);
	}

	@Test
	public void test_0x0371() throws IOException {
		String input = "{ let mut x = 0; { let mut y = box &mut x; x = x; } }";
		checkInvalid(input);
	}

	@Test
	public void test_0x0372() throws IOException {
		String input = "{ let mut x = 0; { let mut y = box &mut x; x = !x; } }";
		checkInvalid(input);
	}

	@Test
	public void test_0x0373() throws IOException {
		String input = "{ let mut x = 0; { let mut y = box &mut x; *y = &mut y; } }";
		checkInvalid(input);
	}

	@Test
	public void test_0x0374() throws IOException {
		String input = "{ let mut x = 1; let mut y = 1; let mut z = &mut x; let mut p = &mut z; *p = &mut y; x}";
		checkInvalid(input);
	}

	@Test
	public void test_0x0375() throws IOException {
		String input = "{ let mut x = 1; let mut y = 1; let mut p = box &mut x; *p = &mut y; x}";
		checkInvalid(input);
	}

	@Test
	public void test_0x0376() throws IOException {
		String input = "{ let mut x = 123; let mut y = box &mut x; let mut z = *y; *y }";
		checkInvalid(input);
	}

	@Test
	public void test_0x0377() throws IOException {
		String input = "{ let mut x = 123; let mut y = box &mut x; let mut z = !*y; *y }";
		checkInvalid(input);
	}

	@Test
	public void test_0x0378() throws IOException {
		// Moved out of box
		String input = "{ let mut x = 123; let mut y = box box x; let mut z = *y; *y }";
		checkInvalid(input);
	}

	@Test
	public void test_0x0379() throws IOException {
		String input = "{ let mut x = 123; let mut y = box box x; let mut z = !*y; *y }";
		checkInvalid(input);
	}

	@Test
	public void test_0x037A() throws IOException {
		String input = "{ let mut x = 123; let mut y = box &mut x; let mut z = *y; let mut w = *y; }";
		checkInvalid(input);
	}

	@Test
	public void test_0x037B() throws IOException {
		String input = "{ let mut x = 123; let mut y = box &mut x; let mut z = !*y; let mut w = *y; }";
		checkInvalid(input);
	}

	@Test
	public void test_0x037C() throws IOException {
		String input = "{ let mut x = 123; let mut y = box &mut x; let mut z = *y; let mut w = !*y; }";
		checkInvalid(input);
	}

	@Test
	public void test_0x037D() throws IOException {
		String input = "{ let mut x = 123; let mut y = box box x; let mut z = *y; let mut w = *y; }";
		checkInvalid(input);
	}

	@Test
	public void test_0x037E() throws IOException {
		String input = "{ let mut x = 123; let mut y = box box x; let mut z = !*y; let mut w = *y; }";
		checkInvalid(input);
	}

	@Test
	public void test_0x037F() throws IOException {
		String input = "{ let mut x = 123; let mut y = box box x; let mut z = *y; let mut w = !*y; }";
		checkInvalid(input);
	}

	@Test
	public void test_0x0380() throws IOException {
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
	public void test_0x0381() throws IOException {
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
	public void test_0x0382() throws IOException {
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
	public void test_0x0383() throws IOException {
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
	public void test_0x0384() throws IOException {
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
	public void test_0x0385() throws IOException {
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
	public void test_0x0386() throws IOException {
		// Moved out of box
		String input = "{ let mut x = 123; let mut y = box box x; let mut z = *y; let mut w = &y; }";
		checkInvalid(input);
	}

	@Test
	public void test_0x0387() throws IOException {
		String input = "{ let mut x = 123; let mut y = &x; let mut z = &mut y; **z = 1; }";
		checkInvalid(input);
	}

	@Test
	public void test_0x0388() throws IOException {
		String input = "{ let mut x1 = 1; let mut x2 = 123; let mut y = &x1; let mut z = &mut y; *z = &mut x2; }";
		checkInvalid(input);
	}

	@Test
	public void test_0x0389() throws IOException {
		String input = "{ let mut x = 1; let mut y = box &mut x; **y = 123; **y }";
		checkInvalid(input);
	}

	@Test
	public void test_0x038A() throws IOException {
		String input = "{ let mut x = box 1; let mut y = &mut x; **y = 123; **y }";
		checkInvalid(input);
	}

	// ==============================================================
	// Reborrowing Examples
	// ==============================================================

	@Test
	public void test_0x0400() throws IOException {
		String input = "{ let mut x = 1; let mut y = &mut x; { let mut z = &mut *y; *z = 123; } !*y }";
		check(input,OneTwoThree);
	}

	@Test
	public void test_0x0401() throws IOException {
		String input = "{ let mut x = 1; let mut y = &mut x; let mut z = &*y; !*y }";
		check(input,One);
	}

	@Test
	public void test_0x0402() throws IOException {
		String input = "{ let mut x = 1; let mut y = &mut x; let mut z = &mut *y; !*z }";
		check(input,One);
	}

	@Test
	public void test_0x0403() throws IOException {
		String input = "{ let mut x = 1; let mut y = &x; let mut z = &*y; !*z }";
		check(input,One);
	}

	@Test
	public void test_0x0405() throws IOException {
		String input = "{ let mut x = 1; let mut y = &mut x; let mut z = &mut *y; let mut w = &mut *z; !*w }";
		check(input,One);
	}

	@Test
	public void test_0x0406() throws IOException {
		String input = "{ let mut x = 1; let mut y = box x; { let mut z = &mut *y; *z = 123; } *y }";
		check(input,OneTwoThree);
	}

	@Test
	public void test_0x0407() throws IOException {
		String input = "{ let mut x = box 1; { let mut y = &mut *x; *y = 123; } *x }";
		check(input,OneTwoThree);
	}

	@Test
	public void test_0x0408() throws IOException {
		String input = "{ let mut x = box 1; let mut y = box &*x ; *y = &*x;}";
		check(input,Value.Unit);
	}

	@Test
	public void test_0x0450() throws IOException {
		String input = "{ let mut x = 1; let mut y = &mut x; let mut z = &*y; *z }";
		checkInvalid(input);
	}

	@Test
	public void test_0x0451() throws IOException {
		String input = "{ let mut x = 1; let mut y = &mut x; let mut z = &mut *y; let mut w = &mut *z; *w }";
		checkInvalid(input);
	}

	@Test
	public void test_0x0452() throws IOException {
		String input = "{ let mut x = 1; let mut y = &mut x; let mut z = &mut *y; *y }";
		checkInvalid(input);
	}

	@Test
	public void test_0x0453() throws IOException {
		String input = "{ let mut x = 1; let mut y = &mut x; let mut z = &*y; *y = 2; }";
		checkInvalid(input);
	}

	@Test
	public void test_0x0454() throws IOException {
		String input = "{ let mut x = 1; let mut y = &mut x; let mut z = &*y; *z = 2; }";
		checkInvalid(input);
	}

	@Test
	public void test_0x0455() throws IOException {
		String input = "{ let mut x = 1; let mut y = &mut x; let mut z = &mut *y; let mut w = &mut *y; }";
		checkInvalid(input);
	}

	@Test
	public void test_0x0456() throws IOException {
		String input = "{ let mut x = 1; let mut y = &mut x; let mut z = &mut *y; let mut w = &mut *z; *y }";
		checkInvalid(input);
	}


	@Test
	public void test_0x0457() throws IOException {
		String input = "{ let mut x = 1; let mut y = &mut x; let mut z = &mut *y; let mut w = &mut *z; *z }";
		checkInvalid(input);
	}

	@Test
	public void test_0x0458() throws IOException {
		String input = "{ let mut x = 1; let mut y = &mut x; let mut z = &*y; let mut w = &mut *z; }";
		checkInvalid(input);
	}

	@Test
	public void test_0x0459() throws IOException {
		String input = "{ let mut x = 1; let mut y = &mut x; let mut z = &mut *y; !*y }";
		checkInvalid(input);
	}

	@Test
	public void test_0x045A() throws IOException {
		String input = "{ let mut x = 1; let mut y = &mut x; let mut z = &mut *y; let mut w = &mut *z; *w }";
		checkInvalid(input);
	}

	@Test
	public void test_0x045B() throws IOException {
		// NOTE: this is accepted by rust!
		String input = "{ let mut x = 0 ; { let mut y = &mut x ; y = &mut *y ; x = *y } }";
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
			typing.apply(BorrowChecker.EMPTY_ENVIRONMENT, globalLifetime, stmt);
			// Execute block in outermost lifetime "*")
			Pair<State, Term> state = new Pair<>(new State(),stmt);
			// Execute continually until all reductions complete (or exception)
			Term result = semantics.execute(globalLifetime, state.second());
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
			typing.apply(BorrowChecker.EMPTY_ENVIRONMENT, globalLifetime, stmt);
			//
			fail("test shouldn't have passed borrow checking");
		} catch (SyntaxError e) {
			// If we get here, then the borrow checker raised an exception
			e.outputSourceError(System.out);
		}
	}

	public static final OperationalSemantics SEMANTICS = new OperationalSemantics();
}
