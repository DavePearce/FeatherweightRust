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

import featherweightrust.core.Syntax.Expr;
import featherweightrust.core.Syntax.Lifetime;
import featherweightrust.core.Syntax.Stmt;
import featherweightrust.core.Syntax.Value;
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
	public Stmt.Block parseStatementBlock(Context context, Lifetime lifetime) {
		int start = index;

		Lifetime myLifetime = lifetime.freshWithin();

		match("{");
		ArrayList<Stmt> stmts = new ArrayList<>();
		while (index < tokens.size() && !(tokens.get(index) instanceof RightCurly)) {
			Stmt stmt = parseStatement(context, myLifetime);
			stmts.add(stmt);
		}
		match("}");

		return new Stmt.Block(myLifetime, stmts.toArray(new Stmt[stmts.size()]), sourceAttr(start, index - 1));
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
	public Stmt parseStatement(Context context, Lifetime lifetime) {
		checkNotEof();
		Token lookahead = tokens.get(index);
		//
		if (lookahead.text.equals("let")) {
			return parseVariableDeclaration(context);
		} else if(lookahead instanceof LeftCurly) {
			// nested block
			return parseStatementBlock(context, lifetime);
		} else {
			// assignment
			return parseAssignStmtOrExpr(context);
		}
	}

	/**
	 * Parse an assignment statement of the form:
	 *
	 * <pre>
	 * AssignStmt ::= LVal '=' Expr ';'
	 *
	 * LVal ::= Ident
	 *       | LVal '.' Ident
	 *       | LVal '[' Expr ']'
	 * </pre>
	 *
	 * @return
	 */
	public Stmt parseAssignStmtOrExpr(Context context) {
		// standard assignment
		int start = index;
		Expr lhs = parseExpr(context);
		if(index < tokens.size() && tokens.get(index) instanceof Equals) {
			// This is an assignment statement
			match("=");
			Expr rhs = parseExpr(context);
			match(";");
			int end = index;
			// Sanity check permitted lvals
			if (lhs instanceof Expr.Variable) {
				return new Stmt.Assignment((Expr.Variable) lhs, rhs, sourceAttr(start, end - 1));
			} else if (lhs instanceof Expr.Dereference) {
				Expr.Dereference e = (Expr.Dereference) lhs;
				if (e.operand() instanceof Expr.Variable) {
					return new Stmt.IndirectAssignment((Expr.Variable) e.operand(), rhs, sourceAttr(start, end - 1));
				}
			}
			syntaxError("expecting lval, found " + lhs + ".", lhs);
			return null; // deadcode
		} else {
			// This is an expression as a statement
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
	public Stmt.Let parseVariableDeclaration(Context context) {
		int start = index;
		matchKeyword("let");
		matchKeyword("mut");
		// Match and declare variable name
		Expr.Variable variable = parseVariable(context);
		context.declare(variable.name());
		// A variable declaration may optionally be assigned an initialiser
		// expression.
		match("=");
		Expr initialiser = parseExpr(context);
		match(";");
		// Done.
		return new Stmt.Let(variable, initialiser, sourceAttr(start, index - 1));
	}

	public Expr parseExpr(Context context) {
		checkNotEof();

		int start = index;
		Token token = tokens.get(index);

		if (token instanceof LeftBrace) {
			match("(");
			Expr e = parseExpr(context);
			checkNotEof();
			match(")");
			return e;
		} else if (token instanceof Ampersand) {
			return parseBorrow(context);
		} else if (token instanceof Star) {
			return parseDereference(context);
		} else if (token instanceof Identifier) {
			return parseVariable(context);
		} else if (token instanceof Int) {
			int val = match(Int.class, "an integer").value;
			return new Value.Integer(val, sourceAttr(start, index - 1));
		} else if(token.text.equals("box")) {
			return parseBox(context);
		}
		syntaxError("unrecognised term (\"" + token.text + "\")", token);
		return null;
	}

	public Expr.Variable parseVariable(Context context) {
		int start = index;
		Identifier var = matchIdentifier();
		return new Expr.Variable(var.text, sourceAttr(start, index - 1));
	}

	public Expr.Borrow parseBorrow(Context context) {
		int start = index;
		boolean mutable = false;
		match("&");
		if(index < tokens.size() && tokens.get(index).text.equals("mut")) {
			matchKeyword("mut");
			mutable = true;
		}
		Expr operand = parseExpr(context);
		if (!(operand instanceof Expr.Variable)) {
			syntaxError("expecting variable, found " + operand + ".", operand);
		}
		return new Expr.Borrow((Expr.Variable) operand, mutable, sourceAttr(start, index - 1));
	}

	public Expr.Dereference parseDereference(Context context) {
		int start = index;
		match("*");
		Expr operand = parseExpr(context);
		return new Expr.Dereference(operand, sourceAttr(start, index - 1));
	}

	public Expr.Box parseBox(Context context) {
		int start = index;
		matchKeyword("box");
		Expr operand = parseExpr(context);
		return new Expr.Box(operand, sourceAttr(start, index - 1));
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

	private Attribute.Source sourceAttr(int start, int end) {
		Token t1 = tokens.get(start);
		Token t2 = tokens.get(end);
		return new Attribute.Source(t1.start, t2.end());
	}

	private void syntaxError(String msg, Expr e) {
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
