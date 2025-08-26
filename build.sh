#!/bin/bash

mvn clean package -Pbinary -q && cp ./target/lucli ~/bin && lucli --version