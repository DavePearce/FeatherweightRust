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
package featherweightrust.io;

import java.util.*;

import featherweightrust.core.Syntax.Lifetime;
import featherweightrust.core.Syntax.Path;
import featherweightrust.core.Syntax;
import featherweightrust.core.Syntax.LVal;
import featherweightrust.core.Syntax.Term;
import featherweightrust.core.Syntax.Value;
import featherweightrust.core.Syntax.Path.Element;
import featherweightrust.core.Syntax.Term.Access;
import featherweightrust.extensions.ControlFlow;
import featherweightrust.extensions.Tuples;
import featherweightrust.io.Lexer.*;
import featherweightrust.util.SyntacticElement.Attribute;
import featherweightrust.util.SyntaxError;

public class Parser {
	public static final Context ROOT_CONTEXT = new Context();

	private String sourcefile;
	private ArrayList<Token> tokens;
	private int index;

	public Parser(String sourcefile, List<Token> tokens) {
		this.sourcefile = sourcefile;
		this.tokens = new ArrayList<>(tokens);
	}

	/**
	 * Parse a block of zero or more statements, of the form:
	 *
	 * <pre>
	 * StmtBlock ::= '{' Stmt* '}'
	 * </pre>
	 *
	 * @return
	 */
	public Term.Block parseStatementBlock(Context context, Lifetime lifetime) {
		int start = index;

		Lifetime myLifetime = lifetime.freshWithin();

		match("{");
		ArrayList<Term> terms = new ArrayList<>();
		boolean separatorRequired = false;
		while (index < tokens.size() && !(tokens.get(index) instanceof RightCurly)) {
			if(separatorRequired) {
				match(";");
				// Catch empty statement
				if(index < tokens.size() && tokens.get(index) instanceof RightCurly) {
					break;
				}
			}
			Term stmt = parseTerm(context, myLifetime, true);
			terms.add(stmt);
			separatorRequired = !(stmt instanceof Term.Compound);
		}
		match("}");

		return new Term.Block(myLifetime, terms.toArray(new Term[terms.size()]), sourceAttr(start, index - 1));
	}

	public static int lifetime = 0;

	/**
	 * Generate a unique lifetime name
	 *
	 * @return
	 */
	public String freshLifetime() {
		return "l" + lifetime++;
	}

	/**
	 * Parse a given statement.
	 *
	 * @return
	 */
	public Term parseTerm(Context context, Lifetime lifetime, boolean consumed) {
		checkNotEof();
		int start = index;
		Token lookahead = tokens.get(index);
		Term t;
		//
		if (lookahead.text.equals("let")) {
			t = parseVariableDeclaration(context, lifetime);
		} else if (lookahead.text.equals("if")) {
			t = parseIfElseStmt(context, lifetime);
		} else if (lookahead instanceof LeftCurly) {
			// nested block
			t = parseStatementBlock(context, lifetime);
		} else if (lookahead instanceof LeftBrace) {
			t = parseBracketedExpression(context,lifetime, consumed);
		} else if (lookahead instanceof Ampersand) {
			t = parseBorrow(context, lifetime);
		} else if (lookahead instanceof Int) {
			int val = match(Int.class, "an integer").value;
			t = new Value.Integer(val, sourceAttr(start, index - 1));
		} else if (lookahead.text.equals("box")) {
			t = parseBox(context, lifetime, consumed);
		} else {
			t = parseAssignmentOrDereference(context, lifetime, consumed);
		}
		// Done
		return t;
	}

	/**
	 * Parse an indirect assignment or a dereference expression.
	 *
	 * @return
	 */
	public Term parseAssignmentOrDereference(Context context, Lifetime lifetime, boolean consumed) {
		int start = index;
		// Parse potential lhs
		Term.Access lhs = parseDereference(context, lifetime, consumed);
		//
		if (index < tokens.size() && tokens.get(index) instanceof Equals) {
			// This is an assignment statement
			match("=");
			Term rhs = parseTerm(context, lifetime, true);
			int end = index;
			return new Term.Assignment(lhs.operand(), rhs, sourceAttr(start, end - 1));
		} else {
			return lhs;
		}
	}

	/**
	 * Parse a variable declaration, of the form:
	 *
	 * <pre>
	 * VarDecl ::= 'let' Ident '=' Expr ';'
	 * </pre>
	 *
	 * @return
	 */
	public Term.Let parseVariableDeclaration(Context context, Lifetime lifetime) {
		int start = index;
		matchKeyword("let");
		matchKeyword("mut");
		// Match and declare variable name
		String variable = matchIdentifier().text;
		context.declare(variable);
		// A variable declaration may optionally be assigned an initialiser
		// expression.
		match("=");
		Term initialiser = parseTerm(context, lifetime, true);
		// Done.
		return new Term.Let(variable, initialiser, sourceAttr(start, index - 1));
	}

	public Term parseIfElseStmt(Context context, Lifetime lifetime) {
		int start = index;
		matchKeyword("if");
		// Match and declare lhs variable
		Term lhs = parseTerm(context, lifetime, false);
		Token t = match("==","!=");
		// Determine condition
		boolean eq = t.text.equals("==");
		// Match and declare rhs variable
		Term rhs = parseTerm(context, lifetime, false);
		// Parse true block
		Term.Block trueBlock = parseStatementBlock(context,lifetime);
		// Match else
		matchKeyword("else");
		// Parse false block
		Term.Block falseBlock = parseStatementBlock(context,lifetime);
		// Return extended term
		return new ControlFlow.Syntax.IfElse(eq, lhs, rhs, trueBlock, falseBlock, sourceAttr(start, index - 1));
	}

	public Term parseBracketedExpression(Context context, Lifetime lifetime, boolean consumed) {
		int start = index;
		match("(");
		ArrayList<Term> terms = new ArrayList<>();
		terms.add(parseTerm(context, lifetime, consumed));
		checkNotEof();
		while(index < tokens.size() && tokens.get(index) instanceof Comma) {
			match(",");
			terms.add(parseTerm(context, lifetime, consumed));
		}
		match(")");
		switch(terms.size()) {
		case 0:
			syntaxError("no support for empty tuples!", terms.get(2));
			return null;
		case 1:
			return terms.get(0);
		default:
			Term[] ts = terms.toArray(new Term[terms.size()]);
			return new Tuples.Syntax.TupleTerm(ts, sourceAttr(start, index - 1));
		}
	}

	public Term.Borrow parseBorrow(Context context, Lifetime lifetime) {
		int start = index;
		boolean mutable = false;
		match("&");
		if(index < tokens.size() && tokens.get(index).text.equals("mut")) {
			matchKeyword("mut");
			mutable = true;
		}
		LVal operand = parseLVal(context, lifetime);
		//
		return new Term.Borrow(operand, mutable, sourceAttr(start, index - 1));
	}

	public Term.Access parseDereference(Context context, Lifetime lifetime, boolean consumed) {
		int start = index;
		Access.Kind kind;
		if (consumed && index < tokens.size() && tokens.get(index) instanceof Shreak) {
			match("!");
			kind = Access.Kind.COPY;
		} else if (consumed && index < tokens.size() && tokens.get(index) instanceof QuestionMark) {
			match("?");
			kind = Access.Kind.UNSPECIFIED;
		} else if(!consumed) {
			kind = Access.Kind.TEMP;
		} else {
			kind = Access.Kind.MOVE;
		}
		LVal operand = parseLVal(context, lifetime);
		return new Term.Access(kind, operand, sourceAttr(start, index - 1));
	}

	public Term.Box parseBox(Context context, Lifetime lifetime, boolean consumed) {
		int start = index;
		matchKeyword("box");
		Term operand = parseTerm(context, lifetime, consumed);
		return new Term.Box(operand, sourceAttr(start, index - 1));
	}

	public LVal parseLVal(Context context, Lifetime lifetime) {
		int start = index;
		// Parse dereferences
		if (index < tokens.size() && tokens.get(index) instanceof Star) {
			match("*");
			LVal lv;
			if (index < tokens.size() && tokens.get(index) instanceof LeftBrace) {
				match("(");
				lv = parseLVal(context, lifetime);
				match(")");
			} else {
				lv = parseLVal(context, lifetime);
			}
			// Done
			return new LVal(lv.name(), append(lv.path(), Syntax.Path.DEREF_ELEMENT), sourceAttr(start, index - 1));
		} else {
			// Parse variable identifier
			Identifier var = matchIdentifier();
			// Parse trailing dot accesses
			Path post = parseDotPath(context,lifetime,start);
			// Done
			return new LVal(var.text, post, sourceAttr(start, index - 1));
		}
	}

	public Path parseDotPath(Context context, Lifetime lifetime, int start) {
		// Parse access path (if applicable)
		if (index < tokens.size() && tokens.get(index) instanceof Dot) {
			ArrayList<Path.Element> elements = new ArrayList<>();
			do {
				match(".");
				int index = match(Int.class, "an integer").value;
				elements.add(new Tuples.Syntax.Index(index));
			} while (index < tokens.size() && tokens.get(index) instanceof Dot);
			Path.Element[] es = elements.toArray(new Path.Element[elements.size()]);
			return new Path(es, sourceAttr(start, index - 1));
		} else {
			// Common case
			return new Path(sourceAttr(start, index - 1));
		}
	}

	private void checkNotEof() {
		if (index >= tokens.size()) {
			throw new SyntaxError("unexpected end-of-file", sourcefile, index - 1, index - 1);
		}
		return;
	}

	private Token match(String op) {
		checkNotEof();
		Token t = tokens.get(index);
		if (!t.text.equals(op)) {
			syntaxError("expecting '" + op + "', found '" + t.text + "'", t);
		}
		index = index + 1;
		return t;
	}

	private Token match(String... options) {
		checkNotEof();
		Token t = tokens.get(index);
		for(int i=0;i!=options.length;++i) {
			if (t.text.equals(options[i])) {
				index = index + 1;
				return t;
			}
		}
		String s = "";
		for(int i=0;i!=options.length;++i) {
			if(i != 0) {
				s += " or ";
			}
			s += "'" + options[i] + "'";
		}
		syntaxError("expecting '" + s + "', found '" + t.text + "'", t);
		return null;
	}

	@SuppressWarnings("unchecked")
	private <T extends Token> T match(Class<T> c, String name) {
		checkNotEof();
		Token t = tokens.get(index);
		if (!c.isInstance(t)) {
			syntaxError("expecting " + name + ", found '" + t.text + "'", t);
		}
		index = index + 1;
		return (T) t;
	}

	private Identifier matchIdentifier() {
		checkNotEof();
		Token t = tokens.get(index);
		if (t instanceof Identifier) {
			Identifier i = (Identifier) t;
			index = index + 1;
			return i;
		}
		syntaxError("identifier expected", t);
		return null; // unreachable.
	}

	private Keyword matchKeyword(String keyword) {
		checkNotEof();
		Token t = tokens.get(index);
		if (t instanceof Keyword) {
			if (t.text.equals(keyword)) {
				index = index + 1;
				return (Keyword) t;
			}
		}
		syntaxError("keyword " + keyword + " expected.", t);
		return null;
	}

	private static Path append(Path lhs, Path.Element rhs) {
		final int n = lhs.size();
		Element[] es = new Element[n + 1];
		for (int i = 0; i != n; ++i) {
			es[i] = lhs.get(i);
		}
		es[n] = rhs;
		Attribute.Source l = lhs.attribute(Attribute.Source.class);
		return new Path(es, l);
	}

	private Attribute.Source sourceAttr(int start, int end) {
		Token t1 = tokens.get(start);
		Token t2 = tokens.get(end);
		return new Attribute.Source(t1.start, t2.end());
	}

	private void syntaxError(String msg, Term e) {
		Attribute.Source loc = e.attribute(Attribute.Source.class);
		throw new SyntaxError(msg, sourcefile, loc.start, loc.end);
	}

	private void syntaxError(String msg, Token t) {
		throw new SyntaxError(msg, sourcefile, t.start, t.start + t.text.length() - 1);
	}

	/**
	 * Provides information about the current context in which the parser is
	 * operating.
	 *
	 * @author David J. Pearce
	 *
	 */
	public static class Context {
		/**
		 * indicates the set of declared variables within the current context;
		 */
		private final Set<String> environment;

		public Context() {
			this.environment = new HashSet<>();
		}

		private Context(Set<String> environment) {
			this.environment = environment;
		}

		/**
		 * Check whether a given variable is declared in this context or not.
		 *
		 * @param variable
		 * @return
		 */
		public boolean isDeclared(String variable) {
			return environment.contains(variable);
		}

		public void declare(String variable) {
			environment.add(variable);
		}

		/**
		 * Create a new clone of this context
		 */
		@Override
		public Context clone() {
			return new Context(new HashSet<>(environment));
		}
	}
}
