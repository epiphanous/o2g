# o2g

o2g converts an OWL ontology file (in ttl format) to a GraphQL schema.

## Current Release

The current release is [version 1.0.6](https://github.com/nextdude/o2g/releases/tag/release-1.0.6)

## Get Started

  - Download
    - the [o2g script](https://github.com/nextdude/o2g/releases/download/release-1.0.6/o2g)
    - the [o2g jar file](https://github.com/nextdude/o2g/releases/download/release-1.0.6/o2g-assembly-1.0.6.jar)
  - Move the jar file somewhere sensible under your home directory
  - Define an environment variable called `O2G_JAR` pointing to the jar location
  - Copy the `o2g` script into your path and make sure its executable

Now you can run `o2g` in a repo where you're developing your graphql schema.

```bash
$ o2g --help
o2g 1.0.6
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
  -l, --local-names 
                           Use local names where possible, leaving off the ugly prefixes
  -h, --help               prints this usage message
  -v, --version            prints out program version

```

