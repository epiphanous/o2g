package io.epiphanous.semantic.o2g

import java.io.{FileInputStream, PrintWriter}
import java.text.SimpleDateFormat
import java.util
import java.util.TimeZone

import com.overzealous.remark.{Options, Remark}
import com.typesafe.scalalogging.LazyLogging
import org.eclipse.rdf4j.model._
import org.eclipse.rdf4j.model.impl.{SimpleNamespace, SimpleValueFactory}
import org.eclipse.rdf4j.model.util.RDFCollections
import org.eclipse.rdf4j.model.vocabulary._
import org.eclipse.rdf4j.rio.{RDFFormat, Rio}
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.select.Selector

import scala.collection.JavaConverters._
import scala.collection.mutable
import scala.xml.XML

/**
  * Created by nextdude on 7/21/17.
  */
class OntologyProcessor(conf: Conf) extends LazyLogging {

  /** an rdf value factory */
  val vf = SimpleValueFactory.getInstance()

  /** an artificial namespace for unions (as a string) */
  val UNION_NAMESPACE = "gql://union#"

  /** an artificial namespace for unions (as a SimpleNamespace) */
  val UNION_NS = new SimpleNamespace("U", UNION_NAMESPACE)

  /** an artificial namespace for X unions (as a string) */
  val XUNION_NAMESPACE = "gql://xunion#"
  val XUNION_NS = new SimpleNamespace("X", XUNION_NAMESPACE)

  /** an artificial namespace for interfaces (as a string) */
  val INTERFACE_NAMESPACE = "gql://interface#"

  /** an artificial namespace for interfaces (as a SimpleNamespace) */
  val INTERFACE_NS = new SimpleNamespace("I", INTERFACE_NAMESPACE)

  /** schema.org namespace (as a string) */
  val SDO_NAMESPACE = "http://schema.org/"

  /** schema.org namespace (as a SimpleNamespace) */
  val SDO_NS = new SimpleNamespace("schema", SDO_NAMESPACE)

  /** good relations namespace (as a string) */
  val GR_NAMESPACE = "http://purl.org/goodrelations/v1#"

  /** good relations namespace (as a SimpleNamespace) */
  val GR_NS = new SimpleNamespace("gr", GR_NAMESPACE)

  val DEFAULT_NAMESPACE = conf.defaultNamespaceIri
  val DEFAULT_NS = new SimpleNamespace(conf.defaultNamespacePrefix, DEFAULT_NAMESPACE)

  /** shacl namespace (as a string) */
  val SH_NAMESPACE = "http://www.w3.org/ns/shacl#"

  /** shacl namesapce (as a SimpleNamespace) */
  val SH_NS = new SimpleNamespace("sh", SH_NAMESPACE)

  /** sh:minCount IRI */
  val SH_MIN_COUNT = vf.createIRI(SH_NAMESPACE, "minCount")

  /** sh:maxCount IRI */
  val SH_MAX_COUNT = vf.createIRI(SH_NAMESPACE, "maxCount")

  /** default cardinality of properties (minCount, maxCount) */
  val DEFAULT_CARDINALITY = (0, 1)

  /** cardinality marking objects that should be connections */
  val CONNECTION_CARDINALITY = 999

  /** simple types that need to be expanded into objects */
  val SCALAR_TYPES = List(XMLSchema.ANYURI,
                          XMLSchema.ID,
                          XMLSchema.BOOLEAN,
                          XMLSchema.DATE,
                          XMLSchema.DATETIME,
                          XMLSchema.DECIMAL,
                          XMLSchema.DOUBLE,
                          XMLSchema.DURATION,
                          XMLSchema.FLOAT,
                          XMLSchema.INTEGER,
                          XMLSchema.LONG,
                          XMLSchema.STRING,
                          XMLSchema.TIME,
                          XMLSchema.GMONTHDAY,
                          XMLSchema.GDAY,
                          XMLSchema.GMONTH,
                          XMLSchema.GYEAR,
                          XMLSchema.GYEARMONTH)

  val SCALAR_TYPE_MAP = Map(XMLSchema.BOOLEAN -> "Boolean",
                            XMLSchema.ID -> "ID",
                            XMLSchema.STRING -> "String",
                            XMLSchema.INT -> "Int",
                            XMLSchema.INTEGER -> "Int",
                            XMLSchema.LONG -> "Long",
                            XMLSchema.FLOAT -> "Float",
                            XMLSchema.DECIMAL -> "Float",
                            XMLSchema.DOUBLE -> "Float",
                            XMLSchema.ANYURI -> "URL",
                            XMLSchema.DATE -> "Date",
                            XMLSchema.DATETIME -> "DateTime",
                            XMLSchema.TIME -> "Time",
                            XMLSchema.DURATION -> "Duration",
                            XMLSchema.GDAY -> "Day",
                            XMLSchema.GMONTH -> "Month",
                            XMLSchema.GYEAR -> "Year",
                            XMLSchema.GYEARMONTH -> "YearMonth",
                            XMLSchema.GMONTHDAY -> "MonthDay")

  val BUILT_IN_SCALARS = Map(XMLSchema.BOOLEAN -> "Boolean",
                             XMLSchema.ID -> "ID",
                             XMLSchema.STRING -> "String",
                             XMLSchema.INT -> "Int",
                             XMLSchema.INTEGER -> "Int",
                             XMLSchema.FLOAT -> "Float",
                             XMLSchema.DECIMAL -> "Float",
                             XMLSchema.DOUBLE -> "Float")

  /** number of lines required in a block before we emit an end of block comment */
  val END_COMMENT_THRESHOLD = 10

  /** our generated time stamp */
  val nowAsISO = {
    val tz = TimeZone.getTimeZone("UTC")
    val df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'") // Quoted "Z" to indicate UTC, no timezone offset
    df.setTimeZone(tz)
    df.format(System.currentTimeMillis())
  }

  /** load all input ontologies */
  val ontology = loadOntology(conf.inputs.head, conf.catalogXml)
  conf.inputs.tail.foreach(x => ontology.addAll(loadOntology(x, conf.catalogXml)))

  /** our output print writer */
  val writer = Option(conf.out) match {
    case Some(name) =>
      logger.info(s"writing to $name")
      new PrintWriter(name)
    case None => new PrintWriter(System.out)
  }

  // remove dc statements
  List(DC.CONTRIBUTOR, DC.CREATOR, DC.DATE).foreach(dcIRI => {
    ontology.remove(null, dcIRI, null)
    ontology.remove(dcIRI, null, null)
  })

  // remove ontology version props
  ontology
    .filter(null, RDF.TYPE, OWL.ONTOLOGY)
    .subjects()
    .asScala
    .map(s => vf.createIRI(s.toString))
    .foreach(p => ontology.remove(p, null, null))

  // add top level id property in default namespace
  val ID_PROPERTY = vf.createIRI(DEFAULT_NAMESPACE, conf.defaultIdField)
  Map(RDF.TYPE -> OWL.DATATYPEPROPERTY,
      RDFS.SUBPROPERTYOF -> vf.createIRI(SDO_NAMESPACE, "identifier"),
      RDFS.RANGE -> XMLSchema.ID,
      RDFS.DOMAIN -> OWL.THING,
      SH_MAX_COUNT -> vf.createLiteral(1),
      SH_MIN_COUNT -> vf.createLiteral(1),
      RDFS.LABEL -> vf.createLiteral("Unique identifier", "en"),
      RDFS.COMMENT -> vf.createLiteral("A unique object identifier, represented as a UT8 string.", "en"))
    .foreach(po => {
      ontology.add(ID_PROPERTY, po._1, po._2)
    })

  /**
    * Grab the list of prefixes
    */
  val INVERSE_PREFIX = "inverse_"
  val prefixes0: Map[String, String] =
    (ontology.getNamespaces.asScala + DEFAULT_NS + GR_NS + UNION_NS +
      INTERFACE_NS + XUNION_NS)
      .map(ns => ns.getName -> (ns.getPrefix + "_"))
      .toMap
  val prefixes = prefixes0 ++
    prefixes0.map(x => (INVERSE_PREFIX + x._1, INVERSE_PREFIX + x._2))

  /**
    * Grab the fields (that is, any subjects that are either object or datatype properties)
    */
  val unfilteredFields =
    ontology.filter(null, RDF.TYPE, OWL.OBJECTPROPERTY).subjects.asScala.map(_.asInstanceOf[IRI]).toSet ++
      ontology.filter(null, RDF.TYPE, OWL.DATATYPEPROPERTY).subjects.asScala.map(_.asInstanceOf[IRI]).toSet ++
      ontology.filter(null, RDF.TYPE, RDF.PROPERTY).subjects.asScala.map(_.asInstanceOf[IRI]).toSet

  val fields = unfilteredFields.filter(field => {
    ontology.filter(field, RDFS.DOMAIN, null).objects().asScala.toList.nonEmpty
  })

  /** a map of all values for each enumeration type */
  val enumValuesMap = subclasses(sdo("Enumeration"))
    .map(t => {
      val ev = ontology.filter(null, RDF.TYPE, t).subjects().asScala.map(v => v.asInstanceOf[IRI]).toSet
      val evFromComment = Selector
        .select("li", Jsoup.parse(comment(t), t.getNamespace).body())
        .asScala
        .toSet
        .map((e: Element) => e.text())
        .map(vf.createIRI)
      t -> (ev ++ evFromComment)
    })
    .toMap[IRI, Set[IRI]]
    .filterNot(_._2.isEmpty)

  /** all enumeration types */
  val enumerationTypes = enumValuesMap.keySet

  /** a set of all enum values */
  val enumValues = enumValuesMap.foldLeft(mutable.Set.empty[IRI])((z, s) => z ++ s._2)

  /**
    * Grab the interfaces (that is, any objects that are the subclasses of some subject)
    */
  val interfaces = ontology
    .filter(null, RDFS.SUBCLASSOF, null)
    .objects()
    .asScala
    .map(_.asInstanceOf[IRI])
    .toSet -- enumerationTypes

  /** a map to hold the fields for each type */
  val typeFields0: Map[IRI, Set[IRI]] = fields
    .foldLeft(Map.empty[IRI, Set[IRI]])((z: Map[IRI, Set[IRI]], field) => {
      z ++ ontology
        .filter(field, RDFS.DOMAIN, null)
        .objects()
        .asScala
        .toList
        .flatMap {
          case typeIRI: IRI => List(typeIRI)
          case bnode: BNode =>
            val head = ontology.filter(bnode, OWL.UNIONOF, null).objects().asScala.head.asInstanceOf[Resource]
            RDFCollections.asValues(ontology, head, new util.ArrayList[Value]()).asScala.map(_.asInstanceOf[IRI]).toList
        }
        .map(typeIRI => {
          typeIRI -> (z.getOrElse(typeIRI, Set.empty[IRI]) + field)
        })
        .toMap
    })

  /** maps to hold the ranges for each field and unions */
  val (fieldTypes0: Map[IRI, IRI], unions0: Map[IRI, List[IRI]], xunions0: Map[IRI, List[IRI]]) = fields
    .foldLeft((Map.empty[IRI, IRI], Map.empty[IRI, List[IRI]], Map.empty[IRI, List[IRI]]))((z, field) => {
      val (ft, u, x) = z
      val ranges = ontology.filter(field, RDFS.RANGE, null).objects().asScala.toList
      val rangesOfSuperProperties = ontology
        .filter(field, RDFS.SUBPROPERTYOF, null)
        .objects()
        .asScala
        .toList
        .flatMap(x => {
          ontology.filter(x.asInstanceOf[IRI], RDFS.RANGE, null).objects().asScala.toList
        })
      val eitherRanges = ranges ::: rangesOfSuperProperties

      (eitherRanges.headOption match {
        case Some(value) => value
        case None        => XMLSchema.STRING
      }) match {
        case typeIRI: IRI => (ft + (field -> typeIRI), u, x)
        case bnode: BNode =>
          val head = ontology.filter(bnode, OWL.UNIONOF, null).objects().asScala.head.asInstanceOf[Resource]
          val ranges = RDFCollections
            .asValues(ontology, head, new util.ArrayList[Value]())
            .asScala
            .map(_.asInstanceOf[IRI])
            .toList
            .sortBy(_.getLocalName)
          if (ranges.size == 1) {
            val r = ranges.head
            val t = if (interfaces.contains(r)) vf.createIRI(INTERFACE_NAMESPACE, genIRI(r)) else r
            (ft + (field -> t), u, x)
          } else {
            val unionKey = ranges.map(genIRI).mkString("_OR_")
            if (ranges.exists(t => interfaces.contains(t) || enumerationTypes.contains(t) || isScalarType(t))) {
              val xUnionType = vf.createIRI(XUNION_NAMESPACE, unionKey)
              (ft + (field -> xUnionType), u, x + (xUnionType -> ranges))
            } else {
              val unionType = vf.createIRI(UNION_NAMESPACE, unionKey)
              (ft + (field -> unionType), u + (unionType -> ranges), x)
            }
          }
      }
    })

  val fieldCardinality: Map[IRI, (Int, Int)] = fields
    .map(f => {
      val minCount = ontology.filter(f, SH_MIN_COUNT, null).objects().asScala.toList.headOption match {
        case Some(c) => c.stringValue().toInt
        case _       => DEFAULT_CARDINALITY._1
      }
      val maxCount = ontology.filter(f, SH_MAX_COUNT, null).objects().asScala.toList.headOption match {
        case Some(c) => c.stringValue().toInt
        case _       => DEFAULT_CARDINALITY._2
      }
      f -> (minCount, maxCount)
    })
    .toMap

  val connectionTypes = fieldCardinality.filter { case (_, (_, x)) => x == CONNECTION_CARDINALITY }.keys.toSet

  val typeIRIToUnionIRIs: Map[IRI, Set[IRI]] =
    unions0.values.flatten.map(x => (x, unions0.toList.filter(_._2.contains(x)).map(_._1).toSet)).toMap

  val unionIRIToFieldIRIs: Map[IRI, Set[IRI]] =
    fieldTypes0.values.map(x => (x, fieldTypes0.toList.filter(_._2 == x).map(_._1).toSet)).toMap

  val fieldTypes0FilteredToWhiteList: Map[IRI, IRI] =
    fieldTypes0.filterKeys(x => conf.inverseFieldWhiteList.contains(x.stringValue))

  val typeFields0FilteredToWhiteList: Map[IRI, Set[IRI]] =
    typeFields0.mapValues(x => x.filter(y => conf.inverseFieldWhiteList.contains(y.stringValue)))

  val unionIRIToFieldIRIsFilteredToWhiteList: Map[IRI, Set[IRI]] =
    unionIRIToFieldIRIs.mapValues(x => x.filter(y => conf.inverseFieldWhiteList.contains(y.stringValue)))

  val fieldIRIToInverseFieldIRI: Map[IRI, IRI] = fieldTypes0FilteredToWhiteList.keys
    .map(x => {
      (x, vf.createIRI(INVERSE_PREFIX + x.getNamespace, x.getLocalName))
    })
    .toMap
  val inverseFieldIRIToFieldIRI: Map[IRI, IRI] = fieldIRIToInverseFieldIRI.map(_.swap)
  val typeIRIToInverseFieldIRIs: Map[IRI, Set[IRI]] = typeIRIToUnionIRIs
    .mapValues(
      x =>
        x.flatMap(unionIRIToFieldIRIsFilteredToWhiteList)
          .flatMap(fieldIRIToInverseFieldIRI.get)
    )
  val inverseFieldIRIToTypeIRIs: Map[IRI, Set[IRI]] =
    typeFields0FilteredToWhiteList.values.flatten
      .map(
        x =>
          (vf.createIRI(INVERSE_PREFIX + x.getNamespace, x.getLocalName),
           typeFields0FilteredToWhiteList.toList.filter(_._2.contains(x)).map(_._1).toSet)
      )
      .toMap

  val inverseFieldIRIToTypeIRIs2: Map[IRI, (IRI, List[IRI])] = inverseFieldIRIToTypeIRIs.mapValues(x => {
    val ranges = x.toList.sortBy(_.getLocalName)
    if (ranges.size == 1) {
      (ranges.head, ranges)
    } else {
      (vf.createIRI(UNION_NAMESPACE, ranges.map(genIRI).mkString("_OR_")), ranges)
    }
  })

  val inverseFieldTypes0 = inverseFieldIRIToTypeIRIs2.mapValues(_._1)
  val inverseUnions0 = inverseFieldIRIToTypeIRIs2.values.toMap
  val typeFields: Map[IRI, Set[IRI]] =
    typeFields0.map(e => (e._1, e._2 ++ typeIRIToInverseFieldIRIs.getOrElse(e._1, Set.empty)))
  val fieldTypes: Map[IRI, IRI] = fieldTypes0 ++ inverseFieldTypes0
  val unions: Map[IRI, List[IRI]] = (unions0 ++ inverseUnions0).filter(_._2.size > 1)

  /**
    * Grab our list of types (any subjects that have an rdf type,
    * excluding blank nodes, fields, enumeration types,
    * enumeration values and owl:Thing)
    */
  val types =
    ontology
      .filter(null, RDF.TYPE, null)
      .subjects()
      .asScala
      .filterNot(_.isInstanceOf[BNode])
      .map(_.asInstanceOf[IRI])
      .toSet -- fields -- enumerationTypes -- enumValues - OWL.THING

  /**
    * Get direct parents of each type
    */
  val parents = types
    .map(t => {
      val p = ontology
        .filter(t, RDFS.SUBCLASSOF, null)
        .objects()
        .asScala
        .map(_.asInstanceOf[IRI])
        .toSet
        .intersect(interfaces)
      t -> p
    })
    .toMap

  /** The column to wrap comments at */
  val WRAP_LENGTH = 78
  val valueFactory = SimpleValueFactory.getInstance()
  val remarkOptions = Options.markdown()
  val remark = new Remark(remarkOptions)
  val INDENT = "  "

  /**
    * Track the number of lines we emit
    */
  var emittedLines = 0

  /**
    * Track the starting line of the current stanza we're about to emit
    */
  var startLines = 0

  /**
    * load our ontology (including nested imports)
    *
    * @param ontologyUrl - the url for the ontology
    * @return An RDF Model
    */
  def loadOntology(ontologyUrl: String, catalogXml: String): Model = {
    val in = new FileInputStream(ontologyUrl)
    val ontology = Rio.parse(in, ontologyUrl, RDFFormat.TURTLE)
    in.close()
    val imports = ontology.filter(null, OWL.IMPORTS, null).objects().asScala.map(_.asInstanceOf[IRI]).toSet
    val uriMapping = getCatalog(catalogXml)
    imports.foreach(ontIRI => {
      uriMapping
        .get(ontIRI.toString)
        .foreach(fileName => {
          logger.info(s"Loading $fileName...")
          ontology.addAll(loadOntology(fileName, catalogXml))
        })
    })
    ontology
  }

  /**
    * Returns the uri mappings contained in the catalog file.
    *
    * @param catalogXml - path to the catalog file
    * @return uri mappings
    */
  def getCatalog(catalogXml: String): Map[String, String] = {
    val catalog = XML.loadFile(catalogXml)
    (catalog \ "uri").map { a =>
      (a \ "@name").text -> (a \ "@uri").text
    }.toMap
  }

  /**
    * Return a schema.org IRI for localName
    *
    * @param localName the localName of the subject IRI
    * @return IRI
    */
  def sdo(localName: String): IRI =
    vf.createIRI(SDO_NAMESPACE, localName)

  def run(): Unit = {
    emitHeader()
    emitDirectives()
    emitScalars()
    emitInterfaces()
    emitUnions()
    emitXUnions()
    emitTypes()
    emitConnections()
    emitEnums()
    close()
  }

  def blankLine(numLines: Int = 1): Unit =
    1 to numLines foreach { _ =>
      emitLine("")
    }

  def emitHeader(): Unit = {
    emitLine(s"""# GraphQL Schema for ${conf.inputs.mkString(", ")}
         |#
         |# Generated by: ${BuildInfo.name} ${BuildInfo.version}
         |#           on: $nowAsISO
         |""".stripMargin)
  }

  def emitDirectives(): Unit = {
    emitLine(
      s"""# Directs the executor to treat the marked field as a primary key.
         |# When specified at object, interface or schema scope, you must specify
         |# one or more fields that should be used as primary keys within that scope.
         |# Multiple fields in a type may be marked as a primary key and will
         |# be treated in the order they are listed.
         |directive @id(
         |  fields: [String!]
         |) on FIELD_DEFINITION | OBJECT | INTERFACE | SCHEMA
         |
         |# Directs the executor to resolve this field or object with
         |# the specified resolver.
         |directive @resolve(
         |  # specify the resolver name (aka, sql, sparql, etc)
         |  with: String!
         |) on FIELD_DEFINITION | OBJECT | INTERFACE | SCHEMA | FIELD | QUERY | FRAGMENT_DEFINITION | FRAGMENT_SPREAD | INLINE_FRAGMENT""".stripMargin
    )
  }

  def emitScalars(): Unit = {
    SCALAR_TYPES
      .filterNot(BUILT_IN_SCALARS.contains)
      .foreach(t => {
        blankLine()
        val typeIRI = genIRI(t)
        emitLine(s"# $t")
        emitLine(s"scalar $typeIRI")
      })
  }

  def emitConnections(): Unit = {
    emitLine("""
         |###########################################################
         |# Connections (for paging through large collections)
         |###########################################################
         |
         |# Indicates if the current connection has more pages available
         |type PageInfo {
         |  startCursor: String
         |  endCursor: String
         |  hasNextPage: Boolean!
         |  hasPreviousPage: Boolean!
         |}
         |""".stripMargin.trim)

    val labels =
      sortByLocalName(
        connectionTypes
          .map(ft => fieldTypes(ft))
          .map(ft => if (interfaces.contains(ft)) vf.createIRI(INTERFACE_NAMESPACE, genIRI(ft)) else ft)
      ).map(genIRI)
    labels.foreach(label => {
      blankLine()
      emitLine(s"# Connection of $label nodes")
      emitLine(s"type ${label}Connection {")
      emitLine("  totalCount: Int!")
      emitLine("  pageInfo: PageInfo!")
      emitLine(s"  edges: [${label}Edge]!")
      emitLine("}")
      blankLine()
      emitLine(s"# $label edge node")
      emitLine(s"type ${label}Edge {")
      emitLine("  cursor: String!")
      emitLine(s"  node: $label!")
      emitLine("}")
    })
  }

  def emitInterfaces(): Unit = {
    sortByLocalName(interfaces).foreach(t => {
      val fields = allFields(t)
      if (fields.nonEmpty) {
        startBlock("interface", t, Some(INTERFACE_NAMESPACE))
        fieldWriter(t, allFields(t))
        endBlock()
      }
    })
  }

  def emitUnions(): Unit = {
    sortByLocalName(unions.keySet).foreach(u => {
      val labels = unions(u).flatMap(t => subclasses(t) + t).map(genIRI)
      val comment = s"# ${unions(u)
        .map(t => if (interfaces.contains(t)) vf.createIRI(INTERFACE_NAMESPACE, genIRI(t)) else t)
        .map(genIRI)
        .mkString(" or ")}"
      blankLine()
      emitLine(comment)
      emitLine(s"union ${genIRI(u)} = ${labels.mkString(" | ")}")
    })
  }

  def emitXUnions(): Unit = {
    sortByLocalName(xunions0.keySet).foreach(x => {
      blankLine()
      emitLine(s"type ${genIRI(x)} {")
      xunions0(x).foreach(t => {
        val xt = if (interfaces.contains(t)) vf.createIRI(INTERFACE_NAMESPACE, genIRI(t)) else t
        emitLine(s"  ${genIRI(t)}_Value: ${genIRI(xt)}")
      })
      emitLine("}")
    })
  }

  def emitTypes(): Unit = {
    sortByLocalName(types).foreach(t => {
      val fields = allFields(t)
      val ifaces = superclasses(t)
      if (fields.nonEmpty && prefixes.contains(t.getNamespace)) {
        val implements =
          ifaces
            .filter(hasFields)
            .map(iri => vf.createIRI(INTERFACE_NAMESPACE, genIRI(iri)))
            .map(genIRI)
        val impl = if (implements.nonEmpty) Some(implements.mkString("implements ", " & ", " ")) else None
        startBlock("type", t, None, impl)
        fieldWriter(t, fields)
        endBlock()
      }
    })
  }

  def emitEnums(): Unit = {
    sortByLocalName(enumerationTypes).foreach(e => {
      val name = genIRI(e)
      startBlock("enum", e)
      enumValuesMap.get(e).foreach(enumValuesWriter)
      endBlock(Some(s"enum $name"))
    })
  }

  def isScalarType(iri: IRI): Boolean =
    SCALAR_TYPES.contains(iri) || enumerationTypes.contains(iri)

  def isOptionalProp(iri: IRI): Boolean =
    !isRequiredProp(iri)

  def isRequiredProp(iri: IRI): Boolean =
    fieldCardinality.getOrElse(iri, DEFAULT_CARDINALITY)._1 > 0

  def sortByLocalName(set: Set[IRI]): List[IRI] = set.toList.sortBy(_.getLocalName)

  def startBlock(
    blockType: String,
    blockThing: IRI,
    wrapperNamespace: Option[String] = None,
    blockSuffix: Option[String] = None
  ): Unit = {
    startLines = emittedLines
    blankLine()
    emitLine(genComment(mdComment(blockThing)))
    val label = genIRI(blockThing)
    val blockName = if (wrapperNamespace.nonEmpty) genIRI(vf.createIRI(wrapperNamespace.get, label)) else label
    emitLine(s"$blockType $blockName ${blockSuffix.getOrElse("")}{")
  }

  def endBlock(blockSuffix: Option[String] = None): Unit = {
    val wantsComment = (emittedLines - startLines) > END_COMMENT_THRESHOLD
    val endComment =
      if (wantsComment && blockSuffix.nonEmpty)
        s" # end ${blockSuffix.get}"
      else ""
    emitLine(s"}$endComment")
  }

  def enumValuesWriter(values: Set[IRI]): Unit =
    emitLine(s"  ${sortByLocalName(values).map(genIRI).mkString("\n  ")}")

  def getGqlType(
    field: IRI,
    fieldType: IRI,
    isReqOpt: Option[Boolean] = None,
    isListOpt: Option[Boolean] = None
  ): GQLType = {
    if (isConnectionProp(field)) CONNECTION
    else {
      val (isList, isReq) = (isListProp(field), isRequiredProp(field))
      if (isList) {
        if (isReq) LIST_REQ else LIST_OPT
      } else if (isReq) REQ_SCALAR
      else OPT_SCALAR
    }
  }

  def isListProp(iri: IRI): Boolean = {
    val maxCount = fieldCardinality.getOrElse(iri, DEFAULT_CARDINALITY)._2
    maxCount > 1 && maxCount < CONNECTION_CARDINALITY
  }

  def isConnectionProp(iri: IRI): Boolean = connectionTypes.contains(iri)

  def isInverse(field: IRI) = field.getNamespace.startsWith(INVERSE_PREFIX)

  def fieldWriter(aType: IRI, fields: Set[IRI]) = {
    sortByLocalName(fields).foreach(f => {
      blankLine()

      val (gqlType, name, args, typeName, comment, directives) =
        if (isInverse(f)) {
          val inverseF = inverseFieldIRIToFieldIRI(f)
          val inverseT = fieldTypes(inverseF)
//          val typeF = fieldTypes(f)
//          val typeN = genIRI(typeF)
          val cmt = mdComment(inverseF) //s"${mdComment(inverseF)} from [$typeN]($typeF)"
          val gt = getGqlType(inverseF, inverseT)
          (gt, genIRI(inverseF), genArgs(gt), genIRI(inverseT), genComment(cmt, INDENT), "")
        } else {
          val typeF = fieldTypes(f)
          val gt = getGqlType(f, typeF)
          val typeIRI =
            if (interfaces.contains(typeF)) vf.createIRI(INTERFACE_NAMESPACE, genIRI(typeF)) else typeF
          (gt, genIRI(f), genArgs(gt), genIRI(typeIRI), genComment(mdComment(f), INDENT), "") //s"${mdComment(f)} From [${genIRI(aType)}]($aType)", "  "))
        }

      emitLine(comment)
      emitLine(gqlType.__(name, typeName, args, directives))

    })
    fields
  }

  def genArgs(gQLType: GQLType) =
    if (gQLType == CONNECTION)
      "(filter:String, sortBy:String, first:Int, after:String, last:Int, before:String)"
    else ""

  def genComment(s: String, indent: String = "") =
    /*WordUtils.wrap(s, WRAP_LENGTH)*/ s.replaceAll("(?m)^", s"$indent# ")

  def comment(someType: IRI): String = {
    ontology
      .filter(someType, RDFS.COMMENT, null)
      .objects()
      .asScala
      .map(_.stringValue())
      .headOption
      .getOrElse(label(someType))
      .replaceAll("\\s+", " ")
      .trim
  }

  remarkOptions.inlineLinks = true

  def mdComment(someType: IRI): String = {
    val s = comment(someType)
    remark.convert(s)
  }

  def label(someType: IRI): String =
    ontology
      .filter(someType, RDFS.LABEL, null)
      .objects()
      .asScala
      .map(_.stringValue())
      .headOption
      .getOrElse(someType.toString)

  def superclasses(someType: IRI): Set[IRI] = {
    val p = parents.getOrElse(someType, Set())
    p ++ p.flatMap(superclasses)
  }

  def hasFields(someType: IRI): Boolean =
    typeFields.getOrElse(someType, Set.empty[IRI]).nonEmpty

  def allFields(someType: IRI) =
    (superclasses(someType) + someType).flatMap(t => typeFields.getOrElse(t, Set.empty[IRI]))

  def subclasses(someType: IRI): Set[IRI] = {
    val subs = ontology.filter(null, RDFS.SUBCLASSOF, someType).subjects().asScala.map(v => v.asInstanceOf[IRI]).toSet
    subs ++ subs.flatMap(subclasses)
  }

  def genIRI(iri: IRI) = {
    val hasPrefix = prefixes.contains(iri.getNamespace)
    val prefix = if (hasPrefix) prefixes(iri.getNamespace) else iri.getNamespace
    val localString = s"${iri.getLocalName.replaceAll("-", "_")}"
    val prefixedString = s"$prefix$localString"
    prefix match {
      case p if p.equalsIgnoreCase(s"${XMLSchema.PREFIX}_") => SCALAR_TYPE_MAP.getOrElse(iri, prefixedString)
      case p if List("O_", "I_").contains(p)                => prefixedString
      case _ if !hasPrefix                                  => s"<$iri>"
      case _                                                => prefixedString //wouldn't it be nice to just use localString
    }
  }

  def emitLine(s: String): Unit = {
    emittedLines += s.split("\r\n|\n|\r").length
    writer.println(s)
  }

  def close(): Unit = {
    logger.info(s"wrote $emittedLines lines; complete.")
    writer.close()
  }

  sealed trait GQLType {
    def __(n: String, t: String, a: String = "", d: String = ""): String

    def _f(n: String, t: String, a: String = "", d: String = ""): String = s"$INDENT$n$a: $t $d"
  }

  case class DefaultField(
    fieldType: IRI,
    comment: String,
    isRequired: Boolean = false,
    isList: Boolean = false,
    directives: List[String] = List.empty)

  case object REQ_SCALAR extends GQLType {
    override def __(n: String, t: String, a: String = "", d: String = ""): String = _f(n, s"$t!", a, d)
  }

  case object OPT_SCALAR extends GQLType {
    override def __(n: String, t: String, a: String = "", d: String = "") = _f(n, t, a, d)
  }

  case object LIST_REQ extends GQLType {
    override def __(n: String, t: String, a: String = "", d: String = "") = _f(n, s"[$t!]!", a, d)
  }

  case object LIST_OPT extends GQLType {
    override def __(n: String, t: String, a: String = "", d: String = "") = _f(n, s"[$t]!", a, d)
  }

  case object CONNECTION extends GQLType {
    override def __(n: String, t: String, a: String = "", d: String = "") = _f(n, s"${t}Connection!", a, d)
  }

}
