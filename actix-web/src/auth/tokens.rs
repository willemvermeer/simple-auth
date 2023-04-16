use jsonwebtoken::{decode, encode, Algorithm, EncodingKey, Header, Validation};
use serde::{Deserialize, Serialize};
use serde_json::Value;

use crate::auth::claims::*;
use crate::auth::errors::*;

#[derive(Debug, Serialize, Deserialize)]
pub struct IdToken {
    pub header: Header,
    pub claims: JwtClaim,
    pub content: IdClaims,
    pub raw: String,
}

#[derive(Debug, Serialize, Deserialize)]
pub struct AccessToken {
    pub header: Header,
    pub claims: JwtClaim,
    pub content: AccessClaims,
    pub raw: String,
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
        access_claims: AccessClaims,
    ) -> Result<TokenPair> {
        let at_tkn = create_token(
            encoding_key,
            &header,
            common_claims.clone(),
            access_claims.clone(),
        )
        .unwrap();
        let at_hash = hash_token(&at_tkn)?;
        let id_claims_with_hash = id_claims.with_at_hash(at_hash);
        let id_tkn = create_token(
            encoding_key,
            &header,
            common_claims.clone(),
            id_claims_with_hash.clone(),
        )
        .unwrap();
        let access_token = AccessToken {
            header: header.clone(),
            claims: common_claims.clone(),
            content: access_claims,
            raw: at_tkn,
        };
        let id_token = IdToken {
            header: header.clone(),
            claims: common_claims,
            content: id_claims_with_hash,
            raw: id_tkn,
        };
        Ok(TokenPair {
            id_token: id_token,
            access_token: access_token,
        })
    }
}

fn create_token<T: Serialize>(
    encoding_key: &EncodingKey,
    header: &Header,
    claims: JwtClaim,
    content: T,
) -> Result<String> {
    let claims = claims.with_content(content).as_json_value()?;
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
