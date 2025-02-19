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
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import smile.llm.{CompletionPrediction, FinishReason, Message, Role}
import spray.json.*

// domain model
final case class Thread(id: Long,
                        clientIP: Option[String],
                        userAgent: Option[String],
                        createdAt: Instant)
final case class ThreadMessage(id: Long,
                               threadId: Long,
                               role: String,
                               content: String,
                               status: Option[String],
                               createdAt: Instant)

final case class Usage(promptTokens: Int, completionTokens: Int, totalTokens: Int)

object Usage {
  def apply(promptTokens: Int, completionTokens: Int): Usage = {
    new Usage(promptTokens, completionTokens, promptTokens + completionTokens)
  }
}

final case class CompletionRequest(model: String,
                                   messages: Seq[Message],
                                   threadId: Option[Long],
                                   // The maximum number of tokens to generate,
                                   // i.e. maxGenLen parameter of Llama.generate().
                                   max_tokens: Option[Int],
                                   temperature: Option[Double],
                                   top_p: Option[Double],
                                   logprobs: Option[Boolean],
                                   seed: Option[Long],
                                   stream: Option[Boolean])

final case class CompletionChoice(index: Int,
                                  message: Message,
                                  finish_reason: FinishReason,
                                  logprobs: Option[Array[Float]])

final case class CompletionResponse(id: String,
                                    model: String,
                                    usage: Usage,
                                    choices: Seq[CompletionChoice],
                                    `object`: String = "chat.completion",
                                    created: Long = System.currentTimeMillis)

object CompletionResponse {
  def apply(id: Long, completion: CompletionPrediction): CompletionResponse = {
    CompletionResponse(id.toHexString, completion.model,
      Usage(completion.promptTokens.length, completion.completionTokens.length), 
      Seq(CompletionChoice(0, new Message(Role.assistant, completion.content),
                           completion.reason, Option(completion.logprobs))))
  }
}

final case class ListThreadResponse(data: Seq[Thread],
                                    `object`: String = "list")

final case class ListMessageResponse(data: Seq[ThreadMessage],
                                     `object`: String = "list")

// collect json format instances into a support trait:
object JsonSupport extends SprayJsonSupport with DefaultJsonProtocol {
  implicit val instantJsonFormat: JsonFormat[Instant] = new JsonFormat[Instant] {
    override def write(ts: Instant): JsValue = JsString(ts.toString)

    override def read(json: JsValue): Instant = json match {
      case JsString(x) => Instant.parse(x)
      case _ => throw new IllegalArgumentException(s"Can not parse json value [$json] to an Instant object")
    }
  }

  implicit val finishReasonJsonFormat: JsonFormat[FinishReason] = new JsonFormat[FinishReason] {
    override def write(reason: FinishReason): JsValue = JsString(reason.toString)

    override def read(json: JsValue): FinishReason = {
      json match {
        case JsString(text) => FinishReason.valueOf(text)
        case _ => throw DeserializationException(s"Expected a value from enum FinishReason instead of $json")
      }
    }
  }

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
          throw DeserializationException("Message expected")
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
          throw DeserializationException("Usage expected")
      }
    }
  }

  implicit val threadFormat: RootJsonFormat[Thread] = jsonFormat4(Thread.apply)
  implicit val threadMessageFormat: RootJsonFormat[ThreadMessage] = jsonFormat6(ThreadMessage.apply)
  implicit val listThreadResponseFormat: RootJsonFormat[ListThreadResponse] = jsonFormat2(ListThreadResponse.apply)
  implicit val listMessageResponseFormat: RootJsonFormat[ListMessageResponse] = jsonFormat2(ListMessageResponse.apply)
  implicit val choiceFormat: RootJsonFormat[CompletionChoice] = jsonFormat4(CompletionChoice.apply)
  implicit val chatRequestFormat: RootJsonFormat[CompletionRequest] = jsonFormat9(CompletionRequest.apply)
  implicit val chatResponseFormat: RootJsonFormat[CompletionResponse] = jsonFormat6(CompletionResponse.apply)
}
