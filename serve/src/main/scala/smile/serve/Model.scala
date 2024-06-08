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

import java.util.UUID
import akka.http.scaladsl.server.Directives
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import spray.json._
import smile.llm.{CompletionPrediction, Message, Role}

// domain model
final case class Usage(promptTokens: Int, completionTokens: Int, totalTokens: Int)

object Usage {
  def apply(promptTokens: Int, completionTokens: Int): Usage = {
    new Usage(promptTokens, completionTokens, promptTokens + completionTokens)
  }
}

final case class CompletionRequest(model: String,
                                   messages: Array[Message],
                                   max_tokens: Int = 2048,
                                   temperature: Double = 0.6,
                                   top_p: Double = 0.9,
                                   seed: Option[Int] = None,
                                   logprobs: Boolean = false)

final case class CompletionResponse(id: String,
                                    model: String,
                                    usage: Usage,
                                    choices: Array[Message],
                                    `object`: String = "chat.completion",
                                    created: Long = System.currentTimeMillis)

object CompletionResponse {
  def apply(completion: CompletionPrediction): CompletionResponse = {
    new CompletionResponse(UUID.randomUUID.toString, "llama3",
      Usage(completion.promptTokens.length, completion.completionTokens.length), 
      Array(new Message(Role.assistant, completion.content)))
  }
}

// collect json format instances into a support trait:
trait JsonSupport extends SprayJsonSupport with DefaultJsonProtocol {
  implicit object MessageJsonFormat extends RootJsonFormat[Message] {
    def write(message: Message) = JsObject(
      "role" -> JsString(message.role.toString),
      "content" -> JsString(message.content)
    )

    def read(value: JsValue) = {
      value.asJsObject.getFields(
        "role",
        "content"
      ) match {
        case Seq(JsString(role), JsString(content)) =>
          new Message(Role.valueOf(role), content)
        case _ =>
          throw new DeserializationException("Message expected")
      }
    }
  }

  implicit object UsageJsonFormat extends RootJsonFormat[Usage] {
    def write(usage: Usage) = JsObject(
      "prompt_tokens" -> JsNumber(usage.promptTokens),
      "completion_tokens" -> JsNumber(usage.completionTokens),
      "total_tokens" -> JsNumber(usage.totalTokens)
    )

    def read(value: JsValue) = {
      value.asJsObject.getFields(
        "prompt_tokens",
        "completion_tokens",
        "total_tokens"
      ) match {
        case Seq(JsNumber(promptTokens), JsNumber(completionTOkens), JsNumber(totalTokens)) =>
          new Usage(promptTokens.intValue, completionTOkens.intValue, totalTokens.intValue)
        case _ =>
          throw new DeserializationException("Usage expected")
      }
    }
  }

  implicit val requestFormat: RootJsonFormat[CompletionRequest] = jsonFormat7(CompletionRequest.apply)
  implicit val responseFormat: RootJsonFormat[CompletionResponse] = jsonFormat6(CompletionResponse.apply)
}