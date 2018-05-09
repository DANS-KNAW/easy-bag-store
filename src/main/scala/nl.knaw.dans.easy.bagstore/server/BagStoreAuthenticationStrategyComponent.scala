package nl.knaw.dans.easy.bagstore.server

import javax.servlet.http.{ HttpServletRequest, HttpServletResponse }
import nl.knaw.dans.lib.logging.DebugEnhancedLogging
import org.scalatra.ScalatraBase
import org.scalatra.auth.strategy.{ BasicAuthStrategy, BasicAuthSupport }
import org.scalatra.auth.{ ScentryConfig, ScentrySupport }

case class User(id: String)

trait BagStoreAuthenticationStrategyComponent {

  def bagstoreUsername: String
  def bagstorePassword: String

  class BagStoreAuthenticationStrategy(protected override val app: ScalatraBase, realm: String) extends BasicAuthStrategy[User](app, realm) with DebugEnhancedLogging {

    override protected def validate(userName: String, password: String)(implicit request: HttpServletRequest, response: HttpServletResponse): Option[User] = {
      if (userName == bagstoreUsername && password == bagstorePassword)
        Some(User(userName))
      else
        None
    }

    override protected def getUserId(user: User)(implicit request: HttpServletRequest, response: HttpServletResponse): String = {
      user.id
    }
  }

  trait BagStoreAuthenticationSupport extends ScentrySupport[User] with BasicAuthSupport[User] with DebugEnhancedLogging {
    self: ScalatraBase =>

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
}
