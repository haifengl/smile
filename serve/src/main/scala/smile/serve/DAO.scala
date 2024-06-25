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
package smile.serve

import scala.concurrent.{ExecutionContext, Future}
import slick.basic.DatabaseConfig
import slick.jdbc.JdbcProfile
import smile.serve.chat.ChatDB

class DAO(val config: DatabaseConfig[JdbcProfile]) extends ChatDB {
  // setup database
  def setup()(implicit ec: ExecutionContext): Future[Unit] = {
    setupChatDB()
  }
}
