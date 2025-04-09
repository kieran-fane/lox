package com.craftinginterpreters.lox;

import static com.craftinginterpreters.lox.TokenType.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Takes the source and starts making an array of tokens.
 */
class Scanner {
  private final String source;
  private final List<Token> tokens = new ArrayList<>();
  // Keeps track of where the scanner is in the source code.
  private int start = 0;
  private int current = 0;
  private int line = 1;
  private static final Map<String, TokenType> keywords;
  // Putting keywords in our WordMap(Table thing).
  static {
    keywords = new HashMap<>();
    keywords.put("and",    AND);
    keywords.put("class",  CLASS);
    keywords.put("else",   ELSE);
    keywords.put("false",  FALSE);
    keywords.put("for",    FOR);
    keywords.put("fun",    FUN);
    keywords.put("if",     IF);
    keywords.put("nil",    NIL);
    keywords.put("or",     OR);
    keywords.put("print",  PRINT);
    keywords.put("return", RETURN);
    keywords.put("super",  SUPER);
    keywords.put("this",   THIS);
    keywords.put("true",   TRUE);
    keywords.put("var",    VAR);
    keywords.put("while",  WHILE);
  }

  Scanner(String source) {
    this.source = source;
  }

  /**
   * Goes from start to end and scans each token into a collection.
   * @return List<Token> from the source
   */
  List<Token> scanTokens() {
    while (!isAtEnd()) {
      // We are at the beginning of the next lexeme.
      start = current;
      scanToken();
    }

    tokens.add(new Token(EOF, "", null, line));
    return tokens;
  }

  /**
   * Scans for single character tokens. 
   * THE GUTS AND HEART OF THE SCANNER
  */
  private void scanToken() {
    char c = advance();
    switch (c) {
      case '(' -> addToken(LEFT_PAREN);
      case ')' -> addToken(RIGHT_PAREN);
      case '{' -> addToken(LEFT_BRACE);
      case '}' -> addToken(RIGHT_BRACE);
      case ',' -> addToken(COMMA);
      case '.' -> addToken(DOT);
      case '-' -> addToken(MINUS);
      case '+' -> addToken(PLUS);
      case ';' -> addToken(SEMICOLON);
      case '*' -> addToken(STAR);
      case '!' -> addToken(match('=') ? BANG_EQUAL : BANG);
      case '=' -> addToken(match('=') ? EQUAL_EQUAL : EQUAL);
      case '<' -> addToken(match('=') ? LESS_EQUAL : LESS);
      case '>' -> addToken(match('=') ? GREATER_EQUAL : GREATER);
      case '/' -> {
          if (match('/')) {
              // a comment goes until the end of the line. Ignore the line.
              while (peek() != '\n' && !isAtEnd()) advance();
          } else {
              addToken(SLASH);
          }
      }
      case ' ', '\r', '\t' -> {// Ignore whitespace.
      }
      case '\n' -> // Ignore-ish, incrament line.
          line++;
      case '"' -> { // Literal string.
        string(); 
        break;
      }
      default -> { // identifier or num literal
        if (isDigit(c)) { // num literal
          number();
        } else if (isAlpha(c)) { // identifier/keyword
          identifier();
        } else { // Unknown character.
          Lox.error(line, "Unexpected character.");
        }
      }
    }
  }
  
  /**
   * gets the whole sequence of chars for the identifier.
   */
  private void identifier() {
    while (isAlphaNumeric(peek())) advance();
    String text = source.substring(start, current);
    TokenType type = keywords.get(text);
    if (type == null) type = IDENTIFIER;
    addToken(type);
  }

  /**
   * Parses the guts of a number.
   */
  private void number() {
    while (isDigit(peek())) advance();

    // Look for a fractional part.
    if (peek() == '.' && isDigit(peekNext())) {
      // Consume the "."
      advance();

      while (isDigit(peek())) advance();
    }

    addToken(NUMBER,
        Double.parseDouble(source.substring(start, current))); //Lazy conversion with Java funct
        // TODO - make my own parser to create this literal.
  }

  /**
   * Parses the guts of a string. 
   */
  private void string() {
    while (peek() != '"' && !isAtEnd()) {
      /**
       * we allow for multi-line strings...
       * (kind of ugly but oh well)
       */
      if (peek() == '\n') line++;
      advance();
    }

    if (isAtEnd()) {
      Lox.error(line, "Unterminated string.");
      return;
    }

    // The closing ".
    advance();

    // Trim the surrounding quotes.
    /**
     * If we had escape characters we escape them here.
     */
    String value = source.substring(start + 1, current - 1);
    addToken(STRING, value);
  }

  /**
   * Sees if the current char is the expected.
   * @param expected char
   * @return T/F
   */
  private boolean match(char expected) {
    if (isAtEnd()) return false;
    if (source.charAt(current) != expected) return false;

    current++;
    return true;
  }

  /**
   * Looks at the next character
   * @return T/F
   */
  private char peek() {
    if (isAtEnd()) return '\0';
    return source.charAt(current);
  }

  /**
   * Looks at the next next character
   * (showing max lookahead is 2).
   * @return T/F
   */
  private char peekNext() {
    if (current + 1 >= source.length()) return '\0';
    return source.charAt(current + 1);
  } 

  /**
   * Indicates if char is letter.
   * @param c char
   * @return T/F
   */
  private boolean isAlpha(char c) {
    return (c >= 'a' && c <= 'z') ||
           (c >= 'A' && c <= 'Z') ||
            c == '_';
  }

  /**
   * Indicates if char is num or letter.
   * @param c char
   * @return T/F
   */
  private boolean isAlphaNumeric(char c) {
    return isAlpha(c) || isDigit(c);
  }

  /**
   * Indicates if char is a digit.
   */
  private boolean isDigit(char c) {
    return c >= '0' && c <= '9';
  } 

  /**
   * Indicates if we are at the end of the source.
   * @return T/F
   */
  private boolean isAtEnd() {
    return current >= source.length();
  }

  /**
   * gets the next char in the source.
   * @return next char
  */
  private char advance() {
    return source.charAt(current++);
  }
  
  /**
   * Adds this token to the list with literals
   * @param type TokenType
   */
  private void addToken(TokenType type) {
    addToken(type, null);
  }

  /**
   * Does the work for adding
   * (unsure why we need 2 functions to handle this??)
   * @param type TokenType
   * @param literal The literal Object
   */
  private void addToken(TokenType type, Object literal) {
    String text = source.substring(start, current);
    tokens.add(new Token(type, text, literal, line));
  }
}