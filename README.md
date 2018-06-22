# o2g

o2g converts an OWL ontology file (in ttl format) to a GraphQL schema.

## Get Started

```bash
$ ./build.sh
```

```bash
$ java -jar target/scala-2.12/o2g-assembly-1.0.0.jar --help
o2g 1.0.0
Usage: o2g [options]

  -i, --input <ontology-file>
                           An input ontology file to process. You can provide multiple --input options.
  -o, --output <gqls-file>
                           A file to output the graphql schema to. Defaults to standard out.
  -c, --catalog <xml-file>
                           A catalog xml file to resolve import uri's (default: catalog-v001.xml)
  -r, --inverse <field-name>
                           A full IRI of an rdf field name to generate the inverse for. You can provide multiple --inverse options.
  -p, --default-prefix <prefix>
                           The default namespace prefix.
  -n, --default-iri <iri>  The default namespace IRI.
  -h, --help               prints this usage message
  -v, --version            prints out program version
```

It's sensible to write a script to run o2g and to work outside of the repo when using it to develop graphql schemas.
To do this, copy the jar file somewhere (`O2G_JAR`) and write a script like this:

```bash
#!/usr/bin/env bash

: ${O2G_JAR?"Please set O2G_JAR to point at the o2g jar location"}

java -jar $O2G_JAR $@
```
