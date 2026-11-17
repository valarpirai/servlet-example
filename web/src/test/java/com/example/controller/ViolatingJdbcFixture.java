package com.example.controller;

import java.sql.Connection;

/** Fixture: a controller that holds a JDBC connection. It proves the rule bites. */
public class ViolatingJdbcFixture {

  @SuppressWarnings("unused")
  private Connection connection;
}
