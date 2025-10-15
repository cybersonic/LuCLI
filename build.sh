#!/bin/bash

mvn clean package
java -jar target/lucli.jar --version