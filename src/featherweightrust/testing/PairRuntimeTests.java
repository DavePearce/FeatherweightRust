package featherweightrust.testing;

import java.io.IOException;
import org.junit.jupiter.api.Test;

import featherweightrust.core.BorrowChecker;
import featherweightrust.core.OperationalSemantics;
import featherweightrust.core.Syntax.Value;
import featherweightrust.extensions.ControlFlow;
import featherweightrust.extensions.Pairs;
import featherweightrust.extensions.Pairs.Syntax;

import static featherweightrust.core.Syntax.Value.Unit;

public class PairRuntimeTests {
	private static Value.Integer One = new Value.Integer(1);
	private static Value.Integer Two = new Value.Integer(2);
	private static Value PairOneOne = (Value) Syntax.Pair(One,One);
	private static Value PairOneTwo = (Value) Syntax.Pair(One,Two);

	@Test
	public void test_01() throws IOException {
		String input = "{ (1,2) }";
		check(input,PairOneTwo);
	}

	@Test
	public void test_02() throws IOException {
		String input = "{ let mut x = (1,2); }";
		check(input,Unit);
	}

	@Test
	public void test_03() throws IOException {
		String input = "{ let mut x = 1; (x,2)}";
		check(input,PairOneTwo);
	}

	@Test
	public void test_04() throws IOException {
		String input = "{ let mut x = 1; let mut y = 2; (x,y)}";
		check(input,PairOneTwo);
	}

	@Test
	public void test_05() throws IOException {
		String input = "{ let mut x = (0,0); x = (1,2); x}";
		check(input,PairOneTwo);
	}

	@Test
	public void test_06() throws IOException {
		String input = "{ let mut x = 1; let mut y = (&x,&x); let mut z = y.0; *z}";
		check(input,One);
	}

	@Test
	public void test_07() throws IOException {
		String input = "{ let mut x = 1; let mut y = (&x,&x); let mut z = y.1; *z}";
		check(input,One);
	}

	@Test
	public void test_08() throws IOException {
		String input = "{ let mut x = 1; let mut y = (&mut x,2); let mut z = y.0; *z}";
		check(input,One);
	}

	@Test
	public void test_09() throws IOException {
		String input = "{ let mut x = 1; let mut y = (&mut x,2); let mut z = y.1; z}";
		check(input,Two);
	}

	@Test
	public void test_10() throws IOException {
		String input = "{ let mut x = (1,2); x.0 }";
		check(input,One);
	}

	@Test
	public void test_11() throws IOException {
		String input = "{ let mut x = (1,2); x.1 }";
		check(input,Two);
	}

	@Test
	public void test_12() throws IOException {
		String input = "{ let mut x = ((1,2),3); x.0 }";
		check(input,PairOneTwo);
	}

	@Test
	public void test_13() throws IOException {
		String input = "{ let mut x = (3, (1,2)); x.1 }";
		check(input,PairOneTwo);
	}

	public static void check(String input, Value output) throws IOException {
		// Reuse existing checking facility
		CoreRuntimeTests.check(input, output, PAIRS_SEMANTICS, new BorrowChecker(input, PAIRS_TYPING));
	}

	public static final BorrowChecker.Extension PAIRS_TYPING = new Pairs.Typing();
	public static final OperationalSemantics PAIRS_SEMANTICS = new OperationalSemantics(new Pairs.Semantics());
}
