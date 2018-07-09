package io.epiphanous.semantic.o2g

import com.typesafe.scalalogging.LazyLogging
import scopt._

import scala.collection.mutable

/**
  * Created by nextdude on 7/16/17.
  */
object JobRunner extends LazyLogging {

  val parser = new OptionParser[Conf](BuildInfo.name) {

    head(BuildInfo.name, BuildInfo.version)

    opt[String]('i', "input").action((input, conf) => conf.copy(inputs = conf.inputs :+ input))
      .valueName("<ontology-file>")
      .required()
      .unbounded()
      .text("An input ontology file to process. You can provide multiple --input options.")

    opt[String]('o', "output").action((out, conf) => conf.copy(out = out))
      .valueName("<gqls-file>")
      .required()
      .maxOccurs(1)
      .text("A file to output the graphql schema to. Defaults to standard out.")

    opt[String]('c', "catalog").action((catalogXml, conf) => conf.copy(catalogXml = catalogXml))
      .valueName("<xml-file>")
      .optional()
      .maxOccurs(1)
      .text("A catalog xml file to resolve import uri's (default: catalog-v001.xml)")

    opt[String]('r', "inverse").action(
      (fieldName, conf) => conf.copy(inverseFieldWhiteList = conf.inverseFieldWhiteList :+ fieldName))
      .valueName("<field-name>")
      .optional()
      .unbounded()
      .text("A full IRI of an rdf field name to generate the inverse for. You can provide multiple --inverse options.")

    opt[String]('p',"default-prefix").action((prefix, conf) => conf.copy(defaultNamespacePrefix = prefix))
      .valueName("<prefix>")
      .optional()
      .maxOccurs(1)
      .text("The default namespace prefix.")

    opt[String]('n',"default-iri").action((iri, conf) => conf.copy(defaultNamespaceIri = iri))
      .valueName("<iri>")
      .optional()
      .maxOccurs(1)
      .text("The default namespace IRI.")

    help("help").text("prints this usage message").abbr("h")
    version("version").text("prints out program version").abbr("v")
  }

  /**
    * Entry point for the program.
    *
    * @param args cli arguments
    */
  def main(args: Array[String]): Unit = {
    parser.parse(args, Conf.fromConfig()) match {
      case Some(conf) =>
        val processor = new OntologyProcessor(conf)
        processor.run()
      case None =>
      /* duh */
    }
  }

}

