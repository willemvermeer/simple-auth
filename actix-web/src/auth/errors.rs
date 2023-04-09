/// A type alias for `Result<T, auth::Error>`.
pub type Result<T> = std::result::Result<T, Error>;

#[derive(Clone, Debug, Eq, PartialEq)]
pub struct Error {
    pub message: String,
}
impl Error {
    pub fn from<T: std::fmt::Display>(err: T) -> Self {
        Error {
            message: err.to_string(),
        }
    }
}
impl From<serde_json::Error> for Error {
    fn from(value: serde_json::Error) -> Self {
        Self{ message: value.to_string()}
    }
}
impl From<std::string::FromUtf8Error> for Error {
    fn from(value: std::string::FromUtf8Error) -> Self {
        Self{ message: value.to_string()}
    }
}

impl From<base64::EncodeSliceError> for Error {
    fn from(value: base64::EncodeSliceError) -> Self {
        Self{ message: value.to_string()}
    }
}
impl From<jsonwebtoken::errors::Error> for Error {
    fn from(value: jsonwebtoken::errors::Error) -> Self {
        Self{ message: value.to_string()}
    }
}
