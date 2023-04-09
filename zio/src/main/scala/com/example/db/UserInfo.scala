package com.example.db

case class UserInfo(
  id: java.util.UUID,
  name: String,
  email: String,
  hashpassword: String,
  salt: String
)
