javac -d out src/com/craftinginterpreters/lox/*.java
java -cp out com.craftinginterpreters.lox.Lox %1
echo Cleaning up output folder...
rmdir /s /q out
echo Cleanup complete.
