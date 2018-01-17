package com.wix.sms.cm.testkit

import java.util.concurrent.atomic.AtomicReference

import akka.http.scaladsl.model._
import com.wix.e2e.http.RequestHandler
import com.wix.e2e.http.client.extractors.HttpMessageExtractors._
import com.wix.e2e.http.server.WebServerFactory.aMockWebServerWith
import com.wix.sms.cm.model.MessagesParser
import com.wix.sms.cm.{CmHelper, Credentials}
import com.wix.sms.model.Sender

class CmDriver(port: Int) {
  private val delegatingHandler: RequestHandler = { case r: HttpRequest => handler.get().apply(r) }
  private val notFoundHandler: RequestHandler = { case _: HttpRequest => HttpResponse(status = StatusCodes.NotFound) }

  private val handler = new AtomicReference(notFoundHandler)

  private val probe = aMockWebServerWith(delegatingHandler).onPort(port).build
  private val messagesParser = new MessagesParser

  def startProbe() {
    probe.start()
  }

  def stopProbe() {
    probe.stop()
  }

  def resetProbe() {
    handler.set(notFoundHandler)
  }

  def aMessageFor(credentials: Credentials, sender: Sender, destPhone: String, text: String): MessageCtx = {
    new MessageCtx(
      credentials = credentials,
      sender = sender,
      destPhone = destPhone,
      text = text)
  }

  private def prependHandler(handle: RequestHandler) =
    handler.set(handle orElse handler.get())

  class MessageCtx(credentials: Credentials, sender: Sender, destPhone: String, text: String) {
    private val expectedMessages = CmHelper.createMessages(
      credentials = credentials,
      sender = sender,
      destPhone = destPhone,
      text = text
    )

    def succeeds() = {
      returnsText("")
    }

    def isUnauthorized(): Unit = {
      failsWith("Error: ERROR Invalid product token.")
    }

    def failsWith(error: String): Unit = {
      returnsText(error)
    }

    private def returnsText(responseText: String): Unit = {
      prependHandler({
        case HttpRequest(
        HttpMethods.POST,
        Uri.Path("/"),
        _,
        entity,
        _) if isStubbedRequestEntity(entity) =>
          HttpResponse(
            status = StatusCodes.OK,
            entity = HttpEntity(ContentTypes.`text/plain(UTF-8)`, responseText))
      })
    }

    private def isStubbedRequestEntity(entity: HttpEntity): Boolean = {
      val requestXml = entity.extractAsString
      val messages = messagesParser.parse(requestXml)

      messages == expectedMessages
    }
  }
}
