# CrossRef Metadata Participation

A dashboard to show which publishers deposit which metadata.

## To Run

Copy `config.edn.example` to `config.edn`. Make relevant changes. For example, if this lives at http://example.com/metadata-participation, set :url-prefix to "/metadata-participation". If it lives in the root, leave blank.

Then, from the root directory:

  lein run

or 

  lein daemon start metadata-participation

## License

Copyright © 2014 CrossRef
