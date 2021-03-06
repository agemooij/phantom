/*
 *
 *  * Copyright 2014 websudos ltd.
 *  *
 *  * Licensed under the Apache License, Version 2.0 (the "License");
 *  * you may not use this file except in compliance with the License.
 *  * You may obtain a copy of the License at
 *  *
 *  *     http://www.apache.org/licenses/LICENSE-2.0
 *  *
 *  * Unless required by applicable law or agreed to in writing, software
 *  * distributed under the License is distributed on an "AS IS" BASIS,
 *  * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  * See the License for the specific language governing permissions and
 *  * limitations under the License.
 *
 */

package com.websudos.phantom.zookeeper

import java.io.IOException
import java.net.Socket
import scala.concurrent.blocking

import com.datastax.driver.core.{Cluster, Session}

private[zookeeper] case object CassandraInitLock

trait CassandraManager {

  def livePort: Int
  def embeddedPort: Int

  def cluster: Cluster
  def initIfNotInited(keySpace: String)

  implicit def session: Session
}

object DefaultCassandraManager extends CassandraManager {

  val livePort = 9042
  val embeddedPort = 9142
  def cassandraHost: String = "localhost"

  private[this] var inited = false
  @volatile private[this] var _session: Session = null


  def cassandraPort: Int = {
    try {
      new Socket(cassandraHost, livePort)
      livePort
    } catch {
      case ex: IOException => embeddedPort
    }
  }

  lazy val cluster: Cluster = Cluster.builder()
    .addContactPoint(cassandraHost)
    .withPort(cassandraPort)
    .withoutJMXReporting()
    .withoutMetrics()
    .build()

  def session = _session

  def initIfNotInited(keySpace: String): Unit = CassandraInitLock.synchronized {
    if (!inited) {
      _session = blocking {
        val s = cluster.connect()
        s.execute(s"CREATE KEYSPACE IF NOT EXISTS $keySpace WITH replication = {'class': 'SimpleStrategy', 'replication_factor' : 1};")
        s.execute(s"USE $keySpace;")
        s
      }
      inited = true
    }
  }
}

trait SimpleCassandraConnector extends CassandraConnector {

  override implicit lazy val session: Session = {
    manager.initIfNotInited(keySpace)
    manager.session
  }

}
