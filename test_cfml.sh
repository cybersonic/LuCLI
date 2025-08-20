#!/bin/bash
mvn clean package
echo "Testing CFML commands in LuCLI terminal..."

# Test with echo to simulate user input
echo "cfml now()" | ./target/lucli

echo "Test completed."
