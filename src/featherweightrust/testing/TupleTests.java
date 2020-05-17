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

	@Test
	public void test_14() throws IOException {
		String input = "{ let mut x = (1,2); let mut y = &x.0; *y }";
		check(input,One);
	}

	@Test
	public void test_15() throws IOException {
		String input = "{ let mut x = (1,2); let mut y = &x.1; *y }";
		check(input,Two);
	}

	@Test
	public void test_16() throws IOException {
		String input = "{ let mut x = (1,2); let mut y = &x.0; x }";
		check(input,PairOneTwo);
	}


	@Test
	public void test_17() throws IOException {
		String input = "{ let mut x = (1,2); let mut y = &x.1; x }";
		check(input,PairOneTwo);
	}


	@Test
	public void test_18() throws IOException {
		String input = "{ let mut x = (1,2); let mut y = &mut x.0; x.1 }";
		check(input,Two);
	}


	@Test
	public void test_19() throws IOException {
		String input = "{ let mut x = (1,2); let mut y = &mut x.1; x.0 }";
		check(input,One);
	}


	@Test
	public void test_20() throws IOException {
		String input = "{ let mut x = (0,2); { let mut y = &mut x.0; *y = 1; } x}";
		check(input,PairOneTwo);
	}

	@Test
	public void test_21() throws IOException {
		String input = "{ let mut x = (1,0); { let mut y = &mut x.1; *y = 2; } x}";
		check(input,PairOneTwo);
	}

	@Test
	public void test_22() throws IOException {
		String input = "{ let mut x = (box 1, box 2); let mut y = x.0; let mut z = x.1; *y }";
		check(input,One);
	}

	@Test
	public void test_23() throws IOException {
		String input = "{ let mut x = (box 1, box 2); let mut y = x.0; let mut z = x.1; *z }";
		check(input,Two);
	}


	@Test
	public void test_24() throws IOException {
		String input = "{ let mut x1 = 1; let mut x2 = 2; let mut y = (&mut x1, &mut x2); let mut z = y.0; let mut w = y.1; *w }";
		check(input,Two);
	}

	@Test
	public void test_25() throws IOException {
		String input = "{ let mut x1 = 1; let mut x2 = 2; let mut y = (&mut x1, &mut x2); let mut z = y.0; let mut w = y.1; *z }";
		check(input,One);
	}

	@Test
	public void test_26() throws IOException {
		String input = "{ let mut x = 1; let mut y = (&mut x, 2); let mut z = y.0; y.1 }";
		check(input,Two);
	}

	@Test
	public void test_27() throws IOException {
		String input = "{ let mut x = 1; let mut y = (2, &mut x); let mut z = y.1; y.0 }";
		check(input,Two);
	}

	@Test
	public void test_28() throws IOException {
		String input = "{ let mut x = (1, 2); let mut y = x.0; x.0 }";
		check(input,One);
	}

	@Test
	public void test_29() throws IOException {
		String input = "{ let mut x = 1; let mut y = (&mut x, 2); let mut z = y.1; y.1 }";
		check(input,Two);
	}

	@Test
	public void test_30() throws IOException {
		String input = "{ let mut x = 1; let mut y = (2, &mut x); let mut z = y.0; y.0 }";
		check(input,Two);
	}

	@Test
	public void test_31() throws IOException {
		String input = "{ let mut x = (0,2); x.0 = 1; x}";
		check(input,PairOneTwo);
	}

	@Test
	public void test_32() throws IOException {
		String input = "{ let mut x = (1,1); x.1 = 2; x}";
		check(input,PairOneTwo);
	}

	@Test
	public void test_33() throws IOException {
		String input = "{ let mut x = (0,0); x.0 = 1; x.1 = 2; x}";
		check(input,PairOneTwo);
	}

	@Test
	public void test_34() throws IOException {
		String input = "{ let mut x = (1,2); let mut y = &x.0; *y}";
		check(input,One);
	}

	@Test
	public void test_35() throws IOException {
		String input = "{ let mut x = (1,2); let mut y = &x.1; *y}";
		check(input,Two);
	}

	@Test
	public void test_36() throws IOException {
		String input = "{ let mut x = (1,2); let mut y = &x.1; let mut z = *y; x}";
		check(input,PairOneTwo);
	}

	@Test
	public void test_37() throws IOException {
		String input = "{ let mut x = (1,2); let mut y = &mut x.0; x.1 }";
		check(input,Two);
	}

	@Test
	public void test_38() throws IOException {
		String input = "{ let mut x = (1,2); let mut y = &mut x.1; x.0 }";
		check(input,One);
	}

	@Test
	public void test_39() throws IOException {
		String input = "{ let mut x = (0,2); { let mut y = &mut x.0; *y = 1; } x }";
		check(input,PairOneTwo);
	}

	@Test
	public void test_40() throws IOException {
		String input = "{ let mut x = (1,0); { let mut y = &mut x.1; *y = 2; } x }";
		check(input,PairOneTwo);
	}

	@Test
	public void test_41() throws IOException {
		String input = "{ let mut x = (box 0,box 2); let mut y = x.0; x.0 = box 1; (*(x.0), *x.1) }";
		check(input,PairOneTwo);
	}

	@Test
	public void test_42() throws IOException {
		String input = "{ let mut x = (box 1,box 1); let mut y = x.1; x.1 = box 2; (*x.0, *x.1) }";
		check(input,PairOneTwo);
	}

	@Test
	public void test_60() throws IOException {
		String input = "{ let mut x = (); }";
		checkInvalid(input);
	}

	@Test
	public void test_61() throws IOException {
		String input = "{ let mut x = (x,1); }";
		checkInvalid(input);
	}

	@Test
	public void test_62() throws IOException {
		String input = "{ let mut x = (1,x); }";
		checkInvalid(input);
	}

	@Test
	public void test_63() throws IOException {
		String input = "{ let mut x = box 1; (x,x)}";
		checkInvalid(input);
	}

	@Test
	public void test_64() throws IOException {
		String input = "{ let mut x = (1,2); x = 1; }";
		checkInvalid(input);
	}

	@Test
	public void test_65() throws IOException {
		String input = "{ let mut x = 1; x = (1,2); }";
		checkInvalid(input);
	}

	@Test
	public void test_66() throws IOException {
		String input = "{ let mut x = 0; let mut y = (&mut x,&mut x); }";
		checkInvalid(input);
	}

	@Test
	public void test_67() throws IOException {
		String input = "{ let mut x = 0; let mut y = 1; let mut z = (&mut x,&mut x,&mut y); }";
		checkInvalid(input);
	}

	@Test
	public void test_68() throws IOException {
		String input = "{ let mut x = 0; let mut y = 1; let mut z = (&mut x,&mut y,&mut x); }";
		checkInvalid(input);
	}

	@Test
	public void test_69() throws IOException {
		String input = "{ let mut x = 0; let mut y = 1; let mut z = (&mut y,&mut x,&mut x); }";
		checkInvalid(input);
	}

	@Test
	public void test_70() throws IOException {
		String input = "{ let mut x = 0; let mut y = (&mut x,1); let mut z = &mut x;}";
		checkInvalid(input);
	}

	@Test
	public void test_71() throws IOException {
		String input = "{ let mut x = 0; let mut y = (1,&mut x); let mut z = &mut x;}";
		checkInvalid(input);
	}

	@Test
	public void test_72() throws IOException {
		String input = "{ let mut x = (0,0); let mut y = &mut x.0; x}";
		checkInvalid(input);
	}

	@Test
	public void test_73() throws IOException {
		String input = "{ let mut x = (0,0); let mut y = &mut x.1; x}";
		checkInvalid(input);
	}

	@Test
	public void test_74() throws IOException {
		String input = "{ let mut x = (0,0); let mut y = &mut x.0; *y = 2; x}";
		checkInvalid(input);
	}

	@Test
	public void test_75() throws IOException {
		String input = "{ let mut x = (0,0); let mut y = &mut x.1; *y = 2; x}";
		checkInvalid(input);
	}

	@Test
	public void test_76() throws IOException {
		String input = "{ let mut x = (box 1, box 2); let mut y = x.0; let mut z = x.0; }";
		checkInvalid(input);
	}

	@Test
	public void test_77() throws IOException {
		String input = "{ let mut x = (box 1, box 2); let mut y = x.1; let mut z = x.1; }";
		checkInvalid(input);
	}

	@Test
	public void test_78() throws IOException {
		String input = "{ let mut x = 1; let mut y = (&mut x, 0); let mut z = y.0; let mut w = y.0; }";
		checkInvalid(input);
	}

	@Test
	public void test_79() throws IOException {
		String input = "{ let mut x = 1; let mut y = (0, &mut x); let mut z = y.1; let mut w = y.1; }";
		checkInvalid(input);
	}

	@Test
	public void test_80() throws IOException {
		// Partial moves not supported
		String input = "{ let mut x = 1; let mut y = (&mut x, 0); let mut z = y.0; let mut w = y; }";
		checkInvalid(input);
	}

	@Test
	public void test_81() throws IOException {
		// Partial moves not supported
		String input = "{ let mut x = 1; let mut y = (0, &mut x); let mut z = y.1; let mut w = y; }";
		checkInvalid(input);
	}

	@Test
	public void test_82() throws IOException {
		// This tests a "copymove" operation.
		String input = "{ let mut x = 1; let mut y = (&mut x,2); let mut z = y; y.1 }";
		checkInvalid(input);
	}

	@Test
	public void test_83() throws IOException {
		String input = "{ let mut x = 1; let mut y = (&mut x, 2); let mut z = y.0; y.0 }";
		checkInvalid(input);
	}

	@Test
	public void test_84() throws IOException {
		String input = "{ let mut x = 1; let mut y = (2, &mut x); let mut z = y.1; y.1 }";
		checkInvalid(input);
	}

	@Test
	public void test_85() throws IOException {
		String input = "{ let mut x = (0,0); x.0 = (1,2); }";
		checkInvalid(input);
	}

	@Test
	public void test_86() throws IOException {
		String input = "{ let mut x = (0,0); x.1 = (1,2); }";
		checkInvalid(input);
	}

	@Test
	public void test_87() throws IOException {
		String input = "{ let mut x = (0,0); let mut y = &mut x.0; x.0 }";
		checkInvalid(input);
	}

	@Test
	public void test_88() throws IOException {
		String input = "{ let mut x = (0,0); let mut y = &mut x.1; x.1 }";
		checkInvalid(input);
	}

	@Test
	public void test_89() throws IOException {
		String input = "{ let mut x = (box 0, box 0); let mut y = x.0; let mut z = &x; }";
		checkInvalid(input);
	}

	@Test
	public void test_90() throws IOException {
		String input = "{ let mut x = (box 0, box 0); let mut y = 1; x.0 = &y; }";
		checkInvalid(input);
	}

	@Test
	public void test_91() throws IOException {
		String input = "{ let mut x = (1,2); let mut y = x.2; }";
		checkInvalid(input);
	}

	@Test
	public void test_92() throws IOException {
		String input = "{ let mut x = (1,2,3); let mut y = x.3; }";
		checkInvalid(input);
	}

	public static void checkInvalid(String input) throws IOException {
		CoreTests.checkInvalid(input, new Tuples.Checker(input));
	}

	public static void check(String input, Value output) throws IOException {
		// Reuse existing checking facility
		CoreTests.check(input, output, Tuples.SEMANTICS, new Tuples.Checker(input));
	}


}
