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

// simplified copy of easy-deposit-api
// hardly a library candidate because filters (personal info) on headers or other details might be required

trait ServletEnhancedLogging extends DebugEnhancedLogging {
  this: ScalatraBase =>

  before() {
    logger.info(s"${ request.getMethod } ${ request.getRequestURL } remote=${ request.getRemoteAddr } params=$params headers=${ request.headers }")
  }
}
object ServletEnhancedLogging extends DebugEnhancedLogging {

  implicit class RichActionResult(actionResult: ActionResult)(implicit request: HttpServletRequest) {
    def logResponse: ActionResult = {
      logger.info(s"${ request.getMethod } returned status=${ actionResult.status } headers=${ actionResult.headers }")
      actionResult
    }
  }
}
