#!/bin/bash

echo "Comprehensive CFML test in LuCLI..."

# Create a test script with multiple CFML commands
cat << 'EOF' | ./target/lucli
cfml now()
cfml dateFormat(now(), 'yyyy-mm-dd HH:nn:ss')
cfml listToArray('apple,banana,cherry')
cfml structKeyList({name: 'John', age: 30, city: 'New York'})
cfml 2 + 3 * 4
help
exit
EOF

echo "Comprehensive test completed."
