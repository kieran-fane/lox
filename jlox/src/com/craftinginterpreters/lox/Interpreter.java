package com.craftinginterpreters.lox;

import java.util.List;

class Interpreter implements Expr.Visitor<Object>,
                             Stmt.Visitor<Void> {
  private Environment environment = new Environment();

  /**
   * The basis of a program, accept a list of statements
   * (and execute them or error)
   * @param statements 
   * @return null
   */
  void interpret(List<Stmt> statements) {
    try {
      for (Stmt statement : statements) {
        execute(statement);
      }
    } catch (RuntimeError error) {
      Lox.runtimeError(error);
    }
  }
  
  /**
   * extracts value from literal
   * @return Value
   */
  @Override
  public Object visitLiteralExpr(Expr.Literal expr) {
    return expr.value; //Extract value
  }

  /**
   * Evaluates parenthesis
   * @return eval expr
   */
  @Override
  public Object visitGroupingExpr(Expr.Grouping expr) {
    return evaluate(expr.expression); //process group
  }

  /**
   * Evaluates Comma
   */
  @Override
  public Object visitCommaExpr(Expr.Comma expr) {
    evaluate(expr.left);
    return evaluate(expr.right);
  }

  /**
   * Evaluates Conditionals
   */
  @Override
  public Object visitConditionalExpr(Expr.Conditional expr) {
    Object condition = evaluate(expr.condition);

    if (isTruthy(condition)) {
      return evaluate(expr.thenBranch);
    } else {
      return evaluate(expr.elseBranch);
    }
  }

  /**
   * Goes to Vister and accepts the value for the object it is.
   * @param expr expression to evaluate.
   * @return eval expr
   */
  private Object evaluate(Expr expr) {
    return expr.accept(this);
  }

  /**
   * Helper function to accept statements and execute them
   * @param stmt statement
   * @return null
   */
  private void execute(Stmt stmt) {
    stmt.accept(this);
  }

  /**
   * Executes a block of code with its statement and own environment
   * @param statements list
   * @param environment of all variables
   */
  void executeBlock(List<Stmt> statements,
                    Environment environment) {
    Environment previous = this.environment;
    try {
      this.environment = environment;

      for (Stmt statement : statements) {
        execute(statement);
      }
    } finally {
      this.environment = previous;
    }
  }

  /**
   * Goes to Block statement and executes the block of code
   * @param stmt Block Statement
   * @return null
   */
  @Override
  public Void visitBlockStmt(Stmt.Block stmt) {
    executeBlock(stmt.statements, new Environment(environment));
    return null;
  }

  /**
   * Goes to Expression Statement and evaluates the expression in it. 
   * @param stmt Expression Statement
   * @return void. (statements don't have a return)
   */
  @Override
  public Void visitExpressionStmt(Stmt.Expression stmt) {
    evaluate(stmt.expression);
    return null;
  }

  /**
   * Goes to Print Statement and evaluates the expression and prints it to out
   * @param stmt Print statement 
   * @return null (statements don't have a return)
   */
  @Override
  public Void visitPrintStmt(Stmt.Print stmt) {
    Object value = evaluate(stmt.expression);
    System.out.println(stringify(value));
    return null;
  }

  /**
   * Goes to Variable Statement and evaluates the expression
   * and defines it in the environment
   * @param stmt Variable statement
   * @return null
   */
  @Override
  public Void visitVarStmt(Stmt.Var stmt) {
    Object value = null;
    if (stmt.initializer != null) {
      value = evaluate(stmt.initializer);
    }

    environment.define(stmt.name.lexeme, value);
    return null;
  }

  /**
   * Goes to Assignment Expression and assigns it
   * @param expr assignment expression
   * @return Object the value
   */
  @Override
  public Object visitAssignExpr(Expr.Assign expr) {
    Object value = evaluate(expr.value);
    environment.assign(expr.name, value);
    return value;
  }

  /**
   * Takes the left and right of the bin expr and computes it
   * @returns arithmetic value
   */
  @Override
  public Object visitBinaryExpr(Expr.Binary expr) {
    Object left = evaluate(expr.left);
    Object right = evaluate(expr.right); 

    switch (expr.operator.type) {
      case GREATER:
        checkNumberOperands(expr.operator, left, right);
        return (double)left > (double)right;
      case GREATER_EQUAL:
        checkNumberOperands(expr.operator, left, right);
        return (double)left >= (double)right;
      case LESS:
        checkNumberOperands(expr.operator, left, right);
        return (double)left < (double)right;
      case LESS_EQUAL:
        checkNumberOperands(expr.operator, left, right);
        return (double)left <= (double)right;
      case MINUS:
        checkNumberOperands(expr.operator, left, right);
        return (double)left - (double)right;
      case PLUS:
        if (left instanceof Double && right instanceof Double) {
          return (double)left + (double)right;
        } 
        if (left instanceof String && right instanceof String) {
          return (String)left + (String)right; // Concat 2 strings
        }
        throw new RuntimeError(expr.operator,
            "Operands must be two numbers or two strings.");
      case SLASH:
        checkNumberOperands(expr.operator, left, right);
        return (double)left / (double)right;
      case STAR:
        checkNumberOperands(expr.operator, left, right);
        return (double)left * (double)right;
      case BANG_EQUAL: return !isEqual(left, right);
      case EQUAL_EQUAL: return isEqual(left, right);
    }

    // Unreachable.
    return null;
  }

  /**
   * Handles Unary operator and returns the value
   */
  @Override
  public Object visitUnaryExpr(Expr.Unary expr) {
    Object right = evaluate(expr.right);

    switch (expr.operator.type) {
      case BANG:
        return !isTruthy(right);
      case MINUS:
        checkNumberOperand(expr.operator, right);
        return -(double)right;
    }

    // Unreachable.
    return null;
  }

  /**
   * Gets the value of an variable within an expression.
   */
  @Override
  public Object visitVariableExpr(Expr.Variable expr) {
    return environment.get(expr.name);
  }

  /**
   * Error handling for wrong type for number Unary operations
   * @param operator 
   * @param operand
   */
  private void checkNumberOperand(Token operator, Object operand) {
    if (operand instanceof Double) return;
    throw new RuntimeError(operator, "Operand must be a number.");
  }

  /**
   * Error handling for wrong type for number Binary operations
   * @param operator
   * @param left
   * @param right
   */
  private void checkNumberOperands(Token operator,
                                   Object left, Object right) {
    if (left instanceof Double && right instanceof Double) return;
    
    throw new RuntimeError(operator, "Operands must be numbers.");
  }

  /**
   * Determines what is truthy or falsey
   * @param object the object to determine if truthy or falsey
   * @returns T/F
   */
  private boolean isTruthy(Object object) {
    if (object == null) return false;
    if (object instanceof Boolean) return (boolean)object;
    return true;
  }

  /**
   * Determines if 2 objects are equal
   * @param a obj
   * @param b obj
   * @return T/F
   */
  private boolean isEqual(Object a, Object b) {
    // Prevent NullPointerException
    if (a == null && b == null) return true;
    if (a == null) return false;

    return a.equals(b);
  }

  /**
   * Turns object to a string.
   * @param object
   * @return String
   */
  private String stringify(Object object) {
    if (object == null) return "nil";

    if (object instanceof Double) {
      String text = object.toString();
      if (text.endsWith(".0")) {
        text = text.substring(0, text.length() - 2);
      }
      return text;
    }

    return object.toString();
  }
}
