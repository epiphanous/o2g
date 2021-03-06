package io.epiphanous.semantic.o2g

import com.typesafe.config.ConfigFactory

import scala.collection.JavaConverters._

/**
  * Our CLI arguments
  *
  * @param inputs     - URL pointing to the OWL ontology turtle definition
  * @param out        - a file into which we write the graphql schema definition
  * @param catalogXml - a file containing uri mappings for imported rdf files
  */
case class Conf(
  inputs: Seq[String] = List.empty,
  out: String = "stdout",
  catalogXml: String,
  inverseFieldWhiteList: List[String],
  defaultNamespaceIri: String,
  defaultNamespacePrefix: String,
  defaultIdField: String = "_id",
  useLocalNames: Boolean = false)

object Conf {
  val config = ConfigFactory.load()

  def fromConfig() = {
    Conf(catalogXml = config.getString("catalog"),
         inverseFieldWhiteList = config.getStringList("inverse").asScala.toList,
         defaultNamespaceIri = config.getString("default.namespace.iri"),
         defaultNamespacePrefix = config.getString("default.namespace.prefix"),
         defaultIdField = config.getString("default.id.field"))
  }
}
