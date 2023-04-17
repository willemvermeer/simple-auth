mod auth;

mod config {
    use serde::Deserialize;
    #[derive(Debug, Default, Deserialize)]
    pub struct SimpleAuthConfig {
        pub server_addr: String,
        pub pg: deadpool_postgres::Config,
    }
}

mod models {
    use crate::auth::claims::IdClaims;
    use serde::{Deserialize, Serialize};
    use tokio_pg_mapper_derive::PostgresMapper;

    #[derive(Debug, Serialize)]
    pub struct TokenResponse {
        pub access_token: String,
        pub id_token: String,
    }

    #[derive(Debug, Serialize, Deserialize)]
    pub struct LogonRequest {
        pub username: String,
        pub password: String,
    }

    #[derive(Deserialize, PostgresMapper, Serialize)]
    #[pg_mapper(table = "users")] // singular 'user' is a keyword..
    pub struct User {
        pub id: String,
        pub name: String,
        pub email: String,
        pub hashpassword: String,
        pub salt: String,
    }
    impl User {
        pub fn to_id_claims(self) -> IdClaims {
            IdClaims {
                name: self.name,
                email: self.email,
                id: self.id,
                at_hash: None,
            }
        }
    }
}

mod errors {
    use actix_web::{HttpResponse, ResponseError};
    use deadpool_postgres::PoolError;
    use derive_more::{Display, From};
    use tokio_pg_mapper::Error as PGMError;
    use tokio_postgres::error::Error as PGError;

    #[derive(Display, From, Debug)]
    pub enum MyError {
        NotFound,
        PGError(PGError),
        PGMError(PGMError),
        PoolError(PoolError),
    }
    impl std::error::Error for MyError {}

    impl ResponseError for MyError {
        fn error_response(&self) -> HttpResponse {
            match *self {
                MyError::NotFound => HttpResponse::NotFound().finish(),
                MyError::PoolError(ref err) => {
                    HttpResponse::InternalServerError().body(err.to_string())
                }
                _ => HttpResponse::InternalServerError().finish(),
            }
        }
    }
}

mod db {
    use deadpool_postgres::Client;
    use tokio_pg_mapper::FromTokioPostgresRow;

    use crate::{
        errors::MyError,
        models::{LogonRequest, User},
    };

    pub async fn get_user(client: &Client, user_info: &LogonRequest) -> Result<User, MyError> {
        let _stmt = "SELECT id::TEXT, name, email, hashpassword, salt from users where email = $1;"
            .to_string();
        let stmt = client.prepare(&_stmt).await.unwrap();

        client
            .query(&stmt, &[&user_info.username])
            .await?
            .iter()
            .map(|row| User::from_row_ref(row).unwrap())
            .collect::<Vec<User>>()
            .pop()
            .ok_or(MyError::NotFound) // more applicable for SELECTs
    }
}

mod handlers {
    use crate::auth::claims::{AccessClaims, JwtClaim};
    use crate::auth::tokens::TokenPair;
    use crate::{
        db,
        models::{LogonRequest, TokenResponse},
    };
    use actix_web::{web, Error, HttpResponse};
    use base64::{engine::general_purpose, Engine as _};
    use chrono::Duration;
    use deadpool_postgres::{Client, Pool};
    use hmac::{Hmac, Mac};
    use jsonwebtoken::{Algorithm, EncodingKey, Header};
    use sha2::Sha256;
    use std::time::Instant;
    use uuid::Uuid;

    type HmacSha256 = Hmac<Sha256>;

    fn hash_password(password: &str, salt: &str) -> String {
        let decoded_salt = general_purpose::STANDARD.decode(salt).unwrap();
        let mut mac = HmacSha256::new_from_slice(decoded_salt.as_slice()).unwrap();
        mac.update(password.as_bytes());
        let result = mac.finalize().into_bytes();
        general_purpose::STANDARD.encode(result)
    }

    pub async fn logon_user(
        logon_req: web::Json<LogonRequest>,
        state: web::Data<(Pool, EncodingKey)>,
    ) -> Result<HttpResponse, Error> {
        let user_info: LogonRequest = logon_req.into_inner();

        // get 'static' variables from the state
        let (db_pool, encoding_key) = state.get_ref();

        let start = Instant::now();
        let client: Client = db_pool.get().await.unwrap();
        let user_from_db = db::get_user(&client, &user_info).await?;
        let time_db = start.elapsed();

        let encoded = hash_password(&user_info.password, &user_from_db.salt);
        if encoded != user_from_db.hashpassword.to_string() {
            Ok(HttpResponse::InternalServerError().body("Incorrect password"))
        } else {
            let time_hash = start.elapsed() - time_db;

            let header = Header::new(Algorithm::RS256);
            let common_claims = JwtClaim::empty()
                .with_audience("simple-auth.example.com".to_string())
                .with_issuer("https://example.com".to_string())
                .issued_now()
                .expires_in(Duration::minutes(60).num_seconds().unsigned_abs());

            let id_claims = user_from_db.to_id_claims();
            let access_claims = AccessClaims {
                session_id: Uuid::new_v4().to_string(),
            };

            let token_pair = TokenPair::create(
                &encoding_key,
                &header,
                common_claims,
                id_claims,
                access_claims,
            )
            .unwrap();
            let time_token = start.elapsed() - time_hash;

            let response = TokenResponse {
                access_token: token_pair.access_token.raw,
                id_token: token_pair.id_token.raw,
            };

            println!(
                "Actix-web Db time {}ms Password hash {}ms Token creation {}ms.",
                time_db.as_millis(),
                time_hash.as_millis(),
                time_token.as_millis()
            );

            Ok(HttpResponse::Ok().json(response))
        }
    }
}

use ::config::Config;
use actix_web::{web, App, HttpServer};
use dotenv::dotenv;
use handlers::logon_user;
use tokio_postgres::NoTls;

use crate::config::SimpleAuthConfig;

#[actix_web::main]
async fn main() -> std::io::Result<()> {
    dotenv().ok();
    let config_ = Config::builder()
        .add_source(::config::Environment::default().separator("__"))
        .build()
        .unwrap();

    let config: SimpleAuthConfig = config_.try_deserialize().unwrap();

    let pool = config.pg.builder(NoTls).unwrap().build().unwrap();

    let encoding_key = jsonwebtoken::EncodingKey::from_rsa_pem(include_bytes!("private_key.pem"))
        .expect("Should have been able to read the file");

    let server = HttpServer::new(move || {
        App::new()
            .app_data(web::Data::new((pool.clone(), encoding_key.clone())))
            .service(web::resource("/token").route(web::post().to(logon_user)))
    })
    .workers(50)
    .bind(config.server_addr.clone())?
    .run();

    println!(
        "Actix-web simple auth open for e-Business at http://{}/",
        config.server_addr
    );

    server.await
}
