package com.craftinginterpreters.tool;

import com.craftinginterpreters.lox.Expr;
import com.craftinginterpreters.lox.Expr.Binary;
import com.craftinginterpreters.lox.Expr.Comma;
import com.craftinginterpreters.lox.Expr.Conditional;
import com.craftinginterpreters.lox.Expr.Grouping;
import com.craftinginterpreters.lox.Expr.Literal;
import com.craftinginterpreters.lox.Expr.Unary;
import com.craftinginterpreters.lox.Expr.Visitor;

/**
 * Pretty Printer for AST.
 */

class AstPrinter implements Expr.Visitor<String> {
  String print(Expr expr) {
    return expr.accept(this);
  }

  @Override
  public String visitConditionalExpr(Expr.Conditional expr) {
    return parenthesize("?:", expr.condition, expr.thenBranch, expr.elseBranch);
  }

  @Override
  public String visitCommaExpr(Expr.Comma expr) {
    return parenthesize(",", expr.left, expr.right);
  }

  @Override
  public String visitBinaryExpr(Expr.Binary expr) {
    return parenthesize(expr.operator.lexeme,
                        expr.left, expr.right);
  }

  @Override
  public String visitGroupingExpr(Expr.Grouping expr) {
    return parenthesize("group", expr.expression);
  }

  @Override
  public String visitLiteralExpr(Expr.Literal expr) {
    if (expr.value == null) return "nil";
    return expr.value.toString();
  }

  @Override
  public String visitUnaryExpr(Expr.Unary expr) {
    return parenthesize(expr.operator.lexeme, expr.right);
  }

  private String parenthesize(String name, Expr... exprs) {
    StringBuilder builder = new StringBuilder();

    builder.append("(").append(name);
    for (Expr expr : exprs) {
      builder.append(" ");
      builder.append(expr.accept(this));
    }
    builder.append(")");

    return builder.toString();
  }
}
