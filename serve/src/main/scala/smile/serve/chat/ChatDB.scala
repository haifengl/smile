/*
 * Copyright (c) 2010-2024 Haifeng Li. All rights reserved.
 *
 * Smile is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Smile is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Smile.  If not, see <https://www.gnu.org/licenses/>.
 */
package smile.serve.chat

import java.time.Instant
import scala.concurrent.{ExecutionContext, Future}
import smile.llm.Message
import spray.json.JsObject

/** Data access object. */
trait ChatDB extends Schema {
  import config.profile.api._
  val db = config.db

  // insert and return with generated id
  private val insertThread = threads returning threads.map(_.id) into ((thread, id) => thread.copy(id = id))
  private val insertMessage = messages returning messages.map(_.id) into ((message, id) => message.copy(id = id))

  // setup database
  def setupChatDB()(implicit ec: ExecutionContext): Future[Unit] = {
    val actions = for {
      _ <- threads.schema.createIfNotExists
      _ <- messages.schema.createIfNotExists
    } yield ()

    db.run(actions.transactionally)
  }

  def getThreads(limit: Int = 100, cursor: Option[Long]): Future[Seq[Thread]] = {
    val q = cursor.map(before => threads.filter(_.id < before))
      .getOrElse(threads)
      .sortBy(_.id.desc)
      .take(limit)
      .result

    db.run(q)
  }

  def getThread(id: Long): Future[Option[Thread]] = {
    db.run(threads.filter(_.id === id).result.headOption)
  }

  def insertThread(metadata: JsObject): Future[Thread] = {
    val thread = Thread(0, Instant.now)
    db.run(insertThread += thread)
  }

  /** Only mails without status can be deleted. */
  def deleteThread(id: Long): Future[Int] = {
    db.run(threads.filter(_.id === id).delete)
  }

  /** To get unread messages, set status=Some("unread"). */
  def getMessages(threadId: Long, limit: Int = 100, cursor: Option[Long]): Future[Seq[ThreadMessage]] = {
    val rows = messages.filter(_.threadId === threadId)
    val q = cursor.map(before => rows.filter(_.id < before))
      .getOrElse(rows)
      .sortBy(_.id)
      .take(limit)
      .result

    db.run(q)
  }

  /** Only sender/receive can get message. */
  def getMessage(threadId: Long, id: Long): Future[Option[ThreadMessage]] = {
    db.run(messages.filter(row => row.id === id && row.threadId === threadId).result.headOption)
  }

  private def insertMessages(threadId: Long, messages: Seq[Message], status: Option[String] = None)
                            (implicit ec: ExecutionContext) = {
    val now = Instant.now
    this.messages ++= messages.map { message =>
      ThreadMessage(0, threadId, message.role.toString, message.content, status, now)
    }
  }

  def insertMessages(request: CompletionRequest)
                    (implicit ec: ExecutionContext) : Future[Long] = {
    val now = Instant.now
    val insert = if (request.threadId.isDefined && request.threadId.get > 0) {
      for {
        _ <- insertMessages(request.threadId.get, request.messages)
      } yield request.threadId.get
    } else {
      for {
        thread <- insertThread += Thread(0, now)
        _ <- insertMessages(thread.id, request.messages)
      } yield thread.id
    }
    db.run(insert.transactionally)
  }

  def insertMessages(threadId: Long, response: CompletionResponse)
                    (implicit ec: ExecutionContext): Future[Option[Int]] = {
    val now = Instant.now
    db.run(messages ++= response.choices.map { choice =>
      ThreadMessage(0, threadId, choice.message.role.toString, choice.message.content, Some(choice.finish_reason.toString), now)
    })
  }

  def insertMessage(threadId: Long, message: Message): Future[ThreadMessage] = {
    db.run(insertMessage += ThreadMessage(0, threadId, message.role.toString, message.content, None, Instant.now))
  }

  def updateMessage(id: Long, status: String): Future[Int] = {
    val q = messages.filter(_.id === id).map(_.status).update(Some(status))
    db.run(q)
  }
}
