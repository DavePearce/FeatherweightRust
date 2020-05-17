package featherweightrust.testing;

import org.junit.runner.RunWith;
import org.junit.runners.Suite;

@RunWith(Suite.class)
@Suite.SuiteClasses({ CoreTests.class, ControlFlowTests.class, TupleTests.class })
public class AllTests {
	// NOTE: should figure out migratation this to JUnit 5.
}
