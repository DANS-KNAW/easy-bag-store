package nl.knaw.dans.easy.bagstore.service

import nl.knaw.dans.lib.error._
import nl.knaw.dans.lib.logging.DebugEnhancedLogging
import org.apache.commons.daemon.{ Daemon, DaemonContext }

import scala.util.control.NonFatal

class ServiceStarter extends Daemon with DebugEnhancedLogging {

  var service: ServiceWiring = _

  def init(context: DaemonContext): Unit = {
    logger.info("Initializing service...")

    service = new ServiceWiring {}

    logger.info("Service initialized.")
  }

  def start(): Unit = {
    logger.info("Starting service...")

    service.server.start()
      .doIfSuccess(_ => logger.info("Service started."))
      .doIfFailure {
        case NonFatal(e) => logger.info(s"Service startup failed: ${ e.getMessage }", e)
      }
      .getOrRecover(throw _)
  }

  def stop(): Unit = {
    logger.info("Stopping service...")
    service.server.stop()
      .doIfSuccess(_ => logger.info("Cleaning up ..."))
      .doIfFailure {
        case NonFatal(e) => logger.error(s"Service stop failed: ${ e.getMessage }", e)
      }
      .getOrRecover(throw _)
  }

  def destroy(): Unit = {
    service.server.destroy()
      .doIfSuccess(_ => logger.info("Service stopped."))
      .doIfFailure {
        case e => logger.error(s"Service destroy failed: ${ e.getMessage }", e)
      }
      .getOrRecover(throw _)
  }
}
