package nl.knaw.dans.easy.bagstore.server

trait ServletUtils {

  type IncludeActive = Boolean
  type IncludeInactive = Boolean

  def includedStates(state: Option[String]): (IncludeActive, IncludeInactive) = {
    state match {
      case Some("all") => (true, true)
      case Some("inactive") => (false, true)
      case _ => (true, false)
    }
  }
}
