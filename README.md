# simple-auth
Simple Authentication Web Service for Performance Comparisons

## Database
Start postgres database called `db` on port 5435 in docker container `postgres-simple-auth`:
```bash
cd db
./run_db.sh

```

### Invoke /token endpoint
```bash
curl http://localhost:8780/token -X POST -d '{"username":"john@example.com","password":"TopSecret0!"}' -H 'Content-Type: application/json'
```

### Load test
```bash
wrk -s post-token.lua -d60 -t50 -c50 http://localhost:8781/token
```