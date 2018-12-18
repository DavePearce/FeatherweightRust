package featherweightrust.util;

import featherweightrust.core.Syntax.Expr;
import featherweightrust.core.Syntax.Stmt;

public abstract class AbstractFunction<T,R,S> {

	public R apply(Stmt stmt, T input) {
		if (stmt instanceof Stmt.Declaration) {
			return apply((Stmt.Declaration) stmt, input);
		} else if (stmt instanceof Stmt.Assignment) {
			return apply((Stmt.Assignment) stmt, input);
		} else {
			return apply((Stmt.Block) stmt, input);
		}
	}

	public abstract R apply(Stmt.Declaration stmt, T input);

	public abstract R apply(Stmt.Assignment stmt, T input);

	public abstract R apply(Stmt.Block stmt, T input);

	public S apply(Expr expr, T input) {
		if (expr instanceof Expr.Dereference) {
			return apply((Expr.Dereference) expr, input);
		} else if (expr instanceof Expr.HeapAllocation) {
			return apply((Expr.HeapAllocation) expr, input);
		} else {
			return apply((Expr.Variable) expr, input);
		}
	}

	public abstract S apply(Expr.Dereference expr, T input);

	public abstract S apply(Expr.HeapAllocation expr, T input);

	public abstract S apply(Expr.Variable expr, T input);

}
