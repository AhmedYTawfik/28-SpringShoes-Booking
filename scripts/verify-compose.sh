#!/bin/bash
# scripts/verify-compose.sh

echo "Waiting for all 6 databases to reach healthy state..."
# PostgreSQL mongo redis elasticsearch neo4j cassandra

DB_SERVICES="booking-db booking-mongo booking-redis booking-elasticsearch booking-neo4j booking-cassandra"

for svc in $DB_SERVICES; do
  echo "Checking $svc..."
  until [ "$(docker inspect -f '{{.State.Health.Status}}' $svc)" == "healthy" ]; do
    echo "  $svc is still $(docker inspect -f '{{.State.Health.Status}}' $svc)... waiting 5s"
    sleep 5
  done
  echo "  $svc is HEALTHY"
done

echo "Running basic connection tests..."

echo "PostgreSQL check..."
docker exec booking-db pg_isready -U postgres | grep -q "accepting connections" || { echo "FAIL: postgres not ready"; exit 1; }

echo "Redis check..."
docker exec booking-redis redis-cli -a redispass ping | grep -q PONG || { echo "FAIL: redis ping failed"; exit 1; }

echo "MongoDB check..."
docker exec booking-mongo mongosh -u root -p rootpass --authenticationDatabase admin --eval 'db.adminCommand("ping").ok' | grep -q '1' || { echo "FAIL: mongo ping failed"; exit 1; }

echo "Neo4j check..."
docker exec booking-neo4j cypher-shell -u neo4j -p neo4jpass 'RETURN 1' | grep -q '1' || { echo "FAIL: neo4j query failed"; exit 1; }

echo "Elasticsearch check..."
curl -fsS http://localhost:9200/_cluster/health | grep -q '"status":"\(green\|yellow\)"' || { echo "FAIL: elasticsearch health check failed"; exit 1; }

echo "Cassandra check..."
docker exec booking-cassandra cqlsh -e "DESCRIBE KEYSPACES;" | grep -q "bookingks" || { echo "FAIL: cassandra keyspace check failed"; exit 1; }

echo "ALL DATABASES ARE HEALTHY AND ACCESSIBLE"
