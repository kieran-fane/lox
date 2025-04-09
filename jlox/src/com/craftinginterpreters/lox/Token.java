package com.craftinginterpreters.lox;
/**
 * Defines a Token Object and can turn it into a string.
 */
class Token {
  final TokenType type;
  final String lexeme;
  final Object literal;
  final int line; 

  /** 
   * Makes a token item.
  */
  Token(TokenType type, String lexeme, Object literal, int line) {
    this.type = type;
    this.lexeme = lexeme;
    this.literal = literal;
    this.line = line;
  }

  /**
   * To be used when throwing an error.
   */
  public String toString() {
    return type + " " + lexeme + " " + literal;
  }
}
