package com.craftinginterpreters.lox;

import static com.craftinginterpreters.lox.TokenType.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Recursive Decent for the Grammar of the Language.
 * 
 * program        → declaration* EOF ;
 *
 * declaration    → varDecl
 *                  | statement ;
 * varDecl        → "var" IDENTIFIER ( "=" expression )? ";" ;
 * 
 * statement      → exprStmt
 *                  | ifStmt
 *                  | printStmt
 *                  | whileStmt
 *                  | block ;
 * 
 * forStmt        → "for" "(" ( varDecl | exprStmt | ";" )
 *                expression? ";"
 *                expression? ")" statement ;
 * 
 * whileStmt      → "while" "(" expression ")" statement ;
 * 
 * ifStmt         → "if" "(" expression ")" statement
 *                  ( "else" statement )? ;
 * block          → "{" declaration* "}" ;
 * exprStmt       → expression ";" ;
 * printStmt      → "print" expression ";" ;
 * 
 * expression     → comma;
 * comma          → assignment ( "," assignment )*;
 * assignment     → IDENTIFIER "=" assignment 
 *                  | conditional
 *                  | logic_or;
 * 
 * logic_or       → logic_and ( "or" logic_and )* ;
 * logic_and      → equality ( "and" equality )* ;
 * 
 * ternary        → equality ( "?" expression ":" ternary )? ;
 * equality       → comparison ( ( "!=" | "==" ) comparison )* ;
 * comparison     → term ( ( ">" | ">=" | "<" | "<=" ) term )* ;
 * term           → factor ( ( "-" | "+" ) factor )* ;
 * factor         → unary ( ( "/" | "*" ) unary )* ;
 * unary          → ( "!" | "-" ) unary
 *                  | primary ;
 * primary        → "true" | "false" | "nil"
 *                  | NUMBER | STRING
 *                  | "(" expression ")"
 *                  | IDENTIFIER ;
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
  private int loopDepth = 0; 

  Parser(List<Token> tokens) {
    this.tokens = tokens;
  }

  /**
   * Kicks off the parsing a program is a list of declarations
   * @return Expression or Null
   */
  List<Stmt> parse() {
    List<Stmt> statements = new ArrayList<>();
    while (!isAtEnd()) {
      statements.add(declaration());
    }

    return statements; 
  }

  /**
   * Envokes an expression
   * @return Expression
   */
  private Expr expression() {
    return comma();
  }

  /**
   * Envokes a declaration
   * @return a statement
   */
  private Stmt declaration() {
    try {
      if (match(VAR)) return varDeclaration();

      return statement();
    } catch (ParseError error) {
      synchronize();
      return null;
    }
  }

  /**
   * Envokes a statement when found
   * @return Statment
   */
  private Stmt statement() {
    if (match(FOR)) return forStatement();
    if (match(IF)) return ifStatement();
    if (match(PRINT)) return printStatement();
    if (match(WHILE)) return whileStatement();
    if (match(BREAK)) return breakStatement();
    if (match(LEFT_BRACE)) return new Stmt.Block(block());

    return expressionStatement();
  }

  /**
   * Consumes For statement 
   * @return body of for-statement
   */
  private Stmt forStatement() {
    consume(LEFT_PAREN, "Expect '(' after 'for'.");

    Stmt initializer;
    if (match(SEMICOLON)) {
      initializer = null;
    } else if (match(VAR)) {
      initializer = varDeclaration();
    } else {
      initializer = expressionStatement();
    }

    Expr condition = null;
    if (!check(SEMICOLON)) {
      condition = expression();
    }
    consume(SEMICOLON, "Expect ';' after loop condition.");

    Expr increment = null;
    if (!check(RIGHT_PAREN)) {
      increment = expression();
    }
    consume(RIGHT_PAREN, "Expect ')' after for clauses.");
    
    loopDepth++;
    Stmt body = statement();
    loopDepth--;

    if (increment != null) {
      body = new Stmt.Block(
          Arrays.asList(
              body,
              new Stmt.Expression(increment)));
    }

    if (condition == null) condition = new Expr.Literal(true);
    body = new Stmt.While(condition, body);

    if (initializer != null) {
      body = new Stmt.Block(Arrays.asList(initializer, body));
    }

    if (condition == null) condition = new Expr.Literal(true);
    body = new Stmt.While(condition, body);

    if (initializer != null) {
      body = new Stmt.Block(Arrays.asList(initializer, body));
    }

    return body;
  }

  /**
   * Consumes an If Statement
   * @return
   */
  private Stmt ifStatement() {
    consume(LEFT_PAREN, "Expect '(' after 'if'.");
    Expr condition = expression();
    consume(RIGHT_PAREN, "Expect ')' after if condition."); 

    Stmt thenBranch = statement();
    Stmt elseBranch = null;
    if (match(ELSE)) {
      elseBranch = statement();
    }

    return new Stmt.If(condition, thenBranch, elseBranch);
  }

  /**
   * Consumes a print statement
   * @return Print Statment
   */
  private Stmt printStatement() {
    Expr value = expression();
    consume(SEMICOLON, "Expect ';' after value.");
    return new Stmt.Print(value);
  }

  /**
   * Consume a variable declaration
   * (Handles if there is a syntax error on a declaration)
   * @return a Statement
   */
  private Stmt varDeclaration() {
    Token name = consume(IDENTIFIER, "Expect variable name.");

    Expr initializer = null;
    if (match(EQUAL)) {
      initializer = expression();
    }

    consume(SEMICOLON, "Expect ';' after variable declaration.");
    return new Stmt.Var(name, initializer);
  }

  /**
   * Consumes a while statement
   * @return While statement
   */
  private Stmt whileStatement() {
    consume(LEFT_PAREN, "Expect '(' after 'while'.");
    Expr condition = expression();
    consume(RIGHT_PAREN, "Expect ')' after condition.");
    loopDepth++;
    Stmt body = statement();
    loopDepth--;
    return new Stmt.While(condition, body);
  }

  /**
   * Consumes a break statement
   * @return Break statement
   */
  private Stmt breakStatement() {
    Token keyword = previous();
    if (loopDepth == 0) {
      throw error(keyword, "Cannot use 'break' outside of a loop.");
    }
    consume(SEMICOLON, "Expect ';' after 'break'.");
    return new Stmt.Break(keyword);
  }

  /**
   * Consumes an expression statement
   * @return Expression Statment
   */
  private Stmt expressionStatement() {
    Expr expr = expression();
    consume(SEMICOLON, "Expect ';' after expression.");
    return new Stmt.Expression(expr);
  }

  /**
   * Defines a code block for scoping 
   * @return statements in that code block
   */
  private List<Stmt> block() {
    List<Stmt> statements = new ArrayList<>();

    while (!check(RIGHT_BRACE) && !isAtEnd()) {
      statements.add(declaration());
    }

    consume(RIGHT_BRACE, "Expect '}' after block.");
    return statements;
  }

  /**
   * Creates a variable assignment
   * @return Expression
   */
  private Expr assignment() {
    Expr expr = or();

    if (match(EQUAL)) {
      Token equals = previous();
      Expr value = assignment();

      if (expr instanceof Expr.Variable) {
        Token name = ((Expr.Variable)expr).name;
        return new Expr.Assign(name, value);
      }

      error(equals, "Invalid assignment target."); 
    }

    return expr;
  }

  /**
   * Recursively unravels a or operator
   * @return
   */
  private Expr or() {
    Expr expr = and();

    while (match(OR)) {
      Token operator = previous();
      Expr right = and();
      expr = new Expr.Logical(expr, operator, right);
    }

    return expr;
  }

  /**
   * Recursively unravels an and operator
   * @return
   */
  private Expr and() {
    Expr expr = equality();

    while (match(AND)) {
      Token operator = previous();
      Expr right = equality();
      expr = new Expr.Logical(expr, operator, right);
    }

    return expr;
  }

  /**
   * Recursively unravels a comma operator using the comma ruleset.
   * @return Expression
   * (Kieran Fane)
   */
  private Expr comma() {
    Expr expr = assignment();
  
    while (match(COMMA)) {
      Token operator = previous();
      Expr right = assignment();
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
   * given a list of token types and an operand method handle to remove redundancy.
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
   * Recursively unravels a term using the term ruleset (+ -)
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

    if (match(IDENTIFIER)) { // matches to an identifier/var call
      return new Expr.Variable(previous());
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
      /**
       * "Why not throw or synchronize on this error?"
       *
       * Discovery Note: We don't need to throw the error since
       * we know where we are at while parsing and can safely 
       * continue. Also don't need to synchronize here, since we 
       * are handling binary operators, we know exactly where we 
       * are at in the grammar. (ie synchronize is for larger
       * issues) 
       */

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
