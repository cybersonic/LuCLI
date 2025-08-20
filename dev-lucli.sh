#!/bin/bash
mvn clean package --activate-profiles binary --quiet
./target/lucli