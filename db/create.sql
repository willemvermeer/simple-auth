CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE TABLE IF NOT EXISTS users (
  id uuid PRIMARY KEY,
  name VARCHAR(255) NOT NULL,
  email VARCHAR(255) NOT NULL,
  hashpassword VARCHAR(255) NOT NULL,
  salt VARCHAR(255) NOT NULL
);

-- salt/password for plaintext password TopSecret0!
INSERT INTO users (id, name, email, hashpassword, salt)
VALUES (gen_random_uuid(), 'John Doe', 'john@example.com', 'X864zD50ii23b75iB8UBUrbf0HTIHGRkHuR+ioTD9WE=', 'qmyRlRwXH83LqAUz/V5AUA==');
