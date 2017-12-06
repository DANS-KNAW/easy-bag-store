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
package nl.knaw.dans.easy.bagstore

import org.eclipse.jetty.server.nio.SelectChannelConnector
import org.scalatra.test.EmbeddedJettyContainer
import org.scalatra.test.scalatest.ScalatraSuite

/**
 * Temporary fixture such that the servlets can be tested with ScalatraSuite.
 * This Suite relies on Jetty 9.x, while we still require Jetty 8.x
 * By overriding the two functions below, issues related to these versions are solved.
 */
trait ServletFixture extends EmbeddedJettyContainer {
  this: ScalatraSuite =>

  override def localPort: Option[Int] = server.getConnectors.collectFirst {
    case x: SelectChannelConnector => x.getLocalPort
  }

  override def baseUrl: String = {
    server.getConnectors.collectFirst {
      case conn: SelectChannelConnector =>
        val host = Option(conn.getHost).getOrElse("localhost")
        val port = conn.getLocalPort
        require(port > 0, "The detected local port is < 1, that's not allowed")
        "http://%s:%d".format(host, port)
    }.getOrElse(sys.error("can't calculate base URL: no connector"))
  }
}
