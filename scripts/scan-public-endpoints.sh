#!/bin/bash
# scripts/scan-public-endpoints.sh

SERVICES="booking-service user-service provider-service invoice-service calendar-service"

echo "| Service | Public Endpoint Matchers (from SecurityConfig) | Public Path Logic (from Filter) |"
echo "|---------|---------------------------------------------|---------------------------------|"

for svc in $SERVICES; do
    # Extract permitAll matchers from SecurityConfig
    MATCHERS=$(grep -oE '\.requestMatchers\([^)]*\)\.permitAll\(\)' $svc/src/main/java/com/team28/booking/*/config/SecurityConfig.java | sed 's/.*requestMatchers(\(.*\)).permitAll().*/\1/' | tr '\n' ' ')
    
    # Extract isPublic logic from Filter
    FILTER_LOGIC=$(grep -A 10 "private boolean isPublic" $svc/src/main/java/com/team28/booking/*/auth/JwtAuthenticationFilter.java | grep -E "path\.equals|path\.startsWith" | sed 's/.*"\(.*\)".*/\1/' | tr '\n' ' ')
    
    echo "| $svc | $MATCHERS | $FILTER_LOGIC |"
done
