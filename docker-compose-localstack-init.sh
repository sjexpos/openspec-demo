#!/usr/bin/env bash

aws cloudformation deploy \
  --template-file ./localstack-resources.yml \
  --stack-name development-stack \
  --capabilities CAPABILITY_IAM CAPABILITY_NAMED_IAM \
  --parameter-overrides Environment=development

aws cloudformation deploy \
  --template-file ./localstack-resources.yml \
  --stack-name testing-stack \
  --capabilities CAPABILITY_IAM CAPABILITY_NAMED_IAM \
  --parameter-overrides Environment=testing
