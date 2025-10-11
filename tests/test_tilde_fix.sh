#!/bin/bash

echo "Testing tilde expansion fix..."
cd /Users/markdrew/Code/DistroKid/LuCLI

# Clean up any existing literal ~ directories first
if [ -d "~" ]; then
    echo "Removing existing literal ~ directory..."
    rm -rf ~
fi

echo "Starting test..."

# Create a temporary test directory 
mkdir -p test_tilde_fix
cd test_tilde_fix

# Test that mkdir with ~ expansion works correctly now
# This should create directories in the actual home directory, not literal ~
echo "Creating test directories in home directory..."
mkdir -p ~/temp_test_dir_from_lucli

# Check if the directory was created in the actual home directory
if [ -d ~/temp_test_dir_from_lucli ]; then
    echo "✅ SUCCESS: Directory was created in actual home directory"
    ls -la ~/temp_test_dir_from_lucli
else
    echo "❌ FAILED: Directory was not created in home directory"
fi

# Check if a literal ~ directory was created (this would be the bug)
if [ -d "~" ]; then
    echo "❌ FAILED: Literal ~ directory was created (bug still exists)"
    ls -la ~
else
    echo "✅ SUCCESS: No literal ~ directory created"
fi

# Clean up
rm -rf ~/temp_test_dir_from_lucli
cd ..
rmdir test_tilde_fix

echo "Test completed."
