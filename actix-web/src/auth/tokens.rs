use jsonwebtoken::{encode, EncodingKey, Header};
use serde::{Deserialize, Serialize};

use crate::auth::claims::*;
use crate::auth::errors::*;

#[derive(Debug, Serialize, Deserialize)]
pub struct IdToken {
    pub header: Header,
    pub claims: JwtClaim,
    pub content: IdClaims,
    pub raw: String,
}
impl IdToken {
    pub fn create(
        encoding_key: &EncodingKey,
        header: &Header,
        common_claims: JwtClaim,
        id_claims: IdClaims,
    ) -> Result<IdToken> {
        let id_tkn = create_token(
            encoding_key,
            &header,
            common_claims.clone(),
            id_claims.clone(),
        )
        .unwrap();
        let id_token = IdToken {
            header: header.clone(),
            claims: common_claims,
            content: id_claims,
            raw: id_tkn,
        };
        Ok(id_token)
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
