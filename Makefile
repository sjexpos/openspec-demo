RAW_BRANCH ?= $(shell git rev-parse --abbrev-ref HEAD 2>/dev/null)
BRANCH_SLUG ?= $(if $(filter main,$(RAW_BRANCH)),main,$(shell printf '%s' '$(RAW_BRANCH)' | sed 's|^[^/]*/||' | LC_ALL=C tr -cd '[:alnum:]' | tr 'A-Z' 'a-z'))
export BRANCH_SLUG

LOCALSTACK_HOST=localhost
POSTGRES_HOST=localhost
SERVER_PORT = 8080
FEATURE = $(BRANCH_SLUG)
DBNAME = demo_$(FEATURE)
TESTDBNAME = tests_$(FEATURE)

help:
	@printf "\n"
	@printf "Usage: make [option]\n"
	@printf "\n"
	@printf "Options:\n"
	@awk 'BEGIN {FS = ":.*?#HELP# "} /^[a-zAZ_-]+:.*?#HELP# / {printf "    \033[35m%-35s\033[0m %s\n", $$1, $$2}' $(MAKEFILE_LIST)
	@printf "\n"
	@awk 'BEGIN {FS = ":.*?#SQS# "} /^[a-zAZ_-]+:.*?#SQS# / {printf "    \033[35m%-35s\033[0m %s\n", $$1, $$2}' $(MAKEFILE_LIST)
	@printf "\n"
	@awk 'BEGIN {FS = ":.*?#SSS# "} /^[a-zAZ_-]+:.*?#SSS# / {printf "    \033[35m%-35s\033[0m %s\n", $$1, $$2}' $(MAKEFILE_LIST)
	@printf "\n"

init: #HELP# Initial goal to setup postgres and localstack resources - it is an idempotent command
	@chmod 755 ./compose/wait-for-it.sh
	@./compose/wait-for-it.sh $(POSTGRES_HOST):5432 -t 5 -- echo "Postgres is running"
	@PGPASSWORD=postgres psql -v ON_ERROR_STOP=0 --host $(POSTGRES_HOST) --port 5432 --username postgres --dbname postgres -f ./compose/postgres-users.sql
	@mkdir -p target
	@sed -e "s|{{DBNAME}}|$(DBNAME)|g" ./compose/postgres-database.sql > ./target/postgres-database-develop.sql
	@PGPASSWORD=postgres psql -v ON_ERROR_STOP=1 --host $(POSTGRES_HOST) --port 5432 --username postgres --dbname postgres -f ./target/postgres-database-develop.sql
	@mvn flyway:migrate -Dflyway.url=jdbc:postgresql://$(POSTGRES_HOST):5432/$(DBNAME) -Dflyway.user=develop -Dflyway.password=develop
	@mvn flyway:info -Dflyway.url=jdbc:postgresql://$(POSTGRES_HOST):5432/$(DBNAME) -Dflyway.user=develop -Dflyway.password=develop
	@sed -e "s|{{DBNAME}}|$(TESTDBNAME)|g" ./compose/postgres-database.sql > ./target/postgres-database-test.sql
	@PGPASSWORD=postgres psql -v ON_ERROR_STOP=1 --host $(POSTGRES_HOST) --port 5432 --username postgres --dbname postgres -f ./target/postgres-database-test.sql
	@mvn flyway:migrate -Dflyway.url=jdbc:postgresql://$(POSTGRES_HOST):5432/$(TESTDBNAME) -Dflyway.user=test -Dflyway.password=test
	@mvn flyway:info -Dflyway.url=jdbc:postgresql://$(POSTGRES_HOST):5432/$(TESTDBNAME) -Dflyway.user=test -Dflyway.password=test
	@./compose/wait-for-it.sh $(LOCALSTACK_HOST):4566 -t 5 -- echo "Localstack is running"
	@AWS_ENDPOINT_URL=http://$(LOCALSTACK_HOST):4566 AWS_ACCESS_KEY_ID=test AWS_SECRET_ACCESS_KEY=test AWS_REGION=us-east-1 \
		aws cloudformation deploy \
		--template-file ./compose/localstack-resources.yml \
		--stack-name dev-$(FEATURE)-stack \
		--capabilities CAPABILITY_IAM CAPABILITY_NAMED_IAM \
		--parameter-overrides Environment=dev-$(FEATURE)
	@AWS_ENDPOINT_URL=http://$(LOCALSTACK_HOST):4566 AWS_ACCESS_KEY_ID=test AWS_SECRET_ACCESS_KEY=test AWS_REGION=us-east-1 \
		aws cloudformation deploy \
		--template-file ./compose/localstack-resources.yml \
		--stack-name testing-$(FEATURE)-stack \
		--capabilities CAPABILITY_IAM CAPABILITY_NAMED_IAM \
		--parameter-overrides Environment=testing-$(FEATURE)
	@echo "server.port:$(SERVER_PORT)" > env.properties && \
		echo "db.host:$(POSTGRES_HOST)" >> env.properties && \
		echo "db.dbname:$(DBNAME)" >> env.properties && \
		echo "aws.endpoint:http://$(LOCALSTACK_HOST):4566" >> env.properties && \
		echo "s3.path.access:true" >> env.properties && \
		echo "s3.bucket.assets:dev-$(FEATURE)-assets" >> env.properties && \
		echo "sqs.events.assets:dev-$(FEATURE)-assets-events-queue" >> env.properties && \
		echo "testing.db.dbname:$(TESTDBNAME)" >> env.properties && \
		echo "testing.s3.bucket.assets:testing-$(FEATURE)-assets" >> env.properties && \
		echo "testing.sqs.events.assets:testing-$(FEATURE)-assets-events-queue" >> env.properties

run: #HELP# run application using spring-boot maven plugin
	@AWS_ACCESS_KEY_ID=test AWS_SECRET_ACCESS_KEY=test AWS_S3_PATH_STYLE_ACCESS=true mvn spring-boot:run

print-sqs: #SQS# Show all queues
	@AWS_ACCESS_KEY_ID=test AWS_SECRET_ACCESS_KEY=test AWS_DEFAULT_REGION=us-east-1 aws --endpoint-url=http://$(LOCALSTACK_HOST):4566 sqs list-queues --output table --no-cli-pager

print-sqs-msg: #SQS# Show messages in queue (up to 10)
ifeq ($(QUEUE_URL),)
	@$(eval QUEUE_URL := $(shell read -p "QUEUE_URL: " input; echo $$input))
endif
	@AWS_ACCESS_KEY_ID=test AWS_SECRET_ACCESS_KEY=test AWS_DEFAULT_REGION=us-east-1 aws --endpoint-url=http://$(LOCALSTACK_HOST):4566 sqs receive-message --max-number-of-messages 10 --visibility-timeout 1 --query "Messages[*].MessageId" --queue-url "$(QUEUE_URL)" --output table --no-cli-pager

print-sqs-msg-body: #SQS# Show SQS message body
ifeq ($(QUEUE_URL),)
	@$(eval QUEUE_URL := $(shell read -p "QUEUE_URL: " input; echo $$input))
endif
ifeq ($(MSG_ID),)
	@$(eval MSG_ID := $(shell read -p "MESSAGE_ID: " input; echo $$input))
endif
	@AWS_ACCESS_KEY_ID=test AWS_SECRET_ACCESS_KEY=test AWS_DEFAULT_REGION=us-east-1 aws --endpoint-url=http://$(LOCALSTACK_HOST):4566 sqs receive-message --max-number-of-messages 10 --visibility-timeout 1 --query "Messages[?MessageId=='$(MSG_ID)'].Body" --queue-url "$(QUEUE_URL)" --output table --no-cli-pager

print-sss: #SSS# Show all S3 buckets
	@AWS_ACCESS_KEY_ID=test AWS_SECRET_ACCESS_KEY=test AWS_DEFAULT_REGION=us-east-1 aws --endpoint-url=http://$(LOCALSTACK_HOST):4566 s3api list-buckets --query 'Buckets[].Name' --output table

copy-sss: #SSS# Copy a local file to S3
ifeq ($(SOURCE_FILE),)
	@$(eval SOURCE_FILE := $(shell read -p "LOCAL FILE: " input; echo $$input))
endif
ifeq ($(BUCKET),)
	@$(eval BUCKET := $(shell read -p "BUCKET: " input; echo $$input))
endif
ifeq ($(KEY_OBJ),)
	@$(eval KEY_OBJ := $(shell read -p "KEY: " input; echo $$input))
endif
	@AWS_ACCESS_KEY_ID=test AWS_SECRET_ACCESS_KEY=test AWS_DEFAULT_REGION=us-east-1 aws --endpoint-url=http://$(LOCALSTACK_HOST):4566 s3 cp $(SOURCE_FILE) s3://$(BUCKET)/$(KEY_OBJ)
