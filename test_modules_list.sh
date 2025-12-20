#!/bin/bash
# Test modules list in terminal mode

echo "Testing modules list in terminal mode..."
echo ""
echo "modules list" | java -jar target/lucli.jar terminal
