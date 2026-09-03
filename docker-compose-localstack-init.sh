#!/usr/bin/env bash

aws cloudformation deploy \
  --template-file ./localstack-resources.yml \
  --stack-name develop-stack \
  --capabilities CAPABILITY_IAM CAPABILITY_NAMED_IAM \
  --parameter-overrides Environment=develop
 
localstack-resources.yml