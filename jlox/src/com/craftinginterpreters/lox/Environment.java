package com.craftinginterpreters.lox;

import java.util.HashMap;
import java.util.Map;
/**
 * This is where the Map to variables is kept
 */
class Environment {
  final Environment enclosing;
  private final Map<String, Object> values = new HashMap<>();

  /**
   * Allows for nested environments and scoping of variables
   */
  Environment() {
    enclosing = null;
  }

  Environment(Environment enclosing) {
    this.enclosing = enclosing;
  }

  /**
   * Fetches a value for a variable
   * @param name of variable
   * @return value of the variable
   */
  Object get(Token name) {
    if (values.containsKey(name.lexeme)) {
      return values.get(name.lexeme);
    }

    if (enclosing != null) return enclosing.get(name);
    
    throw new RuntimeError(name,
        "Undefined variable '" + name.lexeme + "'.");
  }

  /**
   * Sets the variable to a value
   * @param name of variable
   * @param value new
   */
  void assign(Token name, Object value) {
    if (values.containsKey(name.lexeme)) {
      values.put(name.lexeme, value);
      return;
    }

    if (enclosing != null) {
      enclosing.assign(name, value);
      return;
    }

    throw new RuntimeError(name,
        "Undefined variable '" + name.lexeme + "'.");
  }

  /**
   * Defines the value for a variable
   * @param name of variable
   * @param value of variable
   */
  void define(String name, Object value) {
    values.put(name, value);
  }

}
