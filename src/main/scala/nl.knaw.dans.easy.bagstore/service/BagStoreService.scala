package nl.knaw.dans.easy.bagstore.service

import nl.knaw.dans.lib.logging.DebugEnhancedLogging

object BagStoreService extends DebugEnhancedLogging {

  def main(args: Array[String]): Unit = {
    logger.info("Starting BagStore Service")

    val service = new ServiceStarter

    Runtime.getRuntime.addShutdownHook(new Thread("service-shutdown") {
      override def run(): Unit = {
        service.stop()
        service.destroy()
      }
    })

    service.init(null)
    service.start()
  }
}
