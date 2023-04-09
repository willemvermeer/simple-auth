use jsonwebtoken::{encode, Algorithm, Header, Validation, decode, EncodingKey};
use serde::{Deserialize, Serialize};
use serde_json::Value;

use crate::auth::claims::*;
use crate::auth::config::AuthConfig;
use crate::auth::errors::*;

#[derive(Debug, Serialize, Deserialize)]
pub struct IdToken {
    pub header: Header,
    pub claims: JwtClaim,
    pub content: IdClaims,
}
impl IdToken {
    pub fn raw_token(&self, encoding_key: &EncodingKey) -> Result<String> {
        create_token(encoding_key, &self.header, self.claims.clone(), self.content.clone())
    }
    pub fn from_raw_token(conf: &AuthConfig, raw_token: &str) -> Result<IdToken> {
        let decoding_key= &conf.decoding_key.clone().expect("token can not be decoded without the decoding key");
        let token_data = decode::<Value>(raw_token, decoding_key, &Validation::new(Algorithm::RS256))?;
        let jwt_claims = serde_json::from_str::<JwtClaim>(&token_data.claims.to_string())?;
        let id_claims = serde_json::from_str::<IdClaims>(&token_data.claims.to_string())?;
        Ok(IdToken{
            header: token_data.header,
            claims: jwt_claims,
            content: id_claims
        })
    }
}

#[derive(Debug, Serialize, Deserialize)]
pub struct AccessToken {
    pub header: Header,
    pub claims: JwtClaim,
    pub content: AccessClaims,
}
impl AccessToken {
    pub fn raw_token(&self, encoding_key: &EncodingKey) -> Result<String> {
        create_token(encoding_key, &self.header, self.claims.clone(), self.content.clone())
    }
    pub fn from_raw_token(conf: &AuthConfig, raw_token: &str) -> Result<AccessToken> {
        let decoding_key= &conf.decoding_key.clone().expect("token can not be decoded without the decoding key");
        let token_data = decode::<Value>(raw_token, decoding_key, &Validation::new(Algorithm::RS256))?;
        let jwt_claims = serde_json::from_str::<JwtClaim>(&token_data.claims.to_string())?;
        let access_claims = serde_json::from_str::<AccessClaims>(&token_data.claims.to_string())?;
        Ok(AccessToken{
            header: token_data.header,
            claims: jwt_claims,
            content: access_claims
        })
    }
}

#[derive(Debug, Serialize, Deserialize)]
pub struct TokenPair {
    pub id_token: IdToken,
    pub access_token: AccessToken,
}
impl TokenPair {
    pub fn create(
        encoding_key: &EncodingKey,
        header: &Header,
        common_claims: JwtClaim,
        id_claims: IdClaims,
        access_claims: AccessClaims
    ) -> Result<TokenPair> {
        let access_token = AccessToken {
            header: header.clone(),
            claims: common_claims.clone(),
            content: access_claims,
        };
        let at_hash = access_token.raw_token(encoding_key).and_then(|at| hash_token(&at))?;

        let id_token = IdToken {
            header: header.clone(),
            claims: common_claims,
            content: id_claims.with_at_hash(at_hash),
        };
        Ok(TokenPair {
            id_token: id_token,
            access_token: access_token,
        })
    }
    pub fn from_raw_tokens(conf: &AuthConfig, raw_id_token: &str, raw_access_token: &str) -> Result<TokenPair> {
        let id_token = IdToken::from_raw_token(conf, raw_id_token)?;
        let access_token = AccessToken::from_raw_token(conf, raw_access_token)?;
        let at_hash = &access_token.raw_token(&conf.encoding_key).and_then(|at| hash_token(&at))?;
        let id_at_hash = &id_token.content.at_hash.clone().unwrap_or("".to_string());
        if !at_hash.eq(id_at_hash) {
            Err(Error{ message: "The access token hash does not match the corresponding id token at_hash value.".to_string() })
        } else {
            Ok(TokenPair {
                id_token: id_token,
                access_token: access_token
            })
        }
    }
}

fn create_token<T: Serialize>(encoding_key: &EncodingKey, header: &Header, claims: JwtClaim, content: T) -> Result<String> {
    let claims = claims
        .with_content(content)
        .as_json_value()?;
    let token = encode(header, &claims, encoding_key)?;
    Ok(token)
}

fn base64_encode(input: &str) -> Result<String> {
    let encoded = base64_encode_u8(input.as_bytes())?;
    let result = String::from_utf8(encoded)?;
    Ok(result)
}

fn base64_encode_u8(input: &[u8]) -> Result<Vec<u8>> {
    use base64::{engine::general_purpose, Engine as _};
    let mut buf = Vec::new();
    buf.resize(input.len() * 4 / 3 + 4, 0);
    let bytes_written = general_purpose::STANDARD.encode_slice(input, &mut buf)?;
    buf.truncate(bytes_written);
    Ok(buf)
}

fn sha256(input: &[u8]) -> Vec<u8> {
    use sha2::{Digest, Sha256};

    let mut hasher = Sha256::new();
    hasher.update(input);
    let result = hasher.finalize();
    result[..].to_vec()
}

fn hash_token(input: &str) -> Result<String> {
    let enc = base64_encode_u8(base64_encode(input)?.as_bytes())?;
    let hash = sha256(&enc);
    let mid = hash.len() / 2;
    let hash_2 = hash.split_at(mid).0;
    let result = String::from_utf8(base64_encode_u8(hash_2)?)?;
    Ok(result)
}
