package nl.knaw.dans.easy.bagstore.server

import javax.servlet.ServletContext

import org.eclipse.jetty.server.Server
import org.eclipse.jetty.servlet.ServletContextHandler
import org.scalatra.LifeCycle
import org.scalatra.servlet.ScalatraListener

import scala.util.Try

trait BagStoreServerComponent {
  this: DefaultServletComponent with BagsServletComponent with StoresServletComponent =>

  val server: BagStoreServer

  class BagStoreServer(serverPort: Int) {

    private val server = new Server(serverPort) {
      this.setHandler(new ServletContextHandler(ServletContextHandler.NO_SESSIONS) {
        this.addEventListener(new ScalatraListener {
          override def probeForCycleClass(classLoader: ClassLoader): (String, LifeCycle) = {
            ("bagindex-lifecycle", new LifeCycle {
              override def init(context: ServletContext): Unit = {
                context.mount(defaultServlet, "/")
                context.mount(bagsServlet, "/bags")
                context.mount(storesServlet, "/stores")
              }
            })
          }
        })
      })
    }

    logger.info(s"HTTP port is $serverPort")

    def start(): Try[Unit] = Try {
      logger.info("Starting BagIndex server...")
      server.start()
    }

    def stop(): Try[Unit] = Try {
      logger.info("Stopping BagIndex server...")
      server.stop()
    }

    def destroy(): Try[Unit] = Try {
      server.destroy()
      logger.info("BagIndex server stopped.")
    }
  }
}
