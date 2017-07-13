package nl.knaw.dans.easy.bagstore.server

import java.net.URI

import nl.knaw.dans.easy.bagstore.component.BagStoresComponent
import org.scalatra.{ Ok, ScalatraServlet }

trait DefaultServletComponent {
  this: BagStoresComponent =>

  val defaultServlet: DefaultServlet

  trait DefaultServlet extends ScalatraServlet {

    val externalBaseUri: URI

    get("/") {
      contentType = "text/plain"
      Ok(s"EASY Bag Store is running.\nAvailable stores at <${externalBaseUri.resolve("stores")}>")
    }

    get("/stores") {
      contentType = "text/plain"
      bagStores.stores
        .keys
        .map(store => s"<${externalBaseUri.resolve(s"stores/$store")}>")
        .mkString("\n")
    }
  }
}
