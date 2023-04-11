sbt docker:stage
cd target/docker/stage
docker stop akka-http-simple-auth
docker build --tag example.com/akka-http-simple-auth:latest .
docker run --rm -d --name akka-http-simple-auth -p 8781:8781 --network="pg-perf" \
  -e CONFIG_FORCE_http_port=8781 \
  -e CONFIG_FORCE_http_interface="0.0.0.0" \
  -e CONFIG_FORCE_db_host="postgres-simple-auth" \
  -e CONFIG_FORCE_db_port=5432 \
  example.com/akka-http-simple-auth \
  -main com.example.Main
