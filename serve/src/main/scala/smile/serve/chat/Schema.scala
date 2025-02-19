/*
 * Copyright (c) 2010-2024 Haifeng Li. All rights reserved.
 *
 * Smile is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Smile is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Smile. If not, see <https://www.gnu.org/licenses/>.
 */
package smile.serve.chat

import java.time.Instant
import slick.basic.DatabaseConfig
import slick.jdbc.JdbcProfile

/** Database schema. */
trait Schema {
  val config: DatabaseConfig[JdbcProfile]
  import config.profile.api.*

  class Threads(tag: Tag) extends Table[Thread](tag, "THREAD") {
    def id = column[Long]("ID", O.PrimaryKey, O.AutoInc)
    def clientIP = column[Option[String]]("CLIENT_IP")
    def userAgent = column[Option[String]]("USER_AGENT")
    def createdAt = column[Instant]("CREATED_AT")

    def * = (id, clientIP, userAgent, createdAt).mapTo[Thread]
    def lastUpdatedIdx = index("THREAD_CREATED_IDX", createdAt)
  }
  val threads = TableQuery[Threads]

  class Messages(tag: Tag) extends Table[ThreadMessage](tag, "MESSAGE") {
    def id = column[Long]("ID", O.PrimaryKey, O.AutoInc)
    def threadId = column[Long]("THREAD_ID")
    def role = column[String]("ROLE")
    def content = column[String]("CONTENT")
    def status = column[Option[String]]("STATUS")
    def createdAt = column[Instant]("CREATED_AT")

    def * = (id, threadId, role, content, status, createdAt).mapTo[ThreadMessage]
    def threadFK = foreignKey("THREAD_FK", threadId, threads)(_.id)
    def createdIdx = index("MESSAGE_CREATED_IDX", createdAt)
  }
  val messages = TableQuery[Messages]
}