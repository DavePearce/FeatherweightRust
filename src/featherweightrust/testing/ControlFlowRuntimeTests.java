package featherweightrust.testing;

import java.io.IOException;
import org.junit.jupiter.api.Test;

import featherweightrust.core.BorrowChecker;
import featherweightrust.core.OperationalSemantics;
import featherweightrust.core.Syntax.Value;
import featherweightrust.extensions.ControlFlow;
import static featherweightrust.core.Syntax.Value.Unit;

public class ControlFlowRuntimeTests {
	private static Value.Integer One = new Value.Integer(1);
	private static Value.Integer Two = new Value.Integer(2);
	private static Value.Integer OneTwoThree = new Value.Integer(123);
	@Test
	public void test_01() throws IOException {
		String input = "{ let mut x = 1; if x == x { 1 } else { 2 } }";
		check(input,One);
	}

	@Test
	public void test_02() throws IOException {
		String input = "{ let mut x = 1; let mut y = 1; if x == y { 1 } else { 2 } }";
		check(input,One);
	}

	@Test
	public void test_03() throws IOException {
		String input = "{ let mut x = 1; let mut y = 2; if x == y { 1 } else { 2 } }";
		check(input,Two);
	}

	@Test
	public void test_04() throws IOException {
		String input = "{ let mut x = 1; let mut y = 1; let mut z = &y; if x == x { z = &x; } else { z = &x; } }";
		check(input,Unit);
	}

	@Test
	public void test_05() throws IOException {
		String input = "{ let mut x = 1; let mut y = 1; let mut z = &y; if x == x { z = &x; } else { } }";
		check(input,Unit);
	}

	@Test
	public void test_06() throws IOException {
		String input = "{ let mut x = 1; let mut y = 2; let mut z = &y; if x == x { z = &x; } else { } let mut w = *z; w }";
		check(input, One);
	}

	@Test
	public void test_07() throws IOException {
		String input = "{ let mut x = 1; let mut y = 2; let mut z = &y; if x != x { z = &x; } else { } let mut w = *z; w }";
		check(input, Two);
	}

	@Test
	public void test_09() throws IOException {
		String input = "{ let mut x = 1; let mut y = if x == x { } else { }; }";
		check(input, Unit);
	}

	@Test
	public void test_10() throws IOException {
		String input = "{ let mut x = 1; let mut y = box 1; if x == x { let mut p = *y; } else { let mut q = *y; } *y = 1; }";
		check(input, Unit);
	}

	@Test
	public void test_11() throws IOException {
		String input = "{ let mut x = 1; let mut y = 1; let mut z = box &mut x; if y == y { let mut p = *z; } else { let mut q = *z; } *z = &mut y; }";
		check(input, Unit);
	}

	@Test
	public void test_12() throws IOException {
		String input = "{ let mut x = 1; let mut p = &x; let mut q = &x; if *p == *q { 1 } else { 2 } }";
		check(input, One);
	}

	@Test
	public void test_13() throws IOException {
		String input = "{ let mut x = box 1; if *x == *x { x = box 2; } else { x = box 3; } *x }";
		check(input, Two);
	}

	@Test
	public void test_14() throws IOException {
		String input = "{ let mut x = box 3; if *x != *x { x = box 2; } else { x = box 1; } *x }";
		check(input, One);
	}

	@Test
	public void test_15() throws IOException {
		String input = "{ let mut x = 1; let mut y = 2; let mut p = &x; if x == x { p = &y; } else { } *p }";
		check(input, Two);
	}

	@Test
	public void test_16() throws IOException {
		String input = "{ let mut x = 1; let mut y = 2; let mut p = &x; if x != x { } else { p = &y; } *p }";
		check(input, Two);
	}

	@Test
	public void test_17() throws IOException {
		String input = "{ let mut x = 1; let mut y = 2; {let mut p = &mut x; if y == y { p = &mut y; } else { } *p = 123;} x }";
		check(input, One);
	}

	@Test
	public void test_18() throws IOException {
		String input = "{ let mut x = 1; let mut y = 2; {let mut p = &mut x; if y == y { p = &mut y; } else { } *p = 123;} y }";
		check(input, OneTwoThree);
	}

	@Test
	public void test_19() throws IOException {
		String input = "{ let mut x = 1; let mut y = 2; {let mut p = &mut x; if y != y { p = &mut y; } else { } *p = 123;} x }";
		check(input, OneTwoThree);
	}

	@Test
	public void test_20() throws IOException {
		String input = "{ let mut x = 1; let mut y = 2; {let mut p = &mut x; if y == y { p = &mut y; } else { } *p = 123;} x }";
		check(input, One);
	}

	public static void check(String input, Value output) throws IOException {
		// Reuse existing checking facility
		CoreRuntimeTests.check(input, output, CFLOW_SEMANTICS, new BorrowChecker(input, CFLOW_TYPING));
	}

	public static final BorrowChecker.Extension CFLOW_TYPING = new ControlFlow.Typing();
	public static final OperationalSemantics CFLOW_SEMANTICS = new OperationalSemantics(new ControlFlow.Semantics());
}
