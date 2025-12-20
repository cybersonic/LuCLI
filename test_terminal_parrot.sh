#!/bin/bash
# Test parrot command in terminal mode

# Send commands to terminal via stdin
echo "parrot this is a test from terminal mode
exit" | java -jar target/lucli.jar
