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

import javax.servlet.http.HttpServletRequest
import nl.knaw.dans.lib.logging.DebugEnhancedLogging
import org.scalatra.{ ActionResult, ScalatraBase }

import scala.util.{ Failure, Success, Try }

// TODO candidate for dans-scala-lib (another package than authentication?)
trait ServletEnhancedLogging extends DebugEnhancedLogging {
  this: ScalatraBase =>

  before() {
    logger.info(s"${ request.getMethod } ${ request.getRequestURL } remote=${ request.getRemoteAddr } params=$params headers=${ request.headers }")
  }
}
object ServletEnhancedLogging extends DebugEnhancedLogging {

  implicit class RichActionResult(actionResult: ActionResult)(implicit request: HttpServletRequest) {
    def logResponse: ActionResult = logResult(actionResult)
  }

  implicit class RichTriedActionResult(tried: Try[ActionResult])(implicit request: HttpServletRequest) {
    // TODO to preserve actionResult into and beyond after filters, copy it into "implicit response: HttpServletResponse"
    // See the last extensive readme version (documentation moved into an incomplete book and guides)
    // https://github.com/scalatra/scalatra/blob/6a614d17c38d19826467adcabf1dc746e3192dfc/README.markdown
    // sections #filters #action
    def getOrRecoverResponse(recover: Throwable => ActionResult): ActionResult = {
      // the signature is more specific than in nl.knaw.dans.lib.error and comes with the trait, not with just an import
      logResult(tried match {
        case Success(actionResult) => actionResult
        case Failure(throwable) => recover(throwable)
      })
    }
  }

  private def logResult(actionResult: ActionResult)(implicit request: HttpServletRequest) = {
    logger.info(s"${ request.getMethod } returned status=${ actionResult.status } headers=${ actionResult.headers }")
    actionResult
  }
}
