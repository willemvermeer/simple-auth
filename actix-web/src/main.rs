mod auth;

mod config {
    use serde::Deserialize;
    #[derive(Debug, Default, Deserialize)]
    pub struct ExampleConfig {
        pub server_addr: String,
        pub pg: deadpool_postgres::Config,
    }
}

mod models {
    use serde::{Deserialize, Serialize};
    use tokio_pg_mapper_derive::PostgresMapper;
    use crate::auth::claims::IdClaims;

    #[derive(Debug, Serialize)]
    pub struct TokenResponse {
        pub access_token: String,
        pub id_token: String,
        pub scope: String,
        pub expires_in: i32,
        pub token_type: String,
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
                at_hash: None
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

    use crate::{errors::MyError, models::{User, LogonRequest}};

    pub async fn get_user(client: &Client, user_info: LogonRequest) -> Result<User, MyError> {
        let _stmt = "SELECT id::TEXT, name, email, hashpassword, salt from users where email = $1;"
            .to_string();
        let stmt = client.prepare(&_stmt).await.unwrap();

        client
            .query(
                &stmt,
                &[ &user_info.username ],
            )
            .await?
            .iter()
            .map(|row| User::from_row_ref(row).unwrap())
            .collect::<Vec<User>>()
            .pop()
            .ok_or(MyError::NotFound) // more applicable for SELECTs
    }
}

mod handlers {
    use actix_web::{web, Error, HttpResponse};
    use deadpool_postgres::{Client, Pool};
    use hmac::{Hmac, Mac};
    use sha2::Sha256;
    use chrono::{prelude::*, Duration};
    use base64::{engine::general_purpose, Engine as _};
    use crate::{db, errors::MyError, models::{LogonRequest, TokenResponse}};
    use std::fs;
    use std::time::Instant;
    use jsonwebtoken::{encode, Algorithm, EncodingKey, DecodingKey, Header};
    use crate::auth::claims::{AccessClaims, JwtClaim};
    use crate::auth::tokens::TokenPair;

    type HmacSha256 = Hmac<Sha256>;

    fn hash_password(password: &str, salt: &str) -> String {
        let decoded_salt = general_purpose::STANDARD.decode(salt).unwrap();
        let mut mac = HmacSha256::new_from_slice(decoded_salt.as_slice()).unwrap();
        mac.update(password.as_bytes());
        let result = mac.finalize().into_bytes();
        general_purpose::STANDARD.encode(result)
    }

    pub async fn add_user(
        logon_req: web::Json<LogonRequest>,
        state: web::Data<(Pool, EncodingKey)>,
    ) -> Result<HttpResponse, Error> {
        let start = Instant::now();

        // println!("Logon endpoint invoked");
        let user_info: LogonRequest = logon_req.into_inner();

        let m1 = start.elapsed();

        let (db_pool, encoding_key) = state.get_ref();

        let client: Client = db_pool.get().await.unwrap();

        let m2 = start.elapsed();

        let request_password = &user_info.password.clone();
        let new_user = db::get_user(&client, user_info).await?;

        let m3 = start.elapsed();

        let hashpassword = new_user.hashpassword.to_string();

        let encoded = hash_password(&request_password, &new_user.salt);

        if encoded != hashpassword {
            println!("Incorrect password for user ");
            Ok(HttpResponse::InternalServerError().body("error"))
        } else {
            println!("Logon success for user");
            let n1 = start.elapsed();

            let header = Header::new(Algorithm::RS256);

            let common_claims = JwtClaim::empty()
                .with_audience("simple-auth.example.com".to_string())
                .with_issuer("https://example.com".to_string())
                .issued_now()
                .expires_in(Duration::minutes(60).num_seconds().unsigned_abs());

            let id_claims = new_user.to_id_claims();
            let access_claims = AccessClaims{ session_id: "session_id".to_string() };

            let n2 = start.elapsed();

            let token_pair = TokenPair::create(&encoding_key, &header, common_claims, id_claims, access_claims).unwrap();
            let id_token = token_pair.id_token.raw;
            let access_token = token_pair.access_token.raw;

            let n3 = start.elapsed();

            let response = TokenResponse {
                id_token: id_token,
                access_token: access_token,
                expires_in: 1000,
                scope: "scope".to_string(),
                token_type: "whatever".to_string(),
            };

            let mx = start.elapsed();
            println!("Elapsed total: {}; db_pool: {}; get_user: {}; hash_password: {}; claims: {}; tokens: {}",
                     mx.as_millis(), (m2-m1).as_millis(), (m3-m2).as_millis(),
                     (n1-m3).as_millis(), (n2-n1).as_millis(), (n3-n2).as_millis());
            Ok(HttpResponse::Ok().json(response))
        }
    }
}

use ::config::Config;
use actix_web::{web, App, HttpServer};
use deadpool_postgres::{Manager, ManagerConfig, Pool, PoolConfig, RecyclingMethod, Runtime};
use dotenv::dotenv;
use handlers::add_user;
use tokio_postgres::NoTls;

use crate::config::ExampleConfig;

#[actix_web::main]
async fn main() -> std::io::Result<()> {

    let num_cpus = num_cpus::get_physical();
    println!("Available CPUs: {}", num_cpus);

    dotenv().ok();
    let config_ = Config::builder()
        .add_source(::config::Environment::default())
        .build()
        .unwrap();

    let config: ExampleConfig = config_.try_deserialize().unwrap();
    println!("config: {}", config.server_addr);
    let pool = config.pg.builder(NoTls).unwrap().max_size(num_cpus * 5).build().unwrap();
    println!("pool started");
    let encoding_key = jsonwebtoken::EncodingKey::from_rsa_pem(include_bytes!("private_key.pem"))
        .expect("Should have been able to read the file");

    println!("encoding read");
    let server = HttpServer::new(move || {
        App::new()
            .app_data(web::Data::new((pool.clone(), encoding_key.clone())))
            .service(web::resource("/token").route(web::post().to(add_user )))
    })
        .workers(num_cpus*5 )
        .bind(config.server_addr.clone())?
        .run();
    println!("Server running at http://{}/", config.server_addr);


    server.await
}