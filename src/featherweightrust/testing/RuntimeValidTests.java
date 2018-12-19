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

import static org.junit.Assert.fail;

import java.io.IOException;
import java.io.StringReader;
import java.util.List;

import org.junit.*;

import featherweightrust.core.BigStepSemantics;
import featherweightrust.core.Syntax.Stmt;
import featherweightrust.io.Lexer;
import featherweightrust.io.Parser;
import featherweightrust.util.Pair;
import featherweightrust.util.SyntaxError;

public class RuntimeValidTests {
	@Test
	public void test_01() throws IOException {
		String input = "let mut x = 1;";
		run(input);
	}

	@Test
	public void test_02() throws IOException {
		String input = "let mut x = box 1;";
		run(input);
	}

	@Test
	public void test_03() throws IOException {
		String input = "let mut x = *(box 1);";
		run(input);
	}

	@Test
	public void test_04() throws IOException {
		String input = "let mut x = **(box (box 1));";
		run(input);
	}

	@Test
	public void test_10() throws IOException {
		String input = "{ let mut x = 1; }";
		run(input);
	}

	@Test
	public void test_11() throws IOException {
		String input = "{ let mut x = box 1; }";
		run(input);
	}

	@Test
	public void test_12() throws IOException {
		String input = "{ let mut x = 1; let mut x = &x; }";
		run(input);
	}

	@Test
	public void test_13() throws IOException {
		String input = "{ let mut x = 1; let mut y = x; }";
		run(input);
	}

	@Test
	public void test_14() throws IOException {
		String input = "{ let mut x = 1; let mut y = &x; }";
		run(input);
	}

	@Test
	public void test_15() throws IOException {
		String input = "{ let mut x = 1; let mut y = &x; let mut z = *y; }";
		run(input);
	}

	@Test
	public void test_16() throws IOException {
		String input = "{ let mut x = 1; { let mut y = 2; } }";
		run(input);
	}

	@Test
	public void test_17() throws IOException {
		String input = "{ let mut x = 1; { let mut y = &x; } }";
		run(input);
	}

	@Test
	public void test_18() throws IOException {
		String input = "{ let mut x = 1; let mut y = &x; { let mut z = 1; y = &z; } x = *z; }";
		run(input);
	}

	public static void run(String input) throws IOException {
		try {
			List<Lexer.Token> tokens = new Lexer(new StringReader(input)).scan();
			// Parse block
			Stmt stmt = new Parser(input,tokens).parseStatement(new Parser.Context());
			// Execute block in outermost lifetime "*")
			Pair<BigStepSemantics.State,Stmt> r = new BigStepSemantics().apply(new BigStepSemantics.State(), "*", stmt);
			//
			System.out.println(r.first() + " :> " + r.second());
		} catch (SyntaxError e) {
			e.outputSourceError(System.err);
			e.printStackTrace();
			fail();
		}
	}
}
