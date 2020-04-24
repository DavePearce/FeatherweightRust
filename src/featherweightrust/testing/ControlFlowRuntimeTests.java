package featherweightrust.testing;

import java.io.IOException;
import org.junit.jupiter.api.Test;

import featherweightrust.core.BorrowChecker;
import featherweightrust.core.OperationalSemantics;
import featherweightrust.extensions.ControlFlow;

public class ControlFlowRuntimeTests {
	@Test
	public void test_01() throws IOException {
		String input = "{ let mut x = 1; if x == x { 1 } else { 2 } }";
		check(input,1);
	}

	@Test
	public void test_02() throws IOException {
		String input = "{ let mut x = 1; let mut y = 1; if x == y { 1 } else { 2 } }";
		check(input,1);
	}

	@Test
	public void test_03() throws IOException {
		String input = "{ let mut x = 1; let mut y = 2; if x == y { 1 } else { 2 } }";
		check(input,2);
	}

	@Test
	public void test_04() throws IOException {
		String input = "{ let mut x = 1; let mut y = 1; let mut z = &y; if x == x { z = &x; } else { z = &x; } }";
		check(input,null);
	}

	@Test
	public void test_05() throws IOException {
		String input = "{ let mut x = 1; let mut y = 1; let mut z = &y; if x == x { z = &x; } else { } }";
		check(input,null);
	}

	public static void check(String input, Integer output) throws IOException {
		// Reuse existing checking facility
		CoreRuntimeTests.check(input, output, CFLOW_SEMANTICS, new BorrowChecker(input, CFLOW_TYPING));
	}

	public static final BorrowChecker.Extension CFLOW_TYPING = new ControlFlow.Typing();
	public static final OperationalSemantics CFLOW_SEMANTICS = new OperationalSemantics(new ControlFlow.Semantics());
}
