package featherweightrust.testing;

import java.io.IOException;
import org.junit.jupiter.api.Test;

import featherweightrust.core.BorrowChecker;
import featherweightrust.core.OperationalSemantics;
import featherweightrust.core.Syntax.Value;
import featherweightrust.extensions.ControlFlow;
import featherweightrust.extensions.Pairs;

import static featherweightrust.core.Syntax.Value.Unit;

public class PairBorrowCheckTests {
	@Test
	public void test_01() throws IOException {
		String input = "{ let mut x = (); }";
		checkInvalid(input);
	}

	@Test
	public void test_02() throws IOException {
		String input = "{ let mut x = (x,1); }";
		checkInvalid(input);
	}

	@Test
	public void test_03() throws IOException {
		String input = "{ let mut x = (1,x); }";
		checkInvalid(input);
	}

	@Test
	public void test_04() throws IOException {
		String input = "{ let mut x = 1; (x,x)}";
		checkInvalid(input);
	}

	@Test
	public void test_05() throws IOException {
		String input = "{ let mut x = (1,2); x = 1; }";
		checkInvalid(input);
	}

	@Test
	public void test_06() throws IOException {
		String input = "{ let mut x = 1; x = (1,2); }";
		checkInvalid(input);
	}

	@Test
	public void test_07() throws IOException {
		String input = "{ let mut x = 0; let mut y = (&mut x,&mut x); }";
		checkInvalid(input);
	}

	@Test
	public void test_08() throws IOException {
		String input = "{ let mut x = 0; let mut y = (&mut x,1); let mut z = &mut x;}";
		checkInvalid(input);
	}

	@Test
	public void test_09() throws IOException {
		String input = "{ let mut x = 0; let mut y = (1,&mut x); let mut z = &mut x;}";
		checkInvalid(input);
	}

	@Test
	public void test_18() throws IOException {
		String input = "{ let mut x = (0,0); let mut y = &mut x.0; x}";
		checkInvalid(input);
	}

	@Test
	public void test_19() throws IOException {
		String input = "{ let mut x = (0,0); let mut y = &mut x.1; x}";
		checkInvalid(input);
	}

	@Test
	public void test_20() throws IOException {
		String input = "{ let mut x = (0,0); let mut y = &mut x.0; *y = 2; x}";
		checkInvalid(input);
	}

	@Test
	public void test_21() throws IOException {
		String input = "{ let mut x = (0,0); let mut y = &mut x.1; *y = 2; x}";
		checkInvalid(input);
	}


	public static void checkInvalid(String input) throws IOException {
		CoreBorrowCheckTests.checkInvalid(input, new BorrowChecker(input, PAIR_TYPING));
	}

	public static final BorrowChecker.Extension PAIR_TYPING = new Pairs.Typing();
}
