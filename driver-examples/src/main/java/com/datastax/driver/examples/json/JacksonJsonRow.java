/*
 * Copyright DataStax, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.datastax.driver.examples.json;

import static com.datastax.oss.driver.api.querybuilder.QueryBuilder.bindMarker;
import static com.datastax.oss.driver.api.querybuilder.QueryBuilder.insertInto;
import static com.datastax.oss.driver.api.querybuilder.QueryBuilder.literal;
import static com.datastax.oss.driver.api.querybuilder.QueryBuilder.selectFrom;

import com.datastax.driver.examples.json.codecs.JacksonJsonCodec;
import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.CqlSessionBuilder;
import com.datastax.oss.driver.api.core.cql.PreparedStatement;
import com.datastax.oss.driver.api.core.cql.ResultSet;
import com.datastax.oss.driver.api.core.cql.Row;
import com.datastax.oss.driver.api.core.cql.Statement;
import com.datastax.oss.driver.api.core.type.codec.TypeCodec;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Illustrates how to map an entire table row to a Java object using the <a
 * href="http://wiki.fasterxml.com/JacksonHome">Jackson</a> library, and leveraging the {@code
 * SELECT JSON} and {@code INSERT JSON} syntaxes introduced in Cassandra 2.2.
 *
 * <p>This example makes usage of a custom {@link TypeCodec codec}, {@link JacksonJsonCodec}. If you
 * plan to follow this example, make sure to include the following Maven dependencies in your
 * project:
 *
 * <p><dependency> <groupId>com.fasterxml.jackson.core</groupId>
 * <artifactId>jackson-databind</artifactId> <version>${jackson.version}</version> </dependency> }
 * </pre>
 *
 * This example also uses the {@link com.datastax.oss.driver.api.querybuilder.QueryBuilder
 * QueryBuilder}; for examples using the "core" API, see {@link PlainTextJson} (they are easily
 * translatable to the queries in this class).
 *
 * <p>Preconditions: - a Cassandra 2.2+ cluster is running and accessible through the contacts
 * points identified by basic.contact-points (see application.conf);
 *
 * <p>Side effects: - creates a new keyspace "examples" in the cluster. If a keyspace with this name
 * already exists, it will be reused; - creates a table "examples.json_jackson_row". If it already
 * exists, it will be reused; - inserts data in the table.
 *
 * @see <a href="http://www.datastax.com/dev/blog/whats-new-in-cassandra-2-2-json-support">What’s
 *     New in Cassandra 2.2: JSON Support</a>
 */
public class JacksonJsonRow {
  // A codec to convert JSON payloads into User instances;
  // this codec is declared in the driver-extras module
  private static final TypeCodec<User> USER_CODEC = new JacksonJsonCodec<>(User.class);

  public static void main(String[] args) {
    try (CqlSession session = new CqlSessionBuilder().addTypeCodecs(USER_CODEC).build()) {
      createSchema(session);
      insertJsonRow(session);
      selectJsonRow(session);
    }
  }

  private static void createSchema(CqlSession session) {
    session.execute(
        "CREATE KEYSPACE IF NOT EXISTS examples "
            + "WITH replication = {'class': 'SimpleStrategy', 'replication_factor': 1}");
    session.execute(
        "CREATE TABLE IF NOT EXISTS examples.json_jackson_row("
            + "id int PRIMARY KEY, name text, age int)");
  }

  // Mapping a User instance to a table row using INSERT JSON
  private static void insertJsonRow(CqlSession session) {
    // Build and execute a simple statement
    Statement stmt =
        insertInto("examples", "json_jackson_row")
            .json("")
            //            .json(new User(1, "alice", 30))
            .build(); // todo implement
    session.execute(stmt);

    // The JSON object can be a bound value if the statement is prepared
    // (we use a local variable here for the sake of example, but in a real application you would
    // cache and reuse
    // the prepared statement)
    PreparedStatement pst =
        session.prepare(
            insertInto("examples", "json_jackson_row").json(bindMarker("user")).build());
    session.execute(pst.bind().set("user", new User(2, "bob", 35), User.class));
  }

  // Retrieving User instances from table rows using SELECT JSON
  private static void selectJsonRow(CqlSession session) {

    // Reading the whole row as a JSON object
    Statement stmt =
        selectFrom("examples", "json_jackson_row")
            .json()
            .all()
            .whereColumn("id")
            .in(literal(1), literal(2))
            .build();

    ResultSet rows = session.execute(stmt);

    for (Row row : rows) {
      // SELECT JSON returns only one column for each row, of type VARCHAR,
      // containing the row as a JSON payload
      User user = row.get(0, User.class);
      System.out.printf("Retrieved user: %s%n", user);
    }
  }

  @SuppressWarnings("unused")
  public static class User {

    private final int id;

    private final String name;

    private final int age;

    @JsonCreator
    public User(
        @JsonProperty("id") int id,
        @JsonProperty("name") String name,
        @JsonProperty("age") int age) {
      this.id = id;
      this.name = name;
      this.age = age;
    }

    public int getId() {
      return id;
    }

    public String getName() {
      return name;
    }

    public int getAge() {
      return age;
    }

    @Override
    public String toString() {
      return String.format("%s (id %d, age %d)", name, id, age);
    }
  }
}
