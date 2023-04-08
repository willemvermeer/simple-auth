docker-compose -f docker-compose.yml up -d
docker cp ./create.sql postgres-simple-auth:/tmp/create.sql
echo "Wait 10 seconds for postgres to startup"
sleep 10;
docker exec -it postgres-simple-auth psql --u simpleauth db -f /tmp/create.sql
echo "Postgres is ready and contains one table users"
