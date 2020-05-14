package featherweightrust.testing;

import java.io.IOException;
import org.junit.jupiter.api.Test;

import featherweightrust.core.BorrowChecker;
import featherweightrust.core.OperationalSemantics;
import featherweightrust.core.Syntax.Value;
import featherweightrust.extensions.ControlFlow;
import featherweightrust.extensions.Tuples;

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
		String input = "{ let mut x = box 1; (x,x)}";
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
		String input = "{ let mut x = 0; let mut y = 1; let mut z = (&mut x,&mut x,&mut y); }";
		checkInvalid(input);
	}

	@Test
	public void test_09() throws IOException {
		String input = "{ let mut x = 0; let mut y = 1; let mut z = (&mut x,&mut y,&mut x); }";
		checkInvalid(input);
	}

	@Test
	public void test_10() throws IOException {
		String input = "{ let mut x = 0; let mut y = 1; let mut z = (&mut y,&mut x,&mut x); }";
		checkInvalid(input);
	}

	@Test
	public void test_16() throws IOException {
		String input = "{ let mut x = 0; let mut y = (&mut x,1); let mut z = &mut x;}";
		checkInvalid(input);
	}

	@Test
	public void test_17() throws IOException {
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

	@Test
	public void test_22() throws IOException {
		String input = "{ let mut x = (box 1, box 2); let mut y = x.0; let mut z = x.0; }";
		checkInvalid(input);
	}

	@Test
	public void test_23() throws IOException {
		String input = "{ let mut x = (box 1, box 2); let mut y = x.1; let mut z = x.1; }";
		checkInvalid(input);
	}

	@Test
	public void test_24() throws IOException {
		String input = "{ let mut x = 1; let mut y = (&mut x, 0); let mut z = y.0; let mut w = y.0; }";
		checkInvalid(input);
	}

	@Test
	public void test_25() throws IOException {
		String input = "{ let mut x = 1; let mut y = (0, &mut x); let mut z = y.1; let mut w = y.1; }";
		checkInvalid(input);
	}

	@Test
	public void test_26() throws IOException {
		// Partial moves not supported
		String input = "{ let mut x = 1; let mut y = (&mut x, 0); let mut z = y.0; let mut w = y; }";
		checkInvalid(input);
	}

	@Test
	public void test_27() throws IOException {
		// Partial moves not supported
		String input = "{ let mut x = 1; let mut y = (0, &mut x); let mut z = y.1; let mut w = y; }";
		checkInvalid(input);
	}

	@Test
	public void test_28() throws IOException {
		// This tests a "copymove" operation.
		String input = "{ let mut x = 1; let mut y = (&mut x,2); let mut z = y; y.1 }";
		checkInvalid(input);
	}

	@Test
	public void test_29() throws IOException {
		String input = "{ let mut x = 1; let mut y = (&mut x, 2); let mut z = y.0; y.0 }";
		checkInvalid(input);
	}


	@Test
	public void test_30() throws IOException {
		String input = "{ let mut x = 1; let mut y = (2, &mut x); let mut z = y.1; y.1 }";
		checkInvalid(input);
	}

	public static void checkInvalid(String input) throws IOException {
		CoreBorrowCheckTests.checkInvalid(input, new BorrowChecker(input, PAIR_TYPING));
	}

	public static final BorrowChecker.Extension PAIR_TYPING = new Tuples.Typing();
}
