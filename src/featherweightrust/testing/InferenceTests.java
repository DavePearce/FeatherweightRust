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

import featherweightrust.core.Syntax.Value;
import static featherweightrust.testing.CoreTests.*;

/**
 * Runtime test cases for the core syntax. Each test should pass borrow checking
 * and execute without raising a fault.
 *
 * @author David J. Pearce
 *
 */
public class InferenceTests {
	private static Value.Integer One = new Value.Integer(1);
	private static Value.Integer OneTwoThree = new Value.Integer(123);

	// ==============================================================
	// Straightforward Examples
	// ==============================================================

	@Test
	public void test_0x0001() throws IOException {
		String input = "{ let mut x = 123; ?x }";
		check(input,OneTwoThree);
	}

	@Test
	public void test_0x0003() throws IOException {
		String input = "{ let mut x = 123; let mut y = ?x; ?y}";
		check(input, OneTwoThree);
	}

	@Test
	public void test_0x0006() throws IOException {
		String input = "{ let mut x = 1; let mut y = 123; ?y}";
		check(input, OneTwoThree);
	}

	@Test
	public void test_0x0008() throws IOException {
		String input = "{ let mut x = 123; let mut y = 1; ?x}";
		check(input, OneTwoThree);
	}

	@Test
	public void test_0x000B() throws IOException {
		String input = "{ let mut x = 1; let mut y = 123; x = 2; ?y}";
		check(input, OneTwoThree);
	}

	@Test
	public void test_0x000D() throws IOException {
		String input = "{ let mut x = 1; { let mut y = 123; ?y } }";
		check(input, OneTwoThree);
	}

	@Test
	public void test_0x000F() throws IOException {
		String input = "{ let mut x = 1; { x = 123; } ?x }";
		check(input, OneTwoThree);
	}

	@Test
	public void test_0x0011() throws IOException {
		String input = "{ let mut x = box 0; { let mut y = ?x; x = box 1; } ?*x }";
		check(input, One);
	}

	@Test
	public void test_0x0013() throws IOException {
		String input = "{ let mut x = box 0; x = box 1; ?*x }";
		check(input, One);
	}

	@Test
	public void test_0x0050() throws IOException {
		String input = "{ let mut x = 123; let mut y = ?x; ?x }";
		check(input,OneTwoThree);
	}

	@Test
	public void test_0x0052() throws IOException {
		String input = "{ let mut x = 123; let mut y = &mut x; let mut z = ?y; ?y }";
		checkInvalid(input);
	}

	@Test
	public void test_0x0057() throws IOException {
		String input = "{ let mut x = box 1; let mut y = ?x; *x = 123; }";
		checkInvalid(input);
	}


	// ==============================================================
	// Allocation Examples
	// ==============================================================

	@Test
	public void test_0x0100() throws IOException {
		String input = "{ let mut x = box 123; ?*x }";
		check(input, OneTwoThree);
	}

	@Test
	public void test_0x0101() throws IOException {
		String input = "{ let mut y = box 123; let mut x = ?*y; ?x }";
		check(input,OneTwoThree);
	}

	@Test
	public void test_0x0102() throws IOException {
		String input = "{ let mut y = box 123; let mut x = ?*y; ?*y }";
		check(input,OneTwoThree);
	}

	@Test
	public void test_0x0103() throws IOException {
		String input = "{ let mut y = 123; let mut z = box &y; let mut x = ?*z; ?*x }";
		check(input, OneTwoThree);
	}

	@Test
	public void test_0x0105() throws IOException {
		String input = "{ let mut y = 123; let mut z = box &y; let mut x = ?*z; ?**z }";
		check(input, OneTwoThree);
	}

	@Test
	public void test_0x0106() throws IOException {
		String input = "{ let mut x = box 1; *x = 123; ?*x }";
		check(input, OneTwoThree);
	}

	@Test
	public void test_0x0108() throws IOException {
		String input = "{ let mut x = box 123; let mut y = box 1; *y = 2; ?*x }";
		check(input, OneTwoThree);
	}

	@Test
	public void test_0x010A() throws IOException {
		String input = "{ let mut x = 123; let mut y = box box ?x; let mut z = ?*y; ?*z }";
		check(input, OneTwoThree);
	}

	@Test
	public void test_0x010B() throws IOException {
		String input = "{ let mut x = box box 1; **x = 123; ?**x }";
		check(input,OneTwoThree);
	}

	@Test
	public void test_0x010D() throws IOException {
		String input = "{ let mut x = box 1; { let mut y = box 123; x = ?y; } ?*x }";
		check(input,OneTwoThree);
	}

	@Test
	public void test_0x010E() throws IOException {
		String input = "{ let mut x = box 1; { let mut y = box 123; x = ?y; } ?*x }";
		check(input,OneTwoThree);
	}

	@Test
	public void test_0x010F() throws IOException {
		String input = "{ let mut x = box box 123; let mut y = ?*x; ?*y }";
		check(input,OneTwoThree);
	}

	@Test
	public void test_0x0150() throws IOException {
		String input = "{ let mut y = 123; let mut z = box &y; let mut x = ?*z; ?*x }";
		check(input,OneTwoThree);
	}

	@Test
	public void test_0x0152() throws IOException {
		String input = "{ let mut y = 123; let mut z = box &y; let mut x = ?*z; ?**z }";
		check(input,OneTwoThree);
	}


	// ==============================================================
	// Immutable Borrowing Examples
	// ==============================================================

	@Test
	public void test_0x0201() throws IOException {
		String input = "{ let mut x = 123; let mut y = &x; ?*y }";
		check(input, OneTwoThree);
	}

	@Test
	public void test_0x0203() throws IOException {
		String input = "{ let mut x = 123; let mut y = &x; let mut z = ?*y; ?z }";
		check(input, OneTwoThree);
	}


	@Test
	public void test_0x0205() throws IOException {
		String input = "{ let mut x = 123; { let mut y = &x; ?*y } }";
		check(input, OneTwoThree);
	}

	@Test
	public void test_0x0206() throws IOException {
		String input = "{ let mut x = 123; { let mut y = &x; let mut z = ?y; ?*y } }";
		check(input, OneTwoThree);
	}


	@Test
	public void test_0x0250() throws IOException {
		String input = "{ let mut x = 123; let mut y = &x; ?*y }";
		check(input, OneTwoThree);
	}

	@Test
	public void test_0x0251() throws IOException {
		String input = "{ let mut x = 123; let mut y = &x; let mut z = ?*y; ?z }";
		check(input, OneTwoThree);
	}

	@Test
	public void test_0x0252() throws IOException {
		String input = "{ let mut x = 123; let mut y = &x; let mut z = ?*y; ?*y }";
		check(input, OneTwoThree);
	}

	@Test
	public void test_0x0255() throws IOException {
		// Moved out of box
		String input = "{ let mut x = 123; let mut y = box box ?x; let mut z = ?*y; ?*z }";
		check(input, OneTwoThree);
	}

	@Test
	public void test_0x0256() throws IOException {
		String input = "{ let mut x = 123; let mut y = &x; let mut z = ?*y; ?x }";
		check(input, OneTwoThree);
	}

	@Test
	public void test_0x0257() throws IOException {
		String input = "{ let mut x = 123; { let mut y = &x; let mut z = ?y; ?*y } }";
		check(input, OneTwoThree);
	}

	// ==============================================================
	// Mutable Borrowing Examples
	// ==============================================================

	@Test
	public void test_0x0300() throws IOException {
		String input = "{ let mut x = 1; let mut y = &mut x; { let mut w = 123; let mut z = &w; ?*z } }";
		check(input, OneTwoThree);
	}

	@Test
	public void test_0x0302() throws IOException {
		String input = "{ let mut x = 123; let mut y = box &mut x; let mut z = ?*y; ?*z }";
		check(input, OneTwoThree);
	}

	@Test
	public void test_0x0303() throws IOException {
		String input = "{ let mut x = 123; let mut y = box &mut x; let mut z = ?*y; *y = ?z; let mut w = ?*y; ?*w }";
		check(input, OneTwoThree);
	}

	@Test
	public void test_0x0304() throws IOException {
		String input = "{ let mut x = 123; let mut y = &x; let mut z = &mut y; ?**z }";
		check(input,OneTwoThree);
	}

	@Test
	public void test_0x0306() throws IOException {
		String input = "{ let mut x1 = 1; let mut x2 = 123; let mut y = &x1; let mut z = &mut y; *z = &x2; ?**z }";
		check(input,OneTwoThree);
	}

	@Test
	public void test_0x0308() throws IOException {
		String input = "{ let mut x = 1; let mut y = &mut x; let mut z = &mut y; **z = 123; ?**z }";
		check(input,OneTwoThree);
	}

	@Test
	public void test_0x030A() throws IOException {
		String input = "{ let mut x = 1; let mut y = box &mut x; **y = 123; ?**y }";
		check(input,OneTwoThree);
	}

	@Test
	public void test_0x030B() throws IOException {
		String input = "{ let mut x = box 1; let mut y = &mut x; **y = 123; ?**y }";
		check(input,OneTwoThree);
	}

	@Test
	public void test_0x0350() throws IOException {
		String input = "{ let mut x = 1; let mut y = &mut x; { let mut w = 123; let mut z = &w; ?*z } }";
		check(input, OneTwoThree);
	}

	@Test
	public void test_0x0352() throws IOException {
		String input = "{ let mut x = 123; let mut y = box &mut x; let mut z = ?*y; ?*z }";
		check(input, OneTwoThree);
	}

	@Test
	public void test_0x0353() throws IOException {
		String input = "{ let mut x = 123; let mut y = box &mut x; let mut z = ?*y; *y = ?z; let mut w = ?*y; ?*w }";
		check(input, OneTwoThree);
	}

	@Test
	public void test_0x0354() throws IOException {
		String input = "{ let mut x = 123; let mut y = &x; let mut z = &mut y; ?**z }";
		check(input, OneTwoThree);
	}

	@Test
	public void test_0x0355() throws IOException {
		String input = "{ let mut x = 123; let mut y = &mut x; let mut z = &y; ?*z }";
		checkInvalid(input);
	}

	@Test
	public void test_0x0356() throws IOException {
		String input = "{ let mut x1 = 1; let mut x2 = 123; let mut y = &x1; let mut z = &mut y; *z = &x2; ?**z }";
		check(input, OneTwoThree);
	}

	@Test
	public void test_0x0357() throws IOException {
		String input = "{ let mut x1 = 1; let mut x2 = 123; let mut y = &mut x1; let mut z = &mut y; *z = &mut x2; ?*z }";
		checkInvalid(input);
	}

	@Test
	public void test_0x0358() throws IOException {
		String input = "{ let mut x = 1; let mut y = &mut x; let mut z = &mut y; **z = 123; ?**z }";
		check(input, OneTwoThree);
	}

	@Test
	public void test_0x035A() throws IOException {
		// FIXME: support moving out of boxes
		String input = "{ let mut x = 123; let mut y = box &mut x; let mut z = ?*y; }";
		check(input, Value.Unit);
	}
	@Test
	public void test_0x035B() throws IOException {
		// NOTE: this test case appears to be something of an issue. It conflicts with
		// core invalid #59 and the issue of strong updaes for boxes.
		String input = "{ let mut x = 123; let mut y = box &mut x; let mut z = ?*y; *y = ?z; }";
		check(input, Value.Unit);
	}

	@Test
	public void test_0x035C() throws IOException {
		String input = "{ let mut x = 0; let mut y = &mut x; let mut z = &mut y; *z = ?z; }";
		checkInvalid(input);
	}

	@Test
	public void test_0x035E() throws IOException {
		String input = "{ let mut x = 1; let mut y = &mut x; { let mut x = 123; let mut z = &x; ?*z } }";
		checkInvalid(input);
	}

	@Test
	public void test_0x0363() throws IOException {
		String input = "{ let mut x = 0; { let mut y = &mut x; *y = ?x; } }";
		checkInvalid(input);
	}

	@Test
	public void test_0x0365() throws IOException {
		String input = "{ let mut x = box 0; let mut y = ?x; let mut z = ?x; }";
		checkInvalid(input);
	}

	@Test
	public void test_0x0366() throws IOException {
		String input = "{ let mut x = box 0; let mut y = ?x; let mut z = ?x; }";
		checkInvalid(input);
	}

	@Test
	public void test_0x0368() throws IOException {
		String input = "{ let mut x = box 0; let mut y = ?x; let mut z = ?*x; }";
		checkInvalid(input);
	}

	@Test
	public void test_0x036A() throws IOException {
		String input = "{ let mut x = box 0; let mut y = ?x; let mut z = &x; }";
		checkInvalid(input);
	}

	@Test
	public void test_0x036B() throws IOException {
		String input = "{ let mut x = box 0; let mut y = ?x; let mut z = box ?x; }";
		checkInvalid(input);
	}

	@Test
	public void test_0x036D() throws IOException {
		String input = "{ let mut x = box 0; let mut y = ?x; *y = ?*x; }";
		checkInvalid(input);
	}

	@Test
	public void test_0x036F() throws IOException {
		String input = "{ let mut x = box 0; { let mut y = &mut x; *y = box ?*x; } }";
		checkInvalid(input);
	}

	@Test
	public void test_0x0371() throws IOException {
		String input = "{ let mut x = 0; { let mut y = box &mut x; x = ?x; } }";
		checkInvalid(input);
	}

	@Test
	public void test_0x0373() throws IOException {
		String input = "{ let mut x = 0; { let mut y = box &mut x; *y = &mut y; } }";
		checkInvalid(input);
	}

	@Test
	public void test_0x0374() throws IOException {
		String input = "{ let mut x = 1; let mut y = 1; let mut z = &mut x; let mut p = &mut z; *p = &mut y; ?x}";
		checkInvalid(input);
	}

	@Test
	public void test_0x0375() throws IOException {
		String input = "{ let mut x = 1; let mut y = 1; let mut p = box &mut x; *p = &mut y; ?x}";
		checkInvalid(input);
	}

	@Test
	public void test_0x0376() throws IOException {
		String input = "{ let mut x = 123; let mut y = box &mut x; let mut z = ?*y; ?*y }";
		checkInvalid(input);
	}

	@Test
	public void test_0x0378() throws IOException {
		// Moved out of box
		String input = "{ let mut x = 123; let mut y = box box ?x; let mut z = ?*y; ?*y }";
		checkInvalid(input);
	}

	@Test
	public void test_0x037A() throws IOException {
		String input = "{ let mut x = 123; let mut y = box &mut x; let mut z = ?*y; let mut w = ?*y; }";
		checkInvalid(input);
	}

	@Test
	public void test_0x037B() throws IOException {
		String input = "{ let mut x = 123; let mut y = box &mut x; let mut z = ?*y; let mut w = ?*y; }";
		checkInvalid(input);
	}

	@Test
	public void test_0x037D() throws IOException {
		String input = "{ let mut x = 123; let mut y = box box x; let mut z = ?*y; let mut w = ?*y; }";
		checkInvalid(input);
	}

	@Test
	public void test_0x0380() throws IOException {
		String input = "{" +
				"let mut x = 123;" +
				"let mut y = 234;" +
				"let mut bx = box &mut x;" +
				"let mut p = ?*bx;" +
				"let mut q = ?bx; " +
				"}";
		checkInvalid(input);
	}

	@Test
	public void test_0x0381() throws IOException {
		String input = "{" +
				"let mut x = 123;" +
				"let mut y = 234;" +
				"let mut bx = box box ?x;" +
				"let mut p = ?*bx;" +
				"let mut q = ?bx; " +
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
				"let mut p = ?*bx;" +
				"let mut q = ?by; " +
				"q = bx;" +
				"}";
		checkInvalid(input);
	}

	@Test
	public void test_0x0383() throws IOException {
		String input = "{" +
				"let mut x = 123;" +
				"let mut y = 234;" +
				"let mut bx = box box ?x;" +
				"let mut by = box box ?y;" +
				"let mut p = ?*bx;" +
				"let mut q = ?by; " +
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
				"let mut p = ?*bx;" +
				"let mut q = ?box by; " +
				"*q = bx;" +
				"}";
		checkInvalid(input);
	}

	@Test
	public void test_0x0385() throws IOException {
		String input = "{" +
				"let mut x = 123;" +
				"let mut y = 234;" +
				"let mut bx = box box ?x;" +
				"let mut by = box box ?y;" +
				"let mut p = ?*bx;" +
				"let mut q = box ?by; " +
				"*q = bx;" +
				"}";
		checkInvalid(input);
	}

	@Test
	public void test_0x0386() throws IOException {
		// Moved out of box
		String input = "{ let mut x = 123; let mut y = box box ?x; let mut z = ?*y; let mut w = &y; }";
		checkInvalid(input);
	}

	@Test
	public void test_0x0389() throws IOException {
		String input = "{ let mut x = 1; let mut y = box &mut x; **y = 123; ?**y }";
		check(input, OneTwoThree);
	}

	@Test
	public void test_0x038A() throws IOException {
		String input = "{ let mut x = box 1; let mut y = &mut x; **y = 123; ?**y }";
		check(input, OneTwoThree);
	}

	// ==============================================================
	// Reborrowing Examples
	// ==============================================================

	@Test
	public void test_0x0400() throws IOException {
		String input = "{ let mut x = 1; let mut y = &mut x; { let mut z = &mut *y; *z = 123; } ?*y }";
		check(input,OneTwoThree);
	}

	@Test
	public void test_0x0401() throws IOException {
		String input = "{ let mut x = 1; let mut y = &mut x; let mut z = &*y; ?*y }";
		check(input,One);
	}

	@Test
	public void test_0x0402() throws IOException {
		String input = "{ let mut x = 1; let mut y = &mut x; let mut z = &mut *y; ?*z }";
		check(input,One);
	}

	@Test
	public void test_0x0403() throws IOException {
		String input = "{ let mut x = 1; let mut y = &x; let mut z = &*y; ?*z }";
		check(input,One);
	}

	@Test
	public void test_0x0405() throws IOException {
		String input = "{ let mut x = 1; let mut y = &mut x; let mut z = &mut *y; let mut w = &mut *z; ?*w }";
		check(input,One);
	}

	@Test
	public void test_0x0406() throws IOException {
		String input = "{ let mut x = 1; let mut y = box x; { let mut z = &mut *y; *z = 123; } ?*y }";
		check(input,OneTwoThree);
	}

	@Test
	public void test_0x0407() throws IOException {
		String input = "{ let mut x = box 1; { let mut y = &mut *x; *y = 123; } ?*x }";
		check(input,OneTwoThree);
	}

	@Test
	public void test_0x0450() throws IOException {
		String input = "{ let mut x = 1; let mut y = &mut x; let mut z = &*y; ?*z }";
		check(input, One);
	}

	@Test
	public void test_0x0451() throws IOException {
		String input = "{ let mut x = 1; let mut y = &mut x; let mut z = &mut *y; let mut w = &mut *z; ?*w }";
		check(input, One);
	}

	@Test
	public void test_0x0452() throws IOException {
		String input = "{ let mut x = 1; let mut y = &mut x; let mut z = &mut *y; ?*y }";
		checkInvalid(input);
	}

	@Test
	public void test_0x0456() throws IOException {
		String input = "{ let mut x = 1; let mut y = &mut x; let mut z = &mut *y; let mut w = &mut *z; ?*y }";
		checkInvalid(input);
	}


	@Test
	public void test_0x0457() throws IOException {
		String input = "{ let mut x = 1; let mut y = &mut x; let mut z = &mut *y; let mut w = &mut *z; ?*z }";
		checkInvalid(input);
	}

	@Test
	public void test_0x0459() throws IOException {
		String input = "{ let mut x = 1; let mut y = &mut x; let mut z = &mut *y; ?*y }";
		checkInvalid(input);
	}

	@Test
	public void test_0x045A() throws IOException {
		String input = "{ let mut x = 1; let mut y = &mut x; let mut z = &mut *y; let mut w = &mut *z; ?*w }";
		check(input, One);
	}
}