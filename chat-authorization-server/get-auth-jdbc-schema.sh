#!/bin/sh

mkdir ./sql-scripts

DBTYPE=$1; shift

if [ "$DBTYPE" == "postgres" ]; then
  apply_sed=true
else
  apply_sed=false
fi

urls=(
  "https://raw.githubusercontent.com/spring-projects/spring-authorization-server/main/oauth2-authorization-server/src/main/resources/org/springframework/security/oauth2/server/authorization/client/oauth2-registered-client-schema.sql"
  "https://raw.githubusercontent.com/spring-projects/spring-authorization-server/main/oauth2-authorization-server/src/main/resources/org/springframework/security/oauth2/server/authorization/oauth2-authorization-consent-schema.sql"
  "https://raw.githubusercontent.com/spring-projects/spring-authorization-server/main/oauth2-authorization-server/src/main/resources/org/springframework/security/oauth2/server/authorization/oauth2-authorization-schema.sql"
)

cd sql-scripts
for url in "${urls[@]}"; do
  file_name=$(basename $url)
  curl -O $url -o "./$file_name"

  if $apply_sed ; then
    sed -i "" 's/blob/text/g' "./$file_name"
  fi
done