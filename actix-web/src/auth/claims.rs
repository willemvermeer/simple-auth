use serde::{Deserialize, Serialize};
use serde_json::Value;
use std::time::{Duration, SystemTime, UNIX_EPOCH};

use crate::auth::errors::*;

#[derive(Debug, Serialize, Deserialize, Clone)]
pub struct AccessClaims {
    pub session_id: String,
}

#[derive(Debug, Serialize, Deserialize, Clone)]
pub struct IdClaims {
    pub id: String,
    pub name: String,
    pub email: String,
    pub at_hash: Option<String>,
}
impl IdClaims {
    pub fn with_at_hash(self, at_hash: String) -> Self {
        IdClaims {
            id: self.id,
            name: self.name,
            email: self.email,
            at_hash: Some(at_hash),
        }
    }
}

#[derive(Debug, Serialize, Deserialize, Clone)]
pub struct JwtClaim {
    #[serde(skip_serializing_if = "Option::is_none")]
    pub iss: Option<String>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub sub: Option<String>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub aud: Option<String>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub exp: Option<u64>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub nbf: Option<u64>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub iat: Option<u64>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub jti: Option<String>,
}
impl JwtClaim {
    pub fn empty() -> JwtClaim {
        JwtClaim {
            iss: None,
            sub: None,
            aud: None,
            exp: None,
            nbf: None,
            iat: None,
            jti: None,
        }
    }
    pub fn with_issuer(self, issuer: String) -> Self {
        JwtClaim {
            iss: Some(issuer),
            sub: self.sub,
            aud: self.aud,
            exp: self.exp,
            nbf: self.nbf,
            iat: self.iat,
            jti: self.jti,
        }
    }
    pub fn with_audience(self, audience: String) -> Self {
        JwtClaim {
            iss: self.iss,
            sub: self.sub,
            aud: Some(audience),
            exp: self.exp,
            nbf: self.nbf,
            iat: self.iat,
            jti: self.jti,
        }
    }
    pub fn expires_in(self, seconds: u64) -> Self {
        JwtClaim {
            iss: self.iss,
            sub: self.sub,
            aud: self.aud,
            exp: Some(duration_since_epoch().as_secs() + seconds),
            nbf: self.nbf,
            iat: self.iat,
            jti: self.jti,
        }
    }
    pub fn issued_now(self) -> Self {
        JwtClaim {
            iss: self.iss,
            sub: self.sub,
            aud: self.aud,
            exp: self.exp,
            nbf: self.nbf,
            iat: Some(duration_since_epoch().as_secs()),
            jti: self.jti,
        }
    }
    pub fn with_content<T: Serialize>(self, content: T) -> JwtClaimWithContent<T> {
        JwtClaimWithContent {
            content: content,
            claim: self,
        }
    }
}

pub struct JwtClaimWithContent<T: Serialize> {
    content: T,
    claim: JwtClaim,
}
impl<T: Serialize> JwtClaimWithContent<T> {
    pub fn as_json_value(&self) -> Result<Value> {
        let json_string = self.as_json()?;
        let json_value = serde_json::from_str::<Value>(&json_string)?;
        Ok(json_value)
    }
    pub fn as_json(&self) -> Result<String> {
        let json_claim = serde_json::to_string(&self.claim)?;
        let json_content = serde_json::to_string(&self.content)?;
        Ok(merge_json(&json_claim, &json_content))
    }
}

fn duration_since_epoch() -> Duration {
    SystemTime::now()
        .duration_since(UNIX_EPOCH)
        .expect("This is the time before time.")
}

fn merge_json(first: &str, second: &str) -> String {
    let ft = first.trim();
    let st = second.trim();
    if ft.is_empty() {
        st.to_string()
    } else if st.is_empty() {
        ft.to_string()
    } else {
        let mut f = ft[..ft.len() - 1].to_string();
        let s = &st[1..];
        f.push(',');
        f.push_str(s);
        f
    }
}
