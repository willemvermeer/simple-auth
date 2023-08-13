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
        pub fn to_id_claims(&self) -> IdClaims {
            IdClaims {
                name: self.name.to_string(),
                email: self.email.to_string(),
                id: self.id.to_string(),
                at_hash: None,
            }
        }
    }
}

mod errors {
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
    use crate::{
        db,
        models::{LogonRequest, TokenResponse},
        AppState,
    };
    use axum::extract::State;
    use axum::http::StatusCode;
    use axum::Json;
    use base64::{engine::general_purpose, Engine as _};
    use deadpool_postgres::Client;
    use hmac::{Hmac, Mac};
    use sha2::Sha256;
    use std::time::Instant;

    type HmacSha256 = Hmac<Sha256>;

    fn hash_password(password: &str, salt: &str) -> String {
        let decoded_salt = general_purpose::STANDARD.decode(salt).unwrap();
        let mut mac = HmacSha256::new_from_slice(decoded_salt.as_slice()).unwrap();
        mac.update(password.as_bytes());
        let result = mac.finalize().into_bytes();
        general_purpose::STANDARD.encode(result)
    }

    pub async fn logon_user(
        State(app_state): State<AppState>,
        Json(logon_req): Json<LogonRequest>,
        // state: web::Data<(Pool, EncodingKey)>,
    ) -> (StatusCode, Json<TokenResponse>) {
        let start = Instant::now();
        let client: Client = app_state.pool.get().await.unwrap();
        let user_from_db = db::get_user(&client, &logon_req).await.unwrap();
        let time_db = start.elapsed();

        let encoded = hash_password(&logon_req.password, &user_from_db.salt);
        if encoded != user_from_db.hashpassword {
            (
                StatusCode::INTERNAL_SERVER_ERROR,
                Json(TokenResponse {
                    access_token: "Incorrect password".to_string(),
                    id_token: "Incorrect password".to_string(),
                }),
            )
        } else {
            let time_hash = start.elapsed() - time_db;

            let token_manager = &app_state.token_manager;

            let token_pair = token_manager
                .create_token_pair(user_from_db.to_id_claims())
                .unwrap();

            let time_token = start.elapsed() - time_hash - time_db;

            let response = TokenResponse {
                access_token: token_pair.access_token.raw,
                id_token: token_pair.id_token.raw,
            };

            println!(
                "Axum Db time {}ms Password hash {}ms Token creation {}ms.",
                time_db.as_millis(),
                time_hash.as_millis(),
                time_token.as_millis()
            );

            (StatusCode::OK, Json(response))
        }
    }
}

#[derive(Clone)]
pub struct AppState {
    pool: deadpool_postgres::Pool,
    token_manager: TokenManager,
}

use crate::auth::manager::TokenManager;
use crate::config::SimpleAuthConfig;
use ::config::Config;
use axum::{
    routing::{get, post},
    Router,
};
use dotenv::dotenv;
use tokio_postgres::NoTls;

#[tokio::main]
async fn main() {
    dotenv().ok();
    let config_ = Config::builder()
        .add_source(::config::Environment::default().separator("__"))
        .build()
        .unwrap();

    let config: SimpleAuthConfig = config_.try_deserialize().unwrap();

    let pool = config.pg.builder(NoTls).unwrap().build().unwrap();

    let encoding_key = jsonwebtoken::EncodingKey::from_rsa_pem(include_bytes!("private_key.pem"))
        .expect("Should have been able to read the file");

    let token_manager = TokenManager {
        encoding_key: encoding_key,
        issuer: "https://example.com".to_string(),
        audience: "simple-auth.example.com".to_string(),
    };

    let app_state = AppState {
        pool,
        token_manager,
    };

    // build our application with a route
    let app = Router::new()
        // `GET /` goes to `root`
        .route("/", get(root))
        .route("/token", post(handlers::logon_user))
        .with_state(app_state);

    axum::Server::bind(&"0.0.0.0:3500".parse().unwrap())
        .serve(app.into_make_service())
        .await
        .unwrap();

    println!(
        "Actix-web simple auth open for e-Business at http://{}/ DB pool size {}",
        config.server_addr,
        config.pg.pool.unwrap().max_size,
    );
}

async fn root() -> &'static str {
    "Hello, World! Simple-auth"
}
