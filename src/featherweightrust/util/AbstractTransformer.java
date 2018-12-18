package featherweightrust.util;

import featherweightrust.core.Syntax.Expr;
import featherweightrust.core.Syntax.Stmt;

public abstract class AbstractTransformer<T> {

	public Pair<T,Stmt> apply(T state, Stmt stmt) {
		if (stmt instanceof Stmt.Declaration) {
			return apply(state, (Stmt.Declaration) stmt);
		} else if (stmt instanceof Stmt.Assignment) {
			return apply(state, (Stmt.Assignment) stmt);
		} else {
			return apply(state, (Stmt.Block) stmt);
		}
	}

	public abstract Pair<T,Stmt> apply(T state, Stmt.Declaration stmt);

	public abstract Pair<T,Stmt> apply(T state, Stmt.Assignment stmt);

	public abstract Pair<T,Stmt> apply(T state, Stmt.Block stmt);

	public Pair<T,Expr> apply(T input, Expr expr) {
		if (expr instanceof Expr.Dereference) {
			return apply(input, (Expr.Dereference) expr);
		} else if (expr instanceof Expr.Box) {
			return apply(input, (Expr.Box) expr);
		} else {
			return apply(input, (Expr.Variable) expr);
		}
	}

	public abstract Pair<T,Expr> apply(T input, Expr.Dereference expr);

	public abstract Pair<T,Expr> apply(T input, Expr.Box expr);

	public abstract Pair<T,Expr> apply(T input, Expr.Variable expr);
}
