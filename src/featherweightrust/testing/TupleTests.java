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
import featherweightrust.extensions.Tuples;
import featherweightrust.extensions.Tuples.Syntax;

import static featherweightrust.core.Syntax.Value.Unit;

public class TupleTests {
	private static Value.Integer One = new Value.Integer(1);
	private static Value.Integer Two = new Value.Integer(2);
	private static Value PairOneOne = new Syntax.TupleValue(One,One);
	private static Value PairOneTwo = new Syntax.TupleValue(One,Two);

	@Test
	public void test_0x0001() throws IOException {
		String input = "{ (1,2) }";
		check(input,PairOneTwo);
	}

	@Test
	public void test_0x0002() throws IOException {
		String input = "{ let mut x = (1,2); }";
		check(input,Unit);
	}

	@Test
	public void test_0x0003() throws IOException {
		String input = "{ let mut x = 1; (x,2)}";
		check(input,PairOneTwo);
	}

	@Test
	public void test_0x0004() throws IOException {
		String input = "{ let mut x = 1; let mut y = 2; (x,y)}";
		check(input,PairOneTwo);
	}


	@Test
	public void test_0x0005() throws IOException {
		String input = "{ let mut x = 1; let mut y = 2; (!x,y)}";
		check(input,PairOneTwo);
	}

	@Test
	public void test_0x0006() throws IOException {
		String input = "{ let mut x = 1; let mut y = 2; (x,!y)}";
		check(input,PairOneTwo);
	}


	@Test
	public void test_0x0007() throws IOException {
		String input = "{ let mut x = 1; let mut y = 2; (!x,!y)}";
		check(input,PairOneTwo);
	}

	@Test
	public void test_0x0008() throws IOException {
		String input = "{ let mut x = (0,0); x = (1,2); x}";
		check(input,PairOneTwo);
	}

	@Test
	public void test_0x0009() throws IOException {
		String input = "{ let mut x = (0,0); x = (1,2); !x}";
		check(input,PairOneTwo);
	}

	@Test
	public void test_0x000A() throws IOException {
		String input = "{ let mut x = 1; let mut y = (&x,&x); let mut z = y.0; !*z}";
		check(input,One);
	}

	@Test
	public void test_0x000B() throws IOException {
		String input = "{ let mut x = 1; let mut y = (&x,&x); let mut z = y.1; !*z}";
		check(input,One);
	}

	@Test
	public void test_0x000C() throws IOException {
		String input = "{ let mut x = 1; let mut y = (&mut x,2); let mut z = y.0; !*z}";
		check(input,One);
	}

	@Test
	public void test_0x000D() throws IOException {
		String input = "{ let mut x = 1; let mut y = (&mut x,2); let mut z = y.1; z}";
		check(input,Two);
	}

	@Test
	public void test_0x000E() throws IOException {
		String input = "{ let mut x = (1,2); x.0 }";
		check(input,One);
	}


	@Test
	public void test_0x000F() throws IOException {
		String input = "{ let mut x = (1,2); !x.0 }";
		check(input,One);
	}

	@Test
	public void test_0x0010() throws IOException {
		String input = "{ let mut x = (1,2); x.1 }";
		check(input,Two);
	}


	@Test
	public void test_0x0011() throws IOException {
		String input = "{ let mut x = (1,2); !x.1 }";
		check(input,Two);
	}

	@Test
	public void test_0x0012() throws IOException {
		String input = "{ let mut x = ((1,2),3); x.0 }";
		check(input,PairOneTwo);
	}

	@Test
	public void test_0x0013() throws IOException {
		String input = "{ let mut x = (3, (1,2)); x.1 }";
		check(input,PairOneTwo);
	}

	@Test
	public void test_0x0014() throws IOException {
		String input = "{ let mut x = (1,2); let mut y = &x.0; !*y }";
		check(input,One);
	}

	@Test
	public void test_0x0015() throws IOException {
		String input = "{ let mut x = (1,2); let mut y = &x.1; !*y }";
		check(input,Two);
	}

	@Test
	public void test_0x0016() throws IOException {
		String input = "{ let mut x = (1,2); let mut y = &x.0; !x }";
		check(input,PairOneTwo);
	}


	@Test
	public void test_0x0017() throws IOException {
		String input = "{ let mut x = (1,2); let mut y = &x.1; !x }";
		check(input,PairOneTwo);
	}


	@Test
	public void test_0x0018() throws IOException {
		String input = "{ let mut x = (1,2); let mut y = &mut x.0; x.1 }";
		check(input,Two);
	}


	@Test
	public void test_0x0019() throws IOException {
		String input = "{ let mut x = (1,2); let mut y = &mut x.1; x.0 }";
		check(input,One);
	}


	@Test
	public void test_0x001A() throws IOException {
		String input = "{ let mut x = (0,2); { let mut y = &mut x.0; *y = 1; } x}";
		check(input,PairOneTwo);
	}

	@Test
	public void test_0x001B() throws IOException {
		String input = "{ let mut x = (1,0); { let mut y = &mut x.1; *y = 2; } x}";
		check(input,PairOneTwo);
	}

	@Test
	public void test_0x001C() throws IOException {
		String input = "{ let mut x = (box 1, box 2); let mut y = x.0; let mut z = x.1; *y }";
		check(input,One);
	}

	@Test
	public void test_0x001D() throws IOException {
		String input = "{ let mut x = (box 1, box 2); let mut y = x.0; let mut z = x.1; *z }";
		check(input,Two);
	}

	@Test
	public void test_0x001E() throws IOException {
		String input = "{ let mut x1 = 1; let mut x2 = 2; let mut y = (&mut x1, &mut x2); let mut z = y.0; let mut w = y.1; !*w }";
		check(input,Two);
	}

	@Test
	public void test_0x001F() throws IOException {
		String input = "{ let mut x1 = 1; let mut x2 = 2; let mut y = (&mut x1, &mut x2); let mut z = y.0; let mut w = y.1; !*z }";
		check(input,One);
	}

	@Test
	public void test_0x0020() throws IOException {
		String input = "{ let mut x = 1; let mut y = (&mut x, 2); let mut z = y.0; y.1 }";
		check(input,Two);
	}

	@Test
	public void test_0x0021() throws IOException {
		String input = "{ let mut x = 1; let mut y = (2, &mut x); let mut z = y.1; y.0 }";
		check(input,Two);
	}

	@Test
	public void test_0x0022() throws IOException {
		String input = "{ let mut x = (1, 2); let mut y = !x.0; x.0 }";
		check(input,One);
	}

	@Test
	public void test_0x0023() throws IOException {
		String input = "{ let mut x = 1; let mut y = (&mut x, 2); let mut z = !y.1; y.1 }";
		check(input,Two);
	}

	@Test
	public void test_0x0024() throws IOException {
		String input = "{ let mut x = 1; let mut y = (2, &mut x); let mut z = !y.0; y.0 }";
		check(input,Two);
	}

	@Test
	public void test_0x0025() throws IOException {
		String input = "{ let mut x = (0,2); x.0 = 1; x}";
		check(input,PairOneTwo);
	}

	@Test
	public void test_0x0026() throws IOException {
		String input = "{ let mut x = (1,1); x.1 = 2; x}";
		check(input,PairOneTwo);
	}

	@Test
	public void test_0x0027() throws IOException {
		String input = "{ let mut x = (0,0); x.0 = 1; x.1 = 2; x}";
		check(input,PairOneTwo);
	}

	@Test
	public void test_0x0028() throws IOException {
		String input = "{ let mut x = (1,2); let mut y = &x.0; !*y}";
		check(input,One);
	}

	@Test
	public void test_0x0029() throws IOException {
		String input = "{ let mut x = (1,2); let mut y = &x.1; !*y}";
		check(input,Two);
	}

	@Test
	public void test_0x002A() throws IOException {
		String input = "{ let mut x = (1,2); let mut y = &x.1; let mut z = !*y; !x}";
		check(input,PairOneTwo);
	}

	@Test
	public void test_0x002B() throws IOException {
		String input = "{ let mut x = (1,2); let mut y = &mut x.0; x.1 }";
		check(input,Two);
	}

	@Test
	public void test_0x002C() throws IOException {
		String input = "{ let mut x = (1,2); let mut y = &mut x.1; x.0 }";
		check(input,One);
	}

	@Test
	public void test_0x002D() throws IOException {
		String input = "{ let mut x = (0,2); { let mut y = &mut x.0; *y = 1; } x }";
		check(input,PairOneTwo);
	}

	@Test
	public void test_0x002E() throws IOException {
		String input = "{ let mut x = (1,0); { let mut y = &mut x.1; *y = 2; } x }";
		check(input,PairOneTwo);
	}

	@Test
	public void test_0x002F() throws IOException {
		String input = "{ let mut x = (box 0,box 2); let mut y = x.0; x.0 = box 1; (*(x.0), *x.1) }";
		check(input,PairOneTwo);
	}

	@Test
	public void test_0x0030() throws IOException {
		String input = "{ let mut x = (box 1,box 1); let mut y = x.1; x.1 = box 2; (*x.0, *x.1) }";
		check(input,PairOneTwo);
	}

	@Test
	public void test_0x0031() throws IOException {
		// test for carry typing
		String input = "{ let mut x = 1; { let mut y = (&x,&x); } let mut z = &mut x; !*z }";
		check(input,One);
	}

	@Test
	public void test_0x0050() throws IOException {
		String input = "{ let mut x = (); }";
		checkInvalid(input);
	}

	@Test
	public void test_0x0051() throws IOException {
		String input = "{ let mut x = (x,1); }";
		checkInvalid(input);
	}

	@Test
	public void test_0x0052() throws IOException {
		String input = "{ let mut x = (1,x); }";
		checkInvalid(input);
	}

	@Test
	public void test_0x0053() throws IOException {
		String input = "{ let mut x = box 1; (x,x)}";
		checkInvalid(input);
	}

	@Test
	public void test_0x0054() throws IOException {
		String input = "{ let mut x = box 1; let mut y = box 1; let mut z = !(x,y)}";
		checkInvalid(input);
	}


	@Test
	public void test_0x0055() throws IOException {
		String input = "{ let mut x = box 1; let mut y = !(1,x)}";
		checkInvalid(input);
	}

	@Test
	public void test_0x0056() throws IOException {
		String input = "{ let mut x = box 1; let mut y = !(x,1)}";
		checkInvalid(input);
	}

	@Test
	public void test_0x0057() throws IOException {
		String input = "{ let mut x = (1, 2); let mut y = x.0; x.0 }";
		checkInvalid(input);
	}

	@Test
	public void test_0x0058() throws IOException {
		String input = "{ let mut x = 1; let mut y = (&mut x, 2); let mut z = y.1; y.1 }";
		checkInvalid(input);
	}

	@Test
	public void test_0x0059() throws IOException {
		String input = "{ let mut x = (1,2); x = 1; }";
		checkInvalid(input);
	}

	@Test
	public void test_0x005A() throws IOException {
		String input = "{ let mut x = 1; x = (1,2); }";
		checkInvalid(input);
	}

	@Test
	public void test_0x005B() throws IOException {
		String input = "{ let mut x = 0; let mut y = (&mut x,&mut x); }";
		checkInvalid(input);
	}
	@Test
	public void test_0x005C() throws IOException {
		String input = "{ let mut x = 1; let mut y = (&x,&x); let mut z = y.0; *z}";
		checkInvalid(input);
	}
	@Test
	public void test_0x005D() throws IOException {
		String input = "{ let mut x = 1; let mut y = (&x,&x); let mut z = y.1; *z}";
		checkInvalid(input);
	}

	@Test
	public void test_0x005E() throws IOException {
		String input = "{ let mut x = (1,2); let mut y = &x.0; *y }";
		checkInvalid(input);
	}

	@Test
	public void test_0x005F() throws IOException {
		String input = "{ let mut x = (1,2); let mut y = &x.1; *y }";
		checkInvalid(input);
	}

	@Test
	public void test_0x0060() throws IOException {
		String input = "{ let mut x = (1,2); let mut y = &x.0; x }";
		checkInvalid(input);
	}


	@Test
	public void test_0x0061() throws IOException {
		String input = "{ let mut x = 0; let mut y = 1; let mut z = (&mut x,&mut x,&mut y); }";
		checkInvalid(input);
	}

	@Test
	public void test_0x0062() throws IOException {
		String input = "{ let mut x = 0; let mut y = 1; let mut z = (&mut x,&mut y,&mut x); }";
		checkInvalid(input);
	}

	@Test
	public void test_0x0063() throws IOException {
		String input = "{ let mut x = 0; let mut y = 1; let mut z = (&mut y,&mut x,&mut x); }";
		checkInvalid(input);
	}

	@Test
	public void test_0x0064() throws IOException {
		String input = "{ let mut x = 0; let mut y = (&mut x,1); let mut z = &mut x;}";
		checkInvalid(input);
	}

	@Test
	public void test_0x0065() throws IOException {
		String input = "{ let mut x = 0; let mut y = (1,&mut x); let mut z = &mut x;}";
		checkInvalid(input);
	}

	@Test
	public void test_0x0066() throws IOException {
		String input = "{ let mut x = (0,0); let mut y = &mut x.0; x}";
		checkInvalid(input);
	}

	@Test
	public void test_0x0067() throws IOException {
		String input = "{ let mut x = (0,0); let mut y = &mut x.1; x}";
		checkInvalid(input);
	}

	@Test
	public void test_0x0068() throws IOException {
		String input = "{ let mut x = (0,0); let mut y = &mut x.0; *y = 2; x}";
		checkInvalid(input);
	}

	@Test
	public void test_0x0069() throws IOException {
		String input = "{ let mut x = (0,0); let mut y = &mut x.1; *y = 2; x}";
		checkInvalid(input);
	}

	@Test
	public void test_0x0070() throws IOException {
		String input = "{ let mut x = (box 1, box 2); let mut y = x.0; let mut z = x.0; }";
		checkInvalid(input);
	}

	@Test
	public void test_0x0071() throws IOException {
		String input = "{ let mut x = (box 1, box 2); let mut y = x.1; let mut z = x.1; }";
		checkInvalid(input);
	}

	@Test
	public void test_0x0072() throws IOException {
		String input = "{ let mut x = 1; let mut y = (&mut x, 0); let mut z = y.0; let mut w = y.0; }";
		checkInvalid(input);
	}

	@Test
	public void test_0x0073() throws IOException {
		String input = "{ let mut x = 1; let mut y = (0, &mut x); let mut z = y.1; let mut w = y.1; }";
		checkInvalid(input);
	}

	@Test
	public void test_0x0074() throws IOException {
		// Partial moves not supported
		String input = "{ let mut x = 1; let mut y = (&mut x, 0); let mut z = y.0; let mut w = y; }";
		checkInvalid(input);
	}

	@Test
	public void test_0x0075() throws IOException {
		// Partial moves not supported
		String input = "{ let mut x = 1; let mut y = (0, &mut x); let mut z = y.1; let mut w = y; }";
		checkInvalid(input);
	}

	@Test
	public void test_0x0076() throws IOException {
		// This tests a "copymove" operation.
		String input = "{ let mut x = 1; let mut y = (&mut x,2); let mut z = y; y.1 }";
		checkInvalid(input);
	}

	@Test
	public void test_0x0077() throws IOException {
		String input = "{ let mut x = 1; let mut y = (&mut x, 2); let mut z = y.0; y.0 }";
		checkInvalid(input);
	}

	@Test
	public void test_0x0078() throws IOException {
		String input = "{ let mut x = 1; let mut y = (2, &mut x); let mut z = y.1; y.1 }";
		checkInvalid(input);
	}

	@Test
	public void test_0x0079() throws IOException {
		String input = "{ let mut x = (0,0); x.0 = (1,2); }";
		checkInvalid(input);
	}

	@Test
	public void test_0x007A() throws IOException {
		String input = "{ let mut x = (0,0); x.1 = (1,2); }";
		checkInvalid(input);
	}

	@Test
	public void test_0x007B() throws IOException {
		String input = "{ let mut x = (0,0); let mut y = &mut x.0; x.0 }";
		checkInvalid(input);
	}

	@Test
	public void test_0x007C() throws IOException {
		String input = "{ let mut x = (0,0); let mut y = &mut x.1; x.1 }";
		checkInvalid(input);
	}

	@Test
	public void test_0x007D() throws IOException {
		String input = "{ let mut x = (box 0, box 0); let mut y = x.0; let mut z = &x; }";
		checkInvalid(input);
	}

	@Test
	public void test_0x007E() throws IOException {
		String input = "{ let mut x = (box 0, box 0); let mut y = 1; x.0 = &y; }";
		checkInvalid(input);
	}

	@Test
	public void test_0x007F() throws IOException {
		String input = "{ let mut x = (1,2); let mut y = x.2; }";
		checkInvalid(input);
	}

	@Test
	public void test_0x0080() throws IOException {
		String input = "{ let mut x = (1,2,3); let mut y = x.3; }";
		checkInvalid(input);
	}

	@Test
	public void test_0x0081() throws IOException {
		String input = "{ let mut x1 = 1; let mut x2 = 2; let mut y = (&mut x1, &mut x2); let mut z = y.0; let mut w = y.1; *w }";
		checkInvalid(input);
	}

	@Test
	public void test_0x0082() throws IOException {
		String input = "{ let mut x1 = 1; let mut x2 = 2; let mut y = (&mut x1, &mut x2); let mut z = y.0; let mut w = y.1; *z }";
		checkInvalid(input);
	}

	public static void checkInvalid(String input) throws IOException {
		CoreTests.checkInvalid(input, new Tuples.Checker(false,input));
	}

	public static void check(String input, Value output) throws IOException {
		// Reuse existing checking facility
		CoreTests.check(input, output, Tuples.SEMANTICS, new Tuples.Checker(false,input));
	}


}
