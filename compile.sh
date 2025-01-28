#!/bin/bash

# Clean bin directory
rm -rf bin

# Create bin directory
mkdir -p bin

# Compile Java files without module-info
javac --release 17 -d bin src/d1/*.java
