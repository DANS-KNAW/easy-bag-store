package nl.knaw.dans.easy.bagstore

import scala.util.{ Success, Try }

trait BagStoreLifeCycle {

  def startup(): Try[Unit]
  def shutdown(): Try[Unit]
  def destroy(): Try[Unit]
}

// default implementation of BagStoreLifeCycle
trait IdleLifeCycle extends BagStoreLifeCycle {
  def startup(): Try[Unit] = Success(())

  def shutdown(): Try[Unit] = Success(())

  def destroy(): Try[Unit] = Success(())
}
