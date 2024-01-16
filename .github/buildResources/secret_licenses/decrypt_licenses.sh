#!/bin/sh

set -e

DIR="$( cd "$( dirname "$0" )" && pwd )"


mkdir -p $HOME/.licenses/jira
# --batch to prevent interactive command
# --yes to assume "yes" for questions
gpg --quiet --batch --yes --decrypt --passphrase="$LICENSE_PASSWORD" \
--output $HOME/.licenses/jira/jsm.license $DIR/jsm.license.gpg
gpg --quiet --batch --yes --decrypt --passphrase="$LICENSE_PASSWORD" \
--output $HOME/.licenses/jira/sr.license $DIR/sr.license.gpg
