package com.craftinginterpreters.lox;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
// import java.util.Scanner;
// import static com.craftinginterpreters.lox.Scanner.*;

public class Lox {
  private static final Interpreter interpreter = new Interpreter();
  static boolean hadError = false;
  static boolean hadRuntimeError = false;

  public static void main(String[] args) throws IOException {
    if (args.length > 1) {
      System.out.println("Usage: jlox [script]");
      System.exit(64); 
    } else if (args.length == 1) {
      runFile(args[0]);
    } else {
      runPrompt();
    }
  }

  /**
   * If we run jlox on a file argument we read the file and execute it.
   * @param path Path specified to lox file
   * @throws IOException Can trow IO error if something happens
   */
  private static void runFile(String path) throws IOException {
    byte[] bytes = Files.readAllBytes(Paths.get(path));
    run(new String(bytes, Charset.defaultCharset()));
    if (hadError) System.exit(65); // Exit and indicate an error has occured
    if (hadRuntimeError) System.exit(70); // Exit and indicate run time error
  }

  /**
   * If we run jlox we can take input line by line to execute.
   * @throws IOException can throw IO error
   */
  private static void runPrompt() throws IOException {
    InputStreamReader input = new InputStreamReader(System.in);
    BufferedReader reader = new BufferedReader(input);

    for (;;) { 
      System.out.print("> ");
      String line = reader.readLine();
      if (line == null) break;
      run(line);
      hadError = false;
    }
  }

  /**
   * Sets up the scanner to get the tokens in the source.
   * @param source
   */
  private static void run(String source) {
    Scanner scanner = new Scanner(source);
    List<Token> tokens = scanner.scanTokens();

    Parser parser = new Parser(tokens);
    List<Stmt> statements = parser.parse();
    // Stop if there was a syntax error.
    if (hadError) return;

    interpreter.interpret(statements);
  }
  
   /**
   * Bare minimum for handling syntax errors
   * @param line the line# that was bad
   * @param message the message of the error.
   */
  static void error(int line, String message) {
    report(line, "", message);
  }

  /**
   * Prints the error for the user to see
   * @param line line number of syntax error
   * @param where what file? (right now blank)
   * @param message error message (hopefully informative)
   * 
   * TODO - add WHERE and print the line(s) to show the user the mistake.
   */
  private static void report(int line, String where,
                             String message) {
    System.err.println(
        "[line " + line + "] Error" + where + ": " + message);
    hadError = true;
  }

  /** "Why is this here?"
   * Per the craftinginterpreters.com
   * "...it’s good engineering practice to separate
   * the code that generates the errors from the code 
   * that reports them."
   */

  /**
   * Bare minimum for handling syntax errors
   * @param line the line# that was bad
   * @param message the message of the error.
   */
  static void error(Token token, String message) {
    if (token.type == TokenType.EOF) {
      report(token.line, " at end", message);
    } else {
      report(token.line, " at '" + token.lexeme + "'", message);
    }
  }

  /**
   * Lox RunTimeError Handler
   * @param error runtime
   */
  static void runtimeError(RuntimeError error) {
    System.err.println(error.getMessage() +
        "\n[line " + error.token.line + "]");
    hadRuntimeError = true;
  }
}
