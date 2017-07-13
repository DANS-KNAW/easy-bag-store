package nl.knaw.dans.easy.bagstore.server

import nl.knaw.dans.easy.bagstore.{ ItemId, NoSuchBagException }
import nl.knaw.dans.easy.bagstore.component.BagStoresComponent
import nl.knaw.dans.lib.logging.DebugEnhancedLogging
import org.joda.time.DateTime
import org.scalatra._
import nl.knaw.dans.lib.error._

import scala.util.Failure

trait BagsServletComponent extends DebugEnhancedLogging {
  this: BagStoresComponent =>

  val bagsServlet: BagsServlet

  trait BagsServlet extends ScalatraServlet with ServletUtils {

    get("/") {
      contentType = "text/plain"
      val (includeActive, includeInactive) = includedStates(params.get("state"))
      bagStores.enumBags(includeActive, includeInactive)
        .map(bagIds => Ok(bagIds.mkString("\n")))
        .getOrRecover(e => {
          logger.error("Unexpected type of failure", e)
          InternalServerError(s"[${new DateTime()}] Unexpected type of failure. Please consult the logs")
        })
    }

    get("/:uuid") {
      contentType = "text/plain"
      val uuidStr = params("uuid")
      ItemId.fromString(uuidStr)
        .recoverWith {
          case _: IllegalArgumentException => Failure(new IllegalArgumentException(s"invalid UUID string: $uuidStr"))
        }
        .flatMap(_.toBagId)
        .flatMap(bagStores.enumFiles(_))
        .map(bagIds => Ok(bagIds.mkString("\n")))
        .getOrRecover {
          case e: IllegalArgumentException => BadRequest(e.getMessage)
          case e: NoSuchBagException => NotFound(e.getMessage)
          case e =>
            logger.error("Unexpected type of failure", e)
            InternalServerError(s"[${new DateTime()}] Unexpected type of failure. Please consult the logs")
        }
    }
  }
}
