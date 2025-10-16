#!/bin/bash

mvn clean package -Pbinary
java -jar target/lucli.jar --version
target/lucli --version
target/lucli --help