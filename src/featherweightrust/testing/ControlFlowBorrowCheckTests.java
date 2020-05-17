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

import org.junit.jupiter.api.Test;

import featherweightrust.core.BorrowChecker;
import featherweightrust.extensions.ControlFlow;

/**
 * Borrow checking tests for the core syntax. Each test should fail to borrow
 * check.
 *
 * @author David J. Pearce
 *
 */
public class ControlFlowBorrowCheckTests {

	// ==============================================================
	// Straightforward Examples
	// ==============================================================

	@Test
	public void test_01() throws IOException {
		String input = "{ if x == x { } else { } }";
		checkInvalid(input);
	}

	@Test
	public void test_02() throws IOException {
		String input = "{ let mut x = 1; if x == y { } else { } }";
		checkInvalid(input);
	}

	@Test
	public void test_03() throws IOException {
		String input = "{ let mut x = 1; if x == x { } else { 1 } }";
		checkInvalid(input);
	}

	@Test
	public void test_04() throws IOException {
		String input = "{ let mut x = 1; if x == x { } else { &x } }";
		checkInvalid(input);
	}

	@Test
	public void test_05() throws IOException {
		String input = "{ let mut x = 1; if x == x { } else { &mut x } }";
		checkInvalid(input);
	}

	@Test
	public void test_06() throws IOException {
		String input = "{ let mut x = 1; if x == x { } else { box 1 } }";
		checkInvalid(input);
	}
	@Test
	public void test_07() throws IOException {
		String input = "{ let mut x = 1; if x == x { 1 } else { } }";
		checkInvalid(input);
	}

	@Test
	public void test_08() throws IOException {
		String input = "{ let mut x = 1; if x == x { } else { 1 } }";
		checkInvalid(input);
	}

	@Test
	public void test_09() throws IOException {
		String input = "{ let mut x = 1; if x == x { &x } else { } }";
		checkInvalid(input);
	}

	@Test
	public void test_10() throws IOException {
		String input = "{ let mut x = 1; if x == x { &x } else { 1 } }";
		checkInvalid(input);
	}

	@Test
	public void test_11() throws IOException {
		String input = "{ let mut x = 1; if x == x { &x } else { &mut x } }";
		checkInvalid(input);
	}

	@Test
	public void test_12() throws IOException {
		String input = "{ let mut x = 1; if x == x { &x } else { box 1 } }";
		checkInvalid(input);
	}

	@Test
	public void test_13() throws IOException {
		String input = "{ let mut x = 1; if x == x { box 1 } else { } }";
		checkInvalid(input);
	}

	@Test
	public void test_14() throws IOException {
		String input = "{ let mut x = 1; if x == x { box 1 } else { 1 } }";
		checkInvalid(input);
	}

	@Test
	public void test_15() throws IOException {
		String input = "{ let mut x = 1; if x == x { box 1 } else { &x } }";
		checkInvalid(input);
	}

	@Test
	public void test_16() throws IOException {
		String input = "{ let mut x = 1; if x == x { box 1 } else { &mut x } }";
		checkInvalid(input);
	}

	@Test
	public void test_17() throws IOException {
		String input = "{ let mut x = 1; let mut y = box 1; if x == x { let mut p = y; } else { let mut q = y; } *y = box 1; }";
		checkInvalid(input);
	}

	@Test
	public void test_18() throws IOException {
		String input = "{ let mut x = 1; let mut y = box 1; if x == x { } else { let mut q = y; } *y }";
		checkInvalid(input);
	}

	@Test
	public void test_19() throws IOException {
		String input = "{ let mut x = 1; let mut y = box 1; if x == x { let mut p = y; } else { } *y }";
		checkInvalid(input);
	}

	@Test
	public void test_20() throws IOException {
		String input = "{ let mut x = 1; let mut y = 2; let mut p = &mut x; if y == y { p = &y; } else { } }";
		checkInvalid(input);
	}

	@Test
	public void test_21() throws IOException {
		String input = "{ let mut x = 1; let mut p = &x; if x == x { let mut y = 2; p = &y; } else { } }";
		checkInvalid(input);
	}

	public static void checkInvalid(String input) throws IOException {
		CoreBorrowCheckTests.checkInvalid(input, new BorrowChecker(input, CFLOW_TYPING));
	}

	public static final BorrowChecker.Extension CFLOW_TYPING = new ControlFlow.Typing();
}
