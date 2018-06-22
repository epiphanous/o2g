# o2g

o2g converts an OWL ontology file (in ttl format) to a GraphQL schema.

## Get Started

  - Download and unzip the latest release
  - Copy `o2g-assembly-X.X.X.jar` release binary somewhere
  - Copy the `o2g` script into your path
  - Define an environment variable called `O2G_JAR` pointing to the jar location

Now you can run `o2g` in a repo where you're developing your graphql schema.

```bash
$ o2g --help
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

