package com.craftinginterpreters.lox;

import static com.craftinginterpreters.lox.TokenType.*;
import java.util.List;

/**
 * Recursive Decent for the Grammar of the Language.
 * 
 * ----------------
 * Equality       |    TOP        Lower
 * Comparison     |     |           |
 * Addition       |  Grammar    Precedence
 * Multiplication |     |           |
 * Unary          |  Bottom       Higher
 * ----------------
 * 
 * expression     → comma ;
 * comma          → ternary ( "," equality )* ;
 * ternary        → equality ( "?" expression ":" ternary )? ;
 * equality       → comparison ( ( "!=" | "==" ) comparison )* ;
 * comparison     → term ( ( ">" | ">=" | "<" | "<=" ) term )* ;
 * term           → factor ( ( "-" | "+" ) factor )* ;
 * factor         → unary ( ( "/" | "*" ) unary )* ;
 * unary          → ( "!" | "-" ) unary
 *                  | primary ;
 * primary        → NUMBER | STRING | "true" | "false" | "nil"
 *                  | "(" expression ")" ;
 * 
 * 
 * Per https://craftinginterpreters.com/parsing-expressions.html:
 * "A parser really has two jobs:
 * 1. Given a valid sequence of tokens, produce a corresponding syntax tree.
 * 2. Given an invalid sequence of tokens, detect any errors
 *    and tell the user about their mistakes."
 */
class Parser {
  private static class ParseError extends RuntimeException {}
  private final List<Token> tokens;
  private int current = 0;

  Parser(List<Token> tokens) {
    this.tokens = tokens;
  }

  /**
   * Kicks off the parsing 
   * @return Expression or Null
   */
  Expr parse() {
    try {
      return expression();
    } catch (ParseError error) {
      return null;
    }
  }

  /**
   * Envokes an expression
   * @return Expression
   */
  private Expr expression() {
    return comma();
  }

  /**
   * Recursively unravels a comma operator using the comma ruleset.
   * @return Expression
   * (Kieran Fane)
   */
  private Expr comma() {
    Expr expr = ternary();
  
    while (match(COMMA)) {
      Token operator = previous();
      Expr right = ternary();
      expr = new Expr.Comma(expr, operator, right);
    }
  
    return expr;
  }

  /**
   * Recursively unravels a conditional operator using the ternary ruleset.
   * @returns Expression
   * (Kieran Fane)
   */
  private Expr ternary() {
    Expr expr = equality();
  
    if (match(QUESTION)) {
      Expr thenBranch = expression();
      consume(COLON, "Expect ':' after then branch of conditional expression.");
      Expr elseBranch = ternary();
      expr = new Expr.Conditional(expr, thenBranch, elseBranch);
    }
  
    return expr;
  }

  /**
   * Recursively unravels an equality expression using the equality ruleset.
   * @return Expression
   */
  private Expr equality() {
    Expr expr = comparison();

    while (match(BANG_EQUAL, EQUAL_EQUAL)) {
      Token operator = previous();
      Expr right = comparison();
      expr = new Expr.Binary(expr, operator, right);
    }

    return expr;
  }

  /**
   * Recursively unravles a comparison using the comparison ruleset.
   * @return Expression
   * 
   * Could simplify this with helper function for left-associative bin operators
   * given a list of toekn types and an operand method handle to remove redundancy.
   * TODO ? (is it really worth it, i like the readability.)
   */
  private Expr comparison() {
    Expr expr = term();

    while (match(GREATER, GREATER_EQUAL, LESS, LESS_EQUAL)) {
      Token operator = previous();
      Expr right = term();
      expr = new Expr.Binary(expr, operator, right);
    }

    return expr;
  }

  /**
   * Recusively unravels a term using the term ruleset (+ -)
   * @return Expression
   */
  private Expr term() {
    Expr expr = factor();

    while (match(MINUS, PLUS)) {
      Token operator = previous();
      Expr right = factor();
      expr = new Expr.Binary(expr, operator, right);
    }

    return expr;
  }

  /**
   * Recursively unravels a factor (* /) using the term ruleset
   * @return Expression
   */
  private Expr factor() {
    Expr expr = unary();

    while (match(SLASH, STAR)) {
      Token operator = previous();
      Expr right = unary();
      expr = new Expr.Binary(expr, operator, right);
    }

    return expr;
  }

  /**
   * Recursively unravel unary operators using the unary ruleset (! -)
   * @return Expression
   */
  private Expr unary() {
    if (match(BANG, MINUS)) {
      Token operator = previous();
      Expr right = unary();
      return new Expr.Unary(operator, right);
    }

    return primary();
  }
  
  /**
   * Extracts Literals from primaries using the primary rule set. 
   * (most rules are terminals so parsing is straight forward)
   * Parenthesis need to generate its own expression and find terminating parenthesis.
   * @return Expression
   */
  private Expr primary() {
    if (match(FALSE)) return new Expr.Literal(false);
    if (match(TRUE)) return new Expr.Literal(true);
    if (match(NIL)) return new Expr.Literal(null);

    if (match(NUMBER, STRING)) {
      return new Expr.Literal(previous().literal);
    }

    if (match(LEFT_PAREN)) {
      Expr expr = expression();
      consume(RIGHT_PAREN, "Expect ')' after expression.");
      return new Expr.Grouping(expr);
    }

    if (match(PLUS, MINUS, STAR, SLASH, BANG_EQUAL, EQUAL_EQUAL,
            GREATER, GREATER_EQUAL, LESS, LESS_EQUAL,
            QUESTION, COLON, COMMA)) {
      Token operator = previous();
      error(operator, "Expect left-hand operand.");

      // Parse and discard the right-hand operand to synchronize
      // Parse it with the correct precedence:
      // Pick the method matching the operator’s precedence!
      switch (operator.type) {
        case PLUS:
        case MINUS:
          term();    // Because + - are at "term" precedence
          break;
        case STAR:
        case SLASH:
          factor();  // Because * / are at "factor" precedence
          break;
        case GREATER:
        case GREATER_EQUAL:
        case LESS:
        case LESS_EQUAL:
          comparison();
          break;
        case BANG_EQUAL:
        case EQUAL_EQUAL:
          equality();
          break;
        case QUESTION:
          ternary();
          break;
        case COMMA:
          comma();
          break;
        default:
          // If it's something else unexpected, just advance.
          advance();
    }

    // Return a dummy literal to satisfy parser type
    return new Expr.Literal(null);
  }

    throw error(peek(), "Expect expression.");
  }

  /**
   * checks for tokens in expression.
   * @param types Tokens
   * @return T/F
   */
  private boolean match(TokenType... types) {
    for (TokenType type : types) {
      if (check(type)) {
        advance();
        return true;
      }
    }

    return false;
  }

  /**
   * Checks to see if the next token is the expected then consumes it.
   * If not then throws an error. 
   * @param type of Token
   * @param message Error msg
   * @return Token
   */
  private Token consume(TokenType type, String message) {
    if (check(type)) return advance();

    throw error(peek(), message);
  }

  /**
   * Looks at the type of current.
   * @param type Type we want to match to.
   * @return T/F
   */
  private boolean check(TokenType type) {
    if (isAtEnd()) return false;
    return peek().type == type;
  }

  /**
   * Consumes current token and returns it. 
   * @return
   */
  private Token advance() {
    if (!isAtEnd()) current++;
    return previous();
  }

  /**
   * Sees if current is the end of file
   * @return Bool
   */
  private boolean isAtEnd() {
    return peek().type == EOF;
  }

  /**
   * Gets the token at the current index
   * @return Token
   */
  private Token peek() {
    return tokens.get(current);
  }

  /**
   * Gets the token that was previous to the current index
   * @return Token
   */
  private Token previous() {
    return tokens.get(current - 1);
  }

  /**
   * 
   * @param token
   * @param message
   * @return
   */
  private ParseError error(Token token, String message) {
    Lox.error(token, message);
    return new ParseError();
  }

  /**
   * Handles the synchronize of tokens after an error is found and 
   * handles the next sequences.  
   */
  private void synchronize() {
    advance();

    while (!isAtEnd()) {
      if (previous().type == SEMICOLON) return;

      switch (peek().type) {
        case CLASS:
        case FUN:
        case VAR:
        case FOR:
        case IF:
        case WHILE:
        case PRINT:
        case RETURN:
          return;
      }

      advance();
    }
  }
}
