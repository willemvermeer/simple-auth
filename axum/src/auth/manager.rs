use crate::auth::claims::{AccessClaims, IdClaims, JwtClaim};
use crate::auth::errors::Result;
use crate::auth::tokens::TokenPair;
use chrono::Duration;
use jsonwebtoken::{Algorithm, Header};
use uuid::Uuid;

#[derive(Clone)]
pub struct TokenManager {
    pub encoding_key: jsonwebtoken::EncodingKey,
    pub issuer: String,
    pub audience: String,
}
impl TokenManager {
    pub fn create_token_pair(&self, id_claims: IdClaims) -> Result<TokenPair> {
        let header = Header::new(Algorithm::RS256);

        let common_claims = JwtClaim::empty()
            .with_audience(self.audience.clone())
            .with_issuer(self.issuer.clone())
            .issued_now()
            .expires_in(Duration::minutes(60).num_seconds().unsigned_abs());

        let access_claims = AccessClaims {
            session_id: Uuid::new_v4().to_string(),
        };

        TokenPair::create(
            &self.encoding_key,
            &header,
            common_claims,
            id_claims,
            access_claims,
        )
    }
}
