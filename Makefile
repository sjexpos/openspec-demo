
help:
	@printf "\n"
	@printf "Usage: make [option]\n"
	@printf "\n"
	@printf "Options:\n"
	@awk 'BEGIN {FS = ":.*?#SQS# "} /^[a-zAZ_-]+:.*?#SQS# / {printf "    \033[35m%-35s\033[0m %s\n", $$1, $$2}' $(MAKEFILE_LIST)
	@printf "\n"
	@awk 'BEGIN {FS = ":.*?#SSS# "} /^[a-zAZ_-]+:.*?#SSS# / {printf "    \033[35m%-35s\033[0m %s\n", $$1, $$2}' $(MAKEFILE_LIST)
	@printf "\n"

print-sqs: #SQS# Show all queues
	@AWS_ACCESS_KEY_ID=test AWS_SECRET_ACCESS_KEY=test AWS_DEFAULT_REGION=us-east-1 aws --endpoint-url=http://localhost:4566 sqs list-queues --output table --no-cli-pager

print-sqs-msg: #SQS# Show messages in queue (up to 10)
ifeq ($(QUEUE_URL),)
	@$(eval QUEUE_URL := $(shell read -p "QUEUE_URL: " input; echo $$input))
endif
	@AWS_ACCESS_KEY_ID=test AWS_SECRET_ACCESS_KEY=test AWS_DEFAULT_REGION=us-east-1 aws --endpoint-url=http://localhost:4566 sqs receive-message --max-number-of-messages 10 --visibility-timeout 1 --query "Messages[*].MessageId" --queue-url "$(QUEUE_URL)" --output table --no-cli-pager

print-sqs-msg-body: #SQS# Show SQS message body
ifeq ($(QUEUE_URL),)
	@$(eval QUEUE_URL := $(shell read -p "QUEUE_URL: " input; echo $$input))
endif
ifeq ($(MSG_ID),)
	@$(eval MSG_ID := $(shell read -p "MESSAGE_ID: " input; echo $$input))
endif
	@AWS_ACCESS_KEY_ID=test AWS_SECRET_ACCESS_KEY=test AWS_DEFAULT_REGION=us-east-1 aws --endpoint-url=http://localhost:4566 sqs receive-message --max-number-of-messages 10 --visibility-timeout 1 --query "Messages[?MessageId=='$(MSG_ID)'].Body" --queue-url "$(QUEUE_URL)" --output table --no-cli-pager

print-sss: #SSS# Show all S3 buckets
	@AWS_ACCESS_KEY_ID=test AWS_SECRET_ACCESS_KEY=test AWS_DEFAULT_REGION=us-east-1 aws --endpoint-url=http://localhost:4566 s3api list-buckets --query 'Buckets[].Name' --output table

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
	@AWS_ACCESS_KEY_ID=test AWS_SECRET_ACCESS_KEY=test AWS_DEFAULT_REGION=us-east-1 aws --endpoint-url=http://localhost:4566 s3 cp $(SOURCE_FILE) s3://$(BUCKET)/$(KEY_OBJ)
