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
import org.scalatra.ScalatraBase
import org.scalatra.auth.strategy.{ BasicAuthStrategy, BasicAuthSupport }
import org.scalatra.auth.{ ScentryConfig, ScentrySupport }

case class User(id: String)

trait BagStoreAuthenticationSupport extends ScentrySupport[User] with BasicAuthSupport[User] {
  self: ScalatraBase =>

  def bagstoreUsername: String
  def bagstorePassword: String

  class BagStoreAuthenticationStrategy(protected override val app: ScalatraBase, realm: String) extends BasicAuthStrategy[User](app, realm) with DebugEnhancedLogging {

    override protected def validate(userName: String, password: String)(implicit request: HttpServletRequest, response: HttpServletResponse): Option[User] = {
      logger.info(s"validate($userName, $password)")
      if (userName == bagstoreUsername && password == bagstorePassword)
        Some(User(userName))
      else
        None
    }

    override protected def getUserId(user: User)(implicit request: HttpServletRequest, response: HttpServletResponse): String = {
      logger.info(s"getUserId($user)")
      user.id
    }
  }

  override val realm = "easy-bag-store"
  private val basicStrategy = "Basic"

  override protected def fromSession: PartialFunction[String, User] = {
    case id: String => User(id)
  }

  override protected def toSession: PartialFunction[User, String] = {
    case usr: User => usr.id
  }

  override type ScentryConfiguration = ScentryConfig
  override protected val scentryConfig: ScentryConfiguration = new ScentryConfig {}

  override protected def configureScentry(): Unit = {
    scentry.unauthenticated {
      scentry.strategies(basicStrategy).unauthenticated()
    }
  }

  override protected def registerAuthStrategies(): Unit = {
    scentry.register(basicStrategy, app => new BagStoreAuthenticationStrategy(app, realm))
  }
}
