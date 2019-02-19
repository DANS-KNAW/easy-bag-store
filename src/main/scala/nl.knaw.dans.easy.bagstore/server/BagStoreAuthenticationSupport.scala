/**
 * Copyright (C) 2016 DANS - Data Archiving and Networked Services (info@dans.knaw.nl)
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
package nl.knaw.dans.easy.bagstore.server

import javax.servlet.http.{ HttpServletRequest, HttpServletResponse }
import nl.knaw.dans.lib.logging.DebugEnhancedLogging
import nl.knaw.dans.lib.logging.servlet._
import nl.knaw.dans.lib.logging.servlet.masked.MaskedAuthorizationHeader
import org.scalatra.auth.strategy.BasicAuthStrategy.BasicAuthRequest
import org.scalatra.{ BadRequest, ScalatraBase, Unauthorized }


trait BagStoreAuthenticationSupport extends DebugEnhancedLogging
  with ServletLogger
  with MaskedAuthorizationHeader
  with PlainLogFormatter {
  self: ScalatraBase =>

  def bagstoreUsername: String

  def bagstorePassword: String

  private val realm = "easy-bag-store"

  def basicAuth()(implicit request: HttpServletRequest, response: HttpServletResponse): Unit = {
    val baReq = new BasicAuthRequest(request)
    if (!baReq.providesAuth) unauthenticated
    else if (!baReq.isBasicAuth) badRequest
    else if (!validate(baReq.username, baReq.password)) {
      logger.info("invalid user name password combination")
      unauthenticated
    }
  }

  private def badRequest = {
    logger.info(s"${ request.getMethod } did not have basic authentication")
    halt(BadRequest("Bad Request").logResponse)
  }

  private def unauthenticated = {
    val headers = Map("WWW-Authenticate" -> s"""Basic realm="$realm"""")
    halt(Unauthorized("Unauthenticated", headers).logResponse)
  }

  protected def validate(userName: String, password: String): Boolean = {
    userName == bagstoreUsername && password == bagstorePassword
  }
}
