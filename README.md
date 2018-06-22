# owl2gqls

A quick and dirty scala program to convert an owl ontology to graphql schema definition language.

## Running the Script

```bash
unix> sbt run
```

This command converts the OWL ontology in `my.ttl` and generates a graphql schema in
IDL format in `my.gqls`. 