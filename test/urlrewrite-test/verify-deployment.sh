#!/bin/bash

# Deployment Verification Script for URL Rewrite
# This script checks that all required files are deployed correctly
# without starting the server

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Configuration
SERVER_NAME="${1:-urlrewrite-test}"
LUCLI_HOME="${LUCLI_HOME:-$HOME/.lucli}"
SERVER_DIR="$LUCLI_HOME/servers/$SERVER_NAME"

echo -e "${BLUE}========================================${NC}"
echo -e "${BLUE}   URL Rewrite Deployment Verification${NC}"
echo -e "${BLUE}========================================${NC}"
echo ""
echo "Server Name: $SERVER_NAME"
echo "Server Directory: $SERVER_DIR"
echo ""

# Function to print success
print_success() {
    echo -e "${GREEN}✓ $1${NC}"
}

# Function to print error
print_error() {
    echo -e "${RED}✗ $1${NC}"
}

# Function to print warning
print_warning() {
    echo -e "${YELLOW}⚠ $1${NC}"
}

# Function to print info
print_info() {
    echo -e "${BLUE}ℹ $1${NC}"
}

# Track overall status
all_checks_passed=true

# Check if server directory exists
echo -e "${BLUE}>>> Checking Server Directory${NC}"
echo ""

if [ -d "$SERVER_DIR" ]; then
    print_success "Server directory exists"
else
    print_error "Server directory not found: $SERVER_DIR"
    echo ""
    echo "The server instance has not been created yet."
    echo "Create it by running:"
    echo "  ./lucli server start --name $SERVER_NAME --port 8888 --webroot test/urlrewrite-test"
    echo ""
    exit 1
fi

# Check WEB-INF directory structure
echo ""
echo -e "${BLUE}>>> Checking Directory Structure${NC}"
echo ""

WEB_INF="$SERVER_DIR/webapps/ROOT/WEB-INF"
if [ -d "$WEB_INF" ]; then
    print_success "WEB-INF directory exists"
else
    print_error "WEB-INF directory not found"
    all_checks_passed=false
fi

WEB_INF_LIB="$WEB_INF/lib"
if [ -d "$WEB_INF_LIB" ]; then
    print_success "WEB-INF/lib directory exists"
else
    print_error "WEB-INF/lib directory not found"
    all_checks_passed=false
fi

# Check for UrlRewriteFilter JAR
echo ""
echo -e "${BLUE}>>> Checking UrlRewriteFilter JAR${NC}"
echo ""

jar_found=false
jar_file=""

if [ -d "$WEB_INF_LIB" ]; then
    for jar in "$WEB_INF_LIB"/urlrewritefilter-*.jar; do
        if [ -f "$jar" ]; then
            jar_found=true
            jar_file="$jar"
            jar_name=$(basename "$jar")
            jar_size=$(ls -lh "$jar" | awk '{print $5}')
            print_success "UrlRewriteFilter JAR found: $jar_name"
            print_info "  Location: $jar"
            print_info "  Size: $jar_size"
            
            # Verify it's a valid JAR
            if jar tf "$jar" > /dev/null 2>&1; then
                print_success "JAR file is valid and readable"
            else
                print_error "JAR file appears to be corrupted"
                all_checks_passed=false
            fi
            
            # Check for main filter class
            if jar tf "$jar" | grep -q "org/tuckey/web/filters/urlrewrite/UrlRewriteFilter.class"; then
                print_success "UrlRewriteFilter class found in JAR"
            else
                print_error "UrlRewriteFilter class not found in JAR"
                all_checks_passed=false
            fi
        fi
    done
fi

if [ "$jar_found" = false ]; then
    print_error "UrlRewriteFilter JAR not found in WEB-INF/lib/"
    print_info "Expected: urlrewritefilter-*.jar"
    all_checks_passed=false
fi

# Check for urlrewrite.xml
echo ""
echo -e "${BLUE}>>> Checking urlrewrite.xml Configuration${NC}"
echo ""

URLREWRITE_XML="$WEB_INF/urlrewrite.xml"
if [ -f "$URLREWRITE_XML" ]; then
    print_success "urlrewrite.xml found"
    print_info "  Location: $URLREWRITE_XML"
    
    # Check file size
    xml_lines=$(wc -l < "$URLREWRITE_XML")
    print_info "  Lines: $xml_lines"
    
    # Validate XML structure
    if grep -q "<urlrewrite>" "$URLREWRITE_XML"; then
        print_success "XML structure appears valid"
    else
        print_error "XML structure may be invalid (missing <urlrewrite> root)"
        all_checks_passed=false
    fi
    
    # Check for key rules
    echo ""
    print_info "Checking URL rewrite rules:"
    
    if grep -q "CFML Template Extension-less URLs" "$URLREWRITE_XML"; then
        print_success "  Extension-less URL rule found"
    else
        print_warning "  Extension-less URL rule not found"
    fi
    
    if grep -q "Root Index" "$URLREWRITE_XML"; then
        print_success "  Root index rule found"
    else
        print_warning "  Root index rule not found"
    fi
    
    # Count total rules
    rule_count=$(grep -c "<rule>" "$URLREWRITE_XML" || echo "0")
    print_info "  Total rules defined: $rule_count"
    
else
    print_error "urlrewrite.xml not found"
    print_info "Expected: $URLREWRITE_XML"
    all_checks_passed=false
fi

# Check web.xml configuration
echo ""
echo -e "${BLUE}>>> Checking web.xml Configuration${NC}"
echo ""

WEB_XML="$WEB_INF/web.xml"
if [ -f "$WEB_XML" ]; then
    print_success "web.xml found"
    print_info "  Location: $WEB_XML"
    
    # Check for UrlRewriteFilter filter definition
    if grep -q "UrlRewriteFilter" "$WEB_XML"; then
        print_success "UrlRewriteFilter reference found in web.xml"
        
        # Check for filter class
        if grep -q "org.tuckey.web.filters.urlrewrite.UrlRewriteFilter" "$WEB_XML"; then
            print_success "UrlRewriteFilter class configured"
        else
            print_error "UrlRewriteFilter class not properly configured"
            all_checks_passed=false
        fi
        
        # Check for filter mapping
        if grep -q "<filter-mapping>" "$WEB_XML" && grep -A 5 "<filter-mapping>" "$WEB_XML" | grep -q "UrlRewriteFilter"; then
            print_success "UrlRewriteFilter mapping configured"
        else
            print_error "UrlRewriteFilter mapping not found"
            all_checks_passed=false
        fi
        
        # Check URL pattern
        if grep -A 10 "UrlRewriteFilter" "$WEB_XML" | grep -q "<url-pattern>/\\*</url-pattern>"; then
            print_success "URL pattern /* configured correctly"
        else
            print_warning "URL pattern /* not found (might use different pattern)"
        fi
        
    else
        print_error "UrlRewriteFilter not configured in web.xml"
        all_checks_passed=false
    fi
    
else
    print_error "web.xml not found"
    print_info "Expected: $WEB_XML"
    all_checks_passed=false
fi

# Check dependencies cache
echo ""
echo -e "${BLUE}>>> Checking Dependencies Cache${NC}"
echo ""

DEPENDENCIES_DIR="$LUCLI_HOME/dependencies"
if [ -d "$DEPENDENCIES_DIR" ]; then
    print_success "Dependencies cache directory exists"
    print_info "  Location: $DEPENDENCIES_DIR"
    
    # Check for cached JAR
    if ls "$DEPENDENCIES_DIR"/urlrewritefilter-*.jar 1> /dev/null 2>&1; then
        cached_jar=$(ls "$DEPENDENCIES_DIR"/urlrewritefilter-*.jar | head -1)
        cached_jar_name=$(basename "$cached_jar")
        cached_jar_size=$(ls -lh "$cached_jar" | awk '{print $5}')
        print_success "Cached UrlRewriteFilter JAR found: $cached_jar_name"
        print_info "  Size: $cached_jar_size"
    else
        print_warning "No cached UrlRewriteFilter JAR found"
        print_info "It will be downloaded on next server start"
    fi
else
    print_warning "Dependencies cache directory not found"
    print_info "It will be created on next server start"
fi

# Check server configuration
echo ""
echo -e "${BLUE}>>> Checking Server Configuration${NC}"
echo ""

SERVER_JSON="$SERVER_DIR/.lucli-server.json"
if [ -f "$SERVER_JSON" ]; then
    print_success "Server configuration found"
    print_info "  Location: $SERVER_JSON"
else
    print_warning "Server configuration not found"
fi

# Summary
echo ""
echo -e "${BLUE}========================================${NC}"
echo -e "${BLUE}         Verification Summary${NC}"
echo -e "${BLUE}========================================${NC}"
echo ""

if [ "$all_checks_passed" = true ]; then
    print_success "All deployment checks passed!"
    echo ""
    echo "The URL rewrite functionality is properly deployed."
    echo "You can now test the server with:"
    echo "  ./lucli server start --name $SERVER_NAME"
    echo ""
    echo "Or run the full test suite:"
    echo "  ./test/urlrewrite-test/test-urlrewrite.sh"
    echo ""
    exit 0
else
    print_error "Some deployment checks failed"
    echo ""
    echo "Please review the errors above and:"
    echo "1. Ensure LuCLI is built: ./build.sh"
    echo "2. Restart the server: ./lucli server start --name $SERVER_NAME"
    echo "3. Check server logs: ./lucli server logs --name $SERVER_NAME"
    echo ""
    exit 1
fi